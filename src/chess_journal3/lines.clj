(ns chess-journal3.lines
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.utils :as u]))

(defn get-next-move [state]
  (let [{:keys [review-lines idx]} state
        fen (u/get-fen state)
        san (:san (nth (first review-lines) (inc idx)))]
    (chess/san-to-move fen san)))

(defn matches-current-line [state line]
  (let [{:keys [idx]} state]
    (and (<= 0 idx)
         (< idx (count line))
         (= (u/get-fen state)
            (:final-fen (nth line idx))))))

(defn get-moves
  ([state] (get-moves state (u/get-fen state)))
  ([state fen] (get (:fen->moves state) fen)))

(defn get-moves-tag [state]
  (let [{:keys [mode color]} state]
    (if (= "games" mode)
      (case color
        "w" "white-games"
        "b" "black-games")
      (case color
        "w" "white-reportoire"
        "b" "black-reportoire"))))

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

(defn- get-trunk-of-subtree [state]
  (let [{:keys [sans fens locked-idx]} state]
    (->> (map (fn [san initial-fen]
                {:san san
                 :initial-fen initial-fen
                 :final-fen (chess/apply-move-san initial-fen san)})
              (take locked-idx sans)
              (take locked-idx fens))
         (cons {:final-fen fen/initial}))))

(defn- get-branches-of-subtree* [state initial-fen]
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

(defn- filter-fen->moves [fen->moves tag]
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
        locked-fen (u/get-locked-fen state)
        trunk (get-trunk-of-subtree state)
        branches (get-branches-of-subtree state locked-fen)
        lines (->> branches
                   (map (fn [branch]
                          (vec (concat (vec trunk)
                                       (vec (rest (vec branch)))))))
                   shuffle)]
    (println (format "Found %s lines." (count lines)))
    (assoc state :lines lines)))

(defn- cycle-to-first-matching-line [state]
  (loop [s state
         retries (count (:lines state))]
    (let [{:keys [idx fens lines]} s
          n (count fens)]
      (if (= fens (->> (first lines)
                       (map :final-fen)
                       (take n)))
        s
        (if (zero? retries)
          (throw (Exception. "Can't find a matching line."))
          (recur (update s :lines cycle)
                 (dec retries)))))))

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
        cycle-to-first-matching-line)))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      load-fen->moves
      load-lines
      u/reset-board))
