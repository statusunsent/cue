(ns cues-test
  (:require
   [clojure.test :refer [deftest is]]
   [cues :refer [clean]]))

(deftest test-clean
  (is (->> []
           clean
           count
           (= 1)))
  (is (->> [["  Hello. This should be gone." -1]]
           clean
           last
           (= ["Hello." -1])))
  (is (->> [["!!" -1]]
           clean
           count
           (= 1)))
  (is (->> [["你好." -1]]
           clean
           last
           (= ["你好." -1])))
  (is (->> [["Hello." -1] ["hello." -2]]
           clean
           rest
           (= [["Hello." -1]])))
  (is (->> [["STRASSE." -1] ["STRAßE." -2]]
           clean
           rest
           (= [["STRASSE." -1]])))
  (is (->> [["Hello!" -1] ["Hello?" -2]]
           clean
           rest
           (= [["Hello!" -1]])))
  (is (->> [["α." -1] ["β." -2]]
           clean
           rest
           (= [["α." -1] ["β." -2]]))))