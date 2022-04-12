import React, { Component } from "react";
import ReactDOM from "react-dom";
import Board from "./Board.js";
import { getActiveColor,
         initialFen,
         getSquareMap,
         isPromotion } from "./chessUtils.js";
import { parseFen,
	 getPieces,
	 getPiecesFromFen } from "./Fen.js";

// Do not update note on cancel (DONE?)

const hflexStyle = {display: "flex", flexDirection: "row"}
const vflexStyle = {display: "flex", flexDirection: "column"}

const deepCopy = o => JSON.parse(JSON.stringify(o));

var handle = null; // Will point to top level React component for app

const syncAppState = () => {
  handle.setState(deepCopy(appState));
};

const initialFrame = {
  fens: [initialFen],
  notes: [""],
  sans: [null],
  selected: 0
};

var appState = {
  frameStack: [initialFrame],
  frameIdx: 0,
  variationComplete: false,
  color: "b",
  selectedSquare: null,
  flipBoard: true,
  lockedVariationFrameIdx: null,
  lockedVariationFrame: null
};

// TODO make these fns of `state` so that we can use them in `Page`.
const getFrame = () => {
  let { frameStack,
	frameIdx } = appState;
  return frameStack[frameIdx];
};

const getFrameSelected = () => {
  let { fens,
	notes,
	sans,
	selected } = getFrame();
  return {
    fen: fens[selected],
    note: notes[selected],
    san: sans[selected]
  };
};

const getFen = () => getFrameSelected().fen;

const getColor = () => appState.color;

const getNote = () => getFrameSelected().note;

const setNote = (note) => {
  let { selected, notes } = getFrame();
  notes[selected] = note;
}

const selectAlternativeMove = (idxToSelect) => {
  console.log(`Selecting move ${idxToSelect}`);
  let { frameIdx,
	frameStack } = appState;
  getFrame().selected = idxToSelect;
  appState.frameStack = frameStack.slice(0, frameIdx + 1);
  syncAppState();
}

const lockVariation = () => {
  let { frameIdx } = appState;
  appState.lockedVariationFrameIdx = frameIdx;
  appState.lockedVariationFrame = getFrame();
  syncAppState();
}

const unlockVariation = () => {
  // More logical for this to be null?
  appState.lockedVariationFrameIdx = null;
  appState.lockedVariationFrame = null;
  syncAppState();
}

const getMaxFrameIdx = () => {
  return appState.frameStack.length - 1;
};

const viewingLatestFrame = () => {
  return (appState.frameIdx == getMaxFrameIdx());
};

const playerHasTurn = () => {
  let { color } = appState;
  let activeColor = getActiveColor(getFen());
  return (color == activeColor);
};

const Button = (props) => {
  return React.createElement(
    "button",
    {
      onClick: props.onClick,
      style: {
	maxWidth: "150px",
	width: "150px"
      }
    },
    props.text
  );
};

const LockButton = (props) => {
  let { frameIdx } = props;
  let button;
  // TODO Clean this up. We are checking for `null` here, so it is
  // confusing that we pass the frame index.
  if (!frameIdx) {
    button = Button({
      text: "Lock",
      onClick: lockVariation
    });
  } else {
    button = Button({
      text: "Unlock",
      onClick: unlockVariation
    });
  }
  return React.createElement(
    "div",
    { style: vflexStyle },
    React.createElement(
      "p",
      {},
      "Lock current variation:"
    ),
    button
  );
};

const AlternativeMoves = (props) => {
  let { selected, sans } = props;
  let numMoves = sans.length;
  let buttons = [];
  for (let i = 0; i < numMoves; i++) {
    if (i != selected) {
      let button = Button({
	onClick: () => { selectAlternativeMove(i); },
	text: sans[i]
      });
      buttons.push(button);
    }
  }
  return React.createElement(
    "div",
    { style: vflexStyle },
    React.createElement(
      "p",
      {},
      "Alternatives:"
    ),
    React.createElement(
      "div",
      {style: vflexStyle },
      ...buttons
    )
  );
  syncAppState();
}

class CommentWidget extends React.Component {
  render() {
    let { notesText } = this.props;
    return React.createElement(
      "div",
      {
	style: {
	  ...vflexStyle,
	  width: "400px",
	  maxWidth: "400px",
	  minHeight: "300px",
	  height: "300px"
	}
      },
      React.createElement(
      	"p",
      	null,
      	notesText
      ),
      Button({
	text: "Update notes",
	onClick: () => {
	  let newNote = prompt("Update note:", notesText);
	  setNote(newNote);
	  syncAppState();
	  fetch('note', {
	    method: 'POST',
	    headers: { 'Content-Type': 'application/json;charset=utf-8' },
	    body: JSON.stringify({
	      fen: getFen(),
	      note: newNote
	    })
	  })
	}
      })
    );
  };
}

class Page extends React.Component {
  constructor(props) {
    super(props);
    this.state = props;
    handle = this;
  };
  
