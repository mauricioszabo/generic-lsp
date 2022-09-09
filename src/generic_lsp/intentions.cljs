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
         (map (fn [{:keys [title kind command]}]
                {:title title
                 :icon (icon-for kind)
                 :selected (fn []
                             (cmds/exec-command (.. text-editor getGrammar -name)
                                                (:command command)
                                                (:arguments command)))}))
         clj->js)))

(defn- provide-intentions [^js text-editor, ^js position]
  (prn :P position))

(defn provider []
  #js {:grammarScopes #js ["*"]
       :getIntentions (fn [^js params]
                        (provide-intentions (.-textEditor params) (.-visibleRange params)))})

(defn list-intentions []
  #js {:grammarScopes #js ["*"]
       :getIntentions (fn [^js params]
                        (show-intentions (.-textEditor params) (.-bufferPosition params)))})
          ; {textEditor, bufferPosition}) {
          ;                                              // Highest priority is shown first of all
          ;                                              // Note: You can also return a Promise
          ;                                              return [
          ;                                                      {
          ;                                                        priority: 100,
          ;                                                        icon: 'bucket',
          ;                                                        class: 'custom-icon-class',
          ;                                                        title: 'Choose color from colorpicker',
          ;                                                        selected: function() {
          ;                                                                              console.log('You clicked the color picker option')}}
          ;
          ;                                                      ,
          ;                                                      {
          ;                                                        priority: 200,
          ;                                                        icon: 'tools',
          ;                                                        title: 'Fix linter issue',
          ;                                                        selected: function() {
          ;                                                                              console.log('You chose to fix linter issue')}}]}})
