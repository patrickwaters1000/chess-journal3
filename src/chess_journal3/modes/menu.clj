(ns chess-journal3.modes.menu
  (:require
    [chess-journal3.constants :as c])
  (:import
    (chess_journal3.utils IState)))

(defrecord Menu
  [color
   db]
  IState
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

(defn init [state]
  (map->Menu {:db (:db state)
              :color (:color state)}))
