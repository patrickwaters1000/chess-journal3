(ns chess-journal3.api
  (:require
    [cheshire.core :as json]
    [chess-journal.chess :as chess]
    [clj-time.core :as t]
    [clojure.string :as string]
    ;;[chess-journal3.engine :as engine]
    [compojure.core :refer :all]
    [org.httpkit.server :refer [run-server]]
    [ring.middleware.params :as rmp]))

(defn sget [m k]
  (if (contains? (set (keys m)) k)
    (get m k)
    (throw (Exception. (format "Map %s does not have key %s"
                               m k)))))

(defroutes app
  ;;(GET "/" [] (slurp "dist/endgameTrainer.html"))
  ;;(GET "/endgameTrainer.js" [] (slurp "dist/endgameTrainer.js"))
  (POST "/click-square" {body :body}
    ;; Return the new fen if proposed move is legal
    (let [data (json/parse-string (slurp body))
          _ (println "request body: " (str data))
          fen (sget data "fen")
          to (sget data "to")
          from (sget data "from")
          promote (get data "promote")
          ;; what happens if not legal move??
          san (chess/move-squares-to-san fen from to promote)
          new-fen (chess/apply-move-san fen san)
          {:keys [active-color
                  full-move-counter]} (chess/parse-fen new-fen)])))

(defn -main [& _]
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 3000}))
