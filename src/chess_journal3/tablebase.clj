(ns chess-journal3.tablebase
  "Wrapper for interfacing with Zyzygy tables, mediated by a Python script."
  (:require
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

(defn interpret-result [state result]
  (if (nil? result)
    "Position not in tablebase"
    (let [{:keys [wdl dtz]} result
          active-color (u/get-active-color state)
          winner (get {"b" "Black"
                       "w" "White"}
                      (cond
                        (pos? wdl) active-color
                        (neg? wdl) (u/other-color active-color)))]
      (if winner
        (format "%s is winning;\nprogress in %d moves" winner (quot (inc dtz) 2))
        "Best play draws"))))

(comment
  (interpret-result (probe "3Q4/1kr5/8/1K6/8/8/8/8 w - - 1 64"))
  ;;
  )
