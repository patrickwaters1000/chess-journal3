(ns chess-journal3.engine
  "Wrapper for inerfacing with external chess engine process according
  to Universal Chess Interface protocol."
  (:require
    [clojure.string :as string])
  (:import
    (java.io
      InputStream
      InputStreamReader
      BufferedReader
      OutputStream
      Closeable)
    (java.lang Process ProcessBuilder)))

;; THIS IS TERRIBLE (but works). REWRITE IT FROM SCRATCH.

(definterface IEngine
  (setElo [elo])
  (getMove [fen millis]))

(defn send-to-engine! [engine-in msg]
  (.write engine-in (.getBytes msg))
  (.flush engine-in))

(defn get-from-engine [engine-out regex]
  (loop [msg (.readLine engine-out)]
    (or (re-matches regex msg)
        (recur (.readLine engine-out)))))

(defn parse-uci-move
  "Parses a move from the format used by Universal Chess Interface."
  [uci-move-str]
  (let [from (string/upper-case (subs uci-move-str 0 2))
        to (string/upper-case (subs uci-move-str 2 4))
        promote (when (< 4 (.length uci-move-str))
                  (string/upper-case (subs uci-move-str 4 5)))]
    (cond-> {:from from
             :to to}
      promote (assoc :promote promote))))

(defrecord Engine [proc in out err lock]
  IEngine
  (getMove [_ fen wait-millis]
    (locking lock
      (send-to-engine! in (format "position fen %s\n" fen))
      (send-to-engine! in (format "go movetime %s\n" wait-millis))
      (let [regex #"^bestmove (.*) ponder (.*)$"
            [_ move _] (get-from-engine out regex)]
        (parse-uci-move move))))
  (setElo [_ elo]
    (locking lock
      (send-to-engine! in (format "setoption name UCI_LimitStrength value true\n" elo))
      (send-to-engine! in (format "setoption name UCI_Elo value %s\n" elo))))
  Closeable
  (close [_]
    (.destroyForcibly proc)))

(defn new-engine []
  (let [pb (ProcessBuilder. ["stockfish"])
        p (.start pb)
        out (BufferedReader.
              (InputStreamReader.
                (.getInputStream p) "UTF-8"))
        err (BufferedReader.
              (InputStreamReader.
                (.getErrorStream p) "UTF-8"))
        in (.getOutputStream p)]
    (map->Engine {:lock (Object.)
                  :proc p
                  :in in
                  :out out
                  :err err})))

(def engine (atom nil))

;;(def default-elo 3000)
(def default-think-millis 5000)

(defn reboot! []
  (when @engine (.close @engine))
  (reset! engine (new-engine))
  ;;(.setElo @engine default-elo)
  )

(defn get-move [fen]
  (.getMove @engine fen default-think-millis))

(comment
  (require '[chess-journal3.chess :as chess])
  (def e (new-engine))
  (.getMove e
            "8/8/8/3k4/8/4K3/3P4/8 w - - 0 1"
            2000)
  (.getMove e
            chess/initial-fen
            2000)
  (.close e)
  (with-open [e (new-engine)]
    (.getMove e chess/initial-fen 1000)))
