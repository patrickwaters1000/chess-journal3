(ns chess-journal3.modes.review
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.lines :as lines]
    [chess-journal3.utils :as u]))

(defn init [state]
  (-> state
      (assoc :mode "review")
      (assoc :game->won nil)
      lines/load-fen->moves
      lines/load-lines
      u/reset-board))

;; Expects review line to start from initial position.
(defn end-of-review-line? [state]
  (let [{:keys [idx lines]} state]
    (= idx (dec (count (first lines))))))

;; TODO Factor this through `move`.
(defn opponent-move [state]
  (let [{:keys [idx lines]} state
        {san :san
         fen :final-fen} (nth (first lines)
                              (inc idx))]
    (-> state
        (update :sans conj san)
        (update :fens conj fen)
        (update :idx inc)
        (assoc :opponent-must-move false))))

;; TODO Use `get-next-move-of-line`.
;; Expects review lines to start from initial position.
(defn move-to-square-is-correct? [state square]
  (let [{:keys [selected-square lines idx]} state
        fen (u/get-fen state)
        san (chess/move-to-san fen {:from selected-square
                                    :to square})
        correct-san (:san (nth (first lines) (inc idx)))]
    (= correct-san san)))

(defn click-square [state square]
  (let [{:keys [selected-square]} state]
    (cond
      (or (= selected-square square)
          (and selected-square
               (not (move-to-square-is-correct? state square))))
        (assoc state :selected-square nil)
      (and selected-square
           (u/players-move? state)
           (move-to-square-is-correct? state square))
        (u/try-move state square)
      (u/player-has-piece-on-square? state square)
        (assoc state :selected-square square)
      :else
        state)))

(defn give-up [state]
  (let [{:keys [lines idx]} state
        fen (u/get-fen state)
        correct-san (:san (nth (first lines) (inc idx)))
        correct-move (chess/san-to-move fen correct-san)]
    (u/do-move state correct-move)))

(defn next-line [state]
  (-> state
      (update :lines u/cycle)
      u/reset-board))

(defn set-opponent-must-move [state]
  (assoc state
    :opponent-must-move
      (and (u/opponents-move? state)
           (not (end-of-review-line? state)))))
