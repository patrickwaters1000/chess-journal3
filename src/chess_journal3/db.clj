(ns chess-journal3.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            ;;[chess-journal.chess :as chess]
            [clj-time.core :as t]))

(def db {:user "patrick"
         :dbtype "postgresql"
         :dbname "chess_journal3"})

;; NOTE jdbc db-specs must include the db, so I'm not sure how to
;; create a db programatically. To create the db, make sure you have
;; sufficient privliges in /etc/postgresql/10/main/pg_hba.conf, then
;; use psql -U postgres to boot up the client.  Then you can run the
;; create database query.

(def create-chess-journal-db-query "
create database chess_journal3 with owner patrick;")

(defn create-positions-table! [db]
  (jdbc/execute! db "
CREATE TABLE positions (
  id INT GENERATED ALWAYS AS IDENTITY,
  fen VARCHAR UNIQUE,
PRIMARY KEY(id));"))

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
    REFERENCES positions(id)
);
"))

(comment
  (create-positions-table! db)
  (create-moves-table! db)
  
  ;;
  )
