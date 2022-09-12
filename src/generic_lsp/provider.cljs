(ns generic-lsp.provider
  (:require [generic-lsp.commands :as cmds]))

(defn command! [language command & params]
  (cmds/exec-command language command params))

(defn- ^:inline active-editor! [] (.. js/atom -workspace getActiveTextEditor))

(defn- from-editor
  ([] (.. (active-editor!) getGrammar -name))
  ([^js editor] (.. editor getGrammar -name)))

(defn- position-from-editor
  ([] (clj->js (cmds/position-from-editor (active-editor!))))
  ([editor] (clj->js (cmds/position-from-editor editor))))

(defn- location-from-editor
  ([] (clj->js (cmds/location-from-editor (active-editor!))))
  ([editor] (clj->js (cmds/location-from-editor editor))))

(defn provider []
  #js {:command command!
       :getServerFromEditor from-editor
       :uriFromFile cmds/file->uri
       :locationFromEditor location-from-editor
       :positionFromEditor position-from-editor})
