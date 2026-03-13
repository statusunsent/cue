(ns cues
  (:require
   [babashka.fs :refer [file]]
   [charred.api :refer [write-csv]]
   [clojure.string :refer [triml]]
   [com.rpl.specter :refer [ALL BEFORE-ELEM FIRST setval* transform*]]
   [core :refer [candidates-file data-directory]]
   [lambdaisland.edn-lines :as edn-lines]))

(defn load-candidates
  []
  (edn-lines/slurp candidates-file))

(def cues-file
  (file data-directory "cues.csv"))

(def truncate
  (comp (partial re-find #"[^!.?]*[!.?]") triml))

(def clean
  (comp (partial setval* BEFORE-ELEM ["sentence" "likelihood"])
        (partial transform* [ALL FIRST] truncate)
        (partial filter (comp (partial re-find #"(?U)\p{Alnum}") first))))

(defn -main
  []
  (write-csv cues-file (clean (load-candidates)) :close-writer? true))