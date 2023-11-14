(ns chess-journal3.modes.menu
  (:require
    [chess-journal3.constants :as c])
  (:import
    (chess_journal3.state GlobalState LocalState)))

(defrecord Menu
  [color
   db]
  LocalState
  (getMode [_] "menu")
  (getFen [_] (c/initial-fen))
  (nextFrame [state] state)
  (prevFrame [state] state)
  (switchColor [state] state)
  (clickSquare [state square] state)
  (cleanUp [state] state)
  (makeClientView [state]
    {:mode (.getMode state)
     :fen c/initial-fen
     :sans []
     :idx 0
     :isLocked false
     :pgn ""
     :color color
     :activeColor "w"}))

(defn init [^GlobalState s]
  (map->Menu {:db (:db s)
              :color "w"}))
