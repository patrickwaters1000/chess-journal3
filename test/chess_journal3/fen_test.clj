(ns chess-journal3.fen-test
  (:require
    [chess-journal3.fen :as fen]
    [clojure.test :refer [is deftest]]))

;; Testing parsing
(let [{:keys [square->piece
              active-color
              white-can-castle-kingside
              white-can-castle-queenside
              black-can-castle-kingside
              black-can-castle-queenside
              en-passant-square
              halfmove-clock
              fullmove-counter]} (fen/parse fen/start)]
  [(= square->piece {"A1" "R" "B1" "N" "C1" "B" "D1" "Q" "E1" "K" "F1" "B" "G1" "N" "H1" "R"
                     "A2" "P" "B2" "P" "C2" "P" "D2" "P" "E2" "P" "F2" "P" "G2" "P" "H2" "P"
                     "A8" "r" "B8" "n" "C8" "b" "D8" "q" "E8" "k" "F8" "b" "G8" "n" "H8" "r"
                     "A7" "p" "B7" "p" "C7" "p" "D7" "p" "E7" "p" "F7" "p" "G7" "p" "H7" "p"})
   (= active-color "w")
   (= white-can-castle-kingside true)
   (= white-can-castle-queenside true)
   (= black-can-castle-kingside true)
   (= black-can-castle-queenside true)
   (= en-passant-square nil)
   (= halfmove-clock 0)
   (= fullmove-counter 1)])
