(ns chess-journal3.state
  "Contains the global app state."
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.engine :as engine]))

(defrecord GlobalState
  [db
   engine
   mode
   mode->local-state])

;; The app has several modes: openings editor, endgames practics, etc. Each mode
;; required a different "local" state. It is very confusing if we don't keep
;; track of local state for each mode, because:
;;
;; 1. It is difficult to remember which fields of the global state are relevant
;; for the current mode.
;;
;; 2. It is difficult to enforce reasonable behavior when changing modes. For
;; example if you are in battle mode, 20 moves into a game, then switch to
;; openings practice, and back to battle mode, what should the current position
;; be?

(definterface LocalState
  (getMode [])
  (getFen [])
  (nextFrame [])
  (prevFrame [])
  (switchColor [])
  (clickSquare [square])
  (makeClientView [])
  ;; We finish handling every route by constructing the response and then
  ;; "cleaning up" the state. For example, in openings review mode we set
  ;; opponent must move to false. We do this to prevent telling the client to
  ;; request an opponent move twice. I'm not really sure this clean up operation
  ;; is a good pattern. Should this really be part of the state interface?
  (cleanUp [])
  (getLine []))

(defn get-initial-app-state [db engine]
  (map->GlobalState
    {:db db
     :engine engine
     :mode "menu"
     :mode->local-state {}}))

(def app-state (atom nil))

(defn get-local-state [^GlobalState s]
  (get-in s [:mode->local-state (:mode s)]))

(defn update-local-state [^GlobalState s f & args]
  (let [mode (:mode s)]
    (apply update-in s [:mode->local-state mode] f args)))

;;(apply update-in {:a {:b 1}} [:a :b] (fn [x y z] (* x (+ y z))) [2 3])

;;(apply update-in {:a {:b 1}} [:a :b] inc [])
