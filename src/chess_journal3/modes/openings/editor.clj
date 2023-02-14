(ns chess-journal3.modes.openings.edit
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.line :as line]
    [chess-journal3.modes.openings.review :as review]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.tree Tree)))

(defrecord OpeningsEditor
  [mode
   tree
   color
   selected-square
   promote-piece
   db])

(defn init [state]
  (-> state
      (assoc :mode "edit")
      (assoc :game->won nil)
      lines/load-fen->moves
      lines/load-lines
      u/reset-board))

(defn line-ends-with-opponent-to-play? [line color]
  (let [active-color (-> line line/final-fen fen/active-color)]
    (not= color active-color)))

(defn move-conflicts-with-reportoire?
  [tree move]
  (let [;; There should be 0 or 1 reportoire moves.
        reportoire-moves (->> (tree/get-moves tree)
                              (map :san)
                              (into #{}))]
    (and (seq reportoire-moves)
         (not (contains? reportoire-moves san)))))

(defn line-conflicts-with-reportoire?
  "Currently within each reportoire 'tag', there must be at most one move
  selected for the player for each position. This makes it easier to generate a
  list of review lines starting from a position. To add secondary moves, you
  must add them under a separate reportoire tag, e.g.,
  'white-reportoire-2nd-choices'.

  If there is no conflict, returns `nil`; otherwise returns a map identifying
  where the conflict starts."
  [tree color line]
  (let [{:keys [color]} state
        tag (get-moves-tag state)]
    (->> (get-line-data state)
         (filter #(= color (-> (:initial-fen %)
                               fen/parse
                               :active-color)))
         (map (fn [n m] (assoc m
                          :fullmove-counter n
                          :active-color color))
              (map inc (range)))
         (filter (partial move-conflicts-with-reportoire? state))
         (map #(select-keys % [:fullmove-counter
                               :active-color
                               :san]))
         first)))

(defn add-line!* [state]
  (let [{:keys [db]} state
        line-data (get-line-data state)]
    (db/insert-tagged-moves! db line-data)
    (-> state
        lines/load-fen->moves
        lines/load-lines)))

(defn add-line! [state]
  (let [{:keys [san
                fullmove-counter
                active-color]
         :as conflict} (line-conflicts-with-reportoire? state)]
    (cond
      (not (line-ends-with-opponent-to-play? state))
        (assoc state
          :error (format "Line must end with %s play"
                         (case (:color state) "b" "white" "w" "black")))
      (some? conflict)
        (assoc state
          :error (format "Line conflicts at %s. %s%s"
                         fullmove-counter
                         (if (= "color" "b") ".. " "")
                         san))
      :else (add-line!* state))))

(defn delete-subtree! [state]
  (if (or (zero? (:idx state))
          (and (= 1 (:idx state))
               (= "b" (:color state))))
    (assoc state :error "Deleting the entire reportoire isn't allowed.")
    (let [{:keys [db]} state
          ;; This next part is tricky; draw a move tree diagram to understand it.
          state* (if (u/opponents-move? state)
                   (u/undo-last-move state)
                   state)
          initial-fen (u/get-fen (update state* :idx dec))
          final-fen (u/get-fen state*)
          old-tag (get-moves-tag state*)
          new-tag (case old-tag
                    "white-reportoire" "deleted-white-reportoire"
                    "black-reportoire" "deleted-black-reportoire")]
      (->> (lines/get-branches-of-subtree state* initial-fen)
           (map rest)
           (filter #(= final-fen (:final-fen (first %))))
           (apply concat)
           (map #(assoc % :old-tag old-tag :new-tag new-tag))
           (db/update-move-tags! db))
      (u/undo-last-move state*))))

(defn click-square [state square]
  (let [{:keys [selected-square]} state]
    (cond
      ;; TODO Check that that player has the move
      (and (not= selected-square square)
           (or (u/player-has-piece-on-square? state square)
               (u/opponent-has-piece-on-square? state square)))
        (assoc state :selected-square square)
      (= selected-square square)
        (assoc state :selected-square nil)
      (and selected-square)
        (u/try-move state square)
      :else
        state)))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      lines/load-fen->moves
      lines/load-lines
      u/reset-board))
