(ns chess-journal3.modes.edit
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.lines :as lines]
    [chess-journal3.utils :as u]))

(defn init [state]
  (-> state
      (assoc :mode "edit")
      (assoc :game->won nil)
      lines/load-fen->moves
      lines/load-lines
      u/reset-board))

(defn get-line-data [state]
  (let [{:keys [fens sans]} state
        tag (lines/get-moves-tag state)]
    (map (fn [[initial-fen final-fen] san]
           {:tag tag
            :initial-fen initial-fen
            :final-fen final-fen
            :san san})
         (partition 2 1 (:fens state))
         (:sans state))))

(defn line-ends-with-opponent-to-play? [state]
  (let [{:keys [fens color]} state
        active-color (-> fens last fen/parse :active-color)]
    (not= color active-color)))

(defn move-conflicts-with-reportoire?
  [state {:keys [initial-fen san]}]
  (let [;; There should be 0 or 1 reportoire moves.
        reportoire-moves (->> (lines/get-moves state initial-fen)
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
  [state]
  (let [{:keys [color]} state
        tag (lines/get-moves-tag state)]
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
          old-tag (lines/get-moves-tag state*)
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
