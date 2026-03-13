(ns cues-test
  (:require
   [clojure.test :refer [deftest is]]
   [cues :refer [clean]]))

(deftest test-clean
  (is (->> []
           clean
           count
           (= 1)))
  (is (= ["Hello." -1] (last (clean [["  Hello. This should be gone." -1]]))))
  (is (= 1 (count (clean [["!!" -1]]))))
  (is (= ["你好." -1] (last (clean [["你好." -1]])))))