(ns chess-journal3.lines-tree
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.line Line)))

(defrecord LinesTree
  [tag->fen->moves
   lines
   idx])

(defn- load-tag->fen->moves [db move-tags]
  (->> (db/get-tagged-moves db move-tags)
       (group-by :tag)
       (map (fn [[tag rows]]
              [tag (group-by :initial-fen rows)]))
       (into {})))

(defn- get-branches-of-subtree* [fen->moves initial-fen]
  (let [lines (atom [])]
    (loop [visited-nodes #{}
           stack [[{:final-fen initial-fen}]]]
      (if (empty? stack)
        @lines
        (let [line (last stack)
              fen (:final-fen (last line))
              moves (->> (get fen->moves fen)
                         (remove visited-nodes))
              new-visited-nodes (into visited-nodes moves)
              new-stack (concat (drop-last stack)
                                (for [m moves]
                                  (conj line m)))]
          (when (empty? moves)
            (swap! lines conj line))
          (recur new-visited-nodes
                 new-stack))))))

(defn get-branches-of-subtree
  "Handles the possibility that fen->moves contains moves with multiple tags."
  [^LinesTree lt initial-fen]
  (let [{:keys [tag->fen->moves]} lt]
    (vec (mapcat (fn [fen->moves]
                   (get-branches-of-subtree* fen->moves initial-fen))
                 (vals tag->fen->moves)))))

(defn- get-continuations [^LinesTree lt ^Line l]
  (let [{:keys [tag->fen->moves]} lt
        trunk (lines/to-move-maps l)
        focused-fen (:final-fen (last trunk))
        branches (get-branches-of-subtree lt focused-fen)]
    (->> branches
         (map (fn [branch]
                (vec (concat (vec trunk)
                             (vec (rest (vec branch)))))))
         shuffle)))

(defn cycle-to-first-matching-line [state]
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

(defn make [db move-tags]
  (map->LinesTree {:tag->fen->moves (load-tag->fen->moves db move-tags)}
                  :lines (get-con)
                  :idx nil))

(defn load-line)
