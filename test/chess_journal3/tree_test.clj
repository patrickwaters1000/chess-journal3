(ns chess-journal3.tree-test
  (:require
    [chess-journal3.line :as line]
    [chess-journal3.move :as move]
    [chess-journal3.tree :as tree]
    [clojure.test :refer [is deftest]])
  (:import
    (chess_journal3.move Move)))

(def ab (move/new {:from "a" :to "b" :san "ab"}))
(def ac (move/new {:from "a" :to "c" :san "ac"}))
(def bd (move/new {:from "b" :to "d" :san "bd"}))
(def de (move/new {:from "d" :to "e" :san "de"}))
(def df (move/new {:from "d" :to "f" :san "df"}))
(def dg (move/new {:from "d" :to "g" :san "dg"}))
(def gh (move/new {:from "g" :to "h" :san "gh"}))

(def fen->moves
  {"a" [ab ac]
   "b" [bd]
   "d" [de df dg]
   "g" [gh]})

(deftest counting-lines
  (is (= {"a" 4 "b" 3 "c" 1 "d" 3 "e" 1 "f" 1 "g" 1 "h" 1}
         (#'tree/count-lines fen->moves "a")))
  (is (= {"d" 3 "e" 1 "f" 1 "g" 1 "h" 1}
         (#'tree/count-lines fen->moves "d")))
  (is (= {"e" 1}
         (#'tree/count-lines fen->moves "e")))
  (is (thrown-with-msg? Exception
                        #"Cycle detected"
                        (#'tree/count-lines
                         (assoc fen->moves "c" [(move/new {:from "c" :to "g" :san "cg"})])
                         "a"))))

(deftest iterating-lines-1
  (let [t0 (tree/new [ab ac bd de df dg gh]
                     (line/from-moves [ab bd]))
        t1 (tree/next-line t0)
        t2 (tree/next-line t1)
        t3 (tree/next-line t2)]
    (is (= (line/fens (:line t0)) ["a" "b" "d" "e"]))
    (is (= (line/fens (:line t1)) ["a" "b" "d" "f"]))
    (is (= (line/fens (:line t2)) ["a" "b" "d" "g" "h"]))
    (is (= t3 t0))))

(deftest iterating-lines-2
  (let [t0 (tree/new [ab ac bd de df dg gh]
                     (line/stub "a"))
        t1 (tree/next-line t0)
        t2 (tree/next-line t1)
        t3 (tree/next-line t2)
        t4 (tree/next-line t3)]
    (is (= (line/fens (:line t0)) ["a" "b" "d" "e"]))
    (is (= (line/fens (:line t1)) ["a" "b" "d" "f"]))
    (is (= (line/fens (:line t2)) ["a" "b" "d" "g" "h"]))
    (is (= (line/fens (:line t3)) ["a" "c"]))
    (is (= t4 t0))))
