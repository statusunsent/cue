(ns candidates
  (:require
   [babashka.fs :refer [create-dirs create-temp-file file move]]
   [clojure.data.priority-map :refer [priority-map-by]]
   [clojure.math :refer [log]]
   [clojure.string :refer [includes? join]]
   [com.rpl.specter :refer [AFTER-ELEM ALL BEGINNING FIRST setval transform]]
   [core :refer [candidates-file data-directory]]
   [lambdaisland.edn-lines :as edn-lines]
   [libpython-clj2.python :refer [$a ->py-list from-import get-item
                                  initialize! py.. with]]))

(initialize!)

(from-import builtins slice)

(from-import torch cuda device inference_mode nn nonzero tensor)

(from-import transformers AutoModelForCausalLM AutoTokenizer)

(def config
  (if (System/getProperty "prod")
    {:model-name "Qwen/Qwen3.5-35B-A3B-Base"
     :exponent 8
;; Batch size is the smallest power‑of‑two value that consistently achieves 100% GPU utilization on the NVIDIA H100 80GB HBM3 GPU.
     :batch-size 512}
    {:model-name "Qwen/Qwen3.5-0.8B-Base"
     :exponent 1
     :batch-size 2}))

(def tokenizer
  ($a AutoTokenizer from_pretrained (:model-name config)))

(def device*
  (device (if ($a cuda is_available)
            "cuda"
            "cpu")))

(def model
  ($a AutoModelForCausalLM from_pretrained (:model-name config) :device_map device*))

(def prompt
  "She's like, \"")

(def prompt-tokens
  ($a tokenizer encode prompt))

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
  (comp #($a % to device*) tensor ->py-list))

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
                                           (model (prepare-batch-tensor token-sequences)
                                                  (prepare-mask-tensor token-sequences)
                                                  :use_cache false
                                                  :logits_to_keep 1))
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

(defn expand-node*
  [prefix-sequence prefix-likelihood predictions surviving-tokens]
  (map (fn [token likelihood]
         [(setval AFTER-ELEM token prefix-sequence) (+ prefix-likelihood likelihood)])
       surviving-tokens
       (py.. (get-item predictions (->py-list surviving-tokens)) tolist)))

(def max-tokens
  100)

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
    (if (->> prefix-sequence
             count
             inc
             (= max-tokens))
      []
      (expand-node* prefix-sequence prefix-likelihood predictions (remove stop-tokens surviving-tokens)))))

(defn spit*
  [f content]
  (let [temp-file-path (create-temp-file)]
    (spit (file temp-file-path) content)
    (move temp-file-path f {:replace-existing true
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
         (map (comp (partial concat prompt-tokens) first))
         predict
         (mapcat expand-node (take (:batch-size config) m))
         (into (pop-n (:batch-size config) m))
         recur)))

(defn -main
  []
  (create-dirs data-directory)
  (search-step (priority-map-by > [] 0)))