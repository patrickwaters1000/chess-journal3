(ns chess-journal3.core-test
  (:require
    [chess-journal3.core :as core]
    [chess-journal3.db :as db]
    [chess-journal3.db-test :as db-test :refer [db]]
    [clojure.test :refer [is deftest]]))

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
(def fen-after-1e4 "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")

(deftest checking-whose-move-it-is
  (is (#'core/players-move? {:fens [initial-fen]
                             :color "w"
                             :idx 0}))
  (is (#'core/opponents-move? {:fens [initial-fen
                                      fen-after-1e4]
                               :color "w"
                               :idx 1}))
  (is (#'core/players-move? {:fens [initial-fen
                                    fen-after-1e4]
                             :color "b"
                             :idx 1})))

(deftest moving
  (is (= {:fens [initial-fen fen-after-1e4]
          :sans ["e4"]
          :idx 1}
         (core/move {:fens [initial-fen]
                     :sans []
                     :idx 0}
                    {:from "E2"
                     :to "E4"}))))

(require '[clojure.java.jdbc :as jdbc])

(deftest getting-line-data
  (is (= [{:tag "white-reportoire"
           :initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
           :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
           :san "e4"}]
         (core/get-line-data {:fens [initial-fen
                                     fen-after-1e4]
                              :sans ["e4"]
                              :idx 0
                              :color "w"}))))

(deftest adding-lines
  (db-test/reset! db)
  (db/insert-tags! db ["white-reportoire"])
  (core/add-line! {:fens [initial-fen
                          fen-after-1e4]
                   :sans ["e4"]
                   :idx 0
                   :color "w"})
  (db/get-tagged-moves db "white-reportoire" initial-fen)
  (jdbc/query db "SELECT * FROM tagged_moves")
  (jdbc/execute! db "
INSERT INTO positions(fen)
VALUES
  ('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'),
  ('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1')")
  (jdbc/query db "SELECT * FROM positions")
  (jdbc/query db "SELECT * FROM moves")
  (jdbc/query db "SELECT * FROM tags"))

(deftest switching-color
  (is (= {}
         (core/switch-color
           {}))))
