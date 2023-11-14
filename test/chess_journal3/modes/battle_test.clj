(ns chess-journal3.modes.battle-test
  (:require
    [chess-journal3.api :as api]
    [chess-journal3.engine-stub :refer [engine]]
    [chess-journal3.modes.battle :as b]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.state :as s]
    [clojure.test :refer [is deftest]]))

(require '[chess-journal3.line :as line])

(let [line {:tag nil
            :fens
              ["rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
               "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"]
            :sans ["e4"]
            :idx 1}
      move {:fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
            :san "a5"
            :tag nil}]
  (line/apply-move line move))

(let [s (s/get-initial-app-state nil engine)
      s (#'api/change-mode s "battle")
      s (s/update-local-state s battle/click-square "E2")
      s (s/update-local-state s battle/click-square "E4")
      s (s/update-local-state s battle/opponent-move)]
  s)

(let [s (s/get-initial-app-state nil engine)
      s (#'api/change-mode s "battle")
      s (s/update-local-state s battle/click-square "E2")
      s (s/update-local-state s battle/click-square "E4")
      s (s/update-local-state s battle/click-square "E4")
      ;;s (s/update-local-state s battle/opponent-move)
      ls (get-in s [:mode->local-state "battle"])
      {:keys [engine engine-movetime engine-elo]} ls
      fen (#'battle/get-fen ls)
      move (.getMove engine fen engine-movetime engine-elo)
      ;;ls (update ls :line line/apply-move move)
      ]
  [(:line ls)
   move])
