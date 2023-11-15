(ns chess-journal3.modes.live-games
  "Implements a mode where the user plays full against the engine, which can be
  saved and resumed later."
  (:require
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.line :as line]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.move :as move]
    [chess-journal3.utils :as u]
    [clj-time.core :as t])
  (:import
    (chess_journal3.line Line)
    (chess_journal3.modes.battle Battle)
    (chess_journal3.move Move)
    (chess_journal3.state GlobalState LocalState)))

(defrecord LiveGames
  [^Battle battle
   db
   live-game-infos
   live-game-idx]
  LocalState
  (getMode [_] "live-games")
  (getFen [_] (.getFen battle))
  (nextFrame [state] (update state :battle #(.nextFrame %)))
  (prevFrame [state] (update state :battle #(.prevFrame %)))
  (switchColor [state] (update state :battle #(.switchColor %)))
  (clickSquare [state square] (update state :battle #(.clickSquare % square)))
  (cleanUp [state] (update state :battle #(.cleanUp %)))
  (getLine [state] (.getLine battle))
  (makeClientView [state] (assoc (.makeClientView battle) :mode "live-games")))

(defn- ^Line get-line [db game-name]
  (let [unsorted-moves (db/get-tagged-moves db game-name)]
    (->> unsorted-moves
         (line/new-from-unsorted-moves c/initial-fen)
         line/jump-to-final-frame)))

(defn- get-current-game-info [^LiveGames s]
  (let [{:keys [live-game-infos
                live-game-idx]} s]
    (nth live-game-infos live-game-idx)))

(defn- ^LiveGames set-line [^LiveGames s]
  (let [{:keys [game-name
                color]} (get-current-game-info s)
        line (get-line (:db s) game-name)]
    (update s
      :battle
      #(-> %
           (assoc :line line
                  :color color
                  :selected-square nil)
           battle/set-opponent-must-move))))

(defn- ^LiveGames reset [^LiveGames s]
  (let [db (:db s)
        game-infos (db/list-live-games db)]
    (-> s
        (assoc :live-game-infos game-infos
               :live-game-idx 0)
        set-line)))

;; TODO Handle cases where the user tries to initialize live games mode, but
;; there are no live games.
(defn ^LiveGames init [^GlobalState app-state]
  (let [{:keys [db]} app-state
        battle (battle/init app-state)
        s (map->LiveGames {:db db
                           :battle battle})]
    (reset s)))

(defn ^LiveGames cycle-game [^LiveGames s increment]
  (assert (contains? #{1 -1} increment))
  (let [n (count (:live-game-infos s))]
    (-> s
        (update :live-game-idx #(mod (+ % increment) n))
        set-line)))

(defn ^LiveGames cycle-to-game [^LiveGames s game-name]
  (let [idx (u/index-of-first #(= game-name (:game-name %))
                              (:live-game-infos s))]
    (assert (not (nil? idx)))
    (-> s
        (assoc :live-game-idx idx)
        set-line)))

(defn ^LiveGames opponent-move [^LiveGames s]
  (update s :battle battle/opponent-move))

(defn create-new-game [db game-name color line]
  (let [moves (line/to-moves line)
        [white black] (case color
                        "w" ["user" "chess-journal3"]
                        "b" ["chess-journal3" "user"])
        date (subs (str (t/now)) 0 10)
        metadata {:white white
                  :black black
                  :date date}]
    (db/insert-live-game! db metadata moves game-name)))

(defn ^LiveGames save-game [^LiveGames s]
  (let [{:keys [db]} s
        game-name (:game-name (get-current-game-info s))
        line-old (get-line db game-name)
        line-new (get-in s [:battle :line])
        num-matching-moves (line/count-matching-moves line-old
                                                      line-new)
        moves-to-delete (->> (line/to-moves line-old)
                             (drop num-matching-moves))
        moves-to-insert (->> (line/to-moves line-new)
                             (drop num-matching-moves)
                             (map #(move/set-tag % game-name)))]
    (when (seq moves-to-delete)
      (println "Deleting moves:\n" moves-to-delete)
      (db/update-move-tags! db "deleted" moves-to-delete))
    (when (seq moves-to-insert)
      (println "Inserting moves:\n" moves-to-insert)
      (db/insert-tagged-moves! db moves-to-insert))
    s))

(defn ^LiveGames end-game [^LiveGames s result]
  (let [db (:db s)
        game-name (:game-name (get-current-game-info s))]
    (db/complete-live-game db game-name result)
    (reset s)))

(defn ^LiveGames set-engine-elo [^LiveGames s elo]
  (update s :battle battle/set-engine-elo elo))

(defn ^LiveGames set-engine-movetime [^LiveGames s movetime]
  (update s :battle battle/set-engine-movetime movetime))
