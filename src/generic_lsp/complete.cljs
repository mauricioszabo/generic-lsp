(ns generic-lsp.complete)

(defonce service (atom nil))

(defn- suggestions [data])
  ; (prn :suggestions data))

(defn- detailed-suggestion [data])
  ; (prn :detailed data))

(defn provider []
  #js {:selector ".source"
       :disableForSelector ".source .comment"

       :inclusionPriority 10
       :excludeLowerPriority false

       :suggestionPriority 20

       :filterSuggestions true

       :getSuggestions (fn [data]
                         (-> data (js->clj :keywordize-keys true) suggestions clj->js))

       :getSuggestionDetailsOnSelect #(detailed-suggestion %)})
