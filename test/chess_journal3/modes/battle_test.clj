(ns chess-journal3.modes.battle-test
  (:require
    [chess-journal3.api :as api]
    [chess-journal3.engine-stub :refer [engine]]
    [chess-journal3.line :as line]
    [chess-journal3.modes.battle :as b]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.state :as s]
    [clojure.test :refer [is deftest]]))

(deftest moving
  (let [s (s/get-initial-app-state nil engine)
        s (#'api/change-mode s "battle")
        s (s/update-local-state s battle/click-square "E2")
        s (s/update-local-state s battle/click-square "E4")
        s (s/update-local-state s battle/opponent-move)]
    (is (= ["e4" "Na6"]
           (-> s s/get-local-state (.line) line/get-sans)))))

(deftest deselecting-seleted-square
  (let [s (s/get-initial-app-state nil engine)
        s (#'api/change-mode s "battle")
        s (s/update-local-state s battle/click-square "E2")
        s (s/update-local-state s battle/click-square "E2")]
    (is (nil? (.selected_square (s/get-local-state s))))))

(deftest changing-seleted-piece
  (let [s (s/get-initial-app-state nil engine)
        s (#'api/change-mode s "battle")
        s (s/update-local-state s battle/click-square "E2")
        s (s/update-local-state s battle/click-square "D2")]
    (is (= "D2" (.selected_square (s/get-local-state s))))))
