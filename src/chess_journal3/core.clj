(ns chess-journal3.core
  (:require
    [chess-journal3.db :as db]
    ;;[chess-journal3.engine :as engine]
    ;;[chess-journal3.modes.battle :as battle]
    [chess-journal3.modes.openings.editor :as openings-editor]
    ;;[chess-journal3.modes.games :as games]
    [chess-journal3.modes.openings.review :as openings-review]
    ;;[chess-journal3.modes.setup :as setup]
    [chess-journal3.utils :as u]))

(defn reset-board [state]
  (cond-> state
    true u/reset-board
    (= "review" (:mode state)) review/set-opponent-must-move))

(defn switch-lock [state]
  (let [{:keys [idx locked-idx]} state
        new-locked-idx (if (pos? locked-idx) 0 idx)]
    (-> state
        (assoc :locked-idx new-locked-idx)
        lines/load-lines)))

(defn opponent-move [state]
  (case (:mode state)
    "review" (review/opponent-move state)
    ("battle"
     "endgames"
     "live-games") (battle/opponent-move state)))

;; NOTE We set the Elo before requesting each move.
(defn set-elo [state elo]
  (assoc state :engine-elo elo))

(defn reboot-engine! [state]
  (spit engine/log-file "")
  (when-let [e (:engine state)]
    (.close e))
  (assoc state :engine (engine/make)))

(defn get-alternative-moves [state]
  (let [{:keys [lines mode idx]} state
        offset (case mode
                 "review" 0
                 "games" 1
                 "edit" 1)]
    (->> lines
         (filter (partial u/matches-current-line
                          ;; Terrible
                          (cond-> state
                            (= mode "review") (update :idx dec))))
         ;; In edit mode, when out of book, this filter prevents an error.
         (filter #(< (+ idx offset) (count %)))
         (map #(nth % (+ idx offset)))
         (group-by :san)
         (map (fn [[san moves]]
                {:san san
                 :lines (count moves)
                 :score (when (= "games" mode)
                          (games/get-score state moves))})))))

(defn cycle-promote-piece [state]
  (update state
    :promote-piece
    {"N" "B"
     "B" "R"
     "R" "Q"
     "Q" "N"}))

(defn alternative-move [state san]
  (let [{:keys [mode]} state
        state* (if (= "review" mode)
                 (u/undo-last-move state)
                 state)
        fen (u/get-fen state*)
        m (chess/san-to-move fen san)]
    (-> state*
        (u/do-move m)
        (assoc :opponent-must-move false)
        lines/cycle-to-first-matching-line)))
