(ns core
  (:require
   [babashka.fs :refer [create-dirs create-temp-file file move]]
   [clojure.data.priority-map :refer [priority-map-by]]
   [clojure.math :refer [log]]
   [clojure.string :refer [includes? join]]
   [com.rpl.specter :refer [AFTER-ELEM ALL BEGINNING FIRST setval transform]]
   [lambdaisland.edn-lines :as edn-lines]
   [libpython-clj2.python :refer [$a ->py-list from-import get-item initialize! py.. with]]))

(initialize!)

(from-import builtins slice)

(from-import torch cuda device inference_mode nn nonzero tensor)

(from-import transformers AutoModelForCausalLM AutoTokenizer)

(def config
  (if (System/getProperty "prod")
    {:model-name "Qwen/Qwen3-30B-A3B-Base"
     :exponent 2
     :batch-size 4}
    {:model-name "Qwen/Qwen3-0.6B-Base"
     :exponent 1
     :batch-size 2}))

(def model-name
  "Qwen/Qwen3-0.6B-Base")

(def tokenizer
  ($a AutoTokenizer from_pretrained (:model-name config)))

(def device*
  (device (if ($a cuda is_available)
            "cuda"
            "cpu")))

(defn allocate-device
  [x]
  ($a x to device*))

(def model
  (allocate-device ($a AutoModelForCausalLM from_pretrained (:model-name config))))

(def prompt
  "She's like, \"")

(def threshold
  (- (* (:exponent config) (log 10))))

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
  (comp allocate-device tensor ->py-list))

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
        (log_softmax (get-item (py.. (with [_ (inference_mode)]
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

(defn spit*
  [f content]
  (let [bar (create-temp-file)]
    (spit (file bar) content)
    (move bar f {:replace-existing true
                 :atomic-move true})))

(def guarantee-file
  (file data-directory "guarantee"))

(defn search-step
  [m]
  (spit* guarantee-file (if (empty? m)
                          threshold
                          (last (first m))))
  (when-not (empty? m)
    (->> m
         (take (:batch-size config))
         (map first)
         predict
         (mapcat expand-node (take (:batch-size config) m))
         (into (pop-n (:batch-size config) m))
         recur)))

(defn -main
  []
  (create-dirs data-directory)
  (search-step (priority-map-by > ($a tokenizer encode prompt) 0)))