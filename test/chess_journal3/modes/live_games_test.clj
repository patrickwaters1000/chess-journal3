(ns chess-journal3.live-games-test
  (:require
    [chess-journal3.api :as api]
    [chess-journal3.db :as db]
    [chess-journal3.db-test :as db-test]
    [chess-journal3.engine-stub :refer [engine]]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.modes.live-games :as lg]
    [chess-journal3.state :as s]
    [clojure.test :refer [is deftest]]))

(deftest initializing
  (db-test/with-test-db [db]
    (let [s (s/get-initial-app-state db engine)
          s (#'api/change-mode s "live-games")]
      (is (= "live-games" (:mode s)))
      (is (= #{"live-games"} (set (keys (:mode->local-state s))))))))

(deftest creating-new-games
  (db-test/with-test-db [db]
    (let [s (s/get-initial-app-state db engine)
          s (#'api/change-mode s "live-games")]
      ;;(is (= [{:game-name "game-1" :color "w"} {:game-name "game-2" :color "b"}] (db/list-live-games db)))
      s)))
s (lg/create-new-game s "game-1")
s (update s :battle battle/switch-color)
_ (lg/create-new-game s "game-2")

(s/get-initial-app-state)
