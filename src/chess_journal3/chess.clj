(ns chess-journal3.chess
  "Wraps `bhlangonijr.chesslib`, exposing functions for applying moves to a fen,
  and checking whether moves are legal. Use of `bhlangonijr.chesslib` is
  contained within this namespace, so that it would be easy to swap out that
  library if necessary."
  (:require
    [clojure.string :as string])
  (:import
    (com.github.bhlangonijr.chesslib Board Piece Square PieceType)
    (com.github.bhlangonijr.chesslib.move Move MoveList MoveGenerator)))

;;-----------------------------------------------------------------------------;;
;;-------------     Converting between representations of moves      ----------;;
;;-----------------------------------------------------------------------------;;

(defn- parse-promote-piece [^com.github.bhlangonijr.chesslib.move.Move m]
  (let [^Piece p (.getPromotion m)
        ^PieceType t (.getPieceType p)]
    (when t
      (condp = t
        PieceType/KNIGHT "N"
        PieceType/BISHOP "B"
        PieceType/ROOK "R"
        PieceType/QUEEN "Q"))))

(defn- ^Piece unparse-promote-piece [to-square promote-piece]
  (let [rank (Integer/parseInt (subs to-square 1 2))]
    (case rank
      8 (case promote-piece
          "N" Piece/WHITE_KNIGHT
          "B" Piece/WHITE_BISHOP
          "R" Piece/WHITE_ROOK
          "Q" Piece/WHITE_QUEEN)
      1 (case promote-piece
          "N" Piece/BLACK_KNIGHT
          "B" Piece/BLACK_BISHOP
          "R" Piece/BLACK_ROOK
          "Q" Piece/BLACK_QUEEN))))

(defn- object-to-san [fen ^com.github.bhlangonijr.chesslib.move.Move m]
  (let [move-list (MoveList. fen)]
    (.add move-list m)
    (first (.toSanArray move-list))))

(defn- san-to-object [fen san]
  (let [move-list (MoveList. fen)]
    (.loadFromSan move-list san)
    (first move-list)))

(defn- move-to-object [from to promote]
  (let [from-square (Square/valueOf (string/upper-case from))
        to-square (Square/valueOf (string/upper-case to))
        promote-piece (when promote
                        (unparse-promote-piece to promote))]
    (if promote
      (Move. from-square to-square promote-piece)
      (Move. from-square to-square))))

(defn get-san
  "Returns Simple Algebraic Notation for the move if the inputs are valid;
  otherwise returns `nil`."
  [fen from-square to-square & {:keys [promote-piece]}]
  (try (->> (move-to-object from-square to-square promote-piece)
            (object-to-san fen))
       (catch Exception e
         (println (.getMessage e)))))

;;-----------------------------------------------------------------------------;;
;;-------------                   Applying moves                     ----------;;
;;-----------------------------------------------------------------------------;;

(defn apply-san [fen san]
  (let [board (Board.)
        move-list (MoveList. fen)]
    (.loadFromFen board fen)
    (.loadFromSan move-list san)
    (.doMove board (first move-list))
    (.getFen board)))

;;-----------------------------------------------------------------------------;;
;;-------------                    Legal moves                       ----------;;
;;-----------------------------------------------------------------------------;;

;; NOTE The namespace deliberately doesn't use chess-journal3.move. Here we are
;; just exposing a Clojure API for the functionality we need from the chess
;; Java library. There is a wrapper for this in chess-journal3.move though.
;; TODO Return promote piece
(defn get-legal-move-sans [fen]
  (let [board (Board.)]
    (.loadFromFen board fen)
    (->> (MoveGenerator/generateLegalMoves board)
         (map #(object-to-san fen %)))))

(defn legal-move?
  ([fen san]
   (contains? (set (get-legal-move-sans fen)) san))
  ([fen from-square to-square promote-piece]
   (let [san (get-san fen from-square to-square :promote-piece promote-piece)]
     (if (nil? san)
       false
       (legal-move? fen san)))))
