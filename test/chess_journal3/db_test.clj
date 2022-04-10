(ns chess-journal3.db-test
  (:require
    [chess-journal3.db :as db]
    [clojure.java.jdbc :as jdbc]
    [clojure.test :refer [is deftest]]))

(def db {:user "pwaters" ;; or "patrick"
         :dbtype "postgresql"
         :dbname "chess_journal3_test"})

(defn reset! [db]
  (jdbc/execute! db "DROP TABLE IF EXISTS tagged_moves;")
  (jdbc/execute! db "DROP TABLE IF EXISTS tags;")
  (jdbc/execute! db "DROP TABLE IF EXISTS moves;")
  (jdbc/execute! db "DROP TABLE IF EXISTS positions;")
  (db/create-positions-table! db)
  (db/create-moves-table! db)
  (db/create-tags-table! db)
  (db/create-tagged-moves-table! db))

(deftest inserting-positions
  (reset! db)
  (db/insert-positions! db
                        ["fen-1"
                         "fen-2"
                         "fen-2"])
  (= [{:id 1 :fen "fen-1"}
      {:id 2 :fen "fen-2"}]
     (jdbc/query db "SELECT * FROM positions;")))

(deftest inserting-moves
  (reset! db)
  (db/insert-positions! db
                        ["fen-1"])
  (db/insert-moves! db
                    [{:initial-fen "fen-1" :final-fen "fen-2" :san "e4"}
                     {:initial-fen "fen-1" :final-fen "fen-2" :san "e4"}])
  (= [{:id 1 :initial_position_id 1 :final_position_id 3 :san "e4"}]
     (jdbc/query db "SELECT * FROM moves;")))

(deftest inserting-tags
  (reset! db)
  (db/insert-tags! db ["main-reportoire"])
  (= [{:id 1 :name "main-reportoire"}]
     (jdbc/query db "SELECT * FROM tags;")))

(deftest inserting-tagged-moves
  (reset! db)
  (db/insert-tags! db ["main-reportoire"])
  (db/insert-tagged-moves! db
                           [{:tag "main-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-2"
                             :san "e4"}
                            {:tag "main-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-3"
                             :san "d4"}
                            {:tag "main-reportoire" ;; Duplicate
                             :initial-fen "fen-1"
                             :final-fen "fen-3"
                             :san "d4"}])
  (= [{:id 1 :tag_id 1 :move_id 1}
      {:id 2 :tag_id 1 :move_id 2}]
     (jdbc/query db "SELECT * FROM tagged_moves;")))
