(ns chess-journal3.modes.battle
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.constants :as c]
    [chess-journal3.engine :as engine]
    [chess-journal3.fen :as fen]
    [chess-journal3.line :as line]
    [chess-journal3.move :as move]
    [chess-journal3.pgn :as pgn]
    [chess-journal3.state]
    [chess-journal3.utils :as u]
    [clj-time.core :as t])
  (:import
    (chess_journal3.line Line)
    (chess_journal3.move Move)
    (chess_journal3.state GlobalState LocalState)))

(declare click-square
         switch-color
         make-client-view)

(defrecord Battle
  [^Line line
   color
   ;; This cannot be a pure function because it is set by `cleanUp` to prevent
   ;; telling the client to request a move twice.
   opponent-must-move
   selected-square
   promote-piece
   engine
   engine-movetime
   engine-elo]
  LocalState
  (getMode [_] "battle")
  (getFen [_] (line/fen line))
  (nextFrame [state] (update state :line line/next-frame))
  (prevFrame [state] (update state :line line/prev-frame))
  (switchColor [state] (switch-color state))
  (clickSquare [state square] (click-square state square))
  (cleanUp [state] (assoc state :opponent-must-move false))
  (getLine [state] line)
  (makeClientView [state] (make-client-view state)))

(defn- make-client-view [^Battle s]
  (let [{:keys [line
                color
                opponent-must-move
                selected-square
                promote-piece]} s
        sans (line/get-sans line)
        visible-sans (-> line line/truncate-at-current-fen line/get-sans)
        fen (line/fen line)]
    {:mode (.getMode s)
     :fen fen
     :sans sans
     :idx (line/get-idx line)
     :selectedSquare selected-square
     :opponentMustMove opponent-must-move
     :pgn (pgn/sans->pgn visible-sans)
     :playerColor color
     :flipBoard (= "b" color)
     :activeColor (fen/get-active-color fen)
     :promotePiece promote-piece}))

(defn get-fen [state]
  (line/fen (.line state)))

;; TODO Handle game over
(defn- opponent-must-move? [state]
  (fen/opponents-move? (get-fen state)
                       (.color state)))

(defn set-opponent-must-move [state]
  (assoc state :opponent-must-move (opponent-must-move? state)))

;; assoc-in state
;; [:mode->local-state :battle]
;; :game->won nil
(defn ^Battle init [^GlobalState app-state]
  (map->Battle
    {:line (line/new-stub nil c/initial-fen)
     :color "w"
     :opponent-must-move false
     :selected-square nil
     :promote-piece "Q"
     :engine (:engine app-state)
     :engine-elo 1800
     :engine-movetime (t/seconds 10)}))

;; Multiple arities allows forcing an opponent move. This is used in endgame
;; modes for using a tablebase instead of the engine.
(defn opponent-move
  ([^Battle state]
   (let [{:keys [engine
                 engine-movetime
                 engine-elo]} state
         fen (get-fen state)
         move (.getMove engine fen engine-elo engine-movetime)]
     (opponent-move state move)))
  ([^Battle state ^Move move]
   (-> state
       (update :line line/apply-move move)
       (assoc :opponent-must-move false))))

(defn click-square [^Battle state square]
  (let [{:keys [selected-square
                line
                promote-piece]} state
        fen (get-fen state)
        move (when selected-square
               (move/new-from-squares nil
                                      fen
                                      selected-square
                                      square
                                      promote-piece))]
    (cond
      (= selected-square square)
        (assoc state :selected-square nil)
      (and (not= selected-square square)
           (fen/active-player-has-piece-on-square? fen square))
        (assoc state :selected-square square)
      move
        (-> state
            (update :line line/apply-move move)
            (assoc :selected-square nil
                   :opponent-must-move true))
      :else
        state)))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      set-opponent-must-move))

(defn set-engine-elo [^Battle s elo]
  (assoc s :engine-elo elo))

(defn set-engine-movetime [^Battle s movetime]
  (assoc s :engine-movetime movetime))
