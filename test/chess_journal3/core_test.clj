(ns chess-journal3.core-test
  (:require
    [chess-journal3.chess :as chess]
    [chess-journal3.core :as core]
    [chess-journal3.db :as db]
    [chess-journal3.db-test :as db-test :refer [db]]
    [chess-journal3.fen :as fen]
    [clojure.test :refer [is deftest]]))

(require '[clojure.java.jdbc :as jdbc])

(def initial-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
(def fen-after-1e4 "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")

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
          :idx 1}
         (core/move {:fens [initial-fen]
                     :sans []
                     :idx 0}
                    {:from "E2"
                     :to "E4"}))))

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
              :idx 0
              :selected-square "E2"
              :color "w"}
             "E4")))))
