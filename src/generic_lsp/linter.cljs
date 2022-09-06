(ns generic-lsp.linter
  (:require [generic-lsp.atom :refer [subscriptions]]
            ["url" :as url]))

(defn- lint! [_text-editor]
  (prn :LINTING))

(defn provider []
  (prn :PROVIDE?)
  #js {:name "generic-lsp-linter"
       :scope "file"
       :lints-on-change true
       :grammarScopes #js ["source"]

       :lint (fn [text-editor]
               (lint! text-editor))})

(defonce service (atom nil))
(defonce linter (atom nil))
(defn consumer [s]
  (prn :LINTER? s)
  (reset! service s)
  (let [new-linter (s #js {:name "Generic LSP Linter"})]
    (.add @subscriptions new-linter)
    (reset! linter new-linter)))

(defn- ^:dev/after-load reload-linter []
  (when-let [s @service]
    (prn :resetting-linter)
    (consumer s)))

(def ^:private severities ["error" "warning" "info"])

(defn set-message! [lsp-message]
  (let [^js linter @linter
        diags (-> lsp-message :diagnostics not-empty)
        file (-> lsp-message :uri url/fileURLToPath)]
    (when (and linter diags)
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

#_
(. @linter clearMessages)
