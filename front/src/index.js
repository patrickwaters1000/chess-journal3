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
    }).then(resp => {
        return resp.json();
    }).then(newState => {
        console.log(`New state ${JSON.stringify(newState)}`);
        handle.setState(newState);
        if (newState.opponentMustMove) {
            event('opponent-move');
        }
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
              mode,
              flipBoard,
	      selectedSquare,
              isLocked
	    } = this.state;
        let buttons;
        if (mode == "edit") {
            buttons = [
                Button({ name: "switch-mode", text: "Review" }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "switch-lock", text: (isLocked ? "Unlock" : "Lock") }),
                Button({ name: "reset", text: "Reset" }),
                Button({ name: "add-line", text: "Add line" }),
            ];
        } else if (mode == "review") {
            buttons = [
                Button({ name: "switch-mode", text: "Edit" }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "switch-lock", text: (isLocked ? "Unlock" : "Lock") }),
              	Button({ name: "reset", text: "Reset" }),
                Button({ name: "give-up", text: "Give up" }),
            ];
        } else {
            buttons = [];
        }
        return React.createElement(
            "div",
            { style: hflexStyle },
            React.createElement(
	        Board,
	        {
	            pieces: getPieces(parseFen(fen)),
	            flipBoard: flipBoard,
	            selectedSquare: selectedSquare,
	            clickSquare: (square) => { event("click-square", square); }
	        }
            ),
            React.createElement(
	        "div",
	        { style: vflexStyle },
                ...buttons
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
        flipBoard: false,
        selectedSquare: null
    });
    ReactDOM.render(page, div);
    event("start");
});
