(ns chess-journal3.modes.endgames-test
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.modes.endgames :as endgames]
    [clojure.test :refer [is deftest]]))

(def lucena (fen/unparse (assoc default-position-stuff
                           :square->piece {"E8" "K" "G7" "k" "E7" "P" "F2" "R" "A1" "r"}
                           :active-color "w")))
(def philidor (fen/unparse (assoc default-position-stuff
                             :square->piece {"E8" "k" "D5" "K" "E5" "P" "A7" "R" "A1" "r"}
                             :active-color "b")))
(def basic-pawn-win (fen/unparse (assoc default-position-stuff
                                   :square->piece {"E5" "P" "E6" "K" "E8" "k"}
                                   :active-color "b")))
(def basic-pawn-draw (fen/unparse (assoc default-position-stuff
                                    :square->piece {"E5" "P" "E6" "K" "E8" "k"}
                                    :active-color "w")))

(deftest getting-material-from-fen
  (is (= "KQRRBBNNPPPPPPPPvKQRRBBNNPPPPPPPP"
         (#'endgames/get-material-from-fen fen/initial)))
  (is (= {"KRPvKR" 2 "KPvK" 2}
         (->> [lucena philidor basic-pawn-win basic-pawn-draw]
              (map #'endgames/get-material-from-fen)
              frequencies))))

(def default-position-stuff
  {:white-can-castle-kingside false
   :white-can-castle-queenside false
   :black-can-castle-kingside false
   :black-can-castle-queenside false
   :en-passant-square nil
   :halfmove-clock 0
   :fullmove-counter 1})

(def endgames
  (with-redefs [db/get-endgames (constantly
                                  (for [fen [lucena
                                             basic-pawn-draw
                                             philidor
                                             basic-pawn-win]]
                                    {:fen fen :objective "" :comment ""}))]
    (#'endgames/get-endgames nil)))

(deftest nexting-with-material
  (let [state {:endgames endgames
               :endgame-idx 0}]
    (is (= 2 (:endgame-idx (endgames/next-with-material state "KRPvKR"))))
    (is (= 0 (:endgame-idx (endgames/next-with-material
                             (endgames/next-with-material state
                                                          "KRPvKR")
                             "KRPvKR"))))
    (is (= 1 (:endgame-idx (endgames/next-with-material state "KPvK"))))
    (is (= 3 (:endgame-idx (endgames/next-with-material
                             (endgames/next-with-material state
                                                          "KPvK")
                             "KPvK"))))))

(deftest cycling-material
  (let [state {:endgames endgames
               :endgame-idx 0}]
    (is (= "KRPvKR" (endgames/get-material state)))
    (is (= "KPvK" (-> state
                      endgames/cycle-material
                      endgames/get-material)))
    (is (= "KRPvKR" (-> state
                        endgames/cycle-material
                        endgames/cycle-material
                        endgames/get-material)))))
