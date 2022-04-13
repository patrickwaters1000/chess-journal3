import React, { Component } from "react";

const dx = window.innerHeight / 8;
const dy = window.innerHeight / 8;

const getSquareStr = (rank, file) => {
  let fileStr = {
    0: "A", 1: "B", 2: "C", 3: "D", 4: "E", 5: "F", 6: "G", 7: "H"
  }[file];
  return `${fileStr}${rank + 1}`;
};

const getPieceFn = (points) => {
    return (props) => {
        let { rank,
              file,
              flipBoard,
              clickSquare } = props;
        const pointsStr = points.map(p => {
            let [x, y] = p;
            let adjustedRank = (flipBoard ? rank : 7 - rank);
            let adjustedFile = (flipBoard ? 7 - file : file);
            let xScaled = (adjustedFile + x ) * dx;
            let yScaled = (adjustedRank + 1 - y) * dy;
            return `${xScaled},${yScaled}`;
        }).join(" ");
        return React.createElement(
            "polygon",
            {
	        points: pointsStr,
	        fill: props.color,
	        stroke: "#000000",
	        onClick: () => { clickSquare(getSquareStr(rank, file)); }
            }
        );
    };
};

const Pawn = getPieceFn([
  [0.3, 0.1],
  [0.45, 0.2],
  [0.45, 0.3],
  [0.45, 0.4],
  [0.37, 0.4],
  [0.45, 0.5],
  [0.5, 0.6],
  [0.55, 0.5],
  [0.63, 0.4],
  [0.55, 0.4],
  [0.55, 0.3],
  [0.55, 0.2],
  [0.7, 0.1]
]);

const Knight = getPieceFn([
  [0.23, 0.1],
  [0.3, 0.3],
  [0.4, 0.4],
  [0.45, 0.6],
  [0.25, 0.45],
  [0.15, 0.5],
  [0.2, 0.6],
  [0.3, 0.7],
  [0.33, 0.8],
  [0.4, 0.75],
  [0.6, 0.7],
  [0.7,0.55],
  [0.66, 0.3],
  [0.73, 0.1]
]);

const Bishop = getPieceFn([
  [0.2, 0.1],
  [0.23, 0.15],
  [0.35, 0.2],
  [0.2, 0.5],
  [0.23, 0.6],
  [0.47, 0.8],
  [0.43, 0.84],
  [0.5, 0.88],
  [0.57, 0.84],
  [0.53, 0.8 ],
  [0.77, 0.6],
  [0.8, 0.5],
  [0.65, 0.2],
  [0.77, 0.15],
  [0.8, 0.1]
]);

const Rook = getPieceFn([
  [0.13, 0.1],
  [0.13, 0.15],
  [0.25, 0.15],
  [0.25, 0.55],
  [0.17, 0.55],
  [0.17, 0.7],
  [0.29, 0.7],
  [0.29, 0.65],
  [0.35, 0.65],
  [0.35, 0.7],
  [0.47, 0.7],
  [0.47, 0.65],
  [0.53, 0.65],
  [0.53, 0.7],
  [0.65, 0.7],
  [0.65, 0.65],
  [0.71, 0.65],
  [0.71, 0.7],
  [0.83, 0.7],
  [0.83, 0.55],
  [0.75, 0.55],
  [0.75, 0.15],
  [0.87, 0.15],
  [0.87, 0.1]
]);

const Queen = getPieceFn([
  [0.15, 0.1],
  [0.2,0.25],
  [0.1, 0.55],
  [0.25, 0.45],
  [0.2, 0.65],
  [0.4, 0.5],
  [0.5, 0.7],
  [0.6, 0.5],
  [0.8, 0.65],
  [0.75, 0.45],
  [0.9, 0.55],
  [0.8, 0.25],
  [0.85, 0.1]
]);

const King = getPieceFn([
  [0.15, 0.1],
  [0.15, 0.15],
  [0.25, 0.2],
  [0.15, 0.5],
  [0.25, 0.6],
  [0.475, 0.6],
  [0.475, 0.65],
  [0.425, 0.65],
  [0.425, 0.7],
  [0.475, 0.7],
  [0.475, 0.75],
  [0.525, 0.75],
  [0.525, 0.70],
  [0.575, 0.70],
  [0.575, 0.65],
  [0.525, 0.65],
  [0.525, 0.60],
  [0.75, 0.60],
  [0.85, 0.5],
  [0.75, 0.2],
  [0.85, 0.15],
  [0.85, 0.1]
]);

const getPiece = piece => {
    let { pieceType, // keys listed for clarity
          rank,
          file,
          clickSquare } = piece;
    return {
        'p': Pawn({...piece, color: "#000000"}),
        'P': Pawn({...piece, color: "#ffffff"}),
        'n': Knight({...piece, color: "#000000"}),
        'N': Knight({...piece, color: "#ffffff"}),
        'b': Bishop({...piece, color: "#000000"}),
        'B': Bishop({...piece, color: "#ffffff"}),
        'r': Rook({...piece, color: "#000000"}),
        'R': Rook({...piece, color: "#ffffff"}),
        'q': Queen({...piece, color: "#000000"}),
        'Q': Queen({...piece, color: "#ffffff"}),
        'k': King({...piece, color: "#000000"}),
        'K': King({...piece, color: "#ffffff"}),
    }[pieceType];
}

const getSquareFillColor = (i, j, isSelected) => {
    if (isSelected) {
	return "#cc0000";
    } else if ((i + j) % 2 == 0) {
	return "#00b33c";
    } else {
	return "#ffffb3";
    }
};

export default class Board extends React.Component {
    render () {
        let { pieces,
              flipBoard,
              clickSquare,
              selectedSquare } = this.props;
        const squares = [];
        for (let i = 0; i < 8; i++) {
            for (let j = 0; j < 8; j++) {
                let squareStr = getSquareStr(j, i);
	        let isSelected = (selectedSquare == squareStr);
	        let fill = getSquareFillColor(i, j, isSelected);
	        let square = React.createElement(
	            "rect",
	            {
	                fill: fill,
	                x: (flipBoard ? (7 - i) : i) * dx,
	                y: (flipBoard ? j : (7 - j)) * dy,
	                width: dx,
	                height: dy,
	                onClick: () => { clickSquare(getSquareStr(j, i)); }
	            }
	        );
	        squares.push(square);
            }
        }
        return React.createElement(
            "svg",
            {
	        width: 8 * dx,
	        height: 8 * dy
            },
            ...squares,
            ...pieces.map(p => {
	        return getPiece({
	            ...p,
	            flipBoard: flipBoard,
	            clickSquare: clickSquare
	        });
            })
        );
    };
}
