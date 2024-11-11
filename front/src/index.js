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
        if (newState.hint) {
            window.alert(newState.hint);
        }
        handle.setState(newState);
        if (newState.opponentMustMove) {
            event(newState.mode+'/opponent-move');
        }
    });
};

const Button = (props) => {
    let { name, text, confirm, prompt, body } = props;
    let onClick;
    if (confirm) {
        onClick = () => {
            if (window.confirm("Are you sure?")) {
                event(name, body);
            }
        };
    } else if (prompt) {
        onClick = () => {
            let input = window.prompt(prompt);
            event(name, input);
        };
    } else {
        onClick = () => { event(name, body); };
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
          score,
          mode } = data;
    let width = (score == null ? "50px" : "150px");
    return React.createElement(
        "button",
        {
            onClick: () => event(`${mode}/alternative-move`, san),
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

const EndgameClassButton = (data) => {
    let { route,
          text,
          level } = data;
    return React.createElement(
        "button",
        {
            onClick : () => { event(route, level); },
            style: {
	        maxWidth: "100px",
	        width: "100px"
            }
        },
        text
    );
}

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
              promotePiece,
              endgameClassButtonConfigs,
              liveGameName,
              endgameEvaluation,
              openingReportoire,
              fenCounter,
            } = this.state;
        let buttons;
        if (mode == "menu") {
            buttons = [
                Button({ name: "set-mode", body: "setup", text: "Setup position" }),
                Button({ name: "set-mode", body: "endgames", text: "Endgames" }),
                Button({ name: "set-mode", body: "openings-review", text: "Openings review" }),
                Button({ name: "set-mode", body: "openings-editor", text: "Openings editor" }),
                Button({ name: "set-mode", body: "battle", text: "Battle" }),
                Button({ name: "set-mode", body: "games", text: "Games" }),
                Button({ name: "set-mode", body: "live-games", text: "Saved games" }),
                Button({ name: "set-mode", body: "rook-vs-pawn", text: "Endgames: rook vs pawn" }),
            ];
        } else if (mode == "openings-editor") {
            buttons = [
                Button({ name: "set-mode", body: "menu", text: "Exit" }),
                Button({ name: "openings-editor/next-reportoire", text: openingReportoire, }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "openings-editor/switch-lock", text: (isLocked ? "Unlock" : "Lock") }),
                Button({ name: "openings-editor/reset", text: "Reset" }),
                Button({ name: "openings-editor/add-line", text: "Add line" }),
                Button({ name: "openings-editor/delete-subtree", text: "Delete subtree", confirm: true }),
            ];
        } else if (mode == "openings-review") {
            buttons = [
                Button({ name: "set-mode", body: "menu", text: "Exit" }),
                Button({ name: "openings-review/next-reportoire", text: openingReportoire, }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "openings-review/switch-lock", text: (isLocked ? "Unlock" : "Lock") }),
            ];
        } else if (mode == "games") {
            buttons = [
                Button({ name: "set-mode", body: "menu", text: "Exit" }),
                Button({ name: "switch-color", text: "Switch color" }),
                Button({ name: "reset", text: "Reset" }),
            ];
        } else if (mode == "battle") {
            buttons = [
                Button({ name: "battle/set-mode", body: "menu", text: "Exit" }),
                Button({ name: "battle/set-engine-elo", text: "Set Elo", prompt: "Elo:" }),
                Button({ name: "battle/set-engine-movetime", text: "Set movetime", prompt: "Time (millis):" }),
                Button({ name: "battle/reboot-engine", text: "Reboot engine" }),
                Button({ name: "battle/switch-color", text: "Switch color" }),
                Button({ name: "battle/cycle-promote-piece", text: `Promotions to ${promotePiece}` }),
                Button({ name: "battle/new-live-game", text: "Save game", prompt: "Game name:" }),
            ];
        } else if (mode == "setup") {
            buttons = [
                Button({ name: "set-mode", body: "menu", text: "Exit" }),
                Button({ name: "set-mode", body: "endgames", text: "Endgames" }),
                Button({ name: "clear", text: "Clear board" }),
                Button({ name: "set-active-color", text: "Set color", prompt: "Who's turn (w or b)?"}),
                Button({ name: "switch-color", text: `You are ${playerColor.toUpperCase()}` }),
                Button({ name: "switch-active-color", text: `${activeColor.toUpperCase()} to play` }),
                Button({ name: "save-endgame", text: "Save" }),
                Button({ name: "set-mode", body: "battle", text: "Battle" }),
            ];
        } else if (mode == "endgames") {
            buttons = [
                Button({ name: "set-mode", body: "menu", text: "Exit" }),
                Button({ name: "set-mode", body: "setup", text: "Setup position" }),
                React.createElement(
                    "div",
                    { style: hflexStyle },
                    ...endgameClassButtonConfigs.map(EndgameClassButton)
                ),
                Button({ name: "next-endgame", text: "Next position" }),
                Button({ name: "previous-endgame", text: "Previous position" }),
                Button({ name: "switch-color", text: `You are ${playerColor.toUpperCase()}` }),
                Button({ name: "cycle-promote-piece", text: `Promotions to ${promotePiece}` }),
                Button({ name: "delete-endgame", text: "Delete", confirm: true }),
                Button({ name: "force-move", text: "Force move", prompt: "Move:" }),
                Button({ name: "toggle-evaluation", text: endgameEvaluation }),
            ];
        } else if (mode == "live-games") {
            buttons = [
                Button({ name: "set-mode", body: "menu", text: "Exit" }),
                Button({ name: "live-games/set-engine-elo", text: "Set engine elo", prompt: "Elo:" }),
                Button({ name: "live-games/set-engine-movetime", text: "Set engine movetime", prompt: "Movetime in milliseconds:" }),
                //Button({ name: "reboot-engine", text: "Reboot engine" }),
                //Button({ name: "cycle-promote-piece", text: `Promotions to ${promotePiece}` }),
                Button({ name: "live-games/cycle-game", body: 1, text: "Next game" }),
                Button({ name: "live-games/cycle-game", body: -1, text: "Previous game" }),
                Button({ name: "live-games/end-game", text: "End game", prompt: "Result" }),
                Button({ name: "live-games/save-game", text: "Save game" }),
                //Button({ name: "switch-color", text: `You are ${playerColor.toUpperCase()}` }),
            ];
        } else if (mode == "rook-vs-pawn") {
            buttons = [
                Button({ name: "set-mode", body: "menu", text: "Exit" }),
                Button({ name: "rook-vs-pawn/prev-position", text: "<" }),
                Button({ name: "rook-vs-pawn/next-position", text: fenCounter }),
                Button({ name: "rook-vs-pawn/force-move", prompt: "Input san:", text: "Force move" }),
                Button({ name: "rook-vs-pawn/hint", text: "Hint" }),
            ];
        } else {
            buttons = [];
        }
        let alternativeMoveButtons = alternativeMoves.map(x => {
            x.mode = mode;
            return x
        }).map(
            AlternativeMoveButton
        );
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
        case "KeyL":
            key = "l";
            break;
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
    event(`${handle.state.mode}/key`, key);
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
