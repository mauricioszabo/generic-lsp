(ns generic-lsp.linter
  (:require ["url" :as url]
            [generic-lsp.atom :refer [subscriptions]]))

(defonce service (atom nil))
(defonce linter (atom nil))
(defn consumer [s]
  (reset! service s)
  (let [new-linter (s #js {:name "Generic LSP Linter"})]
    (.add @subscriptions new-linter)
    (reset! linter new-linter)))

(defn- ^:dev/after-load reload-linter []
  (when-let [s @service]
    (. ^js @linter clearMessages)
    (consumer s)))

(def ^:private severities ["error" "warning" "info" "info"])

(defn set-message! [lsp-message]
  (let [^js linter @linter
        diags (:diagnostics lsp-message)
        file (-> lsp-message :uri url/fileURLToPath)]
    (when (and linter file)
      (.setMessages linter
                    file
                    (->> diags
                         (map (fn [diag]
                                {:severity (-> diag :severity dec severities)
                                 :location {:file file
                                            :position [[(-> diag :range :start :line)
                                                        (-> diag :range :start :character)]
                                                       [(-> diag :range :end :line)
                                                        (-> diag :range :end :character)]]}
                                 :description (:message diag)
                                 :excerpt (:message diag)}))
                         clj->js)))))

;; FIXME: clear only messages for specific lang
(defn clear-messages! [_language]
  (when-let [^js linter @linter]
    (.clearMessages linter)))
