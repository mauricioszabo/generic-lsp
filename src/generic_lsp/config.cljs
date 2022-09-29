(ns generic-lsp.config
  (:require [generic-lsp.known-servers :as known]))

(def config
  (let [grammars (.. js/atom -grammars getGrammars)]
    (->> grammars
         (map #(.-name %))
         (filter identity)
         (reduce (fn [acc grammar-name]
                   (let [server (get known/servers grammar-name {:binary "" :args []})]
                     (assoc acc
                            grammar-name
                            {:title grammar-name
                             :collapsed true
                             :type "object"
                             :properties {:command {:title "Command"
                                                    :type "string"
                                                    :default (:binary server)
                                                    :order 1}
                                          :args {:title "Arguments"
                                                 :type "array"
                                                 :default (:args server)
                                                 :order 2}}})))
                 {:debug {:title "Debug LSP messages?"
                          :description "LSP messages will be sent to the console, over devtools. Run 'Toggle Devtools' command to see them"
                          :type "boolean"
                          :default false
                          :order 0}})
         clj->js)))
