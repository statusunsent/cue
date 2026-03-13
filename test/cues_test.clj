(ns cues-test
  (:require
   [clojure.test :refer [deftest is]]
   [cues :refer [clean]]))

(deftest clean-empty
  (is (->> []
           clean
           count
           (= 1))))