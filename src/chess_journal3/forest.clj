(ns chess-journal3.forest)

(defrecord Forest
  [trees
   tags
   idx])

(defn cycle-to-matching-tree [fen])

(defn- find-valid-tag [tag->fen->moves fen]
  (->> tag->fen->moves
       (filter (fn [tag fen->moves]
                 (let [fens (set (keys fen->moves))]
                   (contains? fens fen))))
       (map key)
       first))
