(ns chess-journal3.db-test
  (:require
    [chess-journal3.constants :refer [initial-fen]]
    [chess-journal3.db :as db]
    [chess-journal3.move :as move]
    [clojure.java.jdbc :as jdbc]
    [clojure.test :refer [is deftest]])
  (:import
    (chess_journal3.move Move)))

(def test-db {:user "pwaters" ;; or "patrick"
              :dbtype "postgresql"
              :dbname "chess_journal3_test"})

(defn setup! [db]
  (db/create-positions-table! db)
  (db/create-moves-table! db)
  (db/create-tags-table! db)
  (db/create-tag-containments-table! db)
  (db/create-tagged-moves-table! db)
  (db/create-games-table! db))

(defn tear-down! [db]
  (jdbc/execute! db "DROP TABLE IF EXISTS games;")
  (jdbc/execute! db "DROP TABLE IF EXISTS tagged_moves;")
  (jdbc/execute! db "DROP TABLE IF EXISTS tag_containments;")
  (jdbc/execute! db "DROP TABLE IF EXISTS tags;")
  (jdbc/execute! db "DROP TABLE IF EXISTS moves;")
  (jdbc/execute! db "DROP TABLE IF EXISTS positions;"))

(comment
  (tear-down! test-db)
  ;;
  )

(defmacro with-test-db [bindings & body]
  {:pre [(vector? bindings)
         (= 1 (count bindings))]}
  `(let [~(first bindings) test-db]
     (setup! test-db)
     (try (do ~@body)
          (finally (tear-down! test-db)))))

(deftest inserting-positions
  (with-test-db [db]
    (db/insert-positions! db
                          ["fen-1"
                           "fen-2"
                           "fen-2"])
    (is (= [{:id 1 :fen "fen-1"}
            {:id 2 :fen "fen-2"}]
           (jdbc/query db "SELECT * FROM positions;")))))

(deftest inserting-moves
  (with-test-db [db]
    (db/insert-moves! db
                      [{:fen initial-fen :san "e4" :tag "white-reportoire"}])
    (is (= [{:id 1 :initial_position_id 1 :final_position_id 2 :san "e4"}]
           (jdbc/query db "SELECT * FROM moves;")))))

(deftest inserting-tags
  (with-test-db [db]
    (db/insert-tags! db ["white-reportoire"])
    (is (= [{:id 1 :name "white-reportoire"}]
           (jdbc/query db "SELECT * FROM tags;")))))

(deftest inserting-and-getting-tagged-moves
  (with-test-db [db]
    (db/insert-tags! db ["white-reportoire"
                         "black-reportoire"])
    (db/insert-tagged-moves! db
                             [{:tag "white-reportoire"
                               :fen initial-fen
                               :san "e4"}
                              {:tag "white-reportoire"
                               :fen initial-fen
                               :san "d4"}
                              {:tag "white-reportoire" ;; Duplicate
                               :fen initial-fen
                               :san "d4"}
                              {:tag "black-reportoire"
                               :fen initial-fen
                               :san "c4"}])
    (is (= [{:id 1 :tag_id 1 :move_id 1}
            {:id 2 :tag_id 1 :move_id 2}
            {:id 4 :tag_id 2 :move_id 4}]
           (jdbc/query db "SELECT * FROM tagged_moves;")))
    (is (= [(move/map->Move {:fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                             :san "e4"
                             :tag "white-reportoire"})
            (move/map->Move {:fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                             :san "d4"
                             :tag "white-reportoire"})]
           (db/get-tagged-moves db "white-reportoire")))))

(deftest updating-move-tags
  (with-test-db [db]
    (db/insert-tags! db ["white-reportoire"
                         "deleted-white-reportoire"])
    (db/insert-tagged-moves! db
                             [{:tag "white-reportoire"
                               :fen initial-fen
                               :san "e4"}
                              {:tag "white-reportoire"
                               :fen initial-fen
                               :san "d4"}
                              {:tag "deleted-white-reportoire"
                               :fen initial-fen
                               :san "c4"}])
    (db/update-move-tags! db
                          "deleted-white-reportoire"
                          [{:tag "white-reportoire"
                            :fen initial-fen
                            :san "d4"}])
    (db/update-move-tags! db
                          "white-reportoire"
                          [{:tag "deleted-white-reportoire"
                            :fen initial-fen
                            :san "c4"}])
    (is (= [(move/map->Move
              {:fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
               :san "e4"
               :tag "white-reportoire"})
            (move/map->Move
              {:fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
               :san "c4"
               :tag "white-reportoire"})]
           (db/get-tagged-moves db "white-reportoire")))))
