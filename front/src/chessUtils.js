export const getActiveColor = (fen) => {
    let words = fen.split(" ");
    switch (words[1]) {
    case "w":
        return "w";
        break;
    case "b":
        return "b";
        break;
    }
};

export const initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";


const getSquareMap = (squareStr) => {
    let file = {
        "A": 0, "B": 1, "C": 2, "D": 3, "E": 4, "F": 5, "G": 6, "H": 7
    }[squareStr.charAt(0)];
    let rank = 8 - parseInt(squareStr.charAt(1));
    return { rank: rank, file: file };
};

const isPromotion = (fromSquare) => {
    let board = parseFEN(appState.fen);
    let { rank, file } = getSquareMap(fromSquare)
    let piece = board[rank][file]
    if ((piece == "p" && rank == 6)
        || (piece == "P" && rank == 1)) {
        return true;
    }
    return false;
}
