(ns chess-journal3.modes.openings.review
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.line :as line]
    [chess-journal3.tree :as tree]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.tree Tree)))

(defrecord OpeningsReview
  [mode
   ^Tree tree
   color
   opponent-must-move
   selected-square
   promote-piece
   db])

(defn- get-move-tag [color]
  (case color
    "w" "white-reportoire"
    "b" "black-reportoire"))

(defn opponent-must-move? [tree color]
  (and (-> tree
           tree/fen
           (fen/opponents-move? color))
       (not (tree/line-complete? tree))))

(defn set-opponent-must-move [state]
  (let [{:keys [tree color]} state
        omm (opponent-must-move? tree color)]
    (assoc state :opponent-must-move omm)))

(defn init [state]
  (let [{:keys [db
                color
                promote-piece]} state
        move-tag (get-move-tag color)
        moves (db/get-tagged-moves db move-tag)
        line (line/stub c/initial-fen)
        tree (tree/new moves line)
        omm (opponent-must-move? tree color)]
    (-> {:mode "openings.review"
         :color color
         :selected-square nil
         :promote-piece promote-piece
         :opponent-must-move omm
         :db db}
        map->OpeningsReview
        set-opponent-must-move)))

;; This fn can handle both the player's moves and the opponent's moves.
(defn move [state]
  (-> state
      (update :tree tree/next-frame)
      set-opponent-must-move))

(defn move-to-square-is-correct? [state square]
  (let [{:keys [selected-square lines idx tree]} state
        fen (-> state :tree tree/fen)
        san (chess/move-to-san fen {:from selected-square
                                    :to square})
        correct-san (-> tree tree/get-sans u/get-unique)]
    (= correct-san san)))

(defn click-square [state square]
  (let [{:keys [selected-square
                color
                tree]} state
        fen (tree/fen tree)]
    (cond
      (and (u/player-has-piece-on-square? color fen square)
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

(defn next-line [state]
  (update state :tree next-line))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      init))
