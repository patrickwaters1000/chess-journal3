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
        // console.log(`New state ${JSON.stringify(newState)}`);
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
    let { name, text, confirm, prompt } = props;
    let onClick;
    if (confirm) {
        onClick = () => {
            if (window.confirm("Are you sure?")) {
                event(name);
            }
        };
    } else if (prompt) {
        onClick = () => {
            let input = window.prompt(prompt);
            event(name, input);
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
          lines,
          score } = data;
    let width = (score == null ? "50px" : "150px");
    return React.createElement(
        "button",
        {
            onClick: () => event("alternative-move", san),
            style: {
	        maxWidth: width,
	        width: width
            }
        },
        (score == null
         ? `${san} (${lines})`
         : `${san} (${lines}, ${(100 * score).toFixed(1)}%)`)
    );
};

const GameButton = (data) => {
    let { opponent,
          date,
          san,
          tag } = data;
    let width = "300px";
    return React.createElement(
        "button",
        {
            onClick: () => event("select-game", tag),
            style: {
	        maxWidth: width,
	        width: width
            }
        },
        `${opponent}, ${date} (${san})`
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
              alternativeMoves,
              pgn,
              games,
              playerColor,
              activeColor,
              promotePiece
	    } = this.state;
        let buttons;
        if (mode == "edit") {
            buttons = [
                Button({ name: "setup-mode", text: "Setup position" }),
                Button({ name: "endgames-mode", text: "Endgames" }),
                Button({ name: "review-mode", text: "Review" }),
                Button({ name: "battle-mode", text: "Battle" }),
                Button({ name: "games-mode", text: "Games" }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "switch-lock", text: (isLocked ? "Unlock" : "Lock") }),
                Button({ name: "reset", text: "Reset" }),
                Button({ name: "add-line", text: "Add line" }),
                Button({ name: "delete-subtree", text: "Delete subtree", confirm: true }),
            ];
        } else if (mode == "review") {
            buttons = [
                Button({ name: "setup-mode", text: "Setup position" }),
                Button({ name: "endgames-mode", text: "Endgames" }),
                Button({ name: "edit-mode", text: "Edit" }),
                Button({ name: "battle-mode", text: "Battle" }),
                Button({ name: "games-mode", text: "Games" }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "switch-lock", text: (isLocked ? "Unlock" : "Lock") }),
              	Button({ name: "reset", text: "Reset" }),
                Button({ name: "give-up", text: "Give up" }),
            ];
        } else if (mode == "games") {
            buttons = [
                Button({ name: "setup-mode", text: "Setup position" }),
                Button({ name: "endgames-mode", text: "Endgames" }),
                Button({ name: "review-mode", text: "Review" }),
                Button({ name: "edit-mode", text: "Edit" }),
                Button({ name: "battle-mode", text: "Battle" }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "reset", text: "Reset" }),
            ];
        } else if (mode == "battle") {
            buttons = [
                Button({ name: "setup-mode", text: "Setup position" }),
                Button({ name: "endgames-mode", text: "Endgames" }),
                Button({ name: "edit-mode", text: "Edit" }),
                Button({ name: "review-mode", text: "Review" }),
                Button({ name: "games-mode", text: "Games" }),
                Button({ name: "set-elo", text: "Set Elo", prompt: "Elo:" }),
                Button({ name: "reboot-engine", text: "Reboot engine" }),
                Button({ name: "cycle-promote-piece", text: `Promotions to ${promotePiece}` }),
            ];
        } else if (mode == "setup") {
            buttons = [
                Button({ name: "endgames-mode", text: "Endgames" }),
                Button({ name: "edit-mode", text: "Edit" }),
                Button({ name: "battle-mode", text: "Battle" }),
                Button({ name: "review-mode", text: "Review" }),
                Button({ name: "games-mode", text: "Games" }),
                Button({ name: "clear", text: "Clear board" }),
                Button({ name: "set-active-color", text: "Set color", prompt: "Who's turn (w or b)?"}),
                Button({ name: "switch-color", text: `You are ${playerColor.toUpperCase()}` }),
                Button({ name: "switch-active-color", text: `${activeColor.toUpperCase()} to play` }),
                // Button({ name: "add-tag", text: "Add tag", prompt: "Tag:"})
                // Button({ name: "save-position", text: "Save" }),
            ];
        //} else if (mode == "endgames") {
        //    buttons = [
        //        Button({ name: "setup-mode", text: "Setup position" }),
        //        Button({ name: "endgames-mode", text: "Endgames" }),
        //        Button({ name: "edit-mode", text: "Edit" }),
        //        Button({ name: "battle-mode", text: "Battle" }),
        //        Button({ name: "review-mode", text: "Review" }),
        //        Button({ name: "games-mode", text: "Games" }),
        //        Button({ name: "switch-color", text: "Switch color" }),
        //        Button({ name: "set-elo", text: "Set Elo", prompt: "Elo:" }),
        //        Button({ name: "reboot-engine", text: "Reboot engine" }),
        //    ];
        //}
        } else {
            buttons = [];
        }
        let alternativeMoveButtons = alternativeMoves.map(AlternativeMoveButton);
        let gameButtons = (games == null
                           ? []
                           : [React.createElement("p", {}, "Games:"),
                              ...games.map(GameButton)]);
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
                        maxWidth: 300
                    }
                },
                ...buttons,
                 React.createElement(
                    "p",
                    {},
                    pgn
                ),
                React.createElement(
                    "div",
                    { style: hflexStyle },
                    ...alternativeMoveButtons
                ),
                React.createElement(
                    "div",
                    { style: hflexStyle },
                    ...gameButtons
                )
            )
        );
    };
}

document.addEventListener("keydown", (e) => {
    let key;
    if (e.shiftKey) {
        switch (e.code) {
        case "KeyK":
            key = "K";
            break;
        case "KeyQ":
            key = "Q";
            break;
        case "KeyR":
            key = "R";
            break;
        case "KeyB":
            key = "B";
            break;
        case "KeyN":
            key = "N";
            break;
        case "KeyP":
            key = "P";
            break;
        }
    } else {
        switch (e.code) {
        case "KeyK":
            key = "k";
            break;
        case "KeyQ":
            key = "q";
            break;
        case "KeyR":
            key = "r";
            break;
        case "KeyB":
            key = "b";
            break;
        case "KeyN":
            key = "n";
            break;
        case "KeyP":
            key = "p";
            break;
        default:
            key = e.code;
        }
    }
    console.log(`Key = ${key}`);
    event("key", key);
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
