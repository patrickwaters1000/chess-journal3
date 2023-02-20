(ns chess-journal3.api
  (:require
    [cheshire.core :as json]
    [chess-journal3.constants :as c]
    ;;[chess-journal3.core :as core]
    [chess-journal3.db :as db]
    ;;[chess-journal3.engine :as engine]
    [chess-journal3.fen :as fen]
    ;;[chess-journal3.modes.endgames :as endgames]
    ;;[chess-journal3.modes.games :as games]
    ;;[chess-journal3.modes.live-games :as live-games]
    [chess-journal3.modes.menu :as menu]
    ;;[chess-journal3.modes.battle :as battle]
    [chess-journal3.modes.openings.editor :as openings-editor]
    [chess-journal3.modes.openings.review :as openings-review]
    ;;[chess-journal3.modes.setup :as setup]
    [chess-journal3.pgn :as pgn]
    ;;[chess-journal3.tablebase :as tablebase]
    [chess-journal3.utils :as u]
    [clj-time.core :as t]
    [clojure.string :as string]
    [compojure.core :refer :all]
    [org.httpkit.server :refer [run-server]]
    [ring.middleware.params :as rmp]))

;; Build front end:
;; > cd front && npx webpack
;;
;; Run app:
;; > lein run -m chess-journal3.api
;;
;; TODO
;; 13. Support deleting subtree from review mode
;;     a. [DONE] Regenerate review lines after delete.
;;     b. Ensure that current fen is with player to move.
;; 16. Show sans with current last san highlighted
;; 17. Undo move doesn't work correctly in review mode (disable it there?)
;; 18. Add explain move feature
;; 20. Right in review mode causes idx oob error
;; 21. Move number in alternative moves buttons
;; 22. Having alternative moves work differently in edit mode is too confusing
;; 23. Instead of `opponentMustMove`, a `pendingMoveId`
;; 25. Add opponent lines to endgames mode
;; 28. Init functions for various modes are not complete. E.g., reset board to
;;     initial fen when starting review mode.
;; 30. Add "force move" to endgames -- prompt the user for a different computer move.
;; 31. Each mode should have an exit function to clean up the app state when
;;     leaving the mode.

(def state (atom (menu/init {:color "w" :db db/db})))

(defn route [f & args]
  (apply swap! state f args)
  (let [v (.makeClientView @state)
        resp (json/generate-string v)]
    (swap! state #(.cleanUp %))
    resp))

;; TODO Client should prefix requests with the current mode. Split this up into
;; simpler APIs in each mode namespace.
(defroutes app
  (GET "/" [] (slurp "front/dist/index.html"))
  (GET "/main.js" [] (slurp "front/dist/main.js"))
  (POST "/start" _ (route menu/init))
  (POST "/click-square" {body :body}
    (let [square (json/parse-string (slurp body))]
      (route #(.clickSquare %) square)))
  (POST "/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))
          f (case #(.getMode @state)
              "openings-review" openings-review/alternative-move
              "openings-editor" openings-editor/alternative-move)]
      (route f san)))
  (POST "/set-mode" {body :body}
    (let [mode (json/parse-string (slurp body))
          f (case mode
              "menu" menu/init
              "openings-editor" openings-editor/init
              "openings-review" openings-review/init
              ;;"battle" battle/init
              ;;"games" games/init
              ;;"setup" setup/init
              ;;"endgames" endgames/init
              ;;"live-games" live-games/init
              )]
      (route f)))
  ;;(POST "/select-game" {body :body} (let [game-tag (json/parse-string (slurp body))] (route "Select game" games/select-game game-tag)))
  ;;(POST "/set-elo" {body :body} (let [elo (Integer/parseInt (json/parse-string (slurp body)))] (route "Set Elo" core/set-elo elo)))
  (POST "/switch-lock" _
    (case (.getMode @state)
      "openings-review" (route openings-review/switch-lock)
      "openings-editor" (route openings-editor/switch-lock)))
  (POST "/switch-color" _ (route #(.switchColor %)))
  (POST "/reset" _
    (case (.getMode @state)
      "openings-editor" (route openings-editor/reset-board)
      "openings-review" (route openings-review/reset-board)))
  (POST "/add-line" _
    (case (.getMode @state)
      "openings-editor" (route openings-editor/add-line!)))
  (POST "/give-up" _
    (case (.getMode @state)
      "openings-review" (route openings-review/give-up)))
  (POST "/opponent-move" _
    (case (.getMode @state)
      "openings-review" (route openings-review/opponent-move)))
  (POST "/delete-subtree" _
    (case (.getMode @state)
      "openings-editor" (route openings-editor/delete-subtree!)))
  ;;(POST "/reboot-engine" _ (route "Reboot engine" core/reboot-engine!))
  (POST "/cycle-promote-piece" _ (route u/cycle-promote-piece))
  ;;(POST "/switch-active-color" _ (route "Switch active color" setup/switch-active-color))
  ;;(POST "/save-endgame" _ (route "Save endgame" endgames/save))
  ;;(POST "/delete-endgame" _ (route "Delete endgame" endgames/delete))
  ;;(POST "/cycle-endgame-class" {body :body} (let [level (json/parse-string (slurp body)) msg (format "Cycle endgame class at level %s" level)] (println (dissoc @state :endgames)) (route msg endgames/cycle-class level)))
  ;;(POST "/refine-endgame-class" _ (route "Refine endgame class" endgames/refine-class))
  ;;(POST "/next-endgame" _ (route "Next endgame" endgames/next))
  ;;(POST "/previous-endgame" _ (route "Previous endgame" endgames/previous))
  ;;(POST "/toggle-evaluation" _ (route "Toggle evaluation" endgames/toggle-evaluation))
  ;;(POST "/force-move" {body :body} (let [move (json/parse-string (slurp body))] (route "Force move" endgames/force-move move)))
  ;;(POST "/new-live-game" {body :body} (let [game-name (json/parse-string (slurp body))] (route "New live game" live-games/new game-name)))
  ;;(POST "/cycle-live-game" _ (route "Cycle live game" live-games/cycle-game))
  ;;(POST "/save-live-game" _ (route "Save live game" live-games/save))
  ;;(POST "/end-live-game" {body :body} (let [result (json/parse-string (slurp body))] (route "End live game" live-games/end result)))
  (POST "/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route #(.prevFrame %))
        "ArrowRight" (route #(.nextFrame %))
        "Enter" (case (.getMode @state)
                  "openings-review" (route openings-review/next-line))
        ("Delete"
         "Backspace") (case (.getMode @state)
                        "openings-editor" (route openings-editor/undo-last-move))
        ;; ("p" "n" "b" "r" "q" "k" "P" "N" "B" "R" "Q" "K") (do (println (str "Key = " keycode)) (route "Set selected piece" setup/set-selected-piece keycode))
        "l" (do (u/pprint-state)
                (route identity))
        (println (format "Unexpected keyboard event %s" keycode))))))

;; When calling a state function from a mode ns, should check that the current mode matches the ns.

(defn -main [& _]
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 3000}))

(comment
  (def s1 initial-state)
  (def s2 (review/init s1))
  (def s3 (core/click-square s2 "E2"))
  (def s4 (core/click-square s2 "E4"))
  ;;
  )
