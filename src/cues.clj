(ns cues
  (:require [core :refer [candidates-file]]
            [lambdaisland.edn-lines :as edn-lines]
            [libpython-clj2.python :refer [$a ->py-list from-import]]))

(def candidates
  (edn-lines/slurp candidates-file))

(from-import sentence_transformers SentenceTransformer)

(from-import torch cuda device inference_mode nn nonzero tensor)

(def device*
  (device (if ($a cuda is_available)
            "cuda"
            "cpu")))

(def model
  (SentenceTransformer "Qwen/Qwen3-Embedding-0.6B" :device device*))