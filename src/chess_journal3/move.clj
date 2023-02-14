(ns chess-journal3.move)

;; TODO Fix the following confusing thing: there are two kinds of "move":
;; 1) The Move defined here
;; 2) A "move" with to and from squares and a san.

(defrecord Move
  [from
   to
   san
   tag])

(defn from [m] (:from m))
(defn to [m] (:to m))
(defn san [m] (:san m))
(defn tag [m] (:tag m))

(def new map->Move)
