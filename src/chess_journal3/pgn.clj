(ns chess-journal3.pgn
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.constants :refer [initial-fen]]
    [chess-journal3.db :as db]
    [clojure.java.io :as io]
    [clojure.string :as string]))

(defn read-metadata-element [s]
  (let [[_ k v] (re-matches #"\[([^ ]+) \"([^\"]+)\"\]" s)]
    (when (and k v)
      [k v])))

(defn hydrate-metadata-field [[k v]]
  (case k
    "Black" [:black v]
    "White" [:white v]
    "Date" [:date v]
    "EndTime" [:end-time v]
    "WhiteElo" [:white-elo (Integer/parseInt v)]
    "BlackElo" [:black-elo (Integer/parseInt v)]
    "Result" [:result v]
    "Round" [:round v]
    "Event" [:event v]
    "TimeControl" [:time-control v]
    "Termination" [:termination v]
    "Site" [:site v]))

(defn read-metadata [data]
  (let [data-2 (drop-while empty? data)
        metadata (->> data-2
                      (map read-metadata-element)
                      (take-while some?)
                      (map hydrate-metadata-field)
                      (into {}))
        data-3 (drop (count (keys metadata)) data-2)]
    [metadata data-3]))

(defn ignore-token? [t]
  (or (re-matches #"[\d]+\." t)
      (contains? #{"1-0" "0-1" "1/2-1/2"} t)))

(defn sans-to-moves [sans]
  (first (reduce (fn [[moves fen] san]
                   (let [new-fen (chess/apply-san fen san)
                         move {:initial-fen fen
                               :final-fen new-fen
                               :san san}]
                     [(conj moves move)
                      new-fen]))
                 [[]
                  initial-fen]
                 sans)))

(defn read-moves [data]
  (let [data-2 (drop-while empty? data)
        san-data (take-while (comp not empty?) data-2)
        data-3 (drop (count san-data) data-2)
        moves (as-> san-data _
                (string/join " " _)
                (string/split _ #"\s")
                (remove ignore-token? _)
                (sans-to-moves _))]
    [moves data-3]))

(defn read-game [data]
  (let [[metadata data-2] (read-metadata data)
        [moves data-3] (read-moves data-2)]
    [{:metadata metadata
      :moves moves}
     data-3]))

(defn read-pgn-file [file]
  (with-open [r (io/reader file)]
    (loop [games []
           data (line-seq r)]
      (if (empty? data)
        games
        (let [[game remaining-data] (read-game data)]
          (recur (conj games game)
                 remaining-data))))))

(defn import-games-from-file [file]
  (doseq [game (read-pgn-file file)
          :let [{:keys [metadata moves]} game
                game-title (db/get-tag-name metadata)]]
    (if (db/game-in-db? metadata)
      (println (format "Skipping %s; it's already in the db." game-title))
      (do (println (format "Importing %s..." game-title))
          (db/insert-game! db/db metadata moves)))))

(defn sans->pgn [sans]
  (->> (partition-all 2 sans)
       (map vector (range))
       (map (fn [[move-idx [white-move black-move]]]
              (if black-move
                (format "%d. %s %s" (inc move-idx) white-move black-move)
                (format "%d. %s" (inc move-idx) white-move))))
       (string/join " ")))
