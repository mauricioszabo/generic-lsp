(ns generic-lsp.intentions
  (:require [promesa.core :as p]
            [generic-lsp.commands :as cmds]))

(defn- icon-for [kind]
  (cond
    (re-find #"extract" kind) :link-external
    (re-find #"write" kind) :pencil))

(defn- show-intentions [^js text-editor, ^js position]
  (p/let [actions (cmds/code-actions text-editor position)]
    (->> actions
         (map (fn [{:keys [title kind command] :as possible-command}]
                (let [command (if (string? command)
                                possible-command
                                command)]
                  {:title title
                   :icon (-> kind str icon-for)
                   :selected (fn []
                               (cmds/exec-command (.. text-editor getGrammar -name)
                                                  (:command command)
                                                  (:arguments command)))})))
         clj->js)))

(defn- provide-intentions [^js _editor, ^js position]
  (prn :P position))

(defn provider []
  #js {:grammarScopes #js ["*"]
       :getIntentions (fn [^js params]
                        (provide-intentions (.-textEditor params) (.-visibleRange params)))})

(defn list-intentions []
  #js {:grammarScopes #js ["*"]
       :getIntentions (fn [^js params]
                        (show-intentions (.-textEditor params) (.-bufferPosition params)))})
