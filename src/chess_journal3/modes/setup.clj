(ns chess-journal3.modes.setup
  (:require
    [chess-journal3.fen :as fen]
    [chess-journal3.utils :as u]))

(defn init [state]
  (assoc state
    :mode "setup"
    :fens [fen/empty]
    :lines []
    :sans []
    :idx 0
    :selected-square nil
    :opponent-must-move false
    :game->score nil
    :setup-selected-piece "K"
    :fen->moves {}
    :color "w"))

(defn- delete-piece-on-square [state square]
  (println "Deleting piece")
  (let [new-fen (-> (u/get-fen state)
                    fen/parse
                    (update :square->piece dissoc square)
                    fen/unparse)]
    (assoc state :fens [new-fen])))

(defn- place-piece-on-square [state square]
  (println "Placing piece " (:setup-selected-piece state))
  (let [piece (:setup-selected-piece state)
        new-fen (-> (u/get-fen state)
                    fen/parse
                    (update :square->piece assoc square piece)
                    fen/unparse)]
    (assoc state :fens [new-fen])))

(defn click-square [state square]
  (if (nil? (u/get-piece-on-square state square))
    (place-piece-on-square state square)
    (delete-piece-on-square state square)))

(defn set-selected-piece [state piece]
  (assoc state :setup-selected-piece piece))

(defn switch-active-color [state]
  (let [new-fen (-> (u/get-fen state)
                    fen/parse
                    (update :active-color u/other-color)
                    fen/unparse)]
    (assoc state :fens [new-fen])))
