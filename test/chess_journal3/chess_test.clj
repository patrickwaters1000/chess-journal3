(ns chess-journal3.chess-test
  (:require
    [chess-journal3.chess :as chess]
    [clojure.test :refer [is deftest]]))

(deftest applying-move-san
  (is (= "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
         (chess/apply-move-san "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                               "e4"))))

(deftest applying-move
  (is (= "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
         (chess/apply-move "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                           {:from "e2"
                            :to "e4"}))))

(deftest changing-san-to-move
  (is (= {:from "E2"
          :to "E4"}
         (chess/san-to-move "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                            "e4"))))
