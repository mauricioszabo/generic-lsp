(ns generic-lsp.status-bar)

(defonce service (atom nil))
(defn consumer [s] (reset! service s))
