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

(defn- parse-promote-piece [^Move m]
  (let [^Piece p (.getPromotion m)
        ^PieceType t (.getPieceType p)]
    (when t
      (condp = t
        PieceType/KNIGHT "N"
        PieceType/BISHOP "B"
        PieceType/ROOK "R"
        PieceType/QUEEN "Q"))))

(defn- ^Piece unparse-promote-piece [move]
  (let [{:keys [to promote]} move
        rank (Integer/parseInt (subs to 1 2))]
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

(defn- object-to-san [fen ^Move m]
  (let [move-list (MoveList. fen)]
    (.add move-list m)
    (first (.toSanArray move-list))))

(defn- san-to-object [fen san]
  (let [move-list (MoveList. fen)]
    (.loadFromSan move-list san)
    (first move-list)))

(defn- object-to-move [^Move m]
  (let [^Piece p (.getPromotion m)
        promote-piece (parse-promote-piece m)]
    (cond-> {:from (str (.getFrom m))
             :to (str (.getTo m))}
      promote-piece (assoc :promote promote-piece))))

(defn- move-to-object [move]
  (let [{:keys [to
                from
                promote]} move
        from-square (Square/valueOf (string/upper-case from))
        to-square (Square/valueOf (string/upper-case to))
        promotePiece (when promote
                       (unparse-promote-piece move))]
    (if promote
      (Move. from-square to-square promotePiece)
      (Move. from-square to-square))))

(defn move-to-san [fen move]
  (->> (move-to-object move)
       (object-to-san fen)))

(defn san-to-move [fen san]
  (object-to-move
    (san-to-object fen san)))

;;-----------------------------------------------------------------------------;;
;;-------------                   Applying moves                     ----------;;
;;-----------------------------------------------------------------------------;;

(defn apply-move-san [fen san]
  (let [board (Board.)
        move-list (MoveList. fen)]
    (.loadFromFen board fen)
    (.loadFromSan move-list san)
    (.doMove board (first move-list))
    (.getFen board)))

(defn apply-move [fen move]
  (let [san (move-to-san fen move)]
    (apply-move-san fen san)))

;;-----------------------------------------------------------------------------;;
;;-------------               Legal moves / checking                 ----------;;
;;-----------------------------------------------------------------------------;;

(defn get-legal-move-sans [fen]
  (let [board (Board.)]
    (.loadFromFen board fen)
    (->> (MoveGenerator/generateLegalMoves board)
         (map (partial object-to-san fen)))))

(defn legal-move-san? [fen san]
  (contains? (set (get-legal-move-sans fen)) san))
