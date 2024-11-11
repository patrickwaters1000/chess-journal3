(ns chess-journal3.modes.endgames.rook-vs-pawn
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.fen :as fen]
    [chess-journal3.line :as line]
    [chess-journal3.modes.battle :as battle]
    [chess-journal3.move :as move]
    [chess-journal3.tablebase :as tablebase]
    [clojure.string :as string])
  (:import
    (chess_journal3.modes.battle Battle)
    (chess_journal3.state GlobalState LocalState)
    (java.util Random)))

(declare make-client-view)

(defrecord RookVsPawn
  [^Battle battle
   fens
   idx
   hint]
  LocalState
  (getMode [_] "rook-vs-pawn")
  (getFen [_] (.getFen battle))
  (nextFrame [state] (update state :battle #(.nextFrame %)))
  (prevFrame [state] (update state :battle #(.prevFrame %)))
  (switchColor [state] (update state :battle #(.switchColor %)))
  (clickSquare [state square] (update state :battle #(.clickSquare % square)))
  (cleanUp [state] (update state :battle #(.cleanUp %)))
  (getLine [state] (.getLine battle))
  (makeClientView [state] (make-client-view state)))

(def fens-file
  (str "/Users/pwaters/src/chess-journal3/"
       "resources/rook_vs_pawn_endgames.txt"))

(defn get-rook-vs-pawn-fens []
  (read-string (slurp fens-file)))

(defn- get-fen [^RookVsPawn state]
  (battle/get-fen (.battle state)))

(defn- set-fen [^RookVsPawn state]
  (let [fen (nth (.fens state) (.idx state))
        line (line/new-stub nil fen)]
    (assoc-in state [:battle :line] line)))

(defn cycle-fen [^RookVsPawn state incr]
  (let [n (count (.fens state))]
    (-> state
        (update :idx #(mod (+ % incr) n))
        (set-fen))))

(defn ^RookVsPawn init [^GlobalState app-state]
  (let [battle (-> (battle/init app-state)
                   (assoc :engine-elo 3000))]
    (-> {:battle battle
         :fens (get-rook-vs-pawn-fens)
         :idx 0
         :hint (atom nil)}
        (map->RookVsPawn)
        (set-fen))))

(defn ^RookVsPawn opponent-move [^RookVsPawn state]
  (let [fen (get-fen state)
        san (:san (first (tablebase/rank-legal-moves fen)))
        move (move/new-from-san nil fen san)]
    (update state :battle battle/opponent-move move)))

(defn ^RookVsPawn force-move [^RookVsPawn state san]
  (let [fen (get-fen state)
        move (move/new-from-san nil fen san)]
    (update state :battle battle/opponent-move move)))

(defn ^RookVsPawn hint [^RookVsPawn state]
  (let [fen (get-fen state)
        ranked-moves (tablebase/rank-legal-moves fen)
        hint-str (string/join "\n" ranked-moves)]
    (reset! (.hint state) hint-str)
    state))

(defn- make-client-view [^RookVsPawn state]
  (let [fen-counter (format "%d / %d" (.idx state) (count (.fens state)))
        hint (deref (.hint state))]
    (reset! (.hint state) nil)
    (assoc (.makeClientView (.battle state))
      :mode "rook-vs-pawn"
      :fenCounter fen-counter
      :hint hint)))

(comment
  (defn- generate-random-square [^Random r]
    (let [rank (inc (mod (.nextInt r) 8))
          file (get fen/files (mod (.nextInt r) 8))]
      (format "%s%d" file rank)))

  (defn- generate-random-squares [^Random r num-squares]
    {:pre [(<= 1 num-squares 64)]}
    (loop [squares #{}]
      (if (= num-squares (count squares))
        squares
        (recur (conj squares (generate-random-square r))))))

  (defn- make-fen [square->piece]
    (fen/unparse
      {:square->piece square->piece
       :active-color "w"
       :halfmove-clock 0
       :fullmove-counter 1}))

  (defn- critical-position?
    "Returns true if the position is winning with White to play, but not winning
  with Black to play."
    [fen]
    (and (pos? (:wdl (tablebase/probe fen)))
         (not (neg? (:wdl (tablebase/probe (fen/reverse-active-color fen)))))))

  (defn- too-easy?
    "Returns true if the tablebase line reduces to a simpler material balance in
  2 moves or less. This rules out cases where White captures Black's pawn too
  easily."
    [fen]
    (let [dtz (:dtz (tablebase/probe fen))
          num-moves (quot (inc dtz) 2)]
      (<= num-moves 2)))

  (defn- generate-random-fen [^Random r]
    (let [squares (generate-random-squares r 4)
          square->piece (zipmap squares ["p" "K" "R" "k"])
          pawn-on-8th-rank (string/includes? (first squares) "8")
          pawn-on-1st-rank (string/includes? (first squares) "1")
          fen (make-fen square->piece)]
      (if (and (not pawn-on-8th-rank)
               (not pawn-on-1st-rank)
               (chess/valid-fen? fen)
               (not (chess/is-check? fen))
               (critical-position? fen)
               (not (too-easy? fen)))
        fen
        (recur r)))))

(comment
  (def rng (Random.))
  (def fens (atom []))
  (doseq [_ (range 100)]
    (let [fen (generate-random-fen rng)]
      (println fen)
      (swap! fens conj fen)))
  (spit "/Users/pwaters/src/chess-journal3/resources/rook_vs_pawn_endgames.txt"
        @fens)
  ;;
  )
