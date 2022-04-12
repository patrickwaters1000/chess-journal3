import React, { Component } from "react";
import ReactDOM from "react-dom";
import Board from "./Board.js";
import { initialFen } from "./chessUtils.js";
import { parseFen,
	 getPieces } from "./Fen.js";

const hflexStyle = {display: "flex", flexDirection: "row"}
const vflexStyle = {display: "flex", flexDirection: "column"}

var handle = null; // Will point to top level React component for app

const event = (name, body) => {
    fetch(name, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=utf-8' },
        body: JSON.stringify(body)
    }).then(resp => resp.json())
        .then(newState => {
            handle.setState(newState);
        });
};

const Button = (props) => {
  let { name, text } = props;
  return React.createElement(
    "button",
    {
      onClick: () => event(name),
      style: {
	maxWidth: "150px",
	width: "150px"
      }
    },
    text
  );
};

class Page extends React.Component {
  constructor(props) {
    super(props);
    this.state = props;
    handle = this;
  };

  render() {
    let { fen,
            sans,
	    idx,
	    color,
	    flipBoard,
	    selectedSquare,
	  } = this.state;
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
	  clickSquareFn: clickSquare
	}
      ),
      React.createElement(
	"div",
	{ style: vflexStyle },
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

document.addEventListener("keydown", (e) => {
  switch (e.code) {
  case "ArrowLeft":
    event("left");
    break;
  case "ArrowRight":
    event("right");
    break;
  }
});

window.addEventListener("DOMContentLoaded", () => {
    const div = document.getElementById("main");
    const page = React.createElement(Page, {
        fen: initialFen,
        sans: [],
        idx: 0,
        color: "w",
        flipBoard: false,
        selectedSquare: null
    });
    ReactDOM.render(page, div);
    event("start");
});
