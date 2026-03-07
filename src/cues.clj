(ns cues
  (:require [core :refer [candidates-file]]
            [lambdaisland.edn-lines :as edn-lines]
            [libpython-clj2.python :refer [from-import]]))

(def candidates
  (edn-lines/slurp candidates-file))

(from-import sentence_transformers SentenceTransformer)

(def model
  (SentenceTransformer "Qwen/Qwen3-Embedding-0.6B"))