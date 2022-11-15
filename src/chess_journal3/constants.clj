(ns chess-journal3.constants
  (:require
    [clojure.java.io :as io]))

(def username
  (-> "username.txt"
      io/resource
      io/reader
      line-seq
      first))

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
