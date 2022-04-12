(ns chess-journal3.core
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen])
  (:import
    (java.util Random)))

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

(def rng (Random.))

;; Modes: edit, review, battle

(def initial-state
  {:db nil
   :fens [initial-fen]
   :sans []
   :idx 0
   :locked-idx 0
   :color "w"
   :mode "review"
   :review-lines [] ;;
   :modulus (.nextInt rng) ;; Determines which line we are reviewing
   :opponent-next-move nil ;; Opponent's move that will be played after a delay
   })

(defn- get-fen [state]
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

(defn move [state move]
  (let [{:keys [idx fens]} state
        fen (get-fen state)
        san (chess/move-to-san fen move)
        new-fen (chess/apply-move fen move)
        new-fens (conj (subvec fens 0 (inc idx)) new-fen)
        new-sans (conj (subvec fens 0 idx) san)]
    (-> state
        (assoc :fens new-fens
               :sans new-sans)
        (update :idx inc))))

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
                           (get-line-data state)))

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

(defn set-opponent-next-move [state]
  ())

(defn reset-line-to-locked-idx [state]
  (let [{:keys [locked-idx fens sans color mode]} state
        state* (-> state
                   (assoc :idx locked-idx
                          :fens (subvec fens 0 (inc locked-idx))
                          :sans (subvec sans 0 locked-idx))
                   (update :modulus inc))]
    (if (and (= "review" mode)
             (opponents-move? state*))
      (set-opponent-next-move state*)
      state*)))

(defn lock [state]
  (let [{:keys [idx]} state]
    (update state :locked-idx idx)))

(defn unlock [state]
  (assoc state :locked-idx 0))

(defn switch-color [state]
  (update state :color {"w" "b"
                        "b" "w"}))
