(ns chess-journal3.db
  (:require
    ;;[chess-journal.chess :as chess]
    [clj-time.core :as t]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]))

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
  fen VARCHAR UNIQUE,
PRIMARY KEY(id));
"))

(defn create-moves-table! [db]
  (jdbc/execute! db "
CREATE TABLE moves (
  id INT  GENERATED ALWAYS AS IDENTITY,
  initial_position_id INT,
  final_position_id INT,
  san varchar,
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
  name TEXT,
PRIMARY KEY(id),
UNIQUE(name));"))

(defn create-tagged-moves-table! [db]
  (jdbc/execute! db "
CREATE TABLE tagged_moves (
  id INT GENERATED ALWAYS AS IDENTITY,
  tag_id INT,
  move_id INT,
PRIMARY KEY(id),
CONSTRAINT fk_tag_id
  FOREIGN KEY(tag_id)
    REFERENCES tags(id),
CONSTRAINT fk_move_id
  FOREIGN KEY(move_id)
    REFERENCES moves(id),
UNIQUE(tag_id, move_id));
"))

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
    (println query)
    (jdbc/execute! db query)))

(defn insert-moves! [db data]
  (->> data
       (mapcat (juxt :initial-fen :final-fen))
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
        values (->> data
                    (map (fn [{:keys [initial-fen final-fen san]}]
                           (format "('%s', '%s', '%s')"
                                   initial-fen final-fen san)))
                    (string/join ",\n    "))
        query (format template values)]
    (println query)
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
    (println query)
    (jdbc/execute! db query)))

(defn insert-tagged-moves! [db data]
  (insert-moves! db data)
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
        values (->> data
                    (map (fn [{:keys [tag initial-fen san]}]
                           (format "('%s', '%s', '%s')"
                                   tag initial-fen san)))
                    (string/join ",\n    "))
        query (format template values)]
    (println query)
    (jdbc/execute! db query)))

(comment
  (defn reset! [db]
    (jdbc/execute! db "DROP TABLE IF EXISTS tagged_moves;")
    (jdbc/execute! db "DROP TABLE IF EXISTS tags;")
    (jdbc/execute! db "DROP TABLE IF EXISTS moves;")
    (jdbc/execute! db "DROP TABLE IF EXISTS positions;")
    (create-positions-table! db)
    (create-moves-table! db)
    (create-tags-table! db)
    (create-tagged-moves-table! db)
    (db/insert-tags! db ["main-reportoire"]))
  (reset! db)
  ;;
  )
