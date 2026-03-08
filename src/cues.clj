(ns cues
  (:require [core :refer [candidates-file]]
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

(defn -main
  []
  (let [candidates (load-candidates)
        embeddings ($a model encode (->py-list (map first candidates)))]
    (vals (group-by (zipmap candidates (last (connected_components ($a ($a model similarity embeddings embeddings) ge threshold)))) candidates))))