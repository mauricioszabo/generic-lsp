(ns generic-lsp.complete
  (:require [generic-lsp.commands :as cmds]
            [promesa.core :as p]))

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

(defn- ^:inline normalize-prefix [to-insert prefix]
  (loop [prefix prefix]
    (cond
      (= (first to-insert) (first prefix)) prefix
      (empty? prefix) nil
      :else (recur (subs prefix 1)))))

(defn- suggestions [^js data]
  (when-not (.-activatedManually data) (cmds/sync-document! (.-editor data) {}))

  (p/let [^js editor (.-editor data)
          {:keys [result]} (cmds/autocomplete editor)
          prefix (get-prefix! editor)
          items (if-let [items (:items result)]
                  items
                  result)]
    (->> items
         (map (fn [result]
                (let [to-insert (:insertText result (:label result))
                      snippet? (-> result :insertTextFormat (= 2))
                      common {:displayText (:label result)
                              :type (some-> result :kind dec types)
                              :description (:detail result)
                              :replacementPrefix (normalize-prefix to-insert prefix)}]
                  (if snippet?
                    (assoc common :snippet to-insert)
                    (assoc common :text to-insert)))))
         not-empty
         clj->js)))


(defn- detailed-suggestion [_data])
  ; (prn :detailed data))

(defn provider
  "Provider for autocomplete"
  []
  #js {:selector ".source"
       :disableForSelector ".source .comment"

       :inclusionPriority 10
       :excludeLowerPriority false

       :suggestionPriority 20

       :filterSuggestions true

       :getSuggestions (fn [data]
                         (suggestions data))

       :getSuggestionDetailsOnSelect #(detailed-suggestion %)})
