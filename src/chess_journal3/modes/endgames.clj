(ns chess-journal3.modes.endgames
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.utils :as u]
    [clojure.string :as string]))

(defn- set-color [state]
  (assoc state :color (u/get-active-color state)))

(def white-pieces #{"K" "Q" "R" "B" "N" "P"})
(def piece-order {"K" 0 "Q" 1 "R" 2 "B" 3 "N" 4 "P" 5})

(defn get-material [fen]
  (let [position (fen/parse fen)
        square->piece (:square->piece position)
        pieces (vals square->piece)
        black-pieces-str (->> pieces
                              (remove #(contains? white-pieces %))
                              (map string/upper-case)
                              (sort-by piece-order)
                              (apply str))
        white-pieces-str (->> pieces
                              (filter #(contains? white-pieces %))
                              (sort-by piece-order)
                              (apply str))]
    (str white-pieces-str "v" black-pieces-str)))

(defn get-file-of-unique-pawn [fen]
  (let [square (->> fen
                    fen/parse
                    :square->piece
                    (filter #(contains? #{"p" "P"} (val %)))
                    u/get-unique
                    key)
        reflect-files #(case % "e" "d" "f" "c" "g" "b" "h" "a" %)]
    (-> square
        (subs 0 1)
        string/lower-case
        reflect-files)))

(defn get-rank-of-unique-pawn [fen]
  (try
    (let [square (->> fen
                      fen/parse
                      :square->piece
                      (filter #(contains? #{"p" "P"} (val %)))
                      u/get-unique
                      key)]
      (Integer/parseInt (subs square 1 2)))
    (catch Exception e
      (throw (Exception.
               (format "Can't get rank of unique pawn in %s" fen))))))

(def taxonomy
  [get-material {"KRPvKR" [get-rank-of-unique-pawn
                           {2 [get-file-of-unique-pawn {}]
                            3 [get-file-of-unique-pawn {}]
                            4 [get-file-of-unique-pawn {}]
                            5 [get-file-of-unique-pawn {}]
                            6 [get-file-of-unique-pawn {}]
                            7 [get-file-of-unique-pawn {}]}]}])

(defn- classify [fen]
  (loop [result []
         subtaxonomy taxonomy]
    (if (nil? subtaxonomy)
      result
      (let [[classifier subtree] subtaxonomy
            taxa (classifier fen)
            new-result (conj result [classifier taxa])
            new-subtaxonomy (->> subtree
                                 (filter #(= taxa (key %)))
                                 (map val)
                                 first)]
        (recur new-result
               new-subtaxonomy)))))

(defn- matches-class? [endgame-class fen]
  (reduce (fn [m [classifier taxa]]
            (if (= taxa (classifier fen))
              true
              (reduced false)))
          true
          endgame-class))

(defn- index-of [x xs]
  (->> xs
       (map-indexed vector)
       (filter #(= x (second %)))
       ffirst))

(defn- cycle-subclass [endgame-class delta endgames]
  (if (empty? endgame-class)
    endgame-class
    (let [branch (vec (drop-last endgame-class))
          [f v1] (last endgame-class)
          vs (->> endgames
                  (map :fen)
                  (filter #(matches-class? branch %))
                  (map f)
                  (into #{})
                  sort)
          i1 (index-of v1 vs)
          i2 (mod (+ i1 delta) (count vs))
          v2 (nth vs i2)]
      (conj branch [f v2]))))

(defn- get-endgames-in-current-class [state]
  (let [{:keys [endgame-class
                endgames]} state]
    (filter #(matches-class? endgame-class (:fen %))
            endgames)))

(defn count-endgames-in-current-class [state]
  (count (get-endgames-in-current-class state)))

(defn- get-current-endgame [state]
  (let [{:keys [endgame-idx]} state
        candidates (get-endgames-in-current-class state)]
    (nth candidates endgame-idx)))

(defn- coarsen-endgame-class-until-match [endgame-class fen]
  (loop [endgame-class endgame-class]
    (if (matches-class? endgame-class fen)
      endgame-class
      (recur (drop-last endgame-class)))))

(defn- current-fen-in-endgames? [state]
  (let [fens (->> (:endgames state)
                  (map :fen)
                  (into #{}))
        fen (u/get-fen state)]
    (contains? fens fen)))

(defn- cycle-to-matching-fen [state]
  (let [fen (u/get-fen state)
        fens (->> (get-endgames-in-current-class state)
                  (map :fen))
        idx (index-of fen fens)]
    (assoc state :endgame-idx idx)))

(defn- cycle-to-matching-fen-if-possible [state]
  (if-not (current-fen-in-endgames? state)
    state
    (let [fen (u/get-fen state)
          endgame-class (-> (:endgame-class state)
                            (coarsen-endgame-class-until-match fen))]
      (-> state
          (assoc :endgame-class endgame-class)
          cycle-to-matching-fen))))

(defn- reset-board [state]
  (let [endgame (get-current-endgame state)
        fen (:fen endgame)]
    (assoc state
      :fens [fen]
      :sans []
      :idx 0
      :selected-square nil)))

(defn init [state]
  (let [{:keys [db]} state]
    (-> state
        (assoc :mode "endgames"
               :endgames (db/get-endgames db))
        cycle-to-matching-fen-if-possible
        reset-board)))

(defn save [state]
  (let [{:keys [db]} state
        fen (u/get-fen state)]
    (db/insert-endgame! db {:fen fen
                            :comment ""
                            :objective ""})
    state))

(defn delete [state]
  (let [fen (u/get-fen state)]
    (db/delete-endgame! (:db state) fen)
    (init state)))

(defn cycle-class [state level]
  (let [{:keys [endgames
                endgame-class]} state
        new-endgame-class (-> endgame-class
                              (subvec 0 (inc level))
                              (cycle-subclass 1 endgames))]
    (-> state
        (assoc :endgame-class new-endgame-class
               :endgame-idx 0
               :show-endgame-evaluation false)
        reset-board)))

(defn refine-class [state]
  (let [fen (:fen (get-current-endgame state))
        level (-> state :endgame-class count inc)
        endgame-class (-> fen classify (subvec 0 level))
        endgame-idx (->> (:endgames state)
                         (map :fen)
                         (filter #(matches-class? endgame-class %))
                         (index-of fen))]
    (assoc state
      :endgame-class endgame-class
      :endgame-idx endgame-idx)))

(defn- cycle-position [state delta]
  (let [n (count-endgames-in-current-class state)]
    (-> state
        (update :endgame-idx #(mod (+ % delta) n))
        reset-board)))

(defn next [state] (cycle-position state 1))
(defn previous [state] (cycle-position state -1))

(defn get-button-text-formatter [classifier]
  (let [c (class classifier)]
    (cond
      (= c chess_journal3.modes.endgames$get_material) "%s"
      (= c chess_journal3.modes.endgames$get_rank_of_unique_pawn) "%sth rank"
      (= c chess_journal3.modes.endgames$get_file_of_unique_pawn) "%s-pawn")))

(defn get-button-configs [state]
  (let [{:keys [endgame-class
                endgame-idx]} state
        cycle-class-buttons (map-indexed
                              (fn [i [classifier taxa]]
                                (let [template (get-button-text-formatter classifier)
                                      text (format template taxa)]
                                  {:text text
                                   :route "cycle-endgame-class"
                                   :level i}))
                              endgame-class)
        num-endgames (count-endgames-in-current-class state)
        refine-class-text (format "%d / %d" (inc endgame-idx) num-endgames)
        refine-class-button {:text refine-class-text
                             :route "refine-endgame-class"}]
    (conj (vec cycle-class-buttons) refine-class-button)))

(defn force-move [state san]
  (let [fen (u/get-fen state)
        move (chess/san-to-move fen san)]
    (if (u/legal-move? state move)
      (-> state
          (u/do-move move)
          battle/set-opponent-must-move)
      state)))

(defn toggle-evaluation [state]
  (update state :show-endgame-evaluation not))
