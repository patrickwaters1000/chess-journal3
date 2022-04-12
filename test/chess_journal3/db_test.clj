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
  (db/insert-tags! db ["white-reportoire"])
  (= [{:id 1 :name "white-reportoire"}]
     (jdbc/query db "SELECT * FROM tags;")))

(deftest hyphenating
  (is (= :a-b-c (#'db/hyphenate :a_b_c)))
  (is (= {:a-b-c 42} (#'db/hyphenate-keys {:a_b_c 42}))))

(deftest inserting-and-getting-tagged-moves
  (reset! db)
  (db/insert-tags! db ["white-reportoire"
                       "black-reportoire"])
  (db/insert-tagged-moves! db
                           [{:tag "white-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-2"
                             :san "e4"}
                            {:tag "white-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-3"
                             :san "d4"}
                            {:tag "white-reportoire" ;; Duplicate
                             :initial-fen "fen-1"
                             :final-fen "fen-3"
                             :san "d4"}
                            {:tag "black-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-4"
                             :san "c4"}])
  (= [{:id 1 :tag_id 1 :move_id 1}
      {:id 2 :tag_id 1 :move_id 2}
      {:id 4 :tag_id 2 :move_id 4}]
     (jdbc/query db "SELECT * FROM tagged_moves;"))
  (= [{:san "e4" :final-fen "fen-2"}
      {:san "d4" :final-fen "fen-3"}]
     (db/get-tagged-moves db "white-reportoire" "fen-1")))

(deftest updating-move-tags
  (reset! db)
  (db/insert-tags! db ["white-reportoire"
                       "deleted-white-reportoire"])
  (db/insert-tagged-moves! db
                           [{:tag "white-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-2"
                             :san "san-1"}
                            {:tag "white-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-3"
                             :san "san-2"}
                            {:tag "deleted-white-reportoire"
                             :initial-fen "fen-1"
                             :final-fen "fen-4"
                             :san "san-3"}])
  (db/update-move-tags! db
                        [{:old-tag "white-reportoire"
                          :new-tag "deleted-white-reportoire"
                          :initial-fen "fen-1"
                          :san "san-2"}
                         {:old-tag "deleted-white-reportoire"
                          :new-tag "white-reportoire"
                          :initial-fen "fen-1"
                          :san "san-3"}])
  (= [{:san "san-1" :final-fen "fen-2"}
      {:san "san-3" :final-fen "fen-4"}]
     (db/get-tagged-moves db "white-reportoire" "fen-1")))
