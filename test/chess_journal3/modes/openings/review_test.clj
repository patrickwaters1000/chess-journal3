(ns chess-journal3.modes.openings.review-test
  (:require
    [chess-journal3.api :as api]
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.db-test :as db-test]
    [chess-journal3.line :as line]
    [chess-journal3.modes.menu :as menu]
    [chess-journal3.modes.openings.review :as review]
    [chess-journal3.testlib :as testlib]
    [chess-journal3.tree :as tree]
    [clojure.test :refer [is deftest]]))

(deftest moving
  (db-test/with-test-db [db]
    (let [tag "white-1e4"
          sans ["e4" "c5" "Nf3" "Nc6" "Bb5"]
          _ (testlib/add-san-seqs-to-db db tag [sans])
          s1 (-> (api/get-initial-state)
                 (review/init tag)
                 (review/click-square "E2")
                 (review/click-square "E4"))
          s2 (review/next-line s1)]
      (is (= "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
             (tree/get-fen (:tree s1))))
      (is (= c/initial-fen
             (tree/get-fen (:tree s2)))))))

(deftest using-locked-variations
  (db-test/with-test-db [db]
    (let [tag "white-1e4"
          san-seqs [["e4" "c6" "Nc3" "d5" "Nf3" "dxe4" "Nxe4"]
                    ["e4" "c6" "Nc3" "d5" "Nf3" "d4" "Ne2"]]
          _ (testlib/add-san-seqs-to-db db tag san-seqs)
          s1 (-> (api/get-initial-state)
                 (review/init tag)
                 (review/click-square "E2")
                 (review/click-square "E4")
                 (review/opponent-move)
                 (review/click-square "B1")
                 (review/click-square "C3"))
          (review/opponent-move)
            (review/click-square "G1")
          (review/click-square "F3")
            (review/opponent-move)
          (review/switch-lock)
            (review/click-square "C3")
          (review/click-square "E4")
            s2 (-> s1
                   (review/next-line))
            s3 (-> s2
                   (review/switch-lock)
                   (review/next-line))]
      (is (= ""
             (tree/get-fen (:tree s1))))
      (is (= ""
             (tree/get-fen (:tree s2))))
      (is (= ""
             (tree/get-fen (:tree s3)))))))

(line/new-from-sans "white-1e4" c/initial-fen)
(line/new-from-sans "white-1e4" c/initial-fen)
;;(def lines
;;  [(line/new-from-sans "white-1e4" c/initial-fen ["e4" "c5" "Nf3" "Nc6" "Bb5"])

;;   ])
