(ns generic-lsp.commands
  (:require [promesa.core :as p]
            [generic-lsp.rpc :as rpc]
            [generic-lsp.atom :as atom]
            [generic-lsp.known-servers :as known]
            [generic-lsp.linter :as linter]
            ["url" :as url]
            ["path" :as path]))

(defonce loaded-servers (atom {}))

(defmulti callback-command :method)

(defmethod callback-command "textDocument/publishDiagnostics" [{:keys [params]}]
  (linter/set-message! params))

(defmethod callback-command :default [params]
  (prn :UNUSED-COMMAND params))

(defn- file->uri [file] (str (url/pathToFileURL file)))

(declare open-document!)
(defn- init-lsp [language server open-editors]
  (p/let [workpace-dirs (->> js/atom
                             .-project
                             .getPaths
                             (map (fn [path]
                                    {:uri (file->uri path)
                                     :name (path/basename path)}))
                             into-array)
          init-res (rpc/send! server "initialize"
                              {:processId nil
                               :clientInfo {:name "Pulsar"}
                               :locale "UTF-8"
                               :capabilities {:textDocument {:synchronization {:didSave true}
                                                             :completion {:contextSupport true
                                                                          :completionItem {:snippetSupport true
                                                                                           :commitCharactersSupport true
                                                                                           :preselectSupport true
                                                                                           :documentationFormat ["markdown" "plaintext"]
                                                                                           :resolveSupport {:properties ["documentation" "detail" "additionalTextEdits"]}}}
                                                             :declaration {}
                                                             :definition {}
                                                             :typeDefinition {}}}
                               :rootUri (-> workpace-dirs first :uri)
                               :workspaceFolders workpace-dirs})]

    (swap! loaded-servers assoc language {:server server
                                          :capabilities (-> init-res :result :capabilities)})
    (doseq [[_ editors] open-editors
            :let [editor (first editors)]
            :when (= language (.. editor getGrammar -name))]
      (open-document! editor))))


(defn- curr-editor-lang [] (.. js/atom -workspace getActiveTextEditor getGrammar -name))

(defn start-lsp-server!
  ([open-editors] (start-lsp-server! open-editors (curr-editor-lang)))
  ([open-editors language]
   (let [server (get known/servers language)]
     (case (:type server)
       :spawn (let [server (rpc/spawn-server!
                            (:binary server)
                            (assoc (:params server)
                                   :args (:args server [])
                                   ; :on-command #(println "<--" %)
                                   :on-unknown-command callback-command))]
                (init-lsp language server open-editors)
                (atom/info! (str "Connected server for " language)))
       (atom/error! (str "Don't know how to run a LSP server for " language))))))

(defn stop-lsp-server!
  ([] (stop-lsp-server! (curr-editor-lang)))
  ([language]
   (if (some-> @loaded-servers (get-in [language :server]) rpc/stop-server!)
     (atom/info! (str "Disconnected server for " language))
     (atom/warn! (str "Didn't find a server for " language)))
   (linter/clear-messages! language)
   (swap! loaded-servers dissoc language)))

(defn send-command! [language command params]
  (when-let [server (get-in @loaded-servers [language :server])]
    (rpc/send! server command params)))

(defn notify! [language command params]
  (when-let [server (get-in @loaded-servers [language :server])]
    (rpc/notify! server command params)))

(def ^:private sync-support [:none :full :incremental])
(defonce ^:private uri-versions (atom {}))

(defn open-document! [^js editor]
  (when-let [path (.getPath editor)]
    (let [language (.. editor getGrammar -name)
          uri (file->uri path)
          version (get @uri-versions uri 0)]
      (notify! language "textDocument/didOpen"
               {:textDocument {:uri uri
                               :version version
                               :languageId (.toLowerCase language)
                               :text (.getText editor)}}))))

(defn close-document! [^js editor]
  (when-let [path (.getPath editor)]
    (let [language (.. editor getGrammar -name)
          uri (file->uri path)]
      (notify! language "textDocument/didClose"
               {:textDocument {:uri uri}}))))

(defn sync-document! [^js editor, ^js _changes]
  (when-let [path (.getPath editor)]
    (let [language (.. editor getGrammar -name)
          sync-capabilities (get-in @loaded-servers [language :capabilities :textDocumentSync])
          sync (get sync-support (:change sync-capabilities))]
      (when (and (:openClose sync-capabilities)
                 (not= :none sync))
        (let [uri (file->uri path)
              version (get @uri-versions uri 0)]
          (swap! uri-versions update uri inc)
          (notify! language "textDocument/didChange"
                   {:textDocument {:uri uri
                                   :version (inc version)}
                    :contentChanges [{:text (.getText editor)}]}))))))

(defn save-document! [^js editor]
  (when-let [path (.getPath editor)]
    (let [language (.. editor getGrammar -name)
          save-capabilities (get-in @loaded-servers [language
                                                     :capabilities
                                                     :textDocumentSync
                                                     :save])]
      (when save-capabilities
        (let [uri (file->uri path)
              message (cond-> {:textDocument {:uri uri}}
                        (:includeText save-capabilities) (assoc :text (.getText editor)))]
          (notify! language "textDocument/didSave" message))))))

(defn- have-capability? [lang name]
  (get-in @loaded-servers [lang :capabilities name]))

(defn- go-to-thing! [capability command explanation]
  (let [lang (curr-editor-lang)
        editor (.. js/atom -workspace getActiveTextEditor)
        position (.getCursorBufferPosition editor)]
    (if (have-capability? lang capability)
      (p/let [res (send-command! lang command
                                 {:textDocument {:uri (-> editor .getPath file->uri)}
                                  :position {:line (.-row position)
                                             :character (.-column position)}})]
        (when (-> res :result not-empty) res))
      (atom/warn! (str "Language " lang " does not support " explanation)))))

(defn go-to-declaration! []
  (p/let [res (go-to-thing! :declarationProvider "textDocument/declaration"
                            "go to declaration")]
    (when res
      (if-let [res (:result res)]
        (atom/open-editor res)
        (atom/warn! "No declaration found")))))

(defn go-to-definition! []
  (p/let [res (go-to-thing! :definitionProvider "textDocument/definition"
                            "go to definition")]
    (when res
      (if-let [res (:result res)]
        (atom/open-editor res)
        (atom/warn! "No definition found")))))

(defn go-to-type-definition! []
  (p/let [res (go-to-thing! :typeDefinitionProvider "textDocument/typeDefinition"
                            "go to type declaration")]
    (when res
      (if-let [res (:result res)]
        (atom/open-editor res)
        (atom/warn! "No type declaration found")))))

(defn autocomplete [^js editor]
  (let [lang (.. editor getGrammar -name)
        position (.getCursorBufferPosition editor)
        uri (some-> editor .getPath file->uri)
        version (get @uri-versions uri 0)]
    (when (have-capability? lang :completionProvider)
      (p/do!
       (send-command! lang "textDocument/completion"
                      {:textDocument {:uri uri}
                       :position {:line (.-row position)
                                  :character (.-column position)}})))))

#_
(p/let [a (autocomplete (js/ce))]
  (prn :C a))
#_
(p/let [res (autocomplete (js/ce))]
  (->> res :result (take 3)))
