(ns chess-journal3.core
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.engine :as engine]
    [chess-journal3.lines :as lines]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.modes.edit :as edit]
    [chess-journal3.modes.games :as games]
    [chess-journal3.modes.review :as review]
    [chess-journal3.modes.setup :as setup]
    [chess-journal3.utils :as u]))

(defn next-frame [state]
  (if (= "games" (:mode state))
    (games/next-frame state)
    (u/next-frame state)))

(defn next-line [state]
  (case (:mode state)
    "games" (games/next-line state)
    "review" (review/next-line state)))

(defn reset-board [state]
  (cond-> state
    true u/reset-board
    (= "review" (:mode state)) review/set-opponent-must-move))

(defn switch-color [state]
  (let [f (case (:mode state)
            ("edit"
             "setup") edit/switch-color
            "review" review/switch-color
            ("battle"
             "endgames"
             "live-games") battle/switch-color)]
    (f state)))

(defn click-square [state square]
  (let [f (case (:mode state)
            "review" review/click-square
            ("battle"
             "endgames"
             "live-games") battle/click-square
            "edit" edit/click-square
            "setup" setup/click-square
            identity)]
    (f state square)))

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
