(ns chess-journal3.pgn-test
  (:require
    [chess-journal3.pgn :as pgn]
    [clojure.string :as string]
    [clojure.test :refer [is deftest]]))

(deftest reading-metadata-element
  (is (= ["Result" "0-1"]
         (pgn/read-metadata-element
           "[Result \"0-1\"]"))))

(def example-data "
[Event \"Live Chess\"]
[Site \"Chess.com\"]
[Date \"2022.05.12\"]
[Round \"-\"]
[White \"kasimlp1234\"]
[Black \"pat104\"]
[Result \"0-1\"]
[WhiteElo \"1845\"]
[BlackElo \"1810\"]
[TimeControl \"600\"]
[EndTime \"12:24:54 PDT\"]
[Termination \"pat104 won by checkmate\"]

1. e4 c5 2. d4 cxd4 3. Qxd4 Nc6 4. Qe3 d6 5. Nc3 a6 6. Bd2 Nf6 7. h3 b5 8. g4 e5
9. Be2 Be6 10. g5 Nd7 11. f4 Nb6 12. f5 Bc4 13. Nd5 Bxe2 14. Nxe2 Nc4 15. Qc3
Ne7 16. O-O-O Nxd5 17. exd5 Rc8 18. Qg3 Qc7 19. Kb1 Be7 20. h4 Qc5 21. Qf3 a5
22. f6 gxf6 23. gxf6 Bd8 24. b3 Na3+ 25. Kb2 Qxc2+ 26. Ka1 Bb6 27. Rc1 Qxd2 28.
Rxc8+ Kd7 29. Rxh8 Bd4+ 30. Nxd4 Qxd4+ 31. Qc3 Qxc3# 0-1

[Event \"Live Chess\"]
[Site \"Chess.com\"]
[Date \"2022.05.12\"]
[Round \"-\"]
[White \"acunad\"]
[Black \"pat104\"]
[Result \"1-0\"]
[WhiteElo \"1850\"]
[BlackElo \"1803\"]
[TimeControl \"600\"]
[EndTime \"13:57:16 PDT\"]
[Termination \"acunad won by resignation\"]

1. d4 Nf6 2. Bf4 e6 3. Nf3 b6 4. e3 Ba6 5. c4 Bb4+ 6. Nbd2 Ne4 7. Bd3 f5 8. O-O
Nxd2 9. Nxd2 O-O 10. a3 Bd6 11. b4 Bxf4 12. exf4 Qf6 13. Nf3 Bb7 14. Be2 Qg6 15.
g3 Qh5 16. Re1 Qh3 17. Bf1 Qh6 18. Qb3 d6 19. Re3 Rf6 20. Rae1 Nd7 21. Ng5 Nf8
22. c5 Bd5 23. Bc4 Bxc4 24. Qxc4 d5 25. Qe2 Re8 26. Re5 Qg6 27. Qb5 h6 28. Nf3
Rd8 29. Qc6 Qf7 30. R5e3 e5 31. Qb7 exf4 32. Re7 Qh5 33. Qxc7 Rd7 34. Rxd7 Nxd7
35. Qxf4 g5 36. Qe3 Kg7 37. Ne5 Re6 38. Qb3 Nxe5 39. Rxe5 Rxe5 40. dxe5 Qf3 41.
Qxf3 1-0")

(deftest reading-metadata
  (is (= {"Black" "pat104"
          "EndTime" "12:24:54 PDT"
          "BlackElo" 1810
          "WhiteElo" 1845
          "Result" "0-1"
          "Round" "-"
          "Event" "Live Chess"
          "White" "kasimlp1234"
          "TimeControl" "600"
          "Date" "2022.05.12"
          "Termination" "pat104 won by checkmate"
          "Site" "Chess.com"}
         (first (pgn/read-metadata (string/split example-data #"\n"))))))

(deftest reading-moves
  (is (= [{:from "E2" :to "E4"}
          {:from "E7" :to "E5"}
          {:from "G1" :to "F3"}
          {:from "B8" :to "C6"}
          {:from "D2" :to "D4"}
          {:from "E5" :to "D4"}
          {:from "F1" :to "C4"}
          {:from "H7" :to "H6"}
          {:from "E1" :to "G1"}
          {:from "F8" :to "C5"}
          {:from "C2" :to "C3"}
          {:from "D8" :to "F6"}
          {:from "E4" :to "E5"}
          {:from "F6" :to "G6"}
          {:from "C3" :to "D4"}
          {:from "C5" :to "B6"}
          {:from "D4" :to "D5"}
          {:from "C6" :to "A5"}
          {:from "C4" :to "D3"}
          {:from "G6" :to "H5"}
          {:from "B1" :to "C3"}
          {:from "G8" :to "E7"}
          {:from "C1" :to "D2"}
          {:from "D7" :to "D6"}
          {:from "E5" :to "D6"}
          {:from "C7" :to "C6"}]
         (first
           (pgn/read-moves
             (string/split "
1. e4 e5 2. Nf3 Nc6 3. d4 exd4 4. Bc4 h6 5. O-O Bc5 6. c3 Qf6 7. e5 Qg6 8. cxd4
Bb6 9. d5 Na5 10. Bd3 Qh5 11. Nc3 Ne7 12. Bd2 d6 13. exd6 c6 1-0"
                           #"\n"))))))
