(ns core
  (:require
   [babashka.fs :refer [file]]
   [libpython-clj2.python :refer [$a from-import initialize!]]))

(initialize!)

(from-import torch cuda device)

(def device*
  (device (if ($a cuda is_available)
            "cuda"
            "cpu")))

(def data-directory
  "data")

(def candidates-file
  (file data-directory "candidates.ednl"))
