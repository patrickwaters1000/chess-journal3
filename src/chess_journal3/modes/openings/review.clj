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
    [chess-journal3.utils :as u]
    [clojure.set :as set])
  (:import
    (chess_journal3.state LocalState GlobalState)
    (chess_journal3.tree Tree)))

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
  LocalState
  (getMode [_] "openings-review")
  (getFen [_] (tree/get-fen tree))
  (nextFrame [state] (update state :tree tree/next-frame))
  (prevFrame [state] (update state :tree tree/prev-frame))
  (switchColor [state] (switch-color state))
  (clickSquare [state square] (click-square state square))
  (cleanUp [state] (assoc state :opponent-must-move false))
  (getLine [state] (tree/get-line tree))
  (makeClientView [state]
    (let [line (tree/get-line tree)
          sans (line/get-sans line)
          visible-sans (-> line line/truncate-at-current-fen line/get-sans)
          fen (tree/get-fen tree)]
      {:mode (.getMode state)
       :openingReportoire reportoire
       :fen fen
       :sans sans
       :idx (line/get-idx line)
       :isLocked (tree/locked? tree)
       :selectedSquare selected-square
       :opponentMustMove opponent-must-move
       :alternativeMoves (get-alternative-moves tree)
       :pgn (pgn/sans->pgn visible-sans)
       :playerColor color
       :flipBoard (= "b" color)
       :activeColor (fen/get-active-color fen)
       :promotePiece promote-piece})))

(defn- subtree-nodes [relations root]
  (loop [family #{root}
         parents #{root}]
    (let [children (->> relations
                        (filter #(contains? parents (:parent %)))
                        (map :child)
                        (into #{}))
          new-family (set/union family children)]
      (if (empty? children)
        (sort family)
        (recur new-family children)))))

(defn subtree-leaves [relations root]
  (let [parent-nodes (->> relations (map :parent) (into #{}))
        output (->> (subtree-nodes relations root)
                    (remove parent-nodes))]
    output))

(defn- get-root-reportoire [color]
  (case color
    "w" "white-reportoire"
    "b" "black-reportoire"))

(defn get-default-reportoire [db color]
  (let [tag-containments (db/get-tag-containments db)
        root-reportoire (get-root-reportoire color)]
    (first (subtree-leaves tag-containments root-reportoire))))

(defn opponent-must-move? [tree color]
  (and (-> tree
           tree/get-fen
           (fen/opponents-move? color))
       (not (tree/line-complete? tree))))

(defn set-opponent-must-move [^OpeningsReview state]
  (let [{:keys [tree color]} state
        omm (opponent-must-move? tree color)]
    (assoc state :opponent-must-move omm)))

(defn load-tree [db reportoire]
  (let [moves (db/get-tagged-moves db reportoire :include-child-tags true)
        line (line/new-stub reportoire c/initial-fen)]
    (tree/new moves line)))

(defn init
  ([^OpeningsReview state]
   (let [reportoire (get-default-reportoire (:db state) (:color state))]
     (init state reportoire)))
  ([^OpeningsReview state reportoire]
   (let [{:keys [db
                 color
                 promote-piece]} state
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
         set-opponent-must-move))))

;; This fn can handle both the player's moves and the opponent's moves.
(defn move [^OpeningsReview state]
  (-> state
      (update :tree tree/next-frame)
      (assoc :selected-square nil)
      set-opponent-must-move))

(defn opponent-move [^OpeningsReview state]
  {:pre [(fen/opponents-move? (tree/get-fen (:tree state))
                              (:color state))]}
  (move state))

(defn move-to-square-is-correct? [^OpeningsReview state square]
  (let [{:keys [tree
                selected-square
                promote-piece]} state
        fen (-> state :tree tree/get-fen)
        san (chess/get-san fen selected-square square :promote-piece promote-piece)
        correct-sans (-> tree tree/get-sans set)]
    (contains? correct-sans san)))

(defn click-square [^OpeningsReview state square]
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

(defn give-up [^OpeningsReview state]
  (move state))

(defn switch-color [^OpeningsReview state]
  (-> state
      (update :color u/other-color)
      init))

(defn- get-alternative-moves [^Tree t]
  (-> t tree/prev-frame tree/get-alternative-moves))

(defn alternative-move [^OpeningsReview state san]
  (let [frame-idx (tree/get-idx (:tree state))
        t1 (-> (:tree state)
               tree/truncate-line-at-current-fen
               tree/up)
        m (->> (tree/get-moves t1)
               (filter #(= san (move/san %)))
               u/get-unique)
        t2 (-> t1
               (tree/apply-move m)
               tree/complete-line)]
    (-> state
        (assoc :tree t2)
        (update :tree tree/jump-to-frame frame-idx))))

(defn next-line [^OpeningsReview state]
  (-> state
      (update :tree tree/next-line)
      (update :tree tree/jump-to-base-frame)))

(defn switch-lock [^OpeningsReview state] (update state :tree tree/switch-lock))

(defn reset-board [^OpeningsReview state]
  (-> state
      (update :tree tree/jump-to-initial-frame)
      set-opponent-must-move))

(defn next-reportoire [^OpeningsReview state]
  (let [{:keys [color reportoire db]} state
        tag-containments (db/get-tag-containments db)
        root-reportoire (get-root-reportoire color)
        relevant-tags (subtree-leaves tag-containments root-reportoire)
        old-idx (u/index-of-first #(= reportoire %) relevant-tags)
        new-idx (-> old-idx inc (mod (count relevant-tags)))
        new-reportoire (nth relevant-tags new-idx)]
    (init state new-reportoire)))

(comment
  (def tag-containments (db/get-tag-containments db/db))
  (->> tag-containments
       (remove #(contains? #{"white-games" "black-games"}
                           (:parent_tag %))))

  ;;
  )
