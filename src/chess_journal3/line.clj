(ns chess-journal3.line
  (:require
    [chess-journal3.constants :as c]
    [chess-journal3.move :as move])
  (:import
    (chess_journal3.move Move)))

(defrecord Line
  [fens
   sans
   idx])

(defn length [^Line l]
  (count (:sans l)))

(defn fens [l] (:fens l))

(defn fen
  ([^Line l]
   (fen l (:idx l)))
  ([^Line l idx]
   (nth (fens l) idx)))

(defn initial-fen [^Line l]
  (first (fens l)))

(defn final-fen [^Line l]
  (last (fens l)))

(defn penultimate-fen [^Line l]
  {:pre [(pos? (length l))]}
  (last (drop-last (fens l))))

(defn append [^Line l ^Move m]
  {:pre [(= (final-fen l)
            (move/from m))]}
  (-> l
      (update :fens conj (move/to m))
      (update :sans conj (move/san m))))

(defn delete-last [^Line l]
  {:pre [(pos? (length l))]}
  (-> l
      (update :fens (comp vec drop-last))
      (update :sans (comp vec drop-last))
      (update :idx #(min % (dec (length l))))))

(defn stub [fen]
  (map->Line {:fens [fen]
              :sans []
              :idx 0}))

(defn to-moves [^Line l]
  (map (fn [[from to] san]
         (move/new {:from from :to to :san san}))
       (partition 2 1 (:fens l))
       (:sans l)))

(defn from-moves [moves]
  (reduce append
          (stub (move/from (first moves)))
          moves))

(defn complete? [^Line l]
  (= (inc (:idx l))
     (length l)))

(defn next-frame [^Line l]
  {:pre [(< (inc (:idx l))
            (length l))]}
  (update l :idx inc))
