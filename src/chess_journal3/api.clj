(ns chess-journal3.api
  (:require
    [cheshire.core :as json]
    [chess-journal3.core :as core]
    [chess-journal3.db :as db]
    [chess-journal3.engine :as engine]
    [chess-journal3.fen :as fen]
    [chess-journal3.lines :as lines]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.modes.edit :as edit]
    [chess-journal3.modes.games :as games]
    [chess-journal3.modes.review :as review]
    [chess-journal3.modes.setup :as setup]
    [chess-journal3.pgn :as pgn]
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

(def initial-state
  {:db db/db
   :engine (engine/make)
   :engine-elo nil
   :engine-movetime (t/seconds 5)
   :color "w"
   :fen->moves {}
   ;; TODO Rename, since used not just in review mode
   :lines []
   :mode "review"
   :locked-idx 0
   :fens [fen/initial]
   :sans []
   :idx 0
   :selected-square nil
   :opponent-must-move false
   :game->score nil
   :setup-selected-piece nil
   :promote-piece "Q"})

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
  {:fen (u/get-fen state)
   :sans (:sans state)
   :idx (:idx state)
   :mode (:mode state)
   :flipBoard (= "b" (:color state))
   :isLocked (not= 0 (:locked-idx state))
   :selectedSquare (:selected-square state)
   :opponentMustMove (:opponent-must-move state)
   :alternativeMoves (if (contains? #{"review" "games" "edit"}
                                    (:mode state))
                       (core/get-alternative-moves state)
                       [])
   :games (when (= "games" (:mode state))
            (games/get-games state))
   :pgn (pgn/sans->pgn (:sans state))
   ;; Only used in setup mode
   :playerColor (:color state)
   ;; Only used in setup mode
   :activeColor (u/get-active-color state)
   :promotePiece (:promote-piece state)
   :error (:error state)})

(defn route [msg f & args]
  (println msg)
  (apply swap! state f args)
  ;;(log-state)
  (let [v (view @state)
        resp (json/generate-string v)]
    (swap! state dissoc :error :opponent-must-move)
    (println (format "Sending state with opponentMustMove=%s"
                     (:opponentMustMove v)))
    resp))

;; As the app becomes meatier, the meanings of keys, etc., will have to depend
;; on the mode.
(defroutes app
  (GET "/" [] (slurp "front/dist/index.html"))
  (GET "/main.js" [] (slurp "front/dist/main.js"))
  (POST "/start" _ (route "Start" review/init))
  (POST "/click-square" {body :body}
    (let [square (json/parse-string (slurp body))]
      (route (format "Click square %s" square) core/click-square square)))
  (POST "/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (route (format "Alternative move %s" san) lines/alternative-move san)))
  (POST "/edit-mode" _ (route "Switch mode" edit/init))
  (POST "/review-mode" _ (route "Switch mode" review/init))
  (POST "/battle-mode" _ (route "Switch mode" battle/init))
  (POST "/games-mode" _ (route "Switch mode" games/init))
  (POST "/setup-mode" _ (route "Switch mode" setup/init))
  (POST "/select-game" {body :body}
    (let [game-tag (json/parse-string (slurp body))]
      (route "Select game" games/select-game game-tag)))
  (POST "/set-elo" {body :body}
    (let [elo (Integer/parseInt (json/parse-string (slurp body)))]
      (route "Set Elo" core/set-elo elo)))
  (POST "/switch-lock" _ (route "Lock" core/switch-lock))
  (POST "/switch-color" _ (route "Switch color" core/switch-color))
  (POST "/reset" _ (route "Reset board" core/reset-board))
  (POST "/add-line" _ (route "Add line" edit/add-line!))
  (POST "/give-up" _ (route "Give up" review/give-up))
  (POST "/opponent-move" _ (route "Opponent move" core/opponent-move))
  (POST "/delete-subtree" _ (route "Delete subtree" edit/delete-subtree!))
  (POST "/reboot-engine" _ (route "Reboot engine" core/reboot-engine!))
  (POST "/cycle-promote-piece" _ (route "Cycle promote piece" core/cycle-promote-piece))
  (POST "/switch-active-color" _ (route "Switch active color" setup/switch-active-color))
  (POST "/key" {body :body}
    (let [keycode (json/parse-string (slurp body))]
      (case keycode
        "ArrowLeft" (route "Left" u/previous-frame)
        "ArrowRight" (route "Right" u/next-frame)
        "Enter" (route "Next line" core/next-line)
        ("Delete"
         "Backspace") (route "Undo" u/undo-last-move)
        ("p" "n" "b" "r" "q" "k"
         "P" "N" "B" "R" "Q" "K") (do (println (str "Key = " keycode))
                                      (route "Set selected piece"
                                             setup/set-selected-piece
                                             keycode))
        (println (format "Unexpected keyboard event %s" keycode))))))

(defn -main [& _]
  (println "Ready!")
  (run-server (rmp/wrap-params app)
              {:port 3000}))
