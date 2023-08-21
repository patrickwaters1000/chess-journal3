(ns chess-journal3.modes.openings.review-test
  (:require
    [chess-journal3.api :as api]
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.db-test :as db-test]
    [chess-journal3.line :as line]
    [chess-journal3.modes.menu :as menu]
    [chess-journal3.modes.openings.review :as review]
    [chess-journal3.tree :as tree]
    [clojure.test :refer [is deftest]]))

(def test-db db-test/test-db)

(deftest moving
  (db-test/with-test-db [db]
    (let [tag "white-1e4"
          _ (db/insert-tags! test-db [tag])
          sans ["e4" "c5" "Nf3" "Nc6" "Bb5"]
          line (line/new-from-sans tag c/initial-fen sans)
          moves (line/to-moves line)
          _ (db/insert-tagged-moves! db moves)
          s (-> (api/get-initial-state)
                (review/init tag)
                (review/click-square "E2")
                (review/click-square "E4"))]
      (is (= "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
             (tree/get-fen (:tree s)))))))

;;(def lines
;;  [(line/new-from-sans "white-1e4" c/initial-fen ["e4" "c5" "Nf3" "Nc6" "Bb5"])
;;   (line/new-from-sans "white-1e4" c/initial-fen ["e4" "c6" "Nc3" "d5" "Nf3" "dxe4" "Nxe4"])
;;   (line/new-from-sans "white-1e4" c/initial-fen ["e4" "c6" "Nc3" "d5" "Nf3" "d4" "Ne2"])
;;   ])
