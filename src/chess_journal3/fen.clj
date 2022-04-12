(ns chess-journal3.fen
  (:require
    [clojure.string :as string]))

(def regex #"([\S]+) (w|b) ([KQkq]+) ([\-\w\d]+) (\d+) (\d+)")

(defn parse [fen]
  (let [m (re-matches regex fen)]
    (if-not m
      (throw (Exception. (format "Invalid fen %s" fen)))
      (let [[_
             _
             active-color
             castling-str
             en-passant-square
             halfmove-clock
             fullmove-counter] m
            castling (into #{} (string/split castling-str #""))]
        {:fen fen
         :active-color active-color
         :white-can-castle-kingside (contains? castling "K")
         :white-can-castle-queenside (contains? castling "Q")
         :black-can-castle-kingside (contains? castling "k")
         :black-can-castle-queenside (contains? castling "q")
         :en-passant-square en-passant-square
         :halfmove-clock (Integer/parseInt halfmove-clock)
         :fullmove-counter (Integer/parseInt fullmove-counter)}))))
