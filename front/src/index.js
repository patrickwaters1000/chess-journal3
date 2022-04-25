import React, { Component } from "react";
import ReactDOM from "react-dom";
import Board from "./Board.js";
import { initialFen } from "./chessUtils.js";
import { parseFen,
	 getPieces } from "./Fen.js";

const hflexStyle = { display: "flex", flexDirection: "row", flexWrap: "wrap" }
const vflexStyle = { display: "flex", flexDirection: "column", flexWrap: "wrap" }

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
        if (newState.error) {
            window.alert(newState.error);
        }
        handle.setState(newState);
        if (newState.opponentMustMove) {
            event('opponent-move');
        }
    });
};

const Button = (props) => {
    let { name, text, confirm } = props;
    let onClick;
    if (confirm) {
        onClick = () => {
            if (window.confirm("Are you sure?")) {
                event(name);
            }
        };
    } else {
        onClick = () => { event(name); };
    }
    return React.createElement(
        "button",
        {
            onClick: onClick,
            style: {
	        maxWidth: "150px",
	        width: "150px"
            }
        },
        text
    );
};

const AlternativeMoveButton = (data) => {
    let { san,
          lines } = data;
    return React.createElement(
        "button",
        {
            onClick: () => event("alternative-move", san),
            style: {
	        maxWidth: "50px",
	        width: "50px"
            }
        },
        `${san} (${lines})`
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
              isLocked,
              alternativeMoves
	    } = this.state;
        let buttons;
        if (mode == "edit") {
            buttons = [
                Button({ name: "switch-mode", text: "Review" }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "switch-lock", text: (isLocked ? "Unlock" : "Lock") }),
                Button({ name: "reset", text: "Reset" }),
                Button({ name: "add-line", text: "Add line" }),
                Button({ name: "delete-subtree", text: "Delete subtree", confirm: true }),
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
        let alternativeMoveButtons = alternativeMoves.map(AlternativeMoveButton);
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
	        {
                    style: {
                        ...vflexStyle,
                        maxWidth: 150
                    }
                },
                ...buttons,
                React.createElement(
                    "div",
                    { style: hflexStyle },
                    ...alternativeMoveButtons
                )
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
    case "Enter":
        event("enter");
        break;
    case "Delete":
        event("undo");
        break;
    case "Backspace":
        event("undo");
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
        selectedSquare: null,
        alternativeMoves: [],
    });
    ReactDOM.render(page, div);
    event("start");
});
