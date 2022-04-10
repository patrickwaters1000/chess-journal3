(ns chess-journal3.chess
  (:require [clojure.string :as string])
  (:import [com.github.bhlangonijr.chesslib Board Piece]
           [com.github.bhlangonijr.chesslib.move MoveList Move]))

(defn apply-move-san [fen san]
  (let [board (Board.)
        move-list (MoveList. fen)]
    (.loadFromFen board fen)
    (.loadFromSan move-list san)
    (.doMove board (.removeFirst move-list))
    (.getFen board)))


(defn get-promote-piece [to-square promote]
  (let [rank (Integer/parseInt (subs to-square 1 2))]
    (case rank
      8 (case promote
          "N" Piece/WHITE_KNIGHT
          "B" Piece/WHITE_BISHOP
          "R" Piece/WHITE_ROOK
          "Q" Piece/WHITE_QUEEN)
      1 (case promote
          "N" Piece/BLACK_KNIGHT
          "B" Piece/BLACK_BISHOP
          "R" Piece/BLACK_ROOK
          "Q" Piece/BLACK_QUEEN))))

(defn move-to-san [fen move]
  (let [{:keys [to
                from
                promote]} move
        board (Board.)
        from-square (Square/valueOf (string/upper-case from))
        to-square (Square/valueOf (string/upper-case to))
        promotePiece (when promote
                       (get-promote-piece to promote))
        move (if promote
               (Move. from-square to-square promotePiece)
               (Move. from-square to-square))]
    (.loadFromFen board fen)
    (MoveList/encodeToSan board move)))

(defn apply-move [fen move]
  (let [san (move-to-san fen move)]
    (apply-move-san fen san)))

(defn san-to-move [fen san]
  (let [board (Board.)
        move-list (MoveList. fen)]
    (.loadFromFen board fen)
    (.loadFromSan move-list san)
    {:from (str (.getFrom (first move-list)))
     :to (str (.getTo (first move-list)))}))
