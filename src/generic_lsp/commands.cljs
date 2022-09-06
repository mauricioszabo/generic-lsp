(ns generic-lsp.commands
  (:require [promesa.core :as p]
            [generic-lsp.rpc :as rpc]
            [generic-lsp.atom :as atom]
            [generic-lsp.known-servers :as known]))

(defonce loaded-servers (atom {}))

(defn- callback-command [command]
  (prn :CMD command))

(defn start-lsp-server!
  ([] (start-lsp-server! (.. js/atom -workspace getActiveTextEditor getGrammar -name)))
  ([language]
   (if-let [server (get known/servers language)]
     (case (:type server)
       :spawn (let [server (rpc/spawn-server!
                            (:binary server)
                            (assoc (:params server) :on-unknown-command callback-command))]
                (swap! loaded-servers assoc language server)
                (atom/info! (str "Connected server for " language)))
       (atom/error! (str "Don't know how to run a LSP server for " language))))))

(defn stop-lsp-server!
  ([] (stop-lsp-server! (.. js/atom -workspace getActiveTextEditor getGrammar -name)))
  ([language]
   (if (some-> @loaded-servers (get language) rpc/stop-server!)
     (atom/info! (str "Disconnected server for " language))
     (atom/warn! (str "Didn't find a server for " language)))
   (swap! loaded-servers dissoc language)))
