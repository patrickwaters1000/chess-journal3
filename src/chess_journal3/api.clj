(ns chess-journal3.api
  (:require
    [cheshire.core :as json]
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.engine :as engine]
    [chess-journal3.fen :as fen]
    [chess-journal3.modes.battle :as battle]
    ;;[chess-journal3.modes.endgames :as endgames]
    ;;[chess-journal3.modes.games :as games]
    [chess-journal3.modes.live-games :as live-games]
    [chess-journal3.modes.menu :as menu]
    [chess-journal3.modes.openings.editor :as openings-editor]
    [chess-journal3.modes.openings.review :as openings-review]
    ;;[chess-journal3.modes.setup :as setup]
    [chess-journal3.pgn :as pgn]
    [chess-journal3.state :as s]
    ;;[chess-journal3.tablebase :as tablebase]
    [chess-journal3.utils :as u]
    [clj-time.core :as t]
    [clojure.string :as string]
    [compojure.core :refer :all]
    [org.httpkit.server :refer [run-server]]
    [ring.middleware.params :as rmp])
  (:import
    (chess_journal3.modes.battle Battle)))

;; Build front end:
;; > cd front && npx webpack
;;
;; Run app:
;; > lein run -m chess-journal3.api
;;
;; TODO
;; 16. Show sans with current last san highlighted
;; 18. Add explain move feature in openings review (use inputs explanation, and
;;     can read it later).
;; 21. Move number in alternative moves buttons
;; 23. Instead of `opponentMustMove`, a `pendingMoveId`
;; 25. Add opponent lines to endgames mode
;; 32. Alternative move in review mode tries to go up from the root of the tree.
;; 33. Alternative move fails in openings editor.
;; 34. Alternative move buttons persist after exiting to the main menu.
;; 35. Moving pieces fails in review mode unless on a leaf reportoire.

(defn- prepare-response [app-state]
  (let [v (.makeClientView (s/get-local-state @s/app-state))]
    (json/generate-string v)))

(defn- route [f & args]
  (apply swap! s/app-state s/update-local-state f args)
  (let [resp (prepare-response @s/app-state)]
    (println (s/get-local-state @s/app-state))
    (swap! s/app-state s/update-local-state #(.cleanUp %))
    resp))

(defn- global-route [f & args]
  (apply swap! s/app-state f args)
  (let [resp (prepare-response @s/app-state)]
    (println (s/get-local-state @s/app-state))
    (swap! s/app-state s/update-local-state #(.cleanUp %))
    resp))

(defn- change-mode [app-state mode]
  (let [init-fn (case mode
                  "menu" menu/init
                  "openings-editor" openings-editor/init
                  "openings-review" openings-review/init
                  "battle" battle/init
                  ;;"games" games/init
                  ;;"setup" setup/init
                  ;;"endgames" endgames/init
                  "live-games" live-games/init)
        local-state (init-fn app-state)]
    (-> app-state
        (assoc-in [:mode->local-state mode] local-state)
        (assoc :mode mode))))

(defn- create-live-game [app-state game-name]
  (let [^Battle local-state (s/get-local-state app-state)
        db (.db app-state)
        color (.color local-state)
        line (.line local-state)]
    (println (format "Creating game %s" game-name))
    (live-games/create-new-game db game-name color line)
    (-> app-state
        (change-mode "live-games")
        (s/update-local-state live-games/cycle-to-game game-name))))

(defroutes openings-review-routes
  (POST "/openings-review/next-reportoire" _ (route openings-review/next-reportoire))
  (POST "/openings-review/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (route openings-review/alternative-move san)))
  (POST "/openings-review/switch-lock" _ (route openings-review/switch-lock))
  (POST "/openings-review/opponent-move" _ (route openings-review/opponent-move))
  (POST "/openings-review/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route #(.prevFrame %))
        "ArrowRight" (route #(.nextFrame %))
        "Enter" (case (.getMode @s/app-state)
                  "openings-review" (route openings-review/next-line))
        (println (format "Unexpected keyboard event %s" keycode))))))

(defroutes openings-editor-routes
  (POST "/openings-editor/next-reportoire" _ (route openings-editor/next-reportoire))
  (POST "/openings-editor/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (route openings-editor/alternative-move san)))
  (POST "/openings-editor/switch-lock" _ (route openings-editor/switch-lock))
  (POST "/openings-editor/reset" _ (route openings-editor/reset-board))
  (POST "/openings-editor/add-line" _ (route openings-editor/add-line!))
  (POST "/openings-editor/delete-subtree" _ (route openings-editor/delete-subtree!))
  (POST "/openings-editor/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route #(.prevFrame %))
        "ArrowRight" (route #(.nextFrame %))
        ("Delete"
         "Backspace") (route openings-editor/undo-last-move)
        (println (format "Unexpected keyboard event %s" keycode))))))

;; Start position can be standard or other
;; The game can be live or complete
;; Game can be saved. The first time a game is s
(defroutes battle-routes
  (POST "/battle/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route #(.prevFrame %))
        "ArrowRight" (route #(.nextFrame %))
        (println (format "Unexpected keyboard event %s" keycode)))))
  (POST "/battle/opponent-move" _ (route battle/opponent-move))
  (POST "/battle/set-engine-elo" {body :body}
    (let [elo (Integer/parseInt (json/parse-string (slurp body)))]
      (println (format "Setting elo to %d" elo))
      (route battle/set-engine-elo elo)))
  (POST "/battle/set-engine-movetime" {body :body}
    (let [movetime-millis (Integer/parseInt (json/parse-string (slurp body)))
          movetime (t/millis movetime-millis)]
      (route battle/set-engine-movetime movetime)))
  (POST "/battle/new-live-game" {body :body}
    (let [game-name (json/parse-string (slurp body))]
      (global-route create-live-game game-name))))

(defroutes live-games-routes
  (POST "/live-games/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route #(.prevFrame %))
        "ArrowRight" (route #(.nextFrame %))
        (println (format "Unexpected keyboard event %s" keycode)))))
  (POST "/live-games/opponent-move" _ (route live-games/opponent-move))
  (POST "/live-games/save-game" _ (route live-games/save-game))
  (POST "/live-games/end-game" {body :body}
    (let [score (json/parse-string (slurp body))]
      (route live-games/end-game score)))
  (POST "/live-games/cycle-game" {body :body}
    (let [increment (json/parse-string (slurp body))]
      (route live-games/cycle-game increment)))
  (POST "/live-games/set-engine-elo" {body :body}
    (let [elo (Integer/parseInt (json/parse-string (slurp body)))]
      (route live-games/set-engine-elo elo)))
  (POST "/live-games/set-engine-movetime" {body :body}
    (let [movetime-millis (Integer/parseInt (json/parse-string (slurp body)))
          movetime (t/millis movetime-millis)]
      (route live-games/set-engine-movetime movetime))))

