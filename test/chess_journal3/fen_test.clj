(ns chess-journal3.fen-test
  (:require [clojure.test :refer [is deftest]]
            [chess-journal3.fen :as fen]))

(deftest parsing
  (is (= {:fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
          :active-color "b"
          :white-can-castle-kingside true
          :white-can-castle-queenside true
          :black-can-castle-kingside true
          :black-can-castle-queenside true
          :en-passant-square "e3"
          :halfmove-clock 0
          :fullmove-counter 1})))

