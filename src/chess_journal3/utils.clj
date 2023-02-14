(ns chess-journal3.utils
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.fen :as fen]
    [clojure.string :as string]))

(defn- map-vals [f m]
  (reduce-kv (fn [acc k v]
               (assoc acc k (f v)))
             {}
             m))

(defn index-of-first [f xs]
  (->> xs
       (map-indexed vector)
       (filter (comp f second))
       (map first)
       first))

(defn get-unique [xs]
  (when-not (= 1 (count xs))
    (throw (Exception. "Failed")))
  (first xs))

(defn cycle [xs]
  (conj (vec (rest xs)) (first xs)))

(defn other-color [color]
  (case color "w" "b" "b" "w"))

(defn get-locked-fen [state]
  (let [{:keys [fens locked-idx]} state]
    (nth fens locked-idx)))

(defn player-has-piece-on-square? [color fen square]
  (let [piece (fen/piece-on-square fen square)]
    (and piece
         (= color (fen/color-of-piece piece)))))

(defn opponent-has-piece-on-square? [color fen square]
  (player-has-piece-on-square? (other-color color) fen square))

(defn get-move [fen from-square to-square promote-piece]
  (let [piece (fen/piece-on-square fen from-square)
        to-rank (Integer/parseInt (subs to-square 1 2))
        promotion (or (and (= "p" piece) (= 1 to-rank))
                      (and (= "P" piece) (= 8 to-rank)))]
    (cond-> {:from from-square
             :to to-square}
      promotion (assoc :promote promote-piece))))

(defn legal-move? [state move]
  (chess/legal-move? (get-fen state) move))

(defn do-move [state move]
  (let [{:keys [idx fens mode sans promote-piece]} state
        fen (get-fen state)
        san (chess/move-to-san fen move)
        _ (println "Move: " san)
        new-fen (chess/apply-move fen move)
        new-fens (conj (subvec fens 0 (inc idx)) new-fen)
        new-sans (conj (subvec sans 0 idx) san)]
    (-> state
        (assoc :fens new-fens
               :sans new-sans
               :selected-square nil)
        (update :idx inc))))

(defn try-move [state square]
  (let [move (get-move state square)]
    (cond-> state
      (legal-move? state move)
        (do-move move))))

;; TODO Rename. In the case that the starting position is not the initial
;; position, this function name is confusing.
(defn reset-board [state]
  (let [{:keys [locked-idx fens sans]} state]
    (assoc state
      :idx locked-idx
      :fens (vec (take (inc locked-idx) fens))
      :sans (vec (take locked-idx sans))
      :selected-square nil)))

(defn undo-last-move [state]
  (let [{:keys [fens sans]} state
        new-fens (vec (drop-last fens))
        new-sans (vec (drop-last sans))
        new-idx (dec (count new-fens))]
    (assoc state
      :fens new-fens
      :sans new-sans
      :idx new-idx)))

(defn matches-current-line [state line]
  (let [{:keys [idx]} state]
    (and (<= 0 idx)
         (< idx (count line))
         (= (get-fen state)
            (:final-fen (nth line idx))))))
