(ns cues
  (:require
   [babashka.fs :refer [file]]
   [charred.api :refer [write-csv]]
   [com.rpl.specter :refer [BEFORE-ELEM setval]]
   [core :refer [candidates-file data-directory]]
   [lambdaisland.edn-lines :as edn-lines]
   [libpython-clj2.python :refer [$a ->py-list from-import]]))

(from-import sentence_transformers SentenceTransformer)

(from-import scipy.sparse.csgraph connected_components)

(from-import torch cuda device inference_mode nn nonzero tensor)

(def device*
  (device (if ($a cuda is_available)
            "cuda"
            "cpu")))

(def model
  (SentenceTransformer "Qwen/Qwen3-Embedding-0.6B" :device device*))

(defn load-candidates
  []
  (edn-lines/slurp candidates-file))

(def threshold
  0.9)

(def prompt
  "Instruct: Retrieve semantically similar text\nQuery:")

(defn collapse
  [candidates]
  (let [embeddings ($a model encode (->py-list (map first candidates)) :prompt prompt)]
    (->> candidates
         (group-by (->> ($a ($a model similarity embeddings embeddings) ge threshold)
                        connected_components
                        last
                        (zipmap candidates)))
         vals
         (map (partial apply max-key last)))))

(def cues-file
  (file data-directory "cues.csv"))

(defn -main
  []
  (write-csv cues-file (setval BEFORE-ELEM ["sentence" "likelihood"] (collapse (load-candidates)))
             :close-writer? true))