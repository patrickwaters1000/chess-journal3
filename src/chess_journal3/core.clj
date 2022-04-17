(ns chess-journal3.core
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [clojure.string :as string])
  (:import
    (java.util Random)))

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

(def rng (Random.))

;; Modes: edit, review, battle

(defn get-fen [state]
  (let [{:keys [fens idx]} state]
    (nth fens idx)))

(defn- get-locked-fen [state]
  (let [{:keys [fens locked-idx]} state]
    (nth fens locked-idx)))

(defn- players-move? [state]
  (let [{:keys [color]} state
        active-color (-> state get-fen fen/parse :active-color)]
    (= active-color color)))

(defn- opponents-move? [state]
  (not (players-move? state)))

(defn end-of-review-line? [state]
  (let [{:keys [idx review-lines]} state]
    (= idx (dec (count (first review-lines))))))

(defn move [state move]
  (let [{:keys [idx fens mode sans]} state
        fen (get-fen state)
        san (chess/move-to-san fen move)
        new-fen (chess/apply-move fen move)
        new-fens (conj (subvec fens 0 (inc idx)) new-fen)
        new-sans (conj (subvec sans 0 idx) san)
        opponent-must-move (and (= "review" mode)
                                (not (end-of-review-line? ;;state
                                       ;; This is terrible
                                       (update state :idx inc))))]
    (let [new-state (-> state
                        (assoc :fens new-fens
                               :sans new-sans
                               :selected-square nil
                               :opponent-must-move opponent-must-move)
                        (update :idx inc))]
      new-state)))

(defn move-to-square-is-legal? [state square]
  (let [{:keys [selected-square]} state
        fen (get-fen state)]
    ;; TODO Handle promotions
    (chess/legal-move? fen {:from selected-square :to square})))

(defn move-to-square-is-correct? [state square]
  (and (move-to-square-is-legal? state square)
       (let [{:keys [selected-square review-lines idx]} state
             fen (get-fen state)
             san (chess/move-to-san fen {:from selected-square
                                         :to square})
             correct-san (:san (nth (first review-lines) (inc idx)))]
         (= correct-san san))))

(defn can-move? [state]
  (or (= "edit" (:mode state))
      (players-move? state)))

(defn get-line-data [state]
  (let [{:keys [color fens sans]} state
        tag (case color
              "w" "white-reportoire"
              "b" "black-reportoire")]
    (map (fn [[initial-fen final-fen] san]
           {:tag tag
            :initial-fen initial-fen
            :final-fen final-fen
            :san san})
         (partition 2 1 (:fens state))
         (:sans state))))

(defn line-ends-with-opponent-to-play? [state]
  (let [{:keys [fens color]} state
        active-color (-> fens last fen/parse :active-color)]
    (not= color active-color)))

(defn add-line! [state]
  (assert (line-ends-with-opponent-to-play? state))
  (db/insert-tagged-moves! (:db state)
                           (get-line-data state))
  state)

(defn- get-moves [state]
  (let [{:keys [db color]} state
        fen (get-fen state)
        tag (case color
              "w" "white-reportoire"
              "b" "black-reportoire")]
    (db/get-tagged-moves tag fen)))

(defn get-lines-in-subtree [db tag initial-fen]
  (let [get-moves #(db/get-tagged-moves db tag %)
        lines (atom [])]
    (loop [stack [[{:final-fen initial-fen}]]]
      (if (empty? stack)
        @lines
        (let [line (last stack)
              fen (:final-fen (last line))
              moves (get-moves fen)]
          (when (empty? moves)
            (swap! lines conj line))
          (recur (concat (drop-last stack)
                         (for [m moves]
                           (conj line m)))))))))

(defn get-fens-in-subtree [get-moves initial-fen]
  (->> (get-lines-in-subtree get-moves initial-fen)
       (apply concat)
       (map :final-fen)
       (into #{})
       sort))

(defn reset [state]
  (println (dissoc state :review-lines))
  (let [{:keys [locked-idx fens sans color mode]} state
        state* (-> state
                   (assoc :idx locked-idx
                          :fens (vec (take (inc locked-idx) fens))
                          :sans (vec (take locked-idx sans))
                          :selected-square nil))
        opponent-must-move (and (= "review" mode)
                                (opponents-move? state*))
        state** (assoc state* :opponent-must-move opponent-must-move)]
    (println (dissoc state** :review-lines))
    state**))

(defn lock [state]
  (let [{:keys [idx]} state]
    (update state :locked-idx idx)))

(defn unlock [state]
  (assoc state :locked-idx 0))

(defn switch-color [state]
  (-> state
      (update state :color {"w" "b" "b" "w"})
      reset))

(defn player-has-piece-on-square? [state square]
  (let [{:keys [color]} state
        {:keys [square->piece]} (fen/parse (get-fen state))
        piece (get square->piece square)
        is-white-piece (when piece
                         (= piece (string/upper-case piece)))]
    (and piece
         (or (and (= "w" color) is-white-piece)
             (and (= "b" color) (not is-white-piece))))))

(defn opponent-has-piece-on-square? [state square]
  (player-has-piece-on-square? (update state :color {"w" "b"
                                                     "b" "w"})
                               square))

(defn click-square [state square]
  (let [{:keys [selected-square mode]} state]
    (cond
      (or (= selected-square square)
          (and selected-square
               (= "review" mode)
               (not (move-to-square-is-correct? state square)))) (assoc state :selected-square nil)
      (and selected-square
           (can-move? state)
           (if (= "edit" mode)
             (move-to-square-is-legal? state square)
             (move-to-square-is-correct? state square))) (move state {:from selected-square
                                                                      :to square})
      (or (player-has-piece-on-square? state square)
          (and (= "edit" mode)
               opponent-has-piece-on-square? state square)) (assoc state :selected-square square)
      :else state)))

(defn init-edit-mode [state]
  (-> state
      (assoc :mode "edit")
      reset))

(defn init-review-mode [state]
  (let [lines (get-lines-in-subtree (:db state)
                                    (case (:color state)
                                      "w" "white-reportoire"
                                      "b" "black-reportoire")
                                    (get-locked-fen state))]
    (-> state
        (assoc :mode "review"
               :review-lines lines)
        reset)))

(defn switch-mode [state]
  (case (:mode state)
    "edit" (init-review-mode state)
    "review" (init-edit-mode state)))

(defn switch-lock [state]
  (let [{:keys [idx locked-idx]} state]
    (if (pos? locked-idx)
      (assoc state :locked-idx 0)
      (assoc state :locked-idx idx))))

(defn opponent-move [state]
  (let [{:keys [idx review-lines]} state
        {san :san
         fen :final-fen} (nth (first review-lines)
                              (inc idx))]
    (-> state
        (update :sans conj san)
        (update :fens conj fen)
        (update :idx inc)
        (assoc :opponent-must-move false))))

(defn cycle [xs]
  (conj (vec (rest xs)) (first xs)))

(defn next-line [state]
  (if (= "review" (:mode state))
    (-> state
        (update :review-lines cycle)
        reset)
    state))
