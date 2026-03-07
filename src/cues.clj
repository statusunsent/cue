(ns cues
  (:require [core :refer [candidates-file]]
            [lambdaisland.edn-lines :as edn-lines]))

(def candidates
  (edn-lines/slurp candidates-file))