(ns chess-journal3.move
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.fen :as fen]))

;; TODO Fix the following confusing thing: there are two kinds of "move":
;; 1) The Move defined here
;; 2) A "move" with to and from squares and a san.

(defrecord Move
  [fen
   san
   tag])

(defn initial-fen [m] (:fen m))

(defn final-fen [m]
  (chess/apply-san (:fen m) (:san m)))

(defn san [m] (:san m))

(defn tag [m] (:tag m))

(defn new-from-san [tag fen san]
  (map->Move {:tag tag :fen fen :san san}))

(defn new-from-squares [tag fen from-square to-square promote-piece]
  {:pre [(some? fen)
         (some? from-square)
         (some? to-square)]}
  (when (chess/legal-move? fen from-square to-square)
    (let [piece (fen/piece-on-square fen from-square)
          to-rank (Integer/parseInt (subs to-square 1 2))
          promoting (or (and (= "p" piece) (= 1 to-rank))
                        (and (= "P" piece) (= 8 to-rank)))
          san (if promoting
                (chess/get-san fen from-square to-square :promote promote-piece)
                (chess/get-san fen from-square to-square))]
      (from-san tag fen san))))

;; Ignores tags
(defn equals [m1 m2]
  (and (= (san m1) (san m2))
       (= (initial-fen m1) (initial-fen m2))))

(defn get-active-color [^Move m]
  (-> m initial-fen fen/get-active-color))
