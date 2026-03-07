(ns core
  (:require
   [babashka.fs :refer [file]]
   [libpython-clj2.python :refer [initialize!]]))

(initialize!)

(def data-directory
  "data")

(def candidates-file
  (file data-directory "candidates.ednl"))
