(ns chess-journal3.modes.openings.editor
  (:require
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.line :as line]
    [chess-journal3.modes.openings.review :as review]
    [chess-journal3.move :as move]
    [chess-journal3.tree :as tree]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.tree Tree)))

(defrecord OpeningsEditor
  [mode
   reportoire
   ^Tree tree
   color
   selected-square
   promote-piece
   error
   db])

(defn init [state]
  (let [{:keys [db
                color
                promote-piece]} state
        reportoire (review/get-default-reportoire color)
        tree (review/load-tree db reportoire)]
    (-> {:mode "openings-editor"
         :reportoire reportoire
         :color color
         :selected-square nil
         :promote-piece promote-piece
         :db db
         :error nil}
        map->OpeningsEditor)))

(defn reload-tree [state]
  (let [{:keys [db
                tree
                color
                reportoire]} state
        line (tree/get-line tree)
        moves (db/get-tagged-moves db reportoire)]
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
                (not (line/ends-with-opponent-to-play? line))
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
          new-tag (case (review/get-move-tag color)
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
  (let [{:keys [selected-square
                color
                tree
                promote-piece
                tag]} state
        fen (tree/get-fen tree)
        move (when selected-square
               (move/new-from-squares tag
                                      fen
                                      selected-square
                                      square
                                      promote-piece))]
    (cond
      (and (not= selected-square square)
           (fen/active-player-has-piece-on-square? fen color square))
        (assoc state :selected-square square)
      (= selected-square square)
        (assoc state :selected-square nil)
      move
        (update state :tree tree/apply-move move)
      :else
        state)))

(defn switch-color [state]
  (-> state
      (update :color u/other-color)
      init))