  render() {
    let { frameStack,
	  frameIdx,
	  color,
	  flipBoard,
	  selectedSquare,
	  lockedVariationFrameIdx
	} = this.state;
    let frame = frameStack[frameIdx];
    let { fens,
	  notes,
	  selected } = frame;
    console.log(`Frame: ${JSON.stringify(frame)}`);
    let fen = fens[selected];
    let note = notes[selected];
    return React.createElement(
      "div",
      { style: hflexStyle },
      React.createElement(
	Board,
	{
	  pieces: getPieces(parseFen(fen)),
	  flipBoard: flipBoard,
	  // TODO replace selectedPieceSquare => selectedSquare in React component.
	  selectedPieceSquare: selectedSquare,
	  clickPieceFn: clickPiece,
	  clickSquareFn: clickSquare
	}
      ),
      React.createElement(
	"div",
	{ style: vflexStyle },
	React.createElement(
	  CommentWidget,
	  { notesText: note }
	),
	React.createElement(
	  AlternativeMoves,
	  frame
	),
	React.createElement(
	  LockButton,
	  { frameIdx: lockedVariationFrameIdx }
	),
	Button({
	  text: "Give up",
	  onClick: giveUp
	}),
	Button({
	  text: "Switch color",
	  onClick: switchColor
	})
      )
    );
  };
}

const resetBoard = () => {
  let { frameStack,
	lockedVariationFrameIdx } = appState;
  if (lockedVariationFrameIdx) {
    appState.frameStack = frameStack.slice(0, lockedVariationFrameIdx);
    appState.frameIdx = lockedVariationFrameIdx - 1;
  } else {
    appState.frameStack = [initialFrame];
    appState.frameIdx = 0;
  }
  appState.variationComplete = false;
  appState.selectedSquare = null;
  console.log(`After reset, state = ${JSON.stringify(appState)}`);
  syncAppState();
  if (!playerHasTurn()) {
    if (lockedVariationFrameIdx) {
      opponentPlaysLockedMove();
    } else {
      opponentPlaysFirstMove();
    }
  }
};

const switchColor = () => {
  appState.flipBoard = !appState.flipBoard;
  appState.color = appState.color == "b" ? "w" : "b";
  resetBoard();
};

// The `Board` React element expects its `clickPieceFn` to take a
// color and square, thus we take both args here despite that `color`
// is not used.
const clickPiece = (color, square) => {
  if (playerHasTurn()
      && viewingLatestFrame()) {
    if (appState.selectedSquare != square) {
      appState.selectedSquare = square;
    } else {
      appState.selectedSquare = null;
    }
    syncAppState();
  };
};

const movingIsAllowed = () => {
  return (playerHasTurn()
	  && viewingLatestFrame()
	  && appState.selectedSquare != null);
};

const move = (frame) => {
  appState.frameStack.push(frame);
  appState.frameIdx = getMaxFrameIdx();
  appState.selectedSquare = null;
  syncAppState();
};

const giveUp = () => {
  let body = {
    color: getColor(),
    fen: getFen(),
  };
  fetch('moves', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json;charset=utf-8' },
    body: JSON.stringify(body)
  }).then(resp => resp.json())
    .then(respJSON => {
      let { moves } = respJSON;
      alert(JSON.stringify(moves));
    });  
};

const tryMove = (fromSquare, toSquare) => {
  let body = {
    color: getColor(),
    fen: getFen(),
    from: fromSquare,
    to: toSquare,
  };
  // TODO handle promotions
  fetch('move', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json;charset=utf-8' },
    body: JSON.stringify(body)
  }).then(response => response.json())
    .then(responseJSON => {
      //console.log(
      // `Sent move to server. Response: ${JSON.stringify(responseJSON)}`
      //);
      let { correct, fen, end, note } = responseJSON;
      if (correct) {
	console.log(`The move was correct. End = ${end}`);
	move({
	  fens: [fen],
	  notes: [note],
	  sans: [null],
	  selected: 0
	});
	if (end == true) {
	  console.log("End of variation");
	  appState.variationComplete = true;
	} else {
	  console.log("Getting the opponent move");
	  setTimeout(
	    opponentMove,
	    500
	  );
	}
      } else {
	appState.selectedSquare = null;
	syncAppState();
      }
    })
};

const opponentMove = () => {
  fetch(
    'opponent-moves',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json;charset=utf-8' },
      body: JSON.stringify({
	fen: getFen(),
	color: getColor()
      })
    })
    .then(response => response.json())
    .then(responseJSON => {
      let frame = responseJSON;
      move(frame);
    })
};

const clickSquare = (clickedSquare) => {
  console.log(`Clicked square=${clickedSquare}`
	      + `movingIsAllowed=${movingIsAllowed()}`);
  if (movingIsAllowed()) {
    let { selectedSquare } = appState;
    tryMove(selectedSquare, clickedSquare);
  }
}

document.addEventListener("keydown", (e) => {
  switch (e.code) {
  case "ArrowLeft":
    if (appState.frameIdx > 0) {
      appState.frameIdx -= 1;
      syncAppState();
    }
    break;
  case "ArrowRight":
    if (appState.frameIdx < getMaxFrameIdx()) {
      appState.frameIdx += 1;
      syncAppState();
    } else {
      resetBoard();
    }
    break;
  }
});

const opponentPlaysLockedMove = () => {
  setTimeout(
    () => { move(appState.lockedVariationFrame); },
    500
  );
};

const opponentPlaysFirstMove = () => {
  setTimeout(
    opponentMove,
    500
  );
};

window.addEventListener("DOMContentLoaded", () => {
  const div = document.getElementById("main");
  const page = React.createElement(Page, appState);
  ReactDOM.render(page, div);
  if (appState.color == "b") {
    opponentPlaysFirstMove();
  }
});

