(ns chess-journal3.line
  (:require
    [chess-journal3.constants :as c]
    [chess-journal3.fen :as fen]
    [chess-journal3.move :as move]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.move Move)))

;; There will be one more fen than san.
(defrecord Line
  [tag
   fens
   sans
   idx])

(defn get-fens [l] (:fens l))
(defn get-sans [l] (:sans l))
(defn get-idx [l] (:idx l))

(defn length [^Line l]
  (count (:sans l)))

(defn fen
  ([^Line l]
   (fen l (:idx l)))
  ([^Line l idx]
   (nth (get-fens l) idx)))

(defn initial-fen [^Line l]
  (first (get-fens l)))

(defn final-fen [^Line l]
  (last (get-fens l)))

(defn penultimate-fen [^Line l]
  {:pre [(pos? (length l))]}
  (last (drop-last (get-fens l))))

(defn append [^Line l ^Move m]
  {:pre [(= (final-fen l)
            (move/initial-fen m))]}
  (-> l
      (update :fens conj (move/final-fen m))
      (update :sans conj (move/san m))))

(defn drop-last-move [^Line l]
  {:pre [(pos? (length l))]}
  (-> l
      (update :fens (comp vec drop-last))
      (update :sans (comp vec drop-last))
      (update :idx #(min % (dec (length l))))))

(defn new-stub [tag fen]
  (map->Line {:tag tag
              :fens [fen]
              :sans []
              :idx 0}))

(defn to-moves [^Line l]
  (map (fn [fen san]
         (move/new-from-san (:tag l) fen san))
       (drop-last (:fens l))
       (:sans l)))

(defn new-from-moves [moves]
  (reduce append
          (new-stub (move/tag (first moves))
                    (move/initial-fen (first moves)))
          moves))

;; Used for tests
(defn new-from-sans [tag initial-fen sans]
  (new-from-moves
    (reductions (fn [move san]
                  (move/new-from-san tag (move/final-fen move) san))
                (move/new-from-san tag initial-fen (first sans))
                (rest sans))))

(defn complete? [^Line l]
  (= (:idx l)
     (length l)))

(defn next-frame [^Line l]
  (if-not (complete? l)
    (update l :idx inc)
    l))

(defn prev-frame [^Line l]
  (if (pos? (:idx l))
    (update l :idx dec)
    l))

(defn ends-with-opponent-to-play? [^Line l color]
  (not= color (-> l final-fen fen/get-active-color)))

(defn truncate-at-current-fen [^Line l]
  (let [{:keys [idx]} l]
    (-> l
        (update :fens (comp vec #(take (inc idx) %)))
        (update :sans (comp vec #(take idx %))))))

(defn apply-move [^Line l ^Move m]
  (-> l
      truncate-at-current-fen
      (append m)
      next-frame))

(defn jump-to-frame [^Line l idx]
  {:pre [(pos? idx)
         (< idx (length l))]}
  (assoc l :idx idx))

(defn jump-to-final-frame [^Line l]
  (assoc l :idx (dec (length l))))

(defn jump-to-initial-frame [^Line l]
  (assoc l :idx 0))

(defn jump-to-fen [^Line l fen]
  (let [idx (u/index-of-first #(= fen %) (get-fens l))]
    (assert (some? idx))
    (assoc l :idx idx)))
