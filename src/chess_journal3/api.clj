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
    [chess-journal3.modes.endgames :as endgames]
    [chess-journal3.modes.games :as games]
    [chess-journal3.modes.live-games :as live-games]
    [chess-journal3.modes.menu :as menu]
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
;; 25. Add opponent lines to endgames mode
;; 28. Init functions for various modes are not complete. E.g., reset board to
;;     initial fen when starting review mode.
;; 30. Add "force move" to endgames -- prompt the user for a different computer move.
;; 31. Each mode should have an exit function to clean up the app state when
;;     leaving the mode.

(def initial-state
  {:db db/db
   :engine (engine/make)
   :engine-elo nil
   :engine-movetime (t/seconds 5)
   :color "w"
   :fen->moves {}
   ;; TODO Rename, since used not just in review mode
   :lines []
   :mode "menu"
   :locked-idx 0
   :fens [fen/initial]
   :sans []
   :idx 0
   :selected-square nil
   :opponent-must-move false
   :game->score nil
   :setup-selected-piece nil
   :promote-piece "Q"
   :endgames nil
   :endgame-class []
   :endgame-idx 0
   :live-game-names nil
   :live-game-idx nil})

(def state
  (atom initial-state))

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
   :endgameClassButtonConfigs (when (= "endgames" (:mode state))
                                (endgames/get-button-configs state))
   :liveGameName (when (and (:live-game-names state)
                            (:live-game-idx state))
                   (live-games/get-current-game-name state))
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
  (GET "/state" [] (with-out-str
                     (clojure.pprint/pprint @state)))
  (GET "/main.js" [] (slurp "front/dist/main.js"))
  (POST "/start" _ (route "Start" menu/init))
  (POST "/click-square" {body :body}
    (let [square (json/parse-string (slurp body))]
      (route (format "Click square %s" square) core/click-square square)))
  (POST "/alternative-move" {body :body}
    (let [san (json/parse-string (slurp body))]
      (route (format "Alternative move %s" san) lines/alternative-move san)))
  (POST "/set-mode" {body :body}
    (let [mode (json/parse-string (slurp body))
          f (case mode
              "menu" menu/init
              "edit" edit/init
              "review" review/init
              "battle" battle/init
              "games" games/init
              "setup" setup/init
              "endgames" endgames/init
              "live-games" live-games/init)]
      (route (format "%s mode" mode) f)))
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
  (POST "/save-endgame" _ (route "Save endgame" endgames/save))
  (POST "/delete-endgame" _ (route "Delete endgame" endgames/delete))
  (POST "/cycle-endgame-class" {body :body}
    (let [level (json/parse-string (slurp body))
          msg (format "Cycle endgame class at level %s" level)]
      (println (dissoc @state :endgames))
      (route msg endgames/cycle-class level)))
  (POST "/refine-endgame-class" _ (route "Refine endgame class" endgames/refine-class))
  (POST "/next-endgame" _ (route "Next endgame" endgames/next))
  (POST "/previous-endgame" _ (route "Previous endgame" endgames/previous))
  (POST "/force-move" {body :body}
    (let [move (json/parse-string (slurp body))]
      (route "Force move" endgames/force-move move)))
  (POST "/new-live-game" {body :body}
    (let [game-name (json/parse-string (slurp body))]
      (route "New live game" live-games/new game-name)))
  (POST "/cycle-live-game" _ (route "Cycle live game" live-games/cycle-game))
  (POST "/save-live-game" _ (route "Save live game" live-games/save))
  (POST "/end-live-game" {body :body}
    (let [result (json/parse-string (slurp body))]
      (route "End live game" live-games/end result)))
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
