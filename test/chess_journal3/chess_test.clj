(ns chess-journal3.chess-test
  (:require
    [chess-journal3.chess :as chess]
    [clojure.test :refer [is deftest]])
  (:import
    (com.github.bhlangonijr.chesslib Square Piece)
    (com.github.bhlangonijr.chesslib.move Move)))

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
(def fen-after-1e4 "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")

(deftest parsing-promote-piece
  (is (= "N"
         (#'chess/parse-promote-piece (Move. (Square/valueOf "E7")
                                             (Square/valueOf "E8")
                                             Piece/WHITE_KNIGHT))))
  (is (nil? (#'chess/parse-promote-piece (Move. (Square/valueOf "E2")
                                                (Square/valueOf "E4"))))))

(deftest unparsing-promote-piece
  (is (= Piece/WHITE_KNIGHT
         (#'chess/unparse-promote-piece {:to "E8" :promote "N"}))))

(deftest converting-object-to-san
  (is (= "e4"
         (#'chess/object-to-san initial-fen
          (Move. (Square/valueOf "E2")
                 (Square/valueOf "E4"))))))

(deftest converting-san-to-object
  (is (= (Move. (Square/valueOf "E2")
                (Square/valueOf "E4"))
         (#'chess/san-to-object initial-fen "e4"))))

(deftest converting-object-to-move
  (is (= {:from "E2" :to "E4"}
         (#'chess/object-to-move (Move. (Square/valueOf "E2")
                                        (Square/valueOf "E4"))))))

(deftest converting-move-to-object
  (is (= (Move. (Square/valueOf "E2")
                (Square/valueOf "E4"))
         (#'chess/move-to-object {:from "E2" :to "E4"}))))

(deftest converting-move-to-san
  (is (= "e4"
         (chess/move-to-san initial-fen {:from "e2" :to "e4"}))))

(deftest converting-san-to-move
  (is (= {:from "E2" :to "E4"}
         (chess/san-to-move initial-fen "e4"))))

(deftest applying-move-san
  (is (= fen-after-1e4
         (chess/apply-move-san initial-fen "e4"))))

(deftest applying-move
  (is (= fen-after-1e4
         (chess/apply-move initial-fen
                           {:from "e2" :to "e4"}))))

(deftest getting-legal-move-sans
  (is (= ["a3" "a4" "b3" "b4" "c3" "c4" "d3" "d4"
          "e3" "e4" "f3" "f4" "g3" "g4" "h3" "h4"
          "Na3" "Nc3" "Nf3" "Nh3"]
         (chess/get-legal-move-sans initial-fen))))

(deftest checking-if-move-is-legal
  (is (chess/legal-move-san? initial-fen "e4")))
