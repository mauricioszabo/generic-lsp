(ns generic-lsp.commands
  (:require [promesa.core :as p]
            [generic-lsp.rpc :as rpc]
            [generic-lsp.atom :as atom]
            [generic-lsp.known-servers :as known]
            [generic-lsp.linter :as linter]
            ["atom" :refer [TextBuffer Range]]
            ["fs" :as fs]
            ["url" :as url]
            ["path" :as path]))

(def ^:private promised-fs (. fs -promises))
(defonce loaded-servers (atom {}))

(defmulti callback-command :method)

(defonce ^:private diagnostics (atom {}))

(defn- to-range [range]
  (let [{:keys [start end]} range]
    (Range. #js [(:line start) (:character start)]
            #js [(:line end) (:character end)])))

(defmethod callback-command "textDocument/publishDiagnostics" [{:keys [params]} _lang]
  (swap! diagnostics (fn [diags]
                       (if (-> params :diagnostics empty?)
                         (dissoc diags (:uri params))
                         (assoc diags (:uri params) (->> params
                                                         :diagnostics
                                                         (map #(update % :range to-range)))))))
  (linter/set-message! params))

(defn respond! [language message]
  (when-let [server (get-in @loaded-servers [language :server])]
    (rpc/raw-ish-send! server message)))

(defn- apply-change-in-editor [^js editor, change]
  (.setTextInBufferRange editor (to-range (:range change)) (:newText change)))

(defn- apply-changes-in-file [id language file changes]
  (p/let [contents (. promised-fs readFile file)
          ^js buffer (new TextBuffer (str contents))]
    (doseq [change changes]
      (.setTextInRange buffer (to-range (:range change)) (:newText change)))
    (. promised-fs writeFile file (.getText buffer))
    (when id (respond! language {:id id, :result {:applied true}}))))

(defn- apply-changes! [id language file changes]
  (let [^js editor (-> @atom/open-paths (get file) first)
        ordered-changes (->> changes
                             (sort-by (fn [change]
                                        (let [{:keys [start end]} (:range change)
                                              in-pos (juxt :line :character)]
                                          [(in-pos start) (in-pos end)])))
                             reverse)]
    (if editor
      (do
        (.transact editor
          #(doseq [change ordered-changes] (apply-change-in-editor editor change)))
        (when id (respond! language {:id id, :result {:applied true}})))
      (apply-changes-in-file id language file ordered-changes))))

(defmethod callback-command "workspace/applyEdit" [{:keys [params id]} language]
  (doseq [[file changes] (-> params :edit :changes)
          :let [file (-> file str (subs 1) url/fileURLToPath)]]
    (apply-changes! id language file changes)))

(defmethod callback-command :default [params _language]
  (prn :UNUSED-COMMAND params))

(defn file->uri [file] (str (url/pathToFileURL file)))

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
                                                             :formatting {}
                                                             :rangeFormatting {}
                                                             :definition {}
                                                             :codeAction {}
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
                                   :on-unknown-command #(callback-command % language)))]
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

(defn rename! [^js editor old-path]
  (let [language (.. editor getGrammar -name)
        path (.getPath editor)
        new-uri (file->uri path)
        old-uri (file->uri old-path)]
    (notify! language "workspace/didRenameFiles"
             {:files [{:oldUri old-uri :newUri new-uri}]})))

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

(defn- ^:inline from-point [^js point]
  {:line (.-row point), :character (.-column point)})

(defn position-from-editor [^js editor]
  (let [uri (-> editor .getPath file->uri)
        position (.getCursorBufferPosition editor)]
    {:textDocument {:uri uri}
     :position (from-point position)}))

(defn- from-atom-range [^js range]
  {:start (-> range .-start from-point)
   :end (-> range .-end from-point)})

(defn location-from-editor [^js editor]
  (let [range (.getSelectedBufferRange editor)]
    {:textDocument {:uri (-> editor .getPath file->uri)}
     :range (from-atom-range range)}))

(defn- go-to-thing! [capability command explanation]
  (let [lang (curr-editor-lang)
        editor (.. js/atom -workspace getActiveTextEditor)]
    (if (have-capability? lang capability)
      (p/let [res (send-command! lang command (position-from-editor editor))]
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
  (let [lang (.. editor getGrammar -name)]
    (when (have-capability? lang :completionProvider)
      (p/do!
       (send-command! lang "textDocument/completion"
                      (position-from-editor editor))))))

(defn exec-command [lang command arguments]
  (send-command! lang
                 "workspace/executeCommand"
                 {:command command
                  :arguments arguments}))

(defn code-actions [^js editor, ^js position]
  (p/let [pos (from-point position)
          uri (-> editor .getPath file->uri)
          diagnostics (->> (get @diagnostics uri)
                           (filter #(-> % :range ^js (.containsPoint position)))
                           (map #(update % :range from-atom-range)))
          res (send-command! (.. editor getGrammar -name)
                             "textDocument/codeAction"
                             {:textDocument {:uri uri}
                              :range {:start pos :end pos}
                              :context {:diagnostics diagnostics}})]
    (:result res)))

(defn format-doc! []
  (let [lang (curr-editor-lang)
        editor (.. js/atom -workspace getActiveTextEditor)
        position (.getSelectedBufferRange editor)
        tab-size (.. js/atom -config (get "editor.tabLength"))
        spaces? (not= "hard" (.. js/atom -config (get "editor.tabType")))]
    (if (have-capability? lang :documentRangeFormattingProvider)
      (p/let [res (send-command! lang "textDocument/rangeFormatting"
                                 {:textDocument {:uri (-> editor .getPath file->uri)}
                                  :range (from-atom-range position)
                                  :options {:tabSize tab-size
                                            :insertSpaces spaces?}})]

        (when-let [changes (-> res :result not-empty)]
          (apply-changes! nil lang (.getPath editor) changes)))
      (atom/warn! (str "Language " lang " does not support formatting")))))
