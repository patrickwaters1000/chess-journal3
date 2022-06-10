(ns chess-journal3.api
  (:require
    [cheshire.core :as json]
    [chess-journal3.core :as core]
    [chess-journal3.db :as db]
    [chess-journal3.engine :as engine]
    [chess-journal3.fen :as fen]
    [chess-journal3.pgn :as pgn]
    [clojure.string :as string]
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

(def initial-state
  {:db db/db
   :color "w"
   :fen->moves {}
   :review-lines []
   :mode "review"
   :locked-idx 0
   :fens [fen/initial]
   :sans []
   :idx 0
   :selected-square nil
   :opponent-must-move false
   :game->score nil})

(def state
  (atom initial-state))

;; Move to PGN namespace

(defn log-state []
  (println "State:")
  (->> @state
       (map (fn [[k v]]
              (let [v-str (str v)
                    v-str (if (> (count v-str) 1000)
                            (subs v-str 0 1000)
                            v-str)]
                (format "%s: %s\n" (name k) v-str))))
       (run! println))
  (println ""))

(defn view
  "Prepares a view of the app state, to be displayed by the client."
  [state]
  {:fen (core/get-fen state)
   :sans (:sans state)
   :idx (:idx state)
   :mode (:mode state)
   :flipBoard (= "b" (:color state))
   :isLocked (not= 0 (:locked-idx state))
   :selectedSquare (:selected-square state)
   :opponentMustMove (:opponent-must-move state)
   :alternativeMoves (if-not (= "battle" (:mode state))
                       (core/get-alternative-moves state)
                       [])
   :games (when (= "games" (:mode state))
            (core/get-games state))
   :pgn (pgn/sans->pgn (:sans state))
   :error (:error state)})

(defn route [msg f & args]
  (println msg)
  (apply swap! state f args)
  (log-state)
  (let [resp (json/generate-string (view @state))]
    (swap! state dissoc :error)
    resp))

(defroutes app
  (GET "/" [] (slurp "front/dist/index.html"))
  (GET "/main.js" [] (slurp "front/dist/main.js"))
  (POST "/start" _ (route "Start" core/init-review-mode))
  (POST "/click-square" {body :body}
    (let [square (json/parse-string (slurp body))]
      (route (format "Click square %s" square) core/click-square square)))
  (POST "/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (route (format "Alternative move %s" san) core/alternative-move san)))
  (POST "/left" _ (route "Left" core/previous-frame))
  (POST "/right" _ (route "Right" core/next-frame))
  (POST "/enter" _ (route "Next line" core/next-line))
  (POST "/undo" _ (route "Undo" core/undo-last-move))
  (POST "/edit-mode" _ (route "Switch mode" core/switch-mode "edit"))
  (POST "/review-mode" _ (route "Switch mode" core/switch-mode "review"))
  (POST "/battle-mode" _ (route "Switch mode" core/switch-mode "battle"))
  (POST "/games-mode" _ (route "Switch mode" core/switch-mode "games"))
  (POST "/select-game" {body :body}
    (let [game-tag (json/parse-string (slurp body))]
      (route "Select game" core/cycle-to-game game-tag)))
  (POST "/switch-lock" _ (route "Lock" core/switch-lock))
  (POST "/switch-color" _ (route "Switch color" core/switch-color))
  (POST "/reset" _ (route "Reset board" core/reset-board))
  (POST "/add-line" _ (route "Add line" core/add-line!))
  (POST "/give-up" _ (route "Give up" core/give-up))
  (POST "/opponent-move" _ (route "Opponent move" core/opponent-move))
  (POST "/delete-subtree" _ (route "Delete subtree" core/delete-subtree!))
  (POST "/reboot-engine" _ (route "Reboot engine" core/reboot-engine!)))

(defn -main [& _]
  (println "Ready!")
  (engine/reboot!)
  (run-server (rmp/wrap-params app)
              {:port 3000}))
