(ns chess-journal3.modes.games
  (:require
    [chess-journal3.constants :refer [username]]
    [chess-journal3.db :as db]
    [chess-journal3.lines :as lines]
    [chess-journal3.utils :as u]
    [clojure.string :as string]))

(defn get-next-move [state]
  (let [{:keys [review-lines idx]} state
        fen (u/get-fen state)
        san (:san (nth (first review-lines) (inc idx)))]
    (chess/san-to-move fen san)))

(defn next-frame [state]
  (let [{:keys [idx fens mode]} state]
    (if (< idx (dec (count fens)))
      (update state :idx inc)
      (u/do-move state (get-next-move state)))))

(defn next-move [state]
  (u/do-move state (get-next-move state)))

(defn next-frame [state]
  (-> state
      (u/next-frame state)
      next-move))

(defn game-score [game-metadata]
  (let [{:keys [black white result]} game-metadata]
    (cond
      (= username white) (case result
                           "1-0" 1.0
                           "1/2-1/2" 0.5
                           "0-1" 0.0)
      (= username black) (case result
                           "1-0" 0.0
                           "1/2-1/2" 0.5
                           "0-1" 1.0)
      :else (throw (Exception.
                     (format "%s didn't play game with metadata %s"
                             username
                             game-metadata))))))

(defn set-game->won [state]
  (let [game->won (->> (db/get-games (:db state))
                       (map (fn [{:keys [tag]
                                  :as game-metadata}]
                              [tag (game-score game-metadata)]))
                       (into {}))]
    (assoc state :game->won game->won)))

(defn get-games [state]
  (->> (:lines state)
       (filter (partial u/matches-current-line state))
       (map #(nth % (inc (:idx state))))
       (map (fn [{:keys [tag san]}]
              (let [[white black date time] (string/split tag #"__")
                    opponent (case (:color state)
                               "w" black
                               "b" white)]
                {:tag tag
                 :san san
                 :opponent opponent
                 :date date
                 :time time})))))

(defn get-score [state moves]
  (let [{:keys [game->won]} state
        _ (println game->won)
        scores (->> moves
                    (map :tag)
                    (map #(get game->won %)))
        n (count scores)
        s (reduce + 0.0 scores)]
    (/ s n)))

(defn select-game [state tag]
  (loop [retries (count (:lines state))
         state* state]
    (if (= tag (:tag (second (first (:lines state*)))))
      state*
      (do (assert pos? retries)
          (recur (dec retries)
                 (update state* :lines cycle))))))

(defn next-line [state]
  (update state :lines u/cycle))

(defn init [state]
  (-> state
      (assoc :mode "games")
      set-game->won
      lines/load-fen->moves
      lines/load-lines
      u/reset-board))
