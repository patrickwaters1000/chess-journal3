(ns chess-journal3.testlib
  "Utility functions to be used in tests only."
  (:require
    [chess-journal3.constants :as c]
    [chess-journal3.db :as db]
    [chess-journal3.line :as line]))

(defn add-san-seqs-to-db [db tag san-seqs]
  (db/insert-tags! db [tag])
  (run! (fn [sans]
          (let [line (line/new-from-sans tag c/initial-fen sans)
                moves (line/to-moves line)]
            (db/insert-tagged-moves! db moves)))
        san-seqs))
