(ns chess-journal3.core
  (:require
    [chess-journal.constants :refer [initial-fen username]]
    [chess-journal3.chess :as chess]
    [chess-journal3.db :as db]
    [chess-journal3.engine :as engine]
    [chess-journal3.fen :as fen]
    [clojure.string :as string])
  (:import
    (java.util Random)))

(def rng (Random.))

;; Modes: edit, review, battle

;; TODO Add stuff to this function and check whether the state is invalid before
;; responding.
(defn invalid-state?
  "If the state is valid, returns nil. Otherwise returns a list of reasons the
  state is invalid."
  [state]
  (let [{:keys [color]} state]
    (remove nil?
            [(when-not (contains? #{"w" "b"} color)
               (format "Invalid color: %s." color))])))

(defn other-color [color]
  (case color "w" "b" "b" "w"))

(defn get-fen [state]
  (let [{:keys [fens idx]} state]
    (nth fens idx)))

(defn- get-locked-fen [state]
  (let [{:keys [fens locked-idx]} state]
    (nth fens locked-idx)))

(defn- get-moves-tag [state]
  (let [{:keys [mode color]} state]
    (if (= "games" mode)
      (case color
        "w" "white-games"
        "b" "black-games")
      (case color
        "w" "white-reportoire"
        "b" "black-reportoire"))))

(defn get-next-move-of-line [state]
  (let [{:keys [review-lines idx]} state
        fen (get-fen state)
        san (:san (nth (first review-lines) (inc idx)))]
    (chess/san-to-move fen san)))

(defn- players-move? [state]
  (let [{:keys [color]} state
        active-color (-> state get-fen fen/parse :active-color)]
    (= active-color color)))

(defn- opponents-move? [state]
  (not (players-move? state)))

;; Expects review line to start from initial position.
(defn end-of-review-line? [state]
  (let [{:keys [idx review-lines]} state]
    (= idx (dec (count (first review-lines))))))

(defn- get-moves
  ([state] (get-moves state (get-fen state)))
  ([state fen] (get (:fen->moves state) fen)))

(defn load-fen->moves [state]
  (println "Loading fen->moves...")
  (let [{:keys [db mode]} state
        tag (get-moves-tag state)
        data (db/get-tagged-moves db
                                  tag
                                  :include-children (= "games" mode))
        fen->moves (group-by :initial-fen data)]
    (println (format "Found %s moves" (count data)))
    (assoc state :fen->moves fen->moves)))

(defn get-trunk-of-subtree [state]
  (let [{:keys [sans fens locked-idx]} state]
    (->> (map (fn [san initial-fen]
                {:san san
                 :initial-fen initial-fen
                 :final-fen (chess/apply-move-san initial-fen san)})
              (take locked-idx sans)
              (take locked-idx fens))
         (cons {:final-fen fen/initial}))))

(defn get-branches-of-subtree* [state initial-fen]
  (let [lines (atom [])]
    (loop [visited-nodes #{}
           stack [[{:final-fen initial-fen}]]]
      (if (empty? stack)
        @lines
        (let [line (last stack)
              fen (:final-fen (last line))
              moves (->> (get-moves state fen)
                         (remove visited-nodes))
              new-visited-nodes (into visited-nodes moves)
              new-stack (concat (drop-last stack)
                                (for [m moves]
                                  (conj line m)))]
          (when (empty? moves)
            (swap! lines conj line))
          (recur new-visited-nodes
                 new-stack))))))

(defn filter-fen->moves [fen->moves tag]
  (->> fen->moves
       (map (fn [[fen moves]]
              (let [matching-moves (filter #(= tag (:tag %))
                                           moves)]
                [fen matching-moves])))
       (remove (fn [[fen moves]]
                 (empty? moves)))
       (into {})))

(defn get-branches-of-subtree [state initial-fen]
  (let [tags (->> (:fen->moves state)
                  (mapcat val)
                  (map :tag)
                  (into #{})
                  sort)]
    (vec (mapcat (fn [tag]
                   (let [state* (update state
                                  :fen->moves
                                  filter-fen->moves
                                  tag)]
                     (get-branches-of-subtree* state* initial-fen)))
                 tags))))

(defn load-lines [state]
  (println "Loading lines...")
  (let [tag (get-moves-tag state)
        locked-fen (get-locked-fen state)
        trunk (get-trunk-of-subtree state)
        branches (get-branches-of-subtree state locked-fen)
        lines (->> branches
                   (map (fn [branch]
                          (vec (concat (vec trunk)
                                       (vec (rest (vec branch)))))))
                   shuffle)]
    (println (format "Found %s lines." (count lines)))
    (assoc state :review-lines lines)))

(defn opponent-must-move? [state]
  (let [{:keys [mode]} state]
    (or (and (= "battle" mode)
             (opponents-move? state))
        (and (= "review" mode)
             (opponents-move? state)
             (not (end-of-review-line? state))))))

(defn move [state move]
  (let [{:keys [idx fens mode sans]} state
        fen (get-fen state)
        san (chess/move-to-san fen move)
        new-fen (chess/apply-move fen move)
        new-fens (conj (subvec fens 0 (inc idx)) new-fen)
        new-sans (conj (subvec sans 0 idx) san)
        new-state (-> state
                      (assoc :fens new-fens
                             :sans new-sans
                             :selected-square nil)
                      (update :idx inc))
        opponent-must-move (opponent-must-move? new-state)]
    (assoc new-state :opponent-must-move opponent-must-move)))

(defn next-frame [state]
  (let [{:keys [idx fens mode]} state]
    (if (< idx (dec (count fens)))
      (update state :idx inc)
      (if (= "games" mode)
        (move state (get-next-move-of-line state))
        state))))

(defn previous-frame [state]
  (let [{:keys [idx]} state]
    (if (pos? idx)
      (update state :idx dec)
      state)))

(defn move-to-square-is-legal? [state square]
  (let [{:keys [selected-square]} state
        fen (get-fen state)]
    ;; TODO Handle promotions
    (chess/legal-move? fen {:from selected-square :to square})))

;; TODO Use `get-next-move-of-line`.
;; Expects review lines to start from initial position.
(defn move-to-square-is-correct? [state square]
  (and (move-to-square-is-legal? state square)
       (let [{:keys [selected-square review-lines idx]} state
             fen (get-fen state)
             san (chess/move-to-san fen {:from selected-square
                                         :to square})
             correct-san (:san (nth (first review-lines) (inc idx)))]
         (= correct-san san))))

(defn can-move? [state]
  (or (= "edit" (:mode state))
      (players-move? state)))

(defn get-line-data [state]
  (let [{:keys [fens sans]} state
        tag (get-moves-tag state)]
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
        reportoire-moves (->> (get-moves state initial-fen)
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
        load-fen->moves
        load-lines)))

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

(defn reset-board [state]
  (let [{:keys [locked-idx fens sans color mode]} state
        state* (-> state
                   (assoc :idx locked-idx
                          :fens (vec (take (inc locked-idx) fens))
                          :sans (vec (take locked-idx sans))
                          :selected-square nil))
        opponent-must-move (and (= "review" mode)
                                (opponents-move? state*))]
    (assoc state* :opponent-must-move opponent-must-move)))

;; TODO update for battle mode
(defn switch-color [state]
  (-> state
      (update :color other-color)
      load-fen->moves
      load-lines
      reset-board))

(defn player-has-piece-on-square? [state square]
  (let [{:keys [color]} state
        {:keys [square->piece]} (fen/parse (get-fen state))
        piece (get square->piece square)
        is-white-piece (when piece
                         (= piece (string/upper-case piece)))]
    (and piece
         (or (and (= "w" color) is-white-piece)
             (and (= "b" color) (not is-white-piece))))))

(defn opponent-has-piece-on-square? [state square]
  (player-has-piece-on-square? (update state :color other-color)
                               square))

(defn click-square [state square]
  (let [{:keys [selected-square mode]} state]
    (cond
      (or (= selected-square square)
          (and selected-square
               (= "review" mode)
               (not (move-to-square-is-correct? state square))))
        (assoc state :selected-square nil)
      (and selected-square
           (can-move? state)
           (case mode
             ("edit" "battle") (move-to-square-is-legal? state square)
             "review" (move-to-square-is-correct? state square)))
        (move state {:from selected-square :to square})
      (or (player-has-piece-on-square? state square)
          (and (= "edit" mode)
               opponent-has-piece-on-square? state square))
        (assoc state :selected-square square)
      :else
        state)))

(defn game-score [game-metadata]
  (let [{:keys [black white result]} game-metadata]
    (cond
      (= username white) (case result
                           "1-0" 1.0
                           "1/2-1/2" 0.5
                           "0-1" 0.0)
      (= username black) (case result
                           "1-0" 0.0
                           "1/2-1/2" 0.5
                           "0-1" 1.0))))

(defn set-game->won [state]
  (let [game->won (->> (db/get-games (:db state))
                       (map (fn [{:keys [tag]
                                  :as game-metadata}]
                              [tag (game-score game-metadata)]))
                       (into {}))]
    (assoc state :game->won game->won)))

(defn init-edit-mode [state]
  (-> state
      (assoc :mode "edit")
      (assoc :game->won nil)
      load-fen->moves
      load-lines
      reset-board))

(defn init-review-mode [state]
  (-> state
      (assoc :mode "review")
      (assoc :game->won nil)
      load-fen->moves
      load-lines
      reset-board))

(defn init-battle-mode [state]
  (-> state
      (assoc :mode "battle")
      (assoc :game->won nil)))

(defn init-games-mode [state]
  (-> state
      (assoc :mode "games")
      set-game->won
      load-fen->moves
      load-lines
      reset-board))

(defn switch-mode [state new-mode]
  (println (format "Switching to %s mode" new-mode))
  (case new-mode
    "edit" (init-edit-mode state)
    "review" (init-review-mode state)
    "battle" (init-battle-mode state)
    "games" (init-games-mode state)))

(defn switch-lock [state]
  (let [{:keys [idx locked-idx]} state
        new-locked-idx (if (pos? locked-idx) 0 idx)]
    (-> state
        (assoc :locked-idx new-locked-idx)
        load-lines)))

;; TODO Factor this through `move`.
(defn opponent-move-review-mode [state]
  (let [{:keys [idx review-lines]} state
        {san :san
         fen :final-fen} (nth (first review-lines)
                              (inc idx))]
    (-> state
        (update :sans conj san)
        (update :fens conj fen)
        (update :idx inc)
        (assoc :opponent-must-move false))))

(defn opponent-move-battle-mode [state]
  (let [fen (get-fen state)
        move (engine/get-move fen)
        san (chess/move-to-san fen move)
        new-fen (chess/apply-move-san fen san)]
    (-> state
        (update :sans conj san)
        (update :fens conj new-fen)
        (update :idx inc)
        (assoc :opponent-must-move false))))

(defn opponent-move [state]
  (case (:mode state)
    "review" (opponent-move-review-mode state)
    "battle" (opponent-move-battle-mode state)))

(defn cycle [xs]
  (conj (vec (rest xs)) (first xs)))

(defn next-line [state]
  (-> state
      (update :review-lines cycle)
      reset-board))

(defn matches-current-line [state line]
  (let [{:keys [idx]} state]
    (and (<= 0 idx)
         (< idx (count line))
         (= (get-fen state)
            (:final-fen (nth line idx))))))

(defn get-score [state moves]
  (when (= "games" (:mode state))
    (let [{:keys [game->won]} state
          scores (->> moves
                      (map :tag)
                      (map #(get game->won %)))
          n (count scores)
          s (reduce + 0.0 scores)]
      (/ s n))))

(defn get-alternative-moves [state]
  (let [{:keys [review-lines mode idx]} state
        offset (case mode
                 "review" 0
                 "games" 1
                 "edit" 1)]
    (->> review-lines
         (filter (partial matches-current-line
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
                 :score (get-score state moves)})))))

(defn get-games [state]
  {:pre [(= "games" (:mode state))]}
  (->> (:review-lines state)
       (filter (partial matches-current-line state))
       (map #(nth % (inc (:idx state))))
       (map (fn [{:keys [tag san]}]
              (let [[white black date time] (string/split tag #"__")
                    opponent (case (:color state)
                               "w" black
                               "b" white)]
                {:tag tag
                 :san san
                 :opponent opponent
                 :date date
                 :time time})))))

(defn undo-last-move [state]
  (let [{:keys [fens sans]} state
        new-fens (vec (drop-last fens))
        new-sans (vec (drop-last sans))
        new-idx (dec (count new-fens))]
    (assoc state
      :fens new-fens
      :sans new-sans
      :idx new-idx)))

(defn cycle-to-first-matching-line [state]
  (loop [s state
         retries (count (:review-lines state))]
    (let [{:keys [idx fens review-lines]} s
          n (count fens)]
      (if (= fens (->> (first review-lines)
                       (map :final-fen)
                       (take n)))
        s
        (if (zero? retries)
          (throw (Exception. "Can't find a matching line."))
          (recur (update s :review-lines cycle)
                 (dec retries)))))))

(defn alternative-move [state san]
  (let [{:keys [mode]} state
        state* (if (= "review" mode)
                 (undo-last-move state)
                 state)
        fen (get-fen state*)
        m (chess/san-to-move fen san)]
    (-> state*
        (move m)
        (assoc :opponent-must-move false)
        cycle-to-first-matching-line)))

(defn delete-subtree! [state]
  (if (or (zero? (:idx state))
          (and (= 1 (:idx state))
               (= "b" (:color state))))
    (assoc state :error "Deleting the entire reportoire isn't allowed.")
    (let [{:keys [db]} state
          ;; This next part is tricky; draw a move tree diagram to understand it.
          state* (if (opponents-move? state)
                   (undo-last-move state)
                   state)
          initial-fen (get-fen (update state* :idx dec))
          final-fen (get-fen state*)
          old-tag (get-moves-tag state*)
          new-tag (case old-tag
                    "white-reportoire" "deleted-white-reportoire"
                    "black-reportoire" "deleted-black-reportoire")]
      (->> (get-branches-of-subtree state* initial-fen)
           (map rest)
           (filter #(= final-fen (:final-fen (first %))))
           (apply concat)
           (map #(assoc % :old-tag old-tag :new-tag new-tag))
           (db/update-move-tags! db))
      (undo-last-move state*))))

(defn give-up [state]
  (let [{:keys [review-lines idx]} state
        fen (get-fen state)
        correct-san (:san (nth (first review-lines) (inc idx)))
        correct-move (chess/san-to-move fen correct-san)]
    (move state correct-move)))

(defn reboot-engine! [state]
  (engine/reboot!)
  state)

(defn cycle-to-game [state tag]
  (loop [retries (count (:review-lines state))
         state* state]
    (if (= tag (:tag (second (first (:review-lines state*)))))
      state*
      (do (assert pos? retries)
          (recur (dec retries)
                 (update state* :review-lines cycle))))))
