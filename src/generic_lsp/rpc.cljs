(ns generic-lsp.rpc
  (:require [promesa.core :as p]
            ["buffer" :refer [Buffer]]
            ["child_process" :as cp]
            ["net" :as net]))

(declare treat-out)
(defn- deliver-result! [server predefined-content-size]
  (let [content (.slice (:buffer server) 0 predefined-content-size)
        _ (when (.. js/atom -config (get "generic-lsp.debug"))
            (println "<--" (str content)))
        result (-> content js/JSON.parse (js->clj :keywordize-keys true))
        prom (get-in server [:pending (:id result)])]

    ((:on-command server) result)
    (if prom
      (p/resolve! prom result)
      ((:on-unknown-command server) result))
    (-> server
        (update :pending dissoc (:id result))
        (update :buffer #(.slice % predefined-content-size))
        (dissoc :content-size))))

(defn- treat-out [server data]
  (let [buffer (.concat Buffer #js [(:buffer server) data])
        server (assoc server :buffer buffer)
        predefined-content-size (:content-size server)]

   (if predefined-content-size
     (if (-> buffer .-length (>= predefined-content-size))
       (recur (deliver-result! server predefined-content-size) (.from Buffer ""))
       server)

     (if-let [[header content-size] (re-find #"(?i)content-length\s*:\s+(\d+)\r\n\r\n" (str buffer))]
       (recur
         (-> server
             (assoc :content-size (js/parseInt content-size))
             (update :buffer #(.slice % (count header))))
         (.from Buffer ""))
       server))))

(defprotocol RawSend
  (raw-send! [this contents])
  (-stop-server! [this]))

(defrecord Spawn [server]
  RawSend
  (raw-send! [_ contents] (.. ^js server -stdin (write (str contents))))
  (-stop-server! [_] (.kill ^js server)))

(defrecord Network [server]
  RawSend
  (raw-send! [_ contents] (.write ^js server (str contents)))
  (-stop-server! [_] (.end ^js server)))

(defn- prepare-server [{:keys [on-command on-unknown-command]}]
  {:on-command (or on-command identity)
   :on-unknown-command (or on-unknown-command identity)
   :pending {}
   :buffer (.from Buffer "")})

(defn spawn-server! [command params]
  (let [p (p/deferred)
        args (:args params)
        server (cp/spawn command (into-array args) #js {:cwd (first (.. js/atom -project getPaths))})
        res (atom (assoc (prepare-server params) :server (->Spawn server)))
        close (:on-close params)
        success? (.-pid server)]
    (.. server -stdout (on "data" #(swap! res treat-out %)))
    (.. server (on "error" #(p/reject! p %)))
    (.. server -stderr (on "data" #(when (.. js/atom -config (get "generic-lsp.debug"))
                                     (js/console.error (str %)))))
    (when success?
      (.. server (on "close" #(close)))
      (p/resolve! p res))
    p))

(defn connect-server! [host port params]
  (let [server (doto (. net createConnection port host))
        server-elems (prepare-server params)
        res (atom (assoc server-elems :server (->Network server)))]
    (.on server "data" #(swap! res treat-out %))
    res))

(defn raw-ish-send! [server params]
  (let [message (-> params
                    (assoc :jsonrpc "2.0")
                    clj->js
                    js/JSON.stringify)
        size (-> message js/Buffer.from .-length)]
    (when (.. js/atom -config (get "generic-lsp.debug"))
      (println "-->" message))
    (raw-send! (:server @server) (str "Content-Length: " size "\r\n\r\n" message))
    nil))

(defn send! [server command params]
  (let [id (str (gensym "req-"))
        p (p/deferred)]
    (raw-ish-send! server {:id id
                           :method command
                           :params params})
    (swap! server assoc-in [:pending id] p)
    p))

(defn notify! [server command params]
  (raw-ish-send! server {:method command, :params params}))

(defn stop-server! [server]
  (-stop-server! ^js (:server @server)))
