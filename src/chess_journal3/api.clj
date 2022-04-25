(ns chess-journal3.api
  (:require
    [cheshire.core :as json]
    [chess-journal3.core :as core]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
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
;; 19. Show number of lines in review set

(def initial-state
  {:db db/db
   :fens [fen/initial]
   :sans []
   :idx 0
   :selected-square nil
   :locked-idx 0
   :color "w"
   :mode "review"
   :opponent-must-move false
   :review-lines []
   :fen->moves {}})

(def state
  (atom initial-state))

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
   :alternativeMoves (core/get-alternative-moves state)
   :error (:error state)})

(defn route [msg f & args]
  (println msg)
  (apply swap! state f args)
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
  (POST "/switch-mode" _ (route "Switch mode" core/switch-mode))
  (POST "/switch-lock" _ (route "Lock" core/switch-lock))
  (POST "/switch-color" _ (route "Switch color" core/switch-color))
  (POST "/reset" _ (route "Reset board" core/reset-board))
  (POST "/add-line" _ (route "Add line" core/add-line!))
  (POST "/give-up" _ (route "Give up" core/give-up))
  (POST "/opponent-move" _ (route "Opponent move" core/opponent-move))
  (POST "/delete-subtree" _ (route "Delete subtree" core/delete-subtree!)))

(defn -main [& _]
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 3000}))
