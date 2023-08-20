(ns chess-journal3.modes.openings.editor
  (:require
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.line :as line]
    [chess-journal3.modes.openings.review :as review]
    [chess-journal3.move :as move]
    [chess-journal3.pgn :as pgn]
    [chess-journal3.tree :as tree]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.tree Tree)
    (chess_journal3.utils IState)))

(declare switch-color
         click-square)

(defrecord OpeningsEditor
  [reportoire
   ^Tree tree
   color
   selected-square
   promote-piece
   error
   db]
  IState
  (getMode [_] "openings-editor")
  (getFen [_] (tree/get-fen tree))
  (nextFrame [state] (update state :tree tree/next-frame))
  (prevFrame [state] (update state :tree tree/prev-frame))
  (switchColor [state] (switch-color state))
  (clickSquare [state square] (click-square state square))
  (cleanUp [state] (assoc state :error nil))
  (makeClientView [state]
    (let [line (tree/get-line tree)
          sans (line/get-sans line)
          fen (tree/get-fen tree)]
      {:mode (.getMode state)
       :openingReportoire reportoire
       :fen fen
       :sans sans
       :idx (line/get-idx line)
       :isLocked (tree/locked? tree)
       :selectedSquare selected-square
       :alternativeMoves (tree/get-alternative-moves tree)
       :pgn (pgn/sans->pgn sans)
       :playerColor color
       :activeColor (fen/get-active-color fen)
       :flipBoard (= "b" color)
       :promotePiece promote-piece})))

(defn get-reportoires-for-color [db color]
  (let [tag-containments (db/get-tag-containments db)
        root-reportoire (review/get-default-reportoire db color)]
    (review/subtree-leaves tag-containments root-reportoire)))

(defn init
  ([state]
   (let [{:keys [db color]} state
         reportoire (first (get-reportoires-for-color db color))]
     (init state reportoire)))
  ([state reportoire]
   (let [{:keys [db
                 color
                 promote-piece]} state
         tree (review/load-tree db reportoire)]
     (-> {:reportoire reportoire
          :tree tree
          :color color
          :selected-square nil
          :promote-piece promote-piece
          :db db
          :error nil}
         map->OpeningsEditor))))

(defn reload-tree [state]
  (let [{:keys [db
                tree
                color
                reportoire]} state
        line (tree/get-line tree)
        moves (db/get-tagged-moves db reportoire :include-child-tags true)]
    (assoc state :tree (tree/new moves line))))

(defn move-conflicts-with-reportoire?
  [tree move]
  (let [reportoire-moves (tree/get-moves tree)]
    (assert (contains? #{0 1} (count reportoire-moves)))
    (and (seq reportoire-moves)
         (not (some #(move/equals move %) reportoire-moves)))))

(defn line-conflicts-with-reportoire?
  "Returns `nil` if the line does not conflict with the reportoire.
  Otherwise returns the first conflicting move.

  Currently within each reportoire 'tag', there must be at most one move
  selected for the player for each position. To add secondary moves, you
  must add them under a separate reportoire tag, e.g.,
  'white-reportoire-2nd-choices'."
  [tree color line]
  (->> (line/to-moves line)
       (filter #(= color (move/get-active-color %)))
       (filter (partial move-conflicts-with-reportoire? tree))
       first))

(defn add-line! [state]
  (let [{:keys [db tree color]} state
        line (tree/get-line tree)
        conflicting-move (line-conflicts-with-reportoire? tree color line)
        error (cond
                (not (line/ends-with-opponent-to-play? line color))
                  (format "Line must end with %s play"
                          (u/other-color color))
                conflicting-move
                  (format "Line conflicts at %s from %s"
                          (move/san conflicting-move)
                          (move/initial-fen conflicting-move)))]
    (if error
      (assoc state :error error)
      (do (db/insert-tagged-moves! db (line/to-moves line))
          (reload-tree state)))))

(defn delete-subtree! [state]
  (if-not (-> state :tree tree/get-fen fen/players-move?)
    (assoc state :error "Can only delete subtree when it's your move.")
    (let [{:keys [db tree color]} state
          new-tag (case (review/get-default-reportoire db color)
                    "white-reportoire" "deleted-white-reportoire"
                    "black-reportoire" "deleted-black-reportoire")]
      (->> (tree/get-lines-from-current-fen tree)
           (mapcat line/to-moves)
           (into #{})
           (db/update-move-tags! db new-tag))
      (-> state
          (update :tree tree/truncate-line-at-current-fen)
          reload-tree))))

(defn click-square [state square]
  (let [selected-square (.selected_square state)
        color (.color state)
        tree (.tree state)
        promote-piece (.promote_piece state)
        reportoire (.reportoire state)
        fen (tree/get-fen tree)
        move (when selected-square
               (move/new-from-squares reportoire
                                      fen
                                      selected-square
                                      square
                                      promote-piece))]
    (cond
      (and (not= selected-square square)
           (fen/active-player-has-piece-on-square? fen square))
        (assoc state :selected-square square)
      (= selected-square square)
        (assoc state :selected-square nil)
      move
        (-> state
            (update :tree tree/apply-move move)
            (assoc :selected-square nil))
      :else
        state)))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      init))

(defn alternative-move [state san]
  (println (class state))
  (let [t1 (-> (:tree state)
               tree/jump-to-final-frame)
        m (->> (tree/get-moves t1)
               (filter #(= san (move/san %)))
               u/get-unique)
        t2 (tree/apply-move t1 m)]
    (assoc state :tree t2)))

(defn switch-lock [state] (update state :tree tree/switch-lock))
(defn reset-board [state] (update state :tree tree/jump-to-initial-frame))
(defn undo-last-move [state] (update state :tree tree/up))

(defn next-reportoire [state]
  (let [{:keys [color reportoire db]} state
        reportoires (get-reportoires-for-color db color)
        old-idx (u/index-of-first #(= reportoire %) reportoires)
        new-idx (-> old-idx inc (mod (count reportoires)))
        new-reportoire (nth reportoires new-idx)]
    (init state new-reportoire)))
