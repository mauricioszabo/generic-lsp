(ns generic-lsp.complete
  (:require [promesa.core :as p]
            [generic-lsp.commands :as cmds]))

;; TODO: Atom/Pulsar does not have icons for all elements that are available on LSP,
;; and there are some that are not used at all, like builtin, import and require.
;; Maybe we need to add icons for these that are not defined. These are keywords
;; below, for us to be able to know they are not present on Atom yet
(def ^:private types
  [""
   "method"
   "function"
   :constructor
   :field
   "variable"
   "class"
   :interface
   :module
   "property"
   :unit
   "value"
   :enum
   "keyword"
   "snippet"
   "tag"
   :file
   :reference
   :folder
   :enummember
   "constant"
   :struct
   :event
   "builtin"
   "type"])

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
                 :type (some-> result :kind dec types)
                 :replacementPrefix prefix}))
         not-empty
         clj->js)))

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
