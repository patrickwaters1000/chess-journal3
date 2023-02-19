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
   reportoire ;; E.g., 'white-scotch-gambit'.
   ^Tree tree
   color
   opponent-must-move
   selected-square
   promote-piece
   db])

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
        line (line/stub c/initial-fen)]
    (tree/new moves line)))

(defn init [state]
  (let [{:keys [db
                color
                promote-piece]} state
        reportoire (get-default-reportoire color)
        tree (load-tree db reportoire)
        omm (opponent-must-move? tree color)]
    (-> {:mode "openings-review"
         :reportoire reportoire
         :tree tree
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

(defn next-line [state]
  (update state :tree next-line))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      init))