(defroutes common-routes
  (GET "/" [] (slurp "front/dist/index.html"))
  (GET "/main.js" [] (slurp "front/dist/main.js"))
  (POST "/start" _ (route menu/init))
  (POST "/click-square" {body :body}
    (let [square (json/parse-string (slurp body))]
      (route #(.clickSquare % square))))
  (POST "/switch-color" _ (route #(.switchColor %)))
  (POST "/cycle-promote-piece" _ (route u/cycle-promote-piece))
  (POST "/set-mode" {body :body}
    (let [mode (json/parse-string (slurp body))]
      (println mode)
      (global-route change-mode mode))))

;; TODO Client should prefix requests with the current mode. Split this up into
;; simpler APIs in each mode namespace.
(defroutes all-routes
  openings-editor-routes
  openings-review-routes
  battle-routes
  live-games-routes
  common-routes)

;; When calling a state function from a mode ns, should check that the current mode matches the ns.

(defn wrap-print-url [handler]
  (fn [request]
    (println (:uri request))
    (handler request)))

(defn -main [& _]
  (let [engine (engine/make)
        initial-state (s/get-initial-app-state db/db engine)]
    (reset! s/app-state initial-state)
    (println "Ready!")
    (run-server (-> all-routes
                    rmp/wrap-params
                    wrap-print-url)
                {:port 3000})))

;;(defroutes endgames-routes
;;  (POST "/endgames/coarsen-class" _ (route endgames/coarsen-class))
;;  (POST "/endgames/refine-class" _ (route endgames/refine-class))
;;  (POST "/endgames/next-position" _ (route endgames/next-position))
;;  (POST "/endgames/prev-position" _ (route endgames/prev-position))
;;  (POST "/endgames/opponent-move" _ (route endgames/prev-position))
;;  (POST "/endgames/toggle-evaluation" _ (route endgames/toggle-evaluation)))

;;(POST "/key" {body :body}
;;      (let [keycode (json/parse-string (slurp body))]
;;        (case keycode
;;          "ArrowLeft" (route #(.prevFrame %))
;;          "ArrowRight" (route #(.nextFrame %))
;;          ;; ("p" "n" "b" "r" "q" "k" "P" "N" "B" "R" "Q" "K") (do (println (str "Key = " keycode)) (route "Set selected piece" setup/set-selected-piece keycode))
;;          "l" (do (u/pprint-state @state)
;;                  (route identity))
;;          (println (format "Unexpected keyboard event %s" keycode)))))
;;
;;(POST "/select-game" {body :body} (let [game-tag (json/parse-string (slurp body))] (route "Select game" games/select-game game-tag)))
;;
;;(POST "/reboot-engine" _ (route "Reboot engine" core/reboot-engine!))
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
