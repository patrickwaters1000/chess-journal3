(ns chess-journal3.modes.endgames
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.utils :as u]
    [clojure.string :as string]))

(defn- get-current-endgame [state]
  (let [{:keys [endgames endgame-idx]} state]
    (nth endgames endgame-idx)))

(defn- reset-board [state]
  (let [endgame (get-current-endgame state)
        fen (:fen endgame)]
    (assoc state
      :fens [fen]
      :sans []
      :idx 0
      :selected-square nil)))

(defn- set-color [state]
  (assoc state :color (u/get-active-color state)))

(def white-pieces #{"K" "Q" "R" "B" "N" "P"})
(def piece-order {"K" 0 "Q" 1 "R" 2 "B" 3 "N" 4 "P" 5})

(defn- get-material-from-fen [fen]
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

(defn- get-endgames [db]
  (->> (db/get-endgames db)
       (map #(assoc % :material (get-material-from-fen (:fen %))))))

(defn init [state]
  (let [{:keys [db]} state]
    (-> state
        (assoc :mode "endgames"
               :endgames (get-endgames db))
        reset-board)))

(defn get-material [state]
  (:material (get-current-endgame state)))

(defn next-with-material [state material]
  (let [endgames (:endgames state)
        num-endgames (count endgames)
        next-idx (fn [idx] (mod (inc idx) num-endgames))]
    (loop [idx (next-idx (:endgame-idx state))]
      (if (= material (:material (nth endgames idx)))
        (-> state
            (assoc :endgame-idx idx)
            reset-board
            set-color)
        (recur (next-idx idx))))))

(defn next [state]
  (let [material (get-material state)]
    (next-with-material state material)))

(defn save [state]
  (let [{:keys [db]} state
        fen (u/get-fen state)]
    (db/insert-endgame! db {:fen fen
                            :comment ""
                            :objective ""})
    state))

(defn cycle-material [state]
  (let [material (get-material state)
        all-materials (->> (:endgames state)
                           (map :material)
                           (into #{})
                           sort)
        material-idx (->> all-materials
                          (map-indexed vector)
                          (filter #(= material (second %)))
                          (map first)
                          first)
        next-material-idx (mod (inc material-idx)
                               (count all-materials))
        next-material (nth all-materials next-material-idx)]
    (next-with-material state next-material)))
