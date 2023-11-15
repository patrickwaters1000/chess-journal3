(ns chess-journal3.live-games-test
  (:require
    [chess-journal3.api :as api]
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.db-test :as db-test]
    [chess-journal3.engine-stub :refer [engine]]
    [chess-journal3.line :as line]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.modes.live-games :as lg]
    [chess-journal3.state :as s]
    [clojure.test :refer [is deftest]]))

(defn- do-extra-test-db-setup! [db]
  (db/insert-tag! db "live-games"))

(deftest initializing
  (db-test/with-test-db [db]
    (let [s (s/get-initial-app-state db engine)
          s (#'api/change-mode s "live-games")]
      (is (= "live-games" (:mode s)))
      (is (= #{"live-games"} (set (keys (:mode->local-state s))))))))

(deftest creating-a-new-game+playing-moves+saving
  (db-test/with-test-db [db]
    (do-extra-test-db-setup! db)
    (let [;; Create game
          s (s/get-initial-app-state db engine)
          s (#'api/change-mode s "battle")
          _ (lg/create-new-game db "game-1" "w" (.line (s/get-local-state s)))
          ;; Play some moves
          s (#'api/change-mode s "live-games")
          s (s/update-local-state s #(.clickSquare % "E2"))
          s (s/update-local-state s #(.clickSquare % "E4"))
          s (s/update-local-state s lg/opponent-move)
          ;; Save for later
          _ (lg/save-game (s/get-local-state s))
          local-state (s/get-local-state s)
          db-line (#'lg/get-line db "game-1")]
      (is (= {:game-name "game-1" :color "w"}
             (#'lg/get-current-game-info local-state)))
      (is (= ["e4" "Na6"]
             (line/get-sans (get-in local-state [:battle :line]))))
      (is (= ["e4" "Na6"]
             (line/get-sans db-line))))))

(deftest switching-between-games
  (db-test/with-test-db [db]
    (do-extra-test-db-setup! db)
    (let [line-1 (line/new-from-sans nil c/initial-fen ["e4" "c5" "Nf3"])
          line-2 (line/new-from-sans nil c/initial-fen ["d4" "d5" "Nf3" "e6" "c4" "Nf6"])
          line-3 (line/new-from-sans nil c/initial-fen ["c4" "e5" "Nc3" "Nf6"])
          _ (lg/create-new-game db "game-1" "w" line-1)
          _ (lg/create-new-game db "game-2" "b" line-2)
          _ (lg/create-new-game db "game-3" "b" line-3)
          s (s/get-initial-app-state db engine)
          s (#'api/change-mode s "live-games")
          s2 (s/update-local-state s lg/cycle-game 1)
          s3 (s/update-local-state s2 lg/cycle-game 1)
          s4 (s/update-local-state s3 lg/cycle-game 1)
          s5 (s/update-local-state s4 lg/cycle-game -1)]
      (is (= ["game-2" "game-3" "game-1" "game-3"]
             (map (comp :game-name #'lg/get-current-game-info s/get-local-state)
                  [s2 s3 s4 s5])))
      (is (= ["c4" "e5" "Nc3" "Nf6"]
             (line/get-sans (.line (.battle (s/get-local-state s3)))))))))

(deftest ending-a-game
  (db-test/with-test-db [db]
    (do-extra-test-db-setup! db)
    (let [line-1 (line/new-from-sans nil c/initial-fen ["e4" "c5" "Nf3"])
          line-2 (line/new-from-sans nil c/initial-fen ["d4" "d5" "Nf3" "e6" "c4" "Nf6"])
          _ (lg/create-new-game db "game-1" "w" line-1)
          _ (lg/create-new-game db "game-2" "b" line-2)
          s (s/get-initial-app-state db engine)
          s (#'api/change-mode s "live-games")
          s (s/update-local-state s lg/end-game "1/2-1/2")]
      (is (= [{:game-name "game-2" :color "b"}] (db/list-live-games db)))
      (is (= {:game-name "game-2" :color "b"}
             (#'lg/get-current-game-info (s/get-local-state s)))))))
