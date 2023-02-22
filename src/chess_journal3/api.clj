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
;; 18. Add explain move feature
;; 21. Move number in alternative moves buttons
;; 23. Instead of `opponentMustMove`, a `pendingMoveId`
;; 25. Add opponent lines to endgames mode
;; 28. Init functions for various modes are not complete. E.g., reset board to
;;     initial fen when starting review mode.
;; 31. Each mode should have an exit function to clean up the app state when
;;     leaving the mode.

(def state (atom (menu/init {:color "w" :db db/db})))

(defn route [f & args]
  (apply swap! state f args)
  (let [v (.makeClientView @state)
        resp (json/generate-string v)]
    (swap! state #(.cleanUp %))
    resp))

(defroutes openings-review-routes
  (POST "/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (route openings-review/alternative-move san)))
  (POST "/switch-lock" _ (route openings-review/switch-lock))
  (POST "/opponent-move" _ (route openings-review/opponent-move))
  (POST "/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route #(.prevFrame %))
        "ArrowRight" (route #(.nextFrame %))
        "Enter" (case (.getMode @state)
                  "openings-review" (route openings-review/next-line))
        (println (format "Unexpected keyboard event %s" keycode))))))

(defroutes openings-editor-routes
  (POST "/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (route openings-editor/alternative-move san)))
  (POST "/switch-lock" _ (route openings-editor/switch-lock))
  (POST "/reset" _ (route openings-editor/reset-board))
  (POST "/add-line" _ (route openings-editor/add-line!))
  (POST "/delete-subtree" _ (route openings-editor/delete-subtree!))
  (POST "/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route #(.prevFrame %))
        "ArrowRight" (route #(.nextFrame %))
        ("Delete"
         "Backspace") (route openings-editor/undo-last-move)
        (println (format "Unexpected keyboard event %s" keycode))))))

;; TODO Client should prefix requests with the current mode. Split this up into
;; simpler APIs in each mode namespace.
(defroutes app
  (GET "/" [] (slurp "front/dist/index.html"))
  (GET "/main.js" [] (slurp "front/dist/main.js"))
  (POST "/start" _ (route menu/init))
  (context "/openings-review" [req] openings-review-routes)
  (context "/openings-editor" [req] openings-editor-routes)
  (POST "/click-square" {body :body}
    (let [square (json/parse-string (slurp body))]
      (route #(.clickSquare %1 %2) square)))
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
  (POST "/switch-color" _ (route #(.switchColor %)))

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
        ;; ("p" "n" "b" "r" "q" "k" "P" "N" "B" "R" "Q" "K") (do (println (str "Key = " keycode)) (route "Set selected piece" setup/set-selected-piece keycode))
        "l" (do (u/pprint-state @state)
                (route identity))
        (println (format "Unexpected keyboard event %s" keycode))))))

;; When calling a state function from a mode ns, should check that the current mode matches the ns.

(defn -main [& _]
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 3000}))

(comment
  (do (def s1 @state)
      (def s2 (openings-review/init s1))
      (def s3 (openings-review/click-square s2 "E2"))
      (def s4 (openings-review/click-square s3 "E4"))
      (def s5 (openings-review/opponent-move s4))
      (def s6 (openings-review/click-square s5 "G1"))
      (def s7 (openings-review/click-square s6 "F3"))
      (def s8 (openings-review/opponent-move s7))
      (def s9 (openings-review/click-square s8 "D2"))
      (def s10 (openings-review/click-square s9 "D4"))
      (def s11 (openings-review/opponent-move s10))
      (def s12 (openings-review/click-square s11 "F1"))
      (def s13 (openings-review/click-square s12 "C4")))
  (require '[chess-journal3.fen :as fen])
  (let [{:keys [tree
                color]} s12
        fen (tree/get-fen tree)]
    (fen/players-move? fen color))
  (openings-review/move-to-square-is-correct? s12 "C4")

  (def s6 (openings-review/next-line s5))

  (def s6 (openings-review/alternative-move s5 "c6"))
  (require '[chess-journal3.tree :as tree])
  (def t (:tree s6))
  (#'tree/can-go-down? t)
  ()
  (tree/get-moves (:tree s6) "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2")
  "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
  ;;
  )
