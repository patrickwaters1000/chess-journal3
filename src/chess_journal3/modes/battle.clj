(ns chess-journal3.modes.battle
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.engine :as engine]
    [chess-journal3.utils :as u]))

(defn set-opponent-must-move [state]
  (assoc state :opponent-must-move (u/opponents-move? state)))

(defn init [state]
  (-> state
      (assoc :mode "battle"
             :game->won nil)
      set-opponent-must-move))

(defn opponent-move [state]
  (let [{:keys [engine
                engine-movetime
                engine-elo]} state
        fen (u/get-fen state)
        move (engine/get-move engine
                              :fen fen
                              :movetime engine-movetime
                              :elo engine-elo)
        san (chess/move-to-san fen move)
        new-fen (chess/apply-move-san fen san)]
    (-> state
        (update :sans conj san)
        (update :fens conj new-fen)
        (update :idx inc)
        (assoc :opponent-must-move false))))

(defn try-move [state square]
  (let [state* (u/try-move state square)]
    (if (= state state*)
      state
      (set-opponent-must-move state*))))

(defn click-square [state square]
  (let [{:keys [selected-square]} state]
    (cond
      (= selected-square square)
        (assoc state :selected-square nil)
      (and selected-square
           (u/players-move? state))
        (try-move state square)
      (u/player-has-piece-on-square? state square)
        (assoc state :selected-square square)
      :else
        state)))
