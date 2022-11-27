(ns chess-journal3.modes.endgames-test
  (:require
    [chess-journal3.db :as db]
    [chess-journal3.fen :as fen]
    [chess-journal3.modes.endgames :as endgames]
    [clojure.test :refer [is deftest]]))

(def default-position-stuff
  {:white-can-castle-kingside false
   :white-can-castle-queenside false
   :black-can-castle-kingside false
   :black-can-castle-queenside false
   :en-passant-square nil
   :halfmove-clock 0
   :fullmove-counter 1})

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
         (#'endgames/get-material fen/initial)))
  (is (= {"KRPvKR" 2 "KPvK" 2}
         (->> [lucena philidor basic-pawn-win basic-pawn-draw]
              (map #'endgames/get-material)
              frequencies))))

(deftest getting-file-of-unique-pawn
  (is (= "E" (#'endgames/get-file-of-unique-pawn lucena))))

(deftest classifying-endgames
  (is (= ["KRPvKR" "E"]
         (->> (#'endgames/classify lucena)
              (map second))))
  (is (= ["KPvK"]
         (->> (#'endgames/classify basic-pawn-win)
              (map second)))))

(deftest matching-endgame-class
  (is (#'endgames/matches-class? [] lucena))
  (is (#'endgames/matches-class? [[#'endgames/get-material "KRPvKR"]] lucena))
  (is (not (#'endgames/matches-class? [[#'endgames/get-material "KPvK"]] lucena)))
  (is (#'endgames/matches-class? [[#'endgames/get-material "KRPvKR"]
                                  [#'endgames/get-file-of-unique-pawn "E"]]
       lucena)))

(deftest calculating-indices
  (is (= 2 (#'endgames/index-of 5 [0 3 5 2 8])))
  (is (nil? (#'endgames/index-of 6 [0 3 5 2 8]))))

(def endgames
  (for [fen [lucena
             basic-pawn-draw
             philidor
             basic-pawn-win]]
    {:fen fen :objective "" :comment ""}))

(deftest cycling-subclass
  (let [endgame-class [[#'endgames/get-material "KRPvKR"]]
        got (#'endgames/cycle-subclass endgame-class 1 endgames)
        want [[#'endgames/get-material "KPvK"]]]
    (is (= want got)))
  (let [endgame-class [[#'endgames/get-material "KRPvKR"]
                       [#'endgames/get-file-of-unique-pawn "E"]]
        got (#'endgames/cycle-subclass endgame-class 1 endgames)
        want [[#'endgames/get-material "KRPvKR"]
              [#'endgames/get-file-of-unique-pawn "E"]]]
    (is (= want got))))

(deftest getting-endgame-fens-in-current-class
  (let [state {:endgame-class [[#'endgames/get-material "KRPvKR"]
                               [#'endgames/get-file-of-unique-pawn "E"]]
               :endgames endgames}
        got (set (#'endgames/get-endgame-fens-in-current-class state))
        want #{lucena philidor}]
    (is (= want got))))

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

(comment
  (db/insert-endgame! db/db {:fen lucena
                             :comment ""
                             :objective ""})
  (db/get-endgames db/db)

  ;;
  )
