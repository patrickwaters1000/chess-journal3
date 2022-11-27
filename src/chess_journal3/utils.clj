(ns chess-journal3.utils
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.fen :as fen]
    [clojure.string :as string]))

(defn get-unique [xs]
  (when-not (= 1 (count xs))
    (throw (Exception. "Failed")))
  (first xs))

(defn cycle [xs]
  (conj (vec (rest xs)) (first xs)))

(defn other-color [color]
  (case color "w" "b" "b" "w"))

(defn get-fen [state]
  (try
    (let [{:keys [fens idx]} state]
      (nth fens idx))
    (catch Exception e
      (throw (Exception. (format "Failed with fens = %s, idx = %s" (:fens state) (:idx state)))))))

(defn get-active-color [state]
  (-> state get-fen fen/parse :active-color))

(defn get-locked-fen [state]
  (let [{:keys [fens locked-idx]} state]
    (nth fens locked-idx)))

(defn players-move? [state]
  (let [{:keys [color]} state
        active-color (get-active-color state)]
    (= active-color color)))

(defn opponents-move? [state]
  (not (players-move? state)))

(defn next-frame [state]
  (let [{:keys [idx fens mode]} state]
    (if (< idx (dec (count fens)))
      (update state :idx inc)
      state)))

(defn previous-frame [state]
  (let [{:keys [idx]} state]
    (if (pos? idx)
      (update state :idx dec)
      state)))

(defn get-piece-on-square [state square]
  (let [position (fen/parse (get-fen state))]
    (get-in position [:square->piece square])))

(defn player-has-piece-on-square? [state square]
  (let [{:keys [color]} state
        piece (get-piece-on-square state square)
        is-white-piece (when piece
                         (= piece (string/upper-case piece)))]
    (and piece
         (or (and (= "w" color) is-white-piece)
             (and (= "b" color) (not is-white-piece))))))

(defn opponent-has-piece-on-square? [state square]
  (player-has-piece-on-square? (update state :color other-color)
                               square))

(defn- get-move [state to-square]
  (let [{from-square :selected-square
         promote-piece :promote-piece} state
        piece-to-move (get-piece-on-square state from-square)
        to-rank (Integer/parseInt (subs to-square 1 2))
        promotion (or (and (= "p" piece-to-move) (= 1 to-rank))
                      (and (= "P" piece-to-move) (= 8 to-rank)))]
    (cond-> {:from from-square
             :to to-square}
      promotion (assoc :promote promote-piece))))

(defn- legal-move? [state move]
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
