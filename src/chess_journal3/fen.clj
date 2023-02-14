(ns chess-journal3.fen
  (:require
    [clojure.string :as string]))

(def empty "8/8/8/8/8/8/8/8 w - - 0 1")
(def initial "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

(def regex #"([\S]+) (w|b) ([\-KQkq]+) ([\-\w\d]+) (\d+) (\d+)")

;;"rnbq1rk1/pp2nppp/2pb4/3p4/2PP4/2NB1N2/PP3PPP/R1BQ1RK1 b - - 5 8"

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

(defn- unparse-rank [pieces-on-rank]
  (let [encode (fn [blanks]
                 (if (pos? blanks)
                   (str blanks)
                   ""))
        {:keys [acc
                blanks]} (reduce (fn [{:keys [acc
                                              blanks]}
                                      piece]
                                   (if piece
                                     {:acc (str acc (encode blanks) piece)
                                      :blanks 0}
                                     {:acc acc
                                      :blanks (inc blanks)}))
                                 {:acc ""
                                  :blanks 0}
                                 pieces-on-rank)]
    (str acc (encode blanks))))

(defn- unparse-board [square->piece]
  (->> (for [rank (range 8)]
         (for [file (range 8)
               :let [square (get-square rank file)]]
           (get square->piece square)))
       (map unparse-rank)
       (string/join "/")))

;;"rnbq1rk1/pp2nppp/2pb4/3p4/2PP4/2NB1N2/PP3PPP/R1BQ1RK1 b - - 5 8"

(defn- get-castling-str [p]
  (let [m (cond-> ""
            (:white-can-castle-kingside p) (str "K")
            (:white-can-castle-queenside p) (str "Q")
            (:black-can-castle-kingside p) (str "k")
            (:black-can-castle-queenside p) (str "q"))]
    (if (empty? m) "-" m)))

(defn unparse [p]
  (string/join " "
               [(unparse-board (:square->piece p))
                (:active-color p)
                (get-castling-str p)
                (get p :enpassant-square "-")
                (:halfmove-clock p)
                (:fullmove-counter p)]))

(defn get-active-color [fen]
  (-> fen parse :active-color))

(defn players-move? [fen color]
  (= color (get-active-color fen)))

(defn opponents-move? [fen color]
  (not= color (get-active-color fen)))

(defn piece-on-square [fen square]
  (-> fen parse :square->piece (get square)))

(defn color-of-piece [piece]
  (if (= piece (string/upper-case piece))
    "w"
    "b"))
