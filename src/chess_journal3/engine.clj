(ns chess-journal3.engine
  "Wrapper for inerfacing with external chess engine process according
  to Universal Chess Interface protocol."
  (:require
    [chess-journal3.move :as move]
    [clj-time.core :as t]
    [clojure.core.async :as a :refer [>! <! >!! <!! go chan buffer close! thread
                                      alts! alts!! timeout go-loop]]
    [clojure.string :as string])
  (:import
    (java.io
      File
      InputStream
      InputStreamReader
      BufferedReader
      OutputStream
      Closeable)
    (java.lang Process ProcessBuilder)))

(def log-file "engine-log.txt")

(defn- parse-uci-move
  "Parses a move from the format used by Universal Chess Interface."
  [fen uci-move-str]
  (let [from (string/upper-case (subs uci-move-str 0 2))
        to (string/upper-case (subs uci-move-str 2 4))
        promote (when (< 4 (.length uci-move-str))
                  (string/upper-case (subs uci-move-str 4 5)))]
    (move/new-from-squares nil
                           fen
                           from
                           to
                           promote)))

(definterface IEngine
  (getMove [fen elo movetime]))

(declare get-move)

(defrecord Engine
  [^Process p
   in
   out
   err]
  Closeable
  (close [_]
    (.destroyForcibly p)
    (close! in)
    (close! out)
    (close! err))
  IEngine
  (getMove [engine fen elo movetime]
    (get-move engine fen elo movetime)))

(defn- log [msg-type msg-str]
  (spit log-file
        (format "%s %s\n%s\n\n"
                (t/now)
                (name msg-type)
                msg-str)
        :append true))

(defn make []
  (let [pb (ProcessBuilder. ["stockfish"])
        p (.start pb)
        p-out (BufferedReader.
                (InputStreamReader.
                  (.getInputStream p) "UTF-8"))
        p-err (BufferedReader.
                (InputStreamReader.
                  (.getErrorStream p) "UTF-8"))
        p-in (.getOutputStream p)
        out (chan)
        err (chan)
        in (chan)]
    (go-loop []
      (when-some [msg (<! in)]
        (log :in msg)
        (.write p-in (.getBytes msg))
        (.flush p-in)
        (recur)))
    (go (loop []
          (let [msg (.readLine p-out)]
            (log :out msg)
            (when (>! out msg)
              (recur)))))
    (go (loop []
          (let [msg (.readLine p-err)]
            (log :err msg)
            (when (>! out msg)
              (recur)))))
    (map->Engine
      {:process p
       :in in
       :out out
       :err err})))

(defn- recieve
  "Reads the engine's output until a line matches the regex, and returns the
  match."
  [^Engine e regex]
  (loop []
    (let [msg (<!! (:out e))]
      (or (re-matches regex msg)
          (recur)))))

;; TODO Throw exceptions of the engine writes to stderr.
(defn- get-move* [^Engine e fen movetime]
  (let [in (:in e)
        out (:out e)
        move-millis (t/in-millis movetime)
        fen-msg (format "position fen %s\n" fen)
        go-msg (format "go movetime %s\n" move-millis)
        move-regex #"^bestmove (.*) ponder .*$"]
    (>!! in fen-msg)
    (>!! in go-msg)
    (let [[_ move-str] (recieve e move-regex)]
      (parse-uci-move fen move-str))))

(defn- set-elo [^Engine e elo]
  (let [in (:in e)
        msg-1 "setoption name UCI_LimitStrength value true\n"
        msg-2 (format "setoption name UCI_Elo value %s\n" elo)]
    (>!! in msg-1)
    (>!! in msg-2)))

(def lock (Object.))

;; TODO Check if clojure.core/memoize does the same thing.
(def fen-x-elo->move (atom {}))

(defn- get-move [^Engine e fen elo movetime]
  (or (get @fen-x-elo->move [fen elo])
      (locking lock
        (when elo
          (set-elo e elo))
        (let [m (get-move* e fen movetime)
              v (swap! fen-x-elo->move assoc [fen elo] m)]
          (get v [fen elo])))))

(comment
  (get-move* (make)
             "8/8/8/3k4/8/4K3/3P4/8 w - - 0 1"
             (t/seconds 2))
  ;;
  )
