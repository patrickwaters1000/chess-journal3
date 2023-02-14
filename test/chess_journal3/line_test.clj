(ns chess-journal3.line-test
  (:require
    [chess-journal3.line :as line]
    [chess-journal3.move :as move]
    [clojure.test :refer [is deftest]])
  (:import
    (chess_journal3.line Line)
    (chess_journal3.move Move)))

(deftest converting-between-lines-and-moves
  (let [moves [(move/new {:from "a" :to "b" :san "ab"})
               (move/new {:from "b" :to "c" :san "bc"})]
        line (line/from-moves moves)]
    (is (= (line/map->Line {:fens ["a" "b" "c"]
                            :sans ["ab" "bc"]
                            :idx 0}) line))
    (is (= moves (line/to-moves line)))))
