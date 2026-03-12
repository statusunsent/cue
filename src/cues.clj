(ns cues
  (:require
   [babashka.fs :refer [file]]
   [charred.api :refer [write-csv]]
   [com.rpl.specter :refer [BEFORE-ELEM setval]]
   [core :refer [candidates-file data-directory]]
   [lambdaisland.edn-lines :as edn-lines]))

(defn load-candidates
  []
  (edn-lines/slurp candidates-file))

(def cues-file
  (file data-directory "cues.csv"))

(defn -main
  []
  (write-csv cues-file (setval BEFORE-ELEM ["sentence" "likelihood"] (load-candidates))
             :close-writer? true))