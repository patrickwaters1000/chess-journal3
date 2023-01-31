(ns chess-journal3.modes.menu
  (:require
    [chess-journal3.constants :as c]))

(defn init [state]
  (merge state c/initial-state))
