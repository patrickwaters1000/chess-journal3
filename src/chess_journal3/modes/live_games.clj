(ns chess-journal3.modes.live-games
  "Implements a mode where the user plays full against the engine, which can be
  saved and resumed later."
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.modes.edit :as edit]
    [chess-journal3.modes.menu :as menu]
    [chess-journal3.utils :as u]
    [clj-time.core :as t]))

(defn- get-moves [db game-name]
  (loop [unsorted-moves (set (db/get-tagged-moves db game-name))
         sorted-moves []
         current-fen fen/initial]
    (if (empty? unsorted-moves)
      sorted-moves
      (let [next-move (->> unsorted-moves
                           (filter #(= current-fen (:initial-fen %)))
                           u/get-unique)]
        (recur (disj unsorted-moves next-move)
               (conj sorted-moves next-move)
               (:final-fen next-move))))))

(defn get-current-game-name [state]
  (let [{:keys [live-game-names
                live-game-idx]} state]
    (nth live-game-names live-game-idx)))

(defn- set-fens-and-sans [state]
  (let [game-name (get-current-game-name state)
        moves (get-moves (:db state) game-name)
        fens (->> moves (map :final-fen) (reduce conj [fen/initial]))
        sans (mapv :san moves)
        idx (dec (count fens))]
    (assoc state :fens fens :sans sans :idx idx :selected-square nil)))

(defn init [state]
  (let [game-names (db/list-live-games (:db state))]
    (if (empty? game-names)
      (menu/init state)
      (-> state
          (assoc :mode "live-games"
                 :live-game-names game-names
                 :live-game-idx 0)
          set-fens-and-sans))))

(defn cycle-game [state]
  (let [n (count (:live-game-names state))]
    (-> state
        (update :live-game-idx #(mod (inc %) n))
        set-fens-and-sans)))

(defn- cycle-to-game [state game-name]
  (if (= game-name (get-current-game-name state))
    state
    (recur (cycle-game state) game-name)))

(defn new [state game-name]
  (let [{:keys [db color]} state
        [white black] (case color
                        "w" ["user" "chess-journal3"]
                        "b" ["chess-journal3" "user"])
        date (subs (str (t/now)) 0 10)
        metadata {:white white
                  :black black
                  :date date}
        moves (edit/get-line-data state)]
    (db/insert-live-game! db metadata moves game-name)
    (-> state
        init
        (cycle-to-game game-name))))

(defn- count-matching-moves [line-1 line-2]
  (->> (map vector line-1 line-2)
       (take-while #(= (:san (first %))
                       (:san (second %))))
       count))

(defn save [state]
  (let [{:keys [db]} state
        game-name (get-current-game-name state)
        moves-old (get-moves db game-name)
        moves-new (edit/get-line-data state)
        num-matching-moves (count-matching-moves moves-old moves-new)
        moves-to-delete (->> moves-old
                             (drop num-matching-moves)
                             (map #(assoc %
                                     :old-tag (:tag %)
                                     :new-tag "deleted")))
        moves-to-insert (->> moves-new
                             (drop num-matching-moves)
                             (map #(assoc % :tag game-name)))]
    (when (seq moves-to-delete)
      (println "Deleting moves:\n" moves-to-delete)
      (db/update-move-tags! db moves-to-delete))
    (when (seq moves-to-insert)
      (println "Inserting moves:\n" moves-to-insert)
      (db/insert-tagged-moves! db moves-to-insert))
    state))

(defn end [state result]
  (db/complete-live-game (:db state)
                         (get-current-game-name state)
                         result)
  (init state))
