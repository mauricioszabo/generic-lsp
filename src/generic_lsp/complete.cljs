(ns generic-lsp.complete
  (:require [promesa.core :as p]
            [generic-lsp.commands :as cmds]))

; {:editor #object[TextEditor [object Object]],
;  :bufferPosition #object[Point (2, 2)],
;  :scopeDescriptor #object[ScopeDescriptor .source.clojure .meta.symbol.clojure],
;  :prefix "su",
;  :activatedManually true

(defn- get-prefix! [^js editor]
  (let [^js cursor (-> editor .getCursors first)
        start-of-word (-> cursor
                          (.getBeginningOfCurrentWordBufferPosition #js {:wordRegex #"[^\s]*"})
                          .-column)
        current-row (.getBufferRow cursor)
        current-column (.getBufferColumn cursor)]
    (when (< start-of-word current-column)
      (.getTextInBufferRange editor #js [#js [current-row start-of-word]
                                         #js [current-row current-column]]))))

(defn- suggestions [^js data]
  (p/let [editor (.-editor data)
          results (cmds/autocomplete editor)
          prefix (get-prefix! editor)]
    (->> results
         :result
         (map (fn [result]
                {:text (:label result)
                 :type (:kind result)
                 :replacementPrefix prefix}))
         not-empty
         clj->js)))

#_
:foo

(defn- detailed-suggestion [_data])
  ; (prn :detailed data))

(defn provider []
  #js {:selector ".source"
       :disableForSelector ".source .comment"

       :inclusionPriority 10
       :excludeLowerPriority false

       :suggestionPriority 20

       :filterSuggestions true

       :getSuggestions (fn [data]
                         (suggestions data))

       :getSuggestionDetailsOnSelect #(detailed-suggestion %)})
