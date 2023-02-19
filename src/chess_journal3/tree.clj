(ns chess-journal3.tree
  (:require
    [chess-journal3.line :as line]
    [chess-journal3.move :as move]
    [chess-journal3.utils :as u])
  (:import
    (chess_journal3.line Line)
    (chess_journal3.move Move)))

(defrecord Tree
  [tag
   fen->moves
   fen->num-lines
   initial-fen ;; Lines start here.
   base-fen ;; Iteration preserves that lines go through here.
   ^Line line])

(defn contains-fen? [^Tree t fen]
  (some? (get (:fen->moves t) fen)))

(defn- supports-line? [^Tree t ^Line l]
  (and (contains-fen? t (line/initial-fen l))
       (every? #(contains-fen? t (move/initial-fen %))
               (line/to-moves l))))

(defn- can-go-down? [^Tree t]
  (let [fen (line/final-fen (:line t))]
    (contains-fen? t fen)))

(defn- can-go-up? [^Tree t]
  (not= (line/final-fen (:line t))
        (:base-fen t)))

(defn- can-go-right? [^Tree t]
  (let [{:keys [line
                fen->moves]} t
        previous-fen (line/penultimate-fen line)
        current-fen (line/final-fen line)
        moves (get fen->moves previous-fen)
        idx (u/index-of-first #(= current-fen (move/final-fen %)) moves)]
    (< (inc idx) (count moves))))

(defn- down [^Tree t idx]
  (let [{:keys [line
                fen->moves]} t
        fen (line/final-fen line)
        move (nth (get fen->moves fen) idx)]
    (update t :line line/append move)))

(defn- up [^Tree t]
  (update t :line line/drop-last-move))

(defn- right [^Tree t]
  (let [{:keys [line
                fen->moves]} t
        previous-fen (line/penultimate-fen line)
        current-fen (line/final-fen line)
        moves (get fen->moves previous-fen)
        current-idx (u/index-of-first #(= current-fen (move/final-fen %)) moves)
        idx (mod (inc current-idx)
                 (count moves))]
    (-> t up (down idx))))

(defn- complete-line [^Tree t]
  (if-not (can-go-down? t)
    t
    (recur (down t 0))))

;; It looks strange to go right when you "can not go right". In this case we
;; have reached the base fen, which can only happen if we are on the last
;; line. Forcing a rightward move returns to the first line.
(defn next-line [^Tree t]
  (cond
    (not (can-go-up? t))
      (complete-line t)
    (can-go-right? t)
      (-> t right complete-line)
    :else
      (recur (up t))))

(defn- count-lines [fen->moves initial-fen]
  (let [fen->num-lines (atom {})
        visited-fens (atom #{})]
    (loop [stack (list initial-fen)]
      (if (empty? stack)
        @fen->num-lines
        (let [fen (first stack)
              child-fens (->> (get fen->moves fen)
                              (map move/final-fen))
              num-lines (if-not (empty? child-fens)
                          (->> child-fens
                               (map @fen->num-lines)
                               (remove nil?)
                               (reduce + 0))
                          1)
              visited? #(contains? @visited-fens %)
              visited-fen (visited? fen)]
          (when (and (not visited-fen)
                     (some visited? child-fens))
            (throw (Exception. "Cycle detected")))
          (if visited-fen
            (swap! fen->num-lines assoc fen num-lines)
            (swap! visited-fens conj fen))
          (recur (if visited-fen
                   (rest stack)
                   (concat child-fens stack))))))))

(defn new [moves ^Line l]
  {:post [(supports-line? % l)]}
  (let [fen->moves (group-by move/initial-fen moves)
        initial-fen (line/initial-fen l)
        fen->num-lines (count-lines fen->moves initial-fen)]
    (-> {:fen->moves fen->moves
         :fen->num-lines fen->num-lines
         :initial-fen initial-fen
         :base-fen (line/final-fen l)
         :line l}
        map->Tree
        complete-line)))

(defn get-line [^Tree t]
  (:line t))

(defn get-fen [^Tree t]
  (line/fen (:line t)))

(defn line-complete? [^Tree t]
  (line/complete? (:line t)))

(defn next-frame [^Tree t]
  (update t :line line/next-frame))

(defn get-moves
  ([^Tree t]
   (get-moves t (get-fen t)))
  ([^Tree t fen]
   (get (:fen->moves t) fen)))

(defn get-sans [^Tree t]
  (map :san (get-moves t)))

(defn get-lines-from-current-fen [^Tree t]
  (let [fen (get-fen t)
        num-lines (get-in t [:fen->num-lines fen])]
    (:lines (reduce (fn [{:keys [lines
                                 tree]
                          :as acc}
                         _]
                      (-> acc
                          (update :lines conj (get-line tree))
                          (update :tree next-line)))
                    {:lines []
                     :tree (assoc t :initial-fen fen :base-fen fen)}
                    (range num-lines)))))

(defn truncate-line-at-current-fen [^Tree t]
  (update t :line line/truncate-at-current-fen))

(defn apply-move [^Tree t ^Move m]
  (update t :line line/apply-move m))
