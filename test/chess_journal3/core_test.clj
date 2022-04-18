(ns chess-journal3.core-test
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.core :as core]
    [chess-journal3.db :as db]
    [chess-journal3.db-test :as db-test :refer [db]]
    [chess-journal3.fen :as fen]
    [clojure.java.jdbc :as jdbc]
    [clojure.test :refer [is deftest]]))

(defn failing-keys [m1 m2]
  (->> (concat (keys m1) (keys m2))
       (into #{})
       (remove #(= (get m1 %) (get m2 %)))
       sort))

(defn fen-line [& sans] (vec (reductions chess/apply-move-san fen/initial sans)))

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
(def fen-after-1e4 "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
(def fen-after-1e4c5 "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2")

(deftest checking-whose-move-it-is
  (is (#'core/players-move? {:fens [initial-fen]
                             :color "w"
                             :idx 0}))
  (is (#'core/opponents-move? {:fens [initial-fen
                                      fen-after-1e4]
                               :color "w"
                               :idx 1}))
  (is (#'core/players-move? {:fens [initial-fen
                                    fen-after-1e4]
                             :color "b"
                             :idx 1})))

(deftest moving
  (is (= {:fens [initial-fen fen-after-1e4]
          :sans ["e4"]
          :mode "edit"
          :opponent-must-move false
          :selected-square nil
          :idx 1}
         (core/move {:fens [initial-fen]
                     :mode "edit"
                     :sans []
                     :idx 0}
                    {:from "E2"
                     :to "E4"})))
  (is (= {:fens [initial-fen fen-after-1e4]
          :sans ["e4"]
          :mode "review"
          :opponent-must-move true
          :selected-square nil
          :idx 1
          :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                          {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                          {:san "c6" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"}
                          {:san "Nc3" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 1 2"}]]}
         (core/move {:fens [initial-fen]
                     :mode "review"
                     :sans []
                     :idx 0
                     :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                                     {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                                     {:san "c6" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"}
                                     {:san "Nc3" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 1 2"}]]}
                    {:from "E2"
                     :to "E4"})))
  (is (= {:fens (fen-line "e4" "c6" "Nc3")
          :sans ["e4" "c6" "Nc3"]
          :mode "review"
          :opponent-must-move false
          :selected-square nil
          :idx 3
          :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                          {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                          {:san "c6" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"}
                          {:san "Nc3" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 1 2"}]]}
         (core/move {:fens (fen-line "e4" "c6")
                     :mode "review"
                     :sans ["e4" "c6"]
                     :idx 2
                     :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                                     {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                                     {:san "c6" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"}
                                     {:san "Nc3" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 1 2"}]]}
                    {:from "B1"
                     :to "C3"}))))

(deftest getting-line-data
  (is (= [{:tag "white-reportoire"
           :initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
           :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
           :san "e4"}]
         (core/get-line-data {:fens [initial-fen
                                     fen-after-1e4]
                              :sans ["e4"]
                              :idx 0
                              :color "w"}))))

(deftest checking-line-ends-with-opponent-to-play
  (is (core/line-ends-with-opponent-to-play? {:fens [initial-fen
                                                     fen-after-1e4]
                                              :sans ["e4"]
                                              :color "w"}))
  (is (not (core/line-ends-with-opponent-to-play? {:fens [initial-fen
                                                          fen-after-1e4]
                                                   :sans ["e4"]
                                                   :color "b"}))))

(deftest adding-lines
  (db-test/reset! db)
  (db/insert-tags! db ["white-reportoire"])
  (core/add-line! {:db db
                   :fens [initial-fen
                          fen-after-1e4]
                   :sans ["e4"]
                   :idx 0
                   :color "w"})
  (= [{:san "e4" :final-fen fen-after-1e4}]
     (db/get-tagged-moves db "white-reportoire" initial-fen)))

(let [get-moves #(db/get-tagged-moves db "black-reportoire" %)
      stack [[{:final-fen initial-fen}]]
      line (last stack)
      fen (:final-fen (last line))
      moves (get-moves fen)]
  moves)

(deftest getting-lines-in-subtree
  (db-test/reset! db)
  (db/insert-tags! db ["black-reportoire"])
  (vec (for [sans [["e4" "c5" "Nf3" "d6" "d4" "cxd4"]
                   ["e4" "c5" "Nf3" "d6" "c3" "Nf6"]
                   ["e4" "c5" "c3" "Nf6" "e5" "Nd5"]]
             :let [fens (reductions chess/apply-move-san initial-fen sans)]]
         (core/add-line! {:db db
                          :fens fens
                          :sans sans
                          :color "b"
                          :idx 0})))
  (= (core/get-lines-in-subtree db
                                "black-reportoire"
                                initial-fen)
     [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
       {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
       {:san "c5" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"}
       {:san "c3" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/2P5/PP1P1PPP/RNBQKBNR b KQkq - 0 2"}
       {:san "Nf6" :final-fen "rnbqkb1r/pp1ppppp/5n2/2p5/4P3/2P5/PP1P1PPP/RNBQKBNR w KQkq - 1 3"}
       {:san "e5" :final-fen "rnbqkb1r/pp1ppppp/5n2/2p1P3/8/2P5/PP1P1PPP/RNBQKBNR b KQkq - 0 3"}
       {:san "Nd5" :final-fen "rnbqkb1r/pp1ppppp/8/2pnP3/8/2P5/PP1P1PPP/RNBQKBNR w KQkq - 1 4"}]
      [{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
       {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
       {:san "c5" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"}
       {:san "Nf3" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"}
       {:san "d6" :final-fen "rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 3"}
       {:san "c3" :final-fen "rnbqkbnr/pp2pppp/3p4/2p5/4P3/2P2N2/PP1P1PPP/RNBQKB1R b KQkq - 0 3"}
       {:san "Nf6" :final-fen "rnbqkb1r/pp2pppp/3p1n2/2p5/4P3/2P2N2/PP1P1PPP/RNBQKB1R w KQkq - 1 4"}]
      [{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
       {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
       {:san "c5" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"}
       {:san "Nf3" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"}
       {:san "d6" :final-fen "rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 3"}
       {:san "d4" :final-fen "rnbqkbnr/pp2pppp/3p4/2p5/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq d3 0 3"}
       {:san "cxd4" :final-fen "rnbqkbnr/pp2pppp/3p4/8/3pP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 4"}]]))

(deftest switching-color
  (is (= {}
         (core/switch-color
           {}))))

(deftest checking-move-to-square-is-legal
  (is (core/move-to-square-is-legal?
        {:fens [fen/initial]
         :idx 0
         :selected-square "E2"}
        "E4")))

(deftest checking-move-to-square-is-correct
  (is (core/move-to-square-is-correct?
        {:fens [fen/initial]
         :idx 0
         :selected-square "E2"
         :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                         {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}]]}
        "E4"))
  (is (not (core/move-to-square-is-correct?
             {:fens [fen/initial]
              :idx 0
              :selected-square "E2"
              :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                              {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}]]}
             "E3")))
  (is (not (core/move-to-square-is-correct?
             {:fens [fen/initial]
              :idx 0
              :selected-square "E2"
              :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                              {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}]]}
             "E5"))))

(deftest clicking-squares
  (is (= "F2"
         (:selected-square
           (core/click-square
             {:fens [fen/initial]
              :idx 0
              :selected-square nil
              :color "w"}
             "F2"))))
  (is (nil?
        (:selected-square
          (core/click-square
            {:fens [fen/initial]
             :idx 0
             :selected-square "F2"
             :color "w"}
            "F2"))))
  (is (= fen-after-1e4
         (core/get-fen
           (core/click-square
             {:fens [fen/initial]
              :sans []
              :idx 0
              :selected-square "E2"
              :color "w"
              :mode "edit"}
             "E4")))
      (= fen-after-1e4
         (core/get-fen
           (core/click-square
             {:fens [fen/initial]
              :sans []
              :idx 0
              :selected-square "E2"
              :color "w"
              :mode "review"
              :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                              {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}]]}
             "E4")))))

(deftest resetting
  (is (= {:idx 1
          :locked-idx 1
          :fens [fen/initial fen-after-1e4]
          :sans ["e4"]
          :selected-square nil
          :color "w"
          :mode "review"
          :opponent-must-move true}
         (core/reset {:idx 2
                      :locked-idx 1
                      :fens [fen/initial fen-after-1e4 fen-after-1e4c5]
                      :sans ["e4" "c5"]
                      :selected-square "E1"
                      :color "w"
                      :mode "review"}))))

(deftest initializing-review-mode
  (db-test/reset! db)
  (db/insert-tags! db ["white-reportoire"])
  (->> [["e4" "c5" "Nf3"]
        ["e4" "c6" "Nc3"]]
       (run! (fn [sans]
               (core/add-line! {:db db
                                :color "w"
                                :fens (reductions chess/apply-move-san fen/initial sans)
                                :sans sans}))))
  (is (= {:sans []
          :color "w"
          :db db
          :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                          {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                          {:san "c6" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"}
                          {:san "Nc3" :final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 1 2"}]
                         [{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                          {:san "e4" :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                          {:san "c5" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"}
                          {:san "Nf3" :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"}]]
          :mode "review"
          :fens ["rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"]
          :opponent-must-move false
          :selected-square nil
          :idx 0
          :locked-idx 0}
         (core/init-review-mode {:db db
                                 :color "w"
                                 :locked-idx 0
                                 :fens (fen-line "e4" "e5")
                                 :sans ["e4" "e5"]
                                 :idx 0
                                 :mode "edit"}))))

(deftest checking-conflicts-with-reportoire
  (db-test/reset! db)
  (db/insert-tags! db ["white-reportoire"])
  (->> [["e4" "c5" "Nf3"]
        ["e4" "c6" "Nc3"]]
       (run! (fn [sans]
               (core/add-line! {:db db
                                :color "w"
                                :fens (apply fen-line sans)
                                :sans sans}))))
  (is (nil? (core/line-conflicts-with-reportoire?
              {:db db
               :color "w"
               :fens (fen-line "e4" "c5" "Nf3" "d6" "d4")
               :sans ["e4" "c5" "Nf3" "d6" "d4"]})))
  (is (= {:fullmove-counter 1 :active-color "w" :san "h4"}
         (core/line-conflicts-with-reportoire?
           {:db db
            :color "w"
            :fens (fen-line "h4" "e5" "h5")
            :sans ["h4" "e5" "h5"]}))))

(deftest deleting-current-subtree
  (db-test/reset! db)
  (db/insert-tags! db ["white-reportoire"
                       "deleted-white-reportoire"])
  (->> [["e4" "c5" "Nf3" "d6" "d4"]
        ;;["e4" "c5" "Nf3" "f5" "exf5"]
        ["e4" "c6" "Nc3"]]
       (run! (fn [sans]
               (core/add-line! {:db db
                                :color "w"
                                :fens (apply fen-line sans)
                                :sans sans}))))
  (let [state-1 {:db db
                 :color "w"
                 :fens (fen-line "e4" "c5" "Nf3")
                 :sans ["e4" "c5" "Nf3"]
                 :idx 3}
        state-2 (core/delete-subtree! state-1)
        ;; With initial fen as after 1 e4.
        remaining-moves (db/get-tagged-moves db
                                             "white-reportoire"
                                             (last (fen-line "e4")))
        ;; With initial fen as after 1 e4.
        deleted-moves (db/get-tagged-moves db
                                           "deleted-white-reportoire"
                                           (last (fen-line "e4")))]
    [(= {:db {:user "pwaters" :dbtype "postgresql" :dbname "chess_journal3_test"}
         :color "w"
         :fens ["rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"]
         :sans ["e4"]
         :idx 1}
        state-2)
     (= ["c6"]
        (map :san remaining-moves))
     (= ["c5"]
        (map :san deleted-moves))
     (= 4
        (:n (first (jdbc/query db "
SELECT count(*) AS n
FROM tagged_moves tm LEFT JOIN tags t on tm.tag_id = t.id
WHERE t.name = 'deleted-white-reportoire'"))))]))

(deftest cycling-to-first-matching-line
  (let [state {:fens (fen-line "e4" "e5" "Nf3")
               :review-lines (for [sans [["e4" "e6" "d4"]
                                         ["e4" "e5" "Nf3" "d6" "d4"]
                                         ["e4" "c6" "Nc3"]]
                                   :let [fens (apply fen-line sans)]]
                               (for [fen fens]
                                 {:final-fen fen}))}]
    (is (= {:fens ["rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                   "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
                   "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"
                   "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"]
            :review-lines [[{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                            {:final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                            {:final-fen "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"}
                            {:final-fen "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"}
                            {:final-fen "rnbqkbnr/ppp2ppp/3p4/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 3"}
                            {:final-fen "rnbqkbnr/ppp2ppp/3p4/4p3/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq d3 0 3"}]
                           [{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                            {:final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                            {:final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"}
                            {:final-fen "rnbqkbnr/pp1ppppp/2p5/8/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 1 2"}]
                           [{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
                            {:final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
                            {:final-fen "rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"}
                            {:final-fen "rnbqkbnr/pppp1ppp/4p3/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3 0 2"}]]}
           (core/cycle-to-first-matching-line state)))))

(deftest getting-trunk-of-subtree
  (let [state {:fens (fen-line "e4" "c5" "Nf3" "d6" "d4")
               :sans ["e4" "c5" "Nf3" "d6" "d4"]
               :locked-idx 2}]
    (= [{:final-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
        {:san "e4"
         :initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
         :final-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
        {:san "c5"
         :initial-fen "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
         :final-fen "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"}]
       (core/get-trunk-of-subtree state))))
