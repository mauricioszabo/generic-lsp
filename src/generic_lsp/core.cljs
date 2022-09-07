(ns generic-lsp.core
  (:require [generic-lsp.commands :as cmds]
            [generic-lsp.atom :refer [subscriptions]]
            ["atom" :refer [CompositeDisposable]]))

(defonce atom-state (atom nil))

(defn- info! [message]
  (.. js/atom -notifications (addInfo message)))

(defn- text-editor-observer [^js text-editor]
  (.add @subscriptions
        (. text-editor onDidStopChanging #(cmds/sync-document! text-editor (.-changes ^js %))))
  (.add @subscriptions
        (. text-editor onDidSave #(cmds/save-document! text-editor))))

(defn activate [state]
  (reset! atom-state state)
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "generic-lsp:start-LSP-server"
                                #(cmds/start-lsp-server!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "generic-lsp:stop-LSP-server"
                                #(cmds/stop-lsp-server!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:go-to-declaration"
                                #(cmds/go-to-declaration!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:go-to-definition"
                                #(cmds/go-to-definition!))))
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor" "generic-lsp:go-to-type-definition"
                                #(cmds/go-to-type-definition!))))

  (.add @subscriptions
        (.. js/atom -workspace (observeTextEditors #(text-editor-observer %)))))

(defn deactivate [_]
  (.dispose ^js @subscriptions))

(defn- ^:dev/before-load reset-subs []
  (deactivate @atom-state))

(defn- ^:dev/after-load re-activate []
  (reset! subscriptions (CompositeDisposable.))
  (activate @atom-state)
  (info! "Reloaded Generic LSP package"))
