(ns generic-lsp.datatip
  (:require [promesa.core :as p]
            [clojure.string :as str]
            [generic-lsp.commands :as cmds]))

(defonce service (atom nil))

(defn- datatip [^js editor position]
  (p/let [results (cmds/hover! editor)]
    (when results
      (clj->js {:markedStrings (map (fn [{:keys [type value]}]
                                      (let [value (-> value
                                                      (str/replace #"<" "&lt;")
                                                      (str/replace #">" "&gt;"))]
                                        {:type :markdown
                                         :value (if (= type "plaintext")
                                                  (str "```\n" value "\n```")
                                                  value)}))
                                    results)
                :pinnable true
                :range (.. editor bufferRangeForScopeAtCursor)}))))

(defn consumer [s]
  (reset! service s)
  (.addProvider ^js s
                (clj->js {:priority 50
                          :providerName "generic-lsp"
                          :datatip (fn [ & args]
                                     (apply datatip args))})))
