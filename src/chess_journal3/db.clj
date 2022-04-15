(ns chess-journal3.db
  (:require
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
    (jdbc/execute! db query)))

(defn hyphenate [k]
  (keyword (string/replace (name k) "_" "-")))

(defn hyphenate-keys [m]
  (reduce-kv (fn [acc k v]
               (assoc acc (hyphenate k) v))
             {}
             m))

(defn get-tagged-moves [db tag fen]
  (let [template "
SELECT
  m.san AS san,
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
    (map hyphenate-keys
         (jdbc/query db query))))

(defn update-move-tags! [db data]
  (let [template "
WITH v(old_tag, new_tag, initial_fen, san) AS (
  VALUES
    %s
),
input AS (
  SELECT
    m.id AS move_id,
    t1.id AS old_tag_id,
    t2.id AS new_tag_id
  FROM v
    LEFT JOIN tags t1 ON v.old_tag = t1.name
    LEFT JOIN tags t2 ON v.new_tag = t2.name
    LEFT JOIN positions p ON v.initial_fen = p.fen
    LEFT JOIN moves m ON v.san = m.san
      AND p.id = m.initial_position_id
)
UPDATE tagged_moves
SET tag_id = input.new_tag_id
FROM input
WHERE tagged_moves.tag_id = input.old_tag_id
  AND tagged_moves.move_id = input.move_id;
"
        values (->> data
                    (map (fn [{:keys [old-tag new-tag initial-fen san]}]
                           (format "('%s', '%s', '%s', '%s')"
                                   old-tag new-tag initial-fen san)))
                    (string/join ",\n    "))
        query (format template values)]
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
    (insert-tags! db ["white-reportoire"
                      "black-reportoire"
                      "deleted-white-reportoire"
                      "deleted-black-reportoire"]))
  (reset! db)
  ;;
  )
