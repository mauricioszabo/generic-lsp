(ns generic-lsp.rpc
  (:require [promesa.core :as p]
            ["child_process" :as cp]))

(declare treat-out)
(defn- deliver-result! [server predefined-content-size]
  (let [content (subs (:buffer server) 0 predefined-content-size)

        result (-> content js/JSON.parse (js->clj :keywordize-keys true))
        prom (get-in server [:pending (:id result)])]

    ((:on-command server) result)
    (if prom
      (p/resolve! prom result)
      ((:on-unknown-command server) result))
    (-> server
        (update :pending dissoc (:id result))
        (update :buffer subs predefined-content-size)
        (dissoc :content-size))))

(defn- treat-out [server data]
  (let [buffer (str (:buffer server) data)
        server (assoc server :buffer buffer)
        predefined-content-size (:content-size server)]

    (if predefined-content-size
      (if (-> buffer count (>= predefined-content-size))
        (recur (deliver-result! server predefined-content-size) "")
        server)

      (if-let [[header content-size] (re-find #"(?i)content-length\s*:\s+(\d+)\r\n\r\n" buffer)]
        (recur
          (-> server
              (assoc :content-size (js/parseInt content-size))
              (update :buffer subs (count header)))
          "")
        server))))

(defn spawn-server! [command {:keys [args on-command on-unknown-command]}]
  (let [server (cp/spawn command (into-array args))
        res (atom {:server server
                   :on-command (or on-command identity)
                   :on-unknown-command (or on-unknown-command identity)
                   :pending {}
                   :buffer ""})]
    (.. server -stdout (on "data" #(swap! res treat-out %)))
    res))

(defn send! [server command params]
  (let [id (str (gensym "req-"))
        message (-> {:jsonrpc "2.0"
                     :id id
                     :method command
                     :params params}
                    clj->js
                    js/JSON.stringify)
        ^js s (:server @server)
        p (p/deferred)]
    (.. s -stdin (write (str "Content-Length: " (count message) "\r\n"
                             "\r\n" message)))
   (swap! server assoc-in [:pending id] p)
   p))

(defn stop-server! [server]
  (.kill ^js (:server @server)))
