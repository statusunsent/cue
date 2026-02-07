(ns core
  (:require
   [clojure.data.priority-map :refer [priority-map-by]]
   [clojure.java.io :refer [file make-parents]]
   [clojure.math :refer [log]]
   [clojure.string :refer [includes? join]]
   [com.rpl.specter :refer [AFTER-ELEM ALL BEGINNING FIRST setval transform]]
   [libpython-clj2.python :refer [$a ->py-list from-import get-item initialize! py.. with]]))

(initialize!)

(from-import builtins slice)

(from-import torch nn no_grad nonzero tensor)

(from-import transformers AutoModelForCausalLM AutoTokenizer)

(def model-name
  "Qwen/Qwen3-0.6B-Base")

(def tokenizer
  ($a AutoTokenizer from_pretrained model-name))

(def model
  ($a AutoModelForCausalLM from_pretrained model-name))

(def prompt
  "She's like, \"")

(def exponent
  1)

(def threshold
  (- (* exponent (log 10))))

(defn pop-n
  [n coll]
  (if (or (zero? n) (empty? coll))
    coll
    (recur (dec n) (pop coll))))

(def pad-token-id
  (py.. tokenizer -pad_token_id))

(def max-count
  (comp (partial apply max)
        (partial map count)))

(def tensor*
  (comp tensor ->py-list))

(defn prepare-batch-tensor
  [token-sequences]
  (let [target (max-count token-sequences)]
    (tensor* (map #(setval BEGINNING (repeat (- target (count %)) pad-token-id) %) token-sequences))))

(defn prepare-mask-tensor
  [token-sequences]
  (let [target (max-count token-sequences)]
    (->> token-sequences
         (setval [ALL ALL] 1)
         (map #(setval BEGINNING (repeat (- target (count %)) 0) %))
         tensor*)))

(defn predict
  [token-sequences]
  (py.. nn
        -functional
        (log_softmax (get-item (py.. (with [_ (no_grad)]
                                           (model (prepare-batch-tensor token-sequences) (prepare-mask-tensor token-sequences)))
                                     -logits)
                               [(slice nil) -1 (slice nil)]))))

(defn decode*
  [x]
  ($a tokenizer decode x))

(def vocab
  (->> (py.. tokenizer -vocab_size)
       inc
       range
       (map (juxt decode* identity))))

(defn stop?
  [s]
  (or (includes? s ".")
      (includes? s "?")
      (includes? s "!")))

(defn fragment?
  [s]
  (and (not (stop? s))
       (includes? s "\"")))

(def stop-tokens
  (set (map last (filter (comp stop? first) vocab))))

(def fragment-tokens
  (set (map last (filter (comp fragment? first) vocab))))

(def batch-size
  2)

(def data-directory
  "data")

(def candidates-file
  (file data-directory "candidates.ednl"))

(defn expand-node*
  [prefix-sequence prefix-likelihood predictions surviving-tokens]
  (map (fn [token likelihood]
         [(setval AFTER-ELEM token prefix-sequence) (+ prefix-likelihood likelihood)])
       surviving-tokens
       (py.. (get-item predictions (->py-list surviving-tokens)) tolist)))

(defn expand-node
  [[prefix-sequence prefix-likelihood] predictions]
  (let [surviving-tokens (remove fragment-tokens (-> predictions
                                                     ($a ge (- threshold prefix-likelihood))
                                                     nonzero
                                                     (py.. flatten)
                                                     (py.. tolist)))]
    (edn-lines/spit candidates-file
                    (->> surviving-tokens
                         (filter stop-tokens)
                         (expand-node* prefix-sequence prefix-likelihood predictions)
                         (transform [ALL FIRST]
                                    (comp join
                                          (partial map decode*))))
                    {:append? true})
    (expand-node* prefix-sequence prefix-likelihood predictions (remove stop-tokens surviving-tokens))))

(defn search-step
  [m]
  (when-not (empty? m)
    (->> m
         (take batch-size)
         (map first)
         predict
         (mapcat expand-node (take batch-size m))
         (into (pop-n batch-size m))
         recur)))

(defn -main
  []
  (make-parents data-directory)
  (search-step (priority-map-by > ($a tokenizer encode prompt) 0)))