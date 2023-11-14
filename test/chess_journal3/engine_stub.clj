(ns chess-journal3.engine-stub
  "Exposes a fake engine to use in unit tests. The fake engine always plays the
  first legal move in the position, according to dictionary ordering of the move
  sans."
  (:require
    [chess-journal3.engine]
    [chess-journal3.move :as move])
  (:import
    (chess_journal3.engine IEngine)
    (java.io Closeable)))

(declare get-move)

(defrecord Engine []
  Closeable
  (close [_]
    nil)
  IEngine
  (getMove [_ fen _ _]
    (get-move fen)))

(def engine (Engine.))

(defn get-move [fen]
  (->> (move/get-legal-moves nil fen)
       (sort-by :san)
       first))
