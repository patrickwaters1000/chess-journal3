(ns chess-journal3.generate-endgame-positions
  "Generates critical endgame positions for a given combination of pieces."
  (:require
    [chess-journal3.fen :as fen]
    [chess-journal3.tablebase :as tablebase]))

(defn make-fen [square->piece active-color]
  (fen/unparse {:square->piece square->piece
                :active-color active-color
                :halfmove-clock 0
                :fullmove-counter 1}))

(defn evaluate [fen]
  (let [{:keys [dtz]} (tablebase/probe fen)]
    (cond
      (neg? dtz) :losing
      (zero? dtz) :drawn
      (pos? dtz) :winning)))

(defn reverse-evaluation [evaluation]
  (case evaluation
    :winning :losing
    :drawn :drawn
    :losing :winning))

(defn critical?
  "Returns true if evaluation of the position depends on who's turn it is."
  [square->piece]
  (let [[b-val w-val] (for [color ["b" "w"]]
                        (evaluate (make-fen square->piece color)))]
    (not= (reverse-evaluation b-val) w-val)))

(defn generate-piece-arrangements [])

(def fen-1 (make-fen {"E4" "P" "E5" "K" "E7" "k"} "b"))

(with-redefs [chess-journal3.utils/get-active-color (constantly "b")]
  (tablebase/interpret-result nil (tablebase/probe fen-1)))
