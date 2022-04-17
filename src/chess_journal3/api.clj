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

;; TODO
;; 4. Delete subtree
;; 8. Show alternative moves

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
   :opponentMustMove (:opponent-must-move state)})

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
      (println (dissoc @state :review-lines))
      (swap! state core/click-square square)
      (println (dissoc @state :review-lines))
      (json/generate-string (view @state))))
  ;; TODO
  (POST "/left" _
    (do (println "Left")
        (json/generate-string (view @state))))
  ;; TODO
  (POST "/right" _
    (do (println "Right")
        (swap! state core/next-line)
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
        (json/generate-string (view @state))))
  (POST "/give-up" _
    (do (println "Give up")
        ;; (swap! state core/give-up)
        (json/generate-string (view @state))))
  (POST "/opponent-move" _
    (do (println "Opponent move")
        (swap! state core/opponent-move)
        (json/generate-string (view @state)))))

(defn -main [& _]
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 3000}))
