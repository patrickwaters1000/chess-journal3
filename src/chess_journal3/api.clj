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
;;     a. Regenerate review lines after delete.
;;     b. Ensure that current fen is with player to move.
;; 14. If click piece then click square, and move is illegal, square becomes selected. Fix!
;; 16. Show sans with current last san highlighted
;; 17. Undo move doesn't work correctly in review mode (disable it there?)
;; 18. Shuffle review lines

(def initial-state
  {:db db/db
   :fens [fen/initial]
   :sans []
   :idx 0
   :selected-square nil
   :locked-idx 0
   :color "w"
   :mode "review"
   :review-lines []
   :opponent-must-move false})

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

(defroutes app
  (GET "/" []
    (do (println "Sending html")
        (slurp "front/dist/index.html")))
  (GET "/main.js" []
    (do (println "Sending js")
        (slurp "front/dist/main.js")))
  (POST "/start" _
    (do (println "Start")
        (swap! state core/init-review-mode)
        (json/generate-string (view @state))))
  (POST "/click-square" {body :body}
    (let [square (json/parse-string (slurp body))]
      (println (format "Click square %s" square))
      (swap! state core/click-square square)
      (json/generate-string (view @state))))
  (POST "/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (println (format "Alternative move %s" san))
      (swap! state core/alternative-move san)
      (json/generate-string (view @state))))
  (POST "/left" _
    (do (println "Left")
        (swap! state core/previous-frame)
        (json/generate-string (view @state))))
  (POST "/right" _
    (do (println "Right")
        (swap! state core/next-frame)
        (json/generate-string (view @state))))
  (POST "/enter" _
    (do (println "Enter")
        (swap! state core/next-line)
        (json/generate-string (view @state))))
  (POST "/undo" _
    (do (println "Undo")
        (swap! state core/undo-last-move)
        (json/generate-string (view @state))))
  (POST "/switch-mode" _
    (do (println "Switch mode")
        (swap! state core/switch-mode)
        (json/generate-string (view @state))))
  (POST "/switch-lock" _
    (do (println "Lock")
        (swap! state core/switch-lock)
        (json/generate-string (view @state))))
  (POST "/switch-color" _
    (do (println "Switch color")
        (swap! state core/switch-color)
        (json/generate-string (view @state))))
  (POST "/reset" _
    (do (println "Reset")
        (swap! state core/reset)
        (json/generate-string (view @state))))
  (POST "/add-line" _
    (do (println "Add line")
        (swap! state core/add-line!)
        (let [resp (json/generate-string (view @state))]
          (swap! state dissoc :error)
          resp)))
  (POST "/give-up" _
    (do (println "Give up")
        (swap! state core/give-up)
        (json/generate-string (view @state))))
  (POST "/opponent-move" _
    (do (println "Opponent move")
        (swap! state core/opponent-move)
        (json/generate-string (view @state))))
  (POST "/delete-subtree" _
    (do (println "Delete subtree")
        (swap! state core/delete-subtree!)
        (let [resp (json/generate-string (view @state))]
          (swap! state dissoc :error)
          resp))))

(defn -main [& _]
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 3000}))
