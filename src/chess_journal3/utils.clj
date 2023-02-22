(ns chess-journal3.utils
  (:require
    [clojure.string :as string]))

(definterface IState
  (getMode [])
  (getFen []) ;; Do we need this?
  (nextFrame [])
  (prevFrame [])
  (switchColor [])
  (clickSquare [square])
  (makeClientView [])
  (cleanUp []))

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

(defn hyphenate [k]
  (keyword (string/replace (name k) "_" "-")))

(defn hyphenate-keys [m]
  (reduce-kv (fn [acc k v]
               (assoc acc (hyphenate k) v))
             {}
             m))

(defn pprint-state [state]
  (println "State:")
  (->> state
       (map (fn [[k v]]
              (let [v-str (str v)
                    v-str (if (> (count v-str) 1000)
                            (subs v-str 0 1000)
                            v-str)]
                (format "%s: %s\n" (name k) v-str))))
       (run! println))
  (println ""))

(defn cycle-promote-piece [state]
  (update state
    :promote-piece
    {"N" "B"
     "B" "R"
     "R" "Q"
     "Q" "N"}))
