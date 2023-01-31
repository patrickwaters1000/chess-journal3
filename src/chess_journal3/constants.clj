(ns chess-journal3.constants
  (:require
    [clj-time.core :as t]
    [clojure.java.io :as io]))

(def username
  (-> "username.txt"
      io/resource
      io/reader
      line-seq
      first))

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

(def initial-state
  {:engine-elo nil
   :engine-movetime (t/seconds 5)
   :color "w"
   :fen->moves {}
   :lines []
   :mode "menu"
   :locked-idx 0
   :fens [initial-fen]
   :sans []
   :idx 0
   :selected-square nil
   :opponent-must-move false
   :game->score nil
   :setup-selected-piece nil
   :promote-piece "Q"
   :endgames nil
   :endgame-class []
   :endgame-idx 0
   :show-endgame-evaluation false
   :live-game-names nil
   :live-game-idx nil})
