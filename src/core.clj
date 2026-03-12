(ns core
  (:require
   [babashka.fs :refer [file]]))

(def data-directory
  "data")

(def candidates-file
  (file data-directory "candidates.ednl"))
