(ns chess-journal3.utils
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.fen :as fen]
    [clojure.string :as string]))

(defn- map-vals [f m]
  (reduce-kv (fn [acc k v]
               (assoc acc k (f v)))
             {}
             m))

(defn index-of-first [f xs]
  (->> xs
       (map-indexed vector)
       (filter (comp f second))
       (map first)
       first))

(defn get-unique [xs]
  (when-not (= 1 (count xs))
    (throw (Exception. "Failed")))
  (first xs))

(defn cycle [xs]
  (conj (vec (rest xs)) (first xs)))

(defn other-color [color]
  (case color "w" "b" "b" "w"))
