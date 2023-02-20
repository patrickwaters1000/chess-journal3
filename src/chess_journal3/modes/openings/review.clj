(ns chess-journal3.modes.openings.review
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.line :as line]
    [chess-journal3.move :as move]
    [chess-journal3.pgn :as pgn]
    [chess-journal3.tree :as tree]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.tree Tree)
    (chess_journal3.utils IState)))

(declare get-alternative-moves
         click-square
         switch-color)

(defrecord OpeningsReview
  [reportoire ;; E.g., 'white-scotch-gambit'.
   ^Tree tree
   color
   opponent-must-move
   selected-square
   promote-piece
   db]
  IState
  (getMode [_] "openings-review")
  (getFen [_] (tree/get-fen tree))
  (nextFrame [state] (update state :tree tree/next-frame))
  (prevFrame [state] (update state :tree tree/prev-frame))
  (switchColor [state] (switch-color state))
  (clickSquare [state square] (click-square state square))
  (cleanUp [state] (assoc state :opponent-must-move false))
  (makeClientView [state]
    (let [line (tree/get-line tree)
          sans (line/get-sans line)
          fen (tree/get-fen tree)]
      {:mode (.getMode state)
       :fen fen
       :sans sans
       :idx (line/get-idx line)
       :isLocked (tree/locked? tree)
       :selectedSquare selected-square
       :opponentMustMove opponent-must-move
       :alternativeMoves (get-alternative-moves tree)
       :pgn (pgn/sans->pgn sans)
       :playerColor color
       :activeColor (fen/get-active-color fen)
       :promotePiece promote-piece})))

(defn get-default-reportoire [color]
  (case color
    "w" "white-reportoire"
    "b" "black-reportoire"))

(defn opponent-must-move? [tree color]
  (and (-> tree
           tree/get-fen
           (fen/opponents-move? color))
       (not (tree/line-complete? tree))))

(defn set-opponent-must-move [state]
  (let [{:keys [tree color]} state
        omm (opponent-must-move? tree color)]
    (assoc state :opponent-must-move omm)))

(defn load-tree [db reportoire]
  (let [moves (db/get-tagged-moves db reportoire)
        line (line/new-stub c/initial-fen)]
    (tree/new moves line)))

(defn init [state]
  (let [{:keys [db
                color
                promote-piece]} state
        reportoire (get-default-reportoire color)
        tree (load-tree db reportoire)
        omm (opponent-must-move? tree color)]
    (-> {:db db
         :reportoire reportoire
         :tree tree
         :color color
         :selected-square nil
         :promote-piece promote-piece
         :opponent-must-move omm}
        map->OpeningsReview
        set-opponent-must-move)))

;; This fn can handle both the player's moves and the opponent's moves.
(defn move [state]
  (-> state
      (update :tree tree/next-frame)
      set-opponent-must-move))

(defn opponent-move [state]
  {:pre [(fen/opponents-move? (tree/get-fen (:tree state))
                              (:color state))]}
  (move state))

(defn move-to-square-is-correct? [state square]
  (let [{:keys [tree
                selected-square
                promote-piece]} state
        fen (-> state :tree tree/get-fen)
        san (chess/get-san fen selected-square square promote-piece)
        correct-san (-> tree tree/get-sans u/get-unique)]
    (= correct-san san)))

(defn click-square [state square]
  (let [{:keys [selected-square
                color
                tree]} state
        fen (tree/get-fen tree)]
    (cond
      (and (fen/player-has-piece-on-square? fen color square)
           (not= selected-square square))
        (assoc state :selected-square square)
      (or (= selected-square square)
          (and selected-square
               (not (move-to-square-is-correct? state square))))
        (assoc state :selected-square nil)
      (and selected-square
           (fen/players-move? fen color)
           (move-to-square-is-correct? state square))
        (move state)
      :else
        state)))

(defn give-up [state]
  (move state))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      init))

(defn- get-alternative-moves [^Tree t]
  (-> t tree/prev-frame tree/get-alternative-moves))

(defn alternative-move [state san]
  (let [t1 (-> (:tree state)
               tree/jump-to-final-frame
               tree/up)
        m (->> (tree/get-moves t1)
               (filter #(= san (move/san %)))
               u/get-unique)
        t2 (tree/apply-move t1 m)]
    (assoc state :tree t2)))

(defn next-line [state] (update state :tree next-line))
(defn switch-lock [state] (update state :tree tree/switch-lock))

(defn reset-board [state]
  (-> state
      (update :tree tree/jump-to-initial-frame)
      set-opponent-must-move))
