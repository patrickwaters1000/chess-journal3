(ns chess-journal3.db
  (:require
    [chess-journal3.constants :refer [username initial-fen]]
    [chess-journal3.move :as move]
    [clj-time.core :as t]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string])
  (:import
    (chess_journal3.move Move)))

(def db {:user "pwaters" ;; or "patrick"
         :dbtype "postgresql"
         :dbname "chess_journal3"})

;; NOTE jdbc db-specs must include the db, so I'm not sure how to
;; create a db programatically. To create the db, follow these steps:
;;
;; 1. Connect to postgres with sufficient privliges:
;;
;;    > psql postgres # postgres 14 on mac
;;    > psql -U postgres # postgres 10 on linux
;;
;;    You may need to edit /etc/postgresql/10/main/pg_hba.conf to give yourself
;;    privliges.
;;
;; 2. At the postgres CLI client, run:
;;
;;    > create database chess_journal3 with owner patrick;

(defn create-positions-table! [db]
  (jdbc/execute! db "
CREATE TABLE positions (
  id INT GENERATED ALWAYS AS IDENTITY,
  fen VARCHAR UNIQUE NOT NULL,
PRIMARY KEY(id));
"))

(defn create-moves-table! [db]
  (jdbc/execute! db "
CREATE TABLE moves (
  id INT  GENERATED ALWAYS AS IDENTITY,
  initial_position_id INT NOT NULL,
  final_position_id INT NOT NULL,
  san varchar NOT NULL,
PRIMARY KEY(id),
CONSTRAINT fk_initial_position
  FOREIGN KEY(initial_position_id)
    REFERENCES positions(id),
CONSTRAINT fk_final_position
  FOREIGN KEY(final_position_id)
    REFERENCES positions(id),
UNIQUE(initial_position_id, final_position_id));
"))

(defn create-tags-table! [db]
  (jdbc/execute! db "
CREATE TABLE tags (
  id INT GENERATED ALWAYS AS IDENTITY,
  name TEXT NOT NULL,
PRIMARY KEY(id),
UNIQUE(name));"))

(defn create-tagged-moves-table! [db]
  (jdbc/execute! db "
CREATE TABLE tagged_moves (
  id INT GENERATED ALWAYS AS IDENTITY,
  tag_id INT NOT NULL,
  move_id INT NOT NULL,
PRIMARY KEY(id),
CONSTRAINT fk_tag_id
  FOREIGN KEY(tag_id)
    REFERENCES tags(id),
CONSTRAINT fk_move_id
  FOREIGN KEY(move_id)
    REFERENCES moves(id),
UNIQUE(tag_id, move_id));
"))

;; The game's moves should be in the tagged_moves table (note that the game has
;; a tag). So, some tags can contain lots of moves (e.g., white-reportoire), but
;; there are also many tags for unique games.
;; TODO Tags like white-reportoire are too coarse. Support 'refining' the tags
;; of reportoire moves from the GUI, e.g., scotch-gambit-white-reportoire <
;; white-reportoire.
(defn create-games-table! [db]
  (jdbc/execute! db "
CREATE TABLE games (
  id INT GENERATED ALWAYS AS IDENTITY,
  tag_id INT NOT NULL,
  black VARCHAR,
  white VARCHAR,
  date VARCHAR,
  end_time VARCHAR,
  black_elo INT,
  white_elo INT,
  result VARCHAR,
  round VARCHAR,
  event VARCHAR,
  time_control VARCHAR,
  termination VARCHAR,
  site VARCHAR,
PRIMARY KEY(id),
CONSTRAINT fk_tag_id
  FOREIGN KEY(tag_id)
    REFERENCES tags(id),
UNIQUE(black, white, date, end_time));"))

;; Uniqueness condition???
(defn create-tag-containments-table! [db]
  (jdbc/execute! db "
CREATE TABLE tag_containments (
  id INT GENERATED ALWAYS AS IDENTITY,
  small_tag_id INT NOT NULL,
  big_tag_id INT NOT NULL,
PRIMARY KEY(id),
CONSTRAINT fk_small_tag_id
  FOREIGN KEY(small_tag_id)
    REFERENCES tags(id),
CONSTRAINT fk_big_tag_id
  FOREIGN KEY(big_tag_id)
    REFERENCES tags(id));"))

(defn create-endgames-table! [db]
  (jdbc/execute! db "
CREATE TABLE endgames (
  id INT GENERATED ALWAYS AS IDENTITY,
  position_id INT NOT NULL,
  objective VARCHAR NOT NULL,
  comment VARCHAR,
  deleted BOOLEAN,
PRIMARY KEY(id),
CONSTRAINT fk_position_id
  FOREIGN KEY(position_id)
    REFERENCES positions(id),
UNIQUE(position_id));"))

;;(defn create-live-games-table! [db]
;;  (jdbc/execute! db "
;;CREATE TABLE live_games (
;;  id INT GENERATED ALWAYS AS IDENTITY,
;;  tag_id INT NOT NULL,"))

(defn insert-positions! [db fens]
  (let [template "
INSERT INTO positions(fen)
VALUES
  %s
ON CONFLICT DO NOTHING;
"
        values (->> fens
                    (map #(format "('%s')" %))
                    (string/join ",\n  "))
        query (format template values)]
    (jdbc/execute! db query)))

(defn insert-moves! [db moves]
  (->> moves
       (mapcat (juxt move/initial-fen move/final-fen))
       (into #{})
       (insert-positions! db))
  (let [template "
WITH t(initial_fen, final_fen, san) AS (
  VALUES
    %s
)
INSERT INTO moves(initial_position_id, final_position_id, san)
SELECT p1.id, p2.id, t.san
FROM t
  LEFT JOIN positions p1 ON t.initial_fen = p1.fen
  LEFT JOIN positions p2 ON t.final_fen = p2.fen
ON CONFLICT DO NOTHING;
"
        values (->> moves
                    (map (fn [^Move m]
                           (format "('%s', '%s', '%s')"
                                   (move/initial-fen m)
                                   (move/final-fen m)
                                   (move/san m))))
                    (string/join ",\n    "))
        query (format template values)]
    (jdbc/execute! db query)))

(defn insert-tags! [db tags]
  (let [template "
INSERT INTO tags(name)
VALUES
  %s
ON CONFLICT DO NOTHING;
"
        values (->> tags
                    (map #(format "('%s')" %))
                    (string/join ",\n  "))
        query (format template values)]
    (jdbc/execute! db query)))

(defn insert-tagged-moves! [db moves]
  {:pre [(every? move/tag moves)
         (every? move/san moves)
         (every? move/initial-fen moves)]}
  (insert-moves! db moves)
  (let [template "
WITH v(tag, initial_fen, san) AS (
  VALUES
    %s
)
INSERT INTO tagged_moves(tag_id, move_id)
SELECT t.id, m.id
FROM v
  LEFT JOIN tags t ON v.tag = t.name
  LEFT JOIN positions p ON v.initial_fen = p.fen
  LEFT JOIN moves m
    ON p.id = m.initial_position_id
    AND v.san = m.san
ON CONFLICT DO NOTHING;
"
        values (->> moves
                    (map (fn [m]
                           (format "('%s', '%s', '%s')"
                                   (move/tag m) (move/initial-fen m) (move/san m))))
                    (string/join ",\n    "))
        query (format template values)]
    (jdbc/execute! db query)))

(defn insert-tag! [db tag-name]
  (let [template "INSERT INTO tags(name) VALUES ('%s');"
        query (format template tag-name)]
    (jdbc/execute! db query)))

(defn insert-tag-containment! [db small-tag big-tag]
  (let [template "
WITH v(small_tag, big_tag) AS (
  VALUES
    %s
)
INSERT INTO tag_containments(small_tag_id, big_tag_id)
SELECT t1.id, t2.id
FROM v
  LEFT JOIN tags t1 ON v.small_tag = t1.name
  LEFT JOIN tags t2 ON v.big_tag = t2.name;"
        values (format "('%s', '%s')" small-tag big-tag)
        query (format template values)]
    (jdbc/execute! db query)))

(defn list-live-games [db]
  (->> "
SELECT t.name AS name
FROM games g LEFT JOIN tags t ON g.tag_id = t.id
WHERE g.result IS NULL
  AND ((g.white = 'user' AND g.black = 'chess-journal3')
       OR (g.black = 'user' AND g.white = 'chess-journal3'));"
       (jdbc/query db)
       (map :name)))

(defn complete-live-game [db game-name result]
  {:pre [(contains? #{"0-1" "1/2-1/2" "1-0"})]}
  (let [template "
WITH v(game_name, result) AS (
  VALUES ('%s', '%s')
),
input AS (
  SELECT
    t.id AS tag_id,
    v.result AS result
  FROM v LEFT JOIN tags t
    ON v.game_name = t.name
)
UPDATE games
SET result = v.result
FROM input
WHERE games.tag_id = input.tag_id;"
        sql (format template game-name result)]
    (jdbc/execute! db sql)))

;; TODO Improve this terrible function name.
(defn get-tag-name [metadata]
  (string/replace (string/join "__" [(:white metadata)
                                     (:black metadata)
                                     (:date metadata)
                                     (:end-time metadata)])
                  #"\s"
                  ""))

(defn game-in-db? [metadata]
  (let [tag (get-tag-name metadata)
        template "SELECT count(*) AS num_tags FROM tags where name = '%s';"
        query (format template tag)
        results (jdbc/query db query)
        num-tags (:num_tags (first results))]
    (case num-tags
      0 false
      1 true)))

(defn insert-game!* [db metadata]
  (let [template "
WITH v(tag_name,
       black,
       white,
       date,
       end_time,
       black_elo,
       white_elo,
       result,
       round,
       event,
       time_control,
       termination,
       site) AS (
  VALUES
    %s
)
INSERT INTO games(tag_id,
                  black,
                  white,
                  date,
                  end_time,
                  black_elo,
                  white_elo,
                  result,
                  round,
                  event,
                  time_control,
                  termination,
                  site)
SELECT
  t.id,
  v.black,
  v.white,
  v.date,
  v.end_time,
  v.black_elo,
  v.white_elo,
  v.result,
  v.round,
  v.event,
  v.time_control,
  v.termination,
  v.site
FROM v
  LEFT JOIN tags t ON v.tag_name = t.name;"
        tag-name (get-tag-name metadata)
        values (->> [(format "'%s'" tag-name)
                     (format "'%s'" (:black metadata))
                     (format "'%s'" (:white metadata))
                     (format "'%s'" (:date metadata))
                     (format "'%s'" (:end-time metadata))
                     (:black-elo metadata)
                     (:white-elo metadata)
                     (format "'%s'" (:result metadata))
                     (format "'%s'" (:round metadata))
                     (format "'%s'" (:event metadata))
                     (format "'%s'" (:time-control metadata))
                     (format "'%s'" (:termination metadata))
                     (format "'%s'" (:site metadata))]
                    (string/join ", ")
                    (format "(%s)"))
        sql (format template values)]
    (println sql)
    (jdbc/execute! db sql)))

(defn insert-game! [db metadata moves]
  (let [tag (get-tag-name metadata)
        containing-tag (cond
                         (= username (:white metadata)) "white-games"
                         (= username (:black metadata)) "black-games")]
    (insert-tag! db tag)
    (when containing-tag
      (insert-tag-containment! db tag containing-tag))
    (insert-tagged-moves! db (map #(assoc % :tag tag) moves))
    (insert-game!* db metadata)))

(defn insert-live-game!* [db metadata game-name]
  (let [template "
WITH v(tag_name, black, white, date) AS (
  VALUES ('%s', '%s', '%s', '%s')
)
INSERT INTO games(tag_id, black, white, date)
SELECT t.id, v.black, v.white, v.date
FROM v LEFT JOIN tags t ON v.tag_name = t.name;"
        {:keys [black white date]} metadata
        sql (format template game-name black white date)]
    (println sql)
    (jdbc/execute! db sql)))

(defn insert-live-game! [db metadata moves game-name]
  (insert-tag! db game-name)
  (insert-tag-containment! db game-name "live-games")
  (insert-tagged-moves! db (map #(assoc % :tag game-name) moves))
  (insert-live-game!* db metadata game-name))

(defn insert-endgame! [db {:keys [fen
                                  objective
                                  comment]}]
  (insert-positions! db [fen])
  (let [template "
WITH t(fen, objective, comment) AS (
  VALUES
    %s
)
INSERT INTO endgames(position_id, objective, comment)
SELECT p.id, t.objective, t.comment
FROM t
  LEFT JOIN positions p ON t.fen = p.fen
ON CONFLICT DO NOTHING;"
        values (format "('%s', '%s', '%s')"
                       fen objective comment)
        query (format template values)]
    (jdbc/execute! db query)))

(defn delete-endgame! [db fen]
  (let [template "
DELETE FROM endgames e
       USING positions p
WHERE e.position_id = p.id
      AND p.fen = '%s';"
        sql (format template fen)]
    (jdbc/execute! db sql)))

(defn get-child-tags [db tag]
  (let [template "
SELECT child.name AS tag
FROM tags parent
  LEFT JOIN tag_containments r
    ON parent.id = r.big_tag_id
  LEFT JOIN tags child
    ON r.small_tag_id = child.id
WHERE parent.name = '%s'"
        query (format template tag)]
    (map :tag (jdbc/query db query))))

(comment
  (get-child-tags db "black-games")
  ;;
  )

(defn get-tagged-moves [db tag & {:keys [include-child-tags]}]
  (let [children (get-child-tags db tag)
        tags (if include-child-tags
               (concat [tag] children)
               [tag])
        template "
SELECT
  t.name AS tag,
  m.san AS san,
  p1.fen AS from,
  p2.fen AS to
FROM tagged_moves tm
  LEFT JOIN tags t ON tm.tag_id = t.id
  LEFT JOIN moves m ON tm.move_id = m.id
  LEFT JOIN positions p1 ON m.initial_position_id = p1.id
  LEFT JOIN positions p2 ON m.final_position_id = p2.id
WHERE t.name IN (%s)
"
        param (->> tags
                   (map #(format "'%s'" %))
                   (string/join ", "))
        query (format template param)]
    (->> (jdbc/query db query)
         (map (fn [{:keys [tag san from]}]
                (move/new-from-san tag from san))))))

(comment
  ;; Other arity of `get-tagged-moves` not used
  ([db tag fen]
   (let [template "
SELECT
  m.san AS san,
  p1.fen AS initial_fen,
  p2.fen AS final_fen
FROM tagged_moves tm
  LEFT JOIN tags t ON tm.tag_id = t.id
  LEFT JOIN moves m ON tm.move_id = m.id
  LEFT JOIN positions p1 ON m.initial_position_id = p1.id
  LEFT JOIN positions p2 ON m.final_position_id = p2.id
WHERE t.name = '%s'
  AND p1.fen = '%s';
"
         query (format template tag fen)]
     (map u/hyphenate-keys
          (jdbc/query db query)))))

(defn get-games [db]
  (jdbc/query db "
SELECT
  t.name AS tag,
  g.white AS white,
  g.black AS black,
  g.date AS date,
  g.end_time AS end_time,
  g.black_elo AS black_elo,
  g.white_elo AS white_elo,
  g.result AS result,
  g.round AS round,
  g.event AS event,
  g.time_control AS time_control,
  g.termination AS termination,
  g.site AS site
FROM
  games g
    LEFT JOIN tags t ON g.tag_id = t.id;"))

(defn get-all-moves [db tag fen])

(defn get-endgames [db]
  (jdbc/query db "
SELECT p.fen, e.objective, e.comment
FROM endgames e
  LEFT JOIN positions p ON e.position_id = p.id;"))

(defn update-move-tags! [db new-tag moves]
  (let [template "
WITH v(old_tag, new_tag, initial_fen, san) AS (
  VALUES
    %s
),
input AS (
  SELECT
    tm.id AS tagged_move_id,
    t2.id AS new_tag_id
  FROM v
    LEFT JOIN tags t1 ON v.old_tag = t1.name
    LEFT JOIN tags t2 ON v.new_tag = t2.name
    LEFT JOIN positions p ON v.initial_fen = p.fen
    LEFT JOIN moves m
      ON v.san = m.san
      AND p.id = m.initial_position_id
    LEFT JOIN tagged_moves tm
      ON t1.id = tm.tag_id
      AND m.id = tm.move_id
)
UPDATE tagged_moves
SET tag_id = input.new_tag_id
FROM input
WHERE tagged_moves.id = input.tagged_move_id;"
        values (->> moves
                    (map (fn [m]
                           (format "('%s', '%s', '%s', '%s')"
                                   (move/tag m) new-tag (move/initial-fen m) (move/san m))))
                    (string/join ",\n    "))
        query (format template values)]
    (jdbc/execute! db query)))

(defn get-tag-containments [db]
  (jdbc/query db "
SELECT t1.name AS parent,
       t2.name AS child
FROM tag_containments c
     LEFT JOIN tags t1 ON c.big_tag_id = t1.id
     LEFT JOIN tags t2 ON c.small_tag_id = t2.id;"))

(comment
  (defn reset! [db]
    (jdbc/execute! db "DROP TABLE IF EXISTS tagged_moves;")
    (jdbc/execute! db "DROP TABLE IF EXISTS tags;")
    (jdbc/execute! db "DROP TABLE IF EXISTS moves;")
    (jdbc/execute! db "DROP TABLE IF EXISTS positions;")
    (jdbc/execute! db "DROP TABLE IF EXISTS games;")
    (jdbc/execute! db "DROP TABLE IF EXISTS tag_containments;")
    (jdbc/execute! db "DROP TABLE IF EXISTS endgames;")
    (create-positions-table! db)
    (create-moves-table! db)
    (create-tags-table! db)
    (create-tagged-moves-table! db)
    (create-games-table! db)
    (create-tag-containments-table! db)
    (create-endgames-table! db)
    (insert-tags! db ["white-reportoire"
                      "black-reportoire"
                      "deleted-white-reportoire"
                      "deleted-black-reportoire"])
    (insert-tags! db ["white-1d4"
                      "white-1e4"])
    (insert-tag-containment! db "white-1d4" "white-reportoire")
    (insert-tag-containment! db "white-1e4" "white-reportoire")
    (insert-tags! db ["live-games"])
    (insert-tags! db ["white-games"
                      "black-games"]))
  (jdbc/execute! db "ALTER TABLE endgames ADD COLUMN deleted boolean")
  (jdbc/execute! db "ALTER TABLE endgames DROP COLUMN deleted")

  (require '[chess-journal3.pgn :as pgn])
  (def games (vec (pgn/read-file "/Users/pwaters/Downloads/chess_com_games_2022-05-29.pgn")))
  (let [{:keys [metadata moves]} (first games)
        tag (get-tag-name metadata)
        containing-tag (cond
                         (= username (:white metadata)) "white-games"
                         (= username (:black metadata)) "black-games")]
    ;;(insert-tag! db tag)
    ;;(when containing-tag (insert-tag-containment! db tag containing-tag))
    ;;(insert-tagged-moves! db (map #(assoc % :tag tag) moves))
    (insert-game!* db metadata)
    ;;[metadata moves tag containing-tag]
    )
  ;;
  )

;;ERROR: null value in column "initial_position_id" of relation "moves"
;;violates not-null constraint Detail: Failing row contains (23057, null, null,
;;                                                                  d4).

(comment
  ;; 2023-02-26 Re-tag existing white reportoire as 'white-1e4'
  (jdbc/query db "select * from tags where name in ('white-reportoire', 'white-1e4', 'white-1d4');")
  (jdbc/execute! db "update tagged_moves set tag_id = 248 where tag_id = 1;")
  ;; 2023-02-26 Add 1d4 in 'white-1d4' reportoire
  (insert-tagged-moves! db [(move/new-from-san "white-1d4" initial-fen "d4")])
  (jdbc/query db "
WITH v(tag, initial_fen, san) AS (
  VALUES
    ('white-1d4', 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', 'd4')
)
SELECT t.id, m.id
FROM v
  LEFT JOIN tags t ON v.tag = t.name
  LEFT JOIN positions p ON v.initial_fen = p.fen
  LEFT JOIN moves m
    ON p.id = m.initial_position_id
    AND v.san = m.san;")
  ;;
  )

(comment
  (jdbc/query db "select name from tags order by 1")
  (jdbc/query db "
select t2.name
from tags t1
     left join tag_containments c on t1.id = c.small_tag_id
     left join tags t2 on t2.id = c.big_tag_id
where t1.name = 'test-1'")
  (jdbc/query db "
select *
from tagged_moves tm
     left join tags t on tm.tag_id = t.id
where t.name = 'test-1'")
  (jdbc/query db "
select g.*
from games g
     left join tags t on g.tag_id = t.id
where t.name = 'test-1'")
  (insert-live-game!* db {:black "chess-journal3" :white "user" :date "2022-12-11"} "test-1")
  (list-live-games db)
  ;;
  )

(defn create-backup! [db output-file]
  (spit output-file
        {:positions (jdbc/query db "select * from positions")
         :moves (jdbc/query db "select * from moves")
         :tags (jdbc/query db "select * from tags")
         :tagged-moves (jdbc/query db "select * from tagged_moves")
         :games (jdbc/query db "select * from games")
         :tag-containments (jdbc/query db "select * from tag_containments")
         :endgames (jdbc/query db "select * from endgames")}))

(comment
  (create-backup! db "db-backup-2023-02-18.edn")
  (require '[clojure.edn :as edn])
  (def data (edn/read-string (slurp "db-backup-2023-02-18.edn")))
  ;;
  )
