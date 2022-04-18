(ns chess-journal3.fen
  (:require
    [clojure.string :as string]))

(def initial "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

(def regex #"([\S]+) (w|b) ([\-KQkq]+) ([\-\w\d]+) (\d+) (\d+)")
"rnbq1rk1/pp2nppp/2pb4/3p4/2PP4/2NB1N2/PP3PPP/R1BQ1RK1 b - - 5 8"
(def pieces #{"p" "n" "b" "r" "q" "k"
              "P" "N" "B" "R" "Q" "K"})

(def files ["A" "B" "C" "D" "E" "F" "G" "H"])

(defn parse-rank [rank-str]
  (reduce (fn [acc v]
            (if (contains? pieces v)
              (conj acc v)
              (apply conj acc (repeat (Integer/parseInt v) nil))))
          []
          (string/split rank-str #"")))

(defn get-square [rank file]
  (format "%s%s" (nth files file) (- 8 rank)))

(defn parse-board
  "We ought to be able to use the chess library for this. I looked around a
  little in `bhlangonijr.chesslib`, and there doesn't seem to be a public
  function for querying what is on a particular square."
  [board-str]
  (let [board-matrix (mapv parse-rank
                           (string/split board-str #"/"))]
    (into {}
          (for [;; The ranks go in decreasing order.
                r* (range 8)
                c (range 8)
                :let [piece (get-in board-matrix [r* c])
                      square (get-square r* c)]
                :when piece]
            [square piece]))))

(defn parse [fen]
  (let [m (re-matches regex fen)]
    (if-not m
      (throw (Exception. (format "Invalid fen %s" fen)))
      (let [[_
             board-str
             active-color
             castling-str
             en-passant-square
             halfmove-clock
             fullmove-counter] m
            castling (into #{} (string/split castling-str #""))]
        {:square->piece (parse-board board-str)
         :active-color active-color
         :white-can-castle-kingside (contains? castling "K")
         :white-can-castle-queenside (contains? castling "Q")
         :black-can-castle-kingside (contains? castling "k")
         :black-can-castle-queenside (contains? castling "q")
         :en-passant-square (when-not (= "-" en-passant-square)
                              en-passant-square)
         :halfmove-clock (Integer/parseInt halfmove-clock)
         :fullmove-counter (Integer/parseInt fullmove-counter)}))))
