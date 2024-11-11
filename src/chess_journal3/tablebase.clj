(ns chess-journal3.tablebase
  "Wrapper for interfacing with Zyzygy tables, mediated by a Python script."
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.fen :as fen]
    [chess-journal3.utils :as u]
    [clojure.string :as string])
  (:import
    (java.io
      InputStreamReader
      BufferedReader)
    (java.lang Process ProcessBuilder)))

(defn probe [fen]
  (let [pb (ProcessBuilder. ["python" "./tablebase/main.py" fen])
        p (.start pb)
        p-out (BufferedReader.
                (InputStreamReader.
                  (.getInputStream p) "UTF-8"))
        p-err (BufferedReader.
                (InputStreamReader.
                  (.getErrorStream p) "UTF-8"))
        out-lines (line-seq p-out)
        err-lines (line-seq p-err)]
    (.waitFor p)
    (if (seq err-lines)
      (println (string/join "\n" err-lines))
      (let [[wdl dtz] (map #(Integer/parseInt %) out-lines)]
        {:wdl wdl :dtz (Math/abs dtz)}))))

(defn interpret-result [result active-color]
  (if (nil? result)
    "Position not in tablebase"
    (let [{:keys [wdl dtz]} result
          winner (get {"b" "Black"
                       "w" "White"}
                      (cond
                        (pos? wdl) active-color
                        (neg? wdl) (u/other-color active-color)))]
      (if winner
        (format "%s is winning;\nprogress in %d moves" winner (quot (inc dtz) 2))
        "Best play draws"))))

(defn- evaluate [wdl]
  (cond
    (pos? wdl) :winning
    (neg? wdl) :losing
    :else :drawn))

(defn rank-legal-moves [fen]
  (->> (for [san (chess/get-legal-move-sans fen)
             :let [new-fen (chess/apply-san fen san)
                   {:keys [wdl dtz]} (probe new-fen)
                   evaluation (evaluate (- wdl))]]
         {:san san
          :fen fen
          :evaluation evaluation
          :wdl wdl
          :dtz dtz})
       (sort-by (juxt :wdl
                      #(* -1
                          (:wdl %)
                          (:dtz %))))
       (map #(select-keys % [:san :evaluation :dtz]))
       (map (fn [x]
              (println x)
              x))
       doall))

(comment
  (interpret-result (probe "3Q4/1kr5/8/1K6/8/8/8/8 w - - 1 64"))
  (get-tablebase-moves "8/8/3k4/8/8/3PK3/8/8 w - - 1 0")
  ;;
  )
