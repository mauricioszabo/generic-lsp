(ns generic-lsp.core
  (:require [generic-lsp.known-servers :as servers]
            [generic-lsp.commands :as cmds]
            ["atom" :refer [CompositeDisposable]]))

(def subscriptions (atom (CompositeDisposable.)))

(defonce atom-state (atom nil))

(defn- info! [message]
  (.. js/atom -notifications (addInfo message)))

(defn activate [state]
  (reset! atom-state state)
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "generic-lsp:start-LSP-server"
                                #(cmds/start-lsp-server!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "generic-lsp:stop-LSP-server"
                                #(cmds/stop-lsp-server!)))))

(defn deactivate [state]
  (.dispose ^js @subscriptions))

(defn ^:dev/before-load reset-subs []
  (deactivate @atom-state))

(defn ^:dev/after-load re-activate []
  (reset! subscriptions (CompositeDisposable.))
  (activate @atom-state)
  (info! "Reloaded Generic LSP package"))
