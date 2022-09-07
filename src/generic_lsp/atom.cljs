(ns generic-lsp.atom
  (:require ["atom" :refer [CompositeDisposable]]
            ["url" :as url]))

(defonce subscriptions (atom (CompositeDisposable.)))

(defn info! [message]
  (.. js/atom -notifications (addInfo message))
  nil)

(defn error! [message]
  (.. js/atom -notifications (addError message))
  nil)

(defn warn! [message]
  (.. js/atom -notifications (addWarning message))
  nil)

(defn open-editor [uri line column]
  (let [file-name (url/fileURLToPath uri)
        position (clj->js (cond-> {:initialLine line :searchAllPanes true}
                                  column (assoc :initialColumn column)))]
    (.. js/atom -workspace (open file-name position))))
