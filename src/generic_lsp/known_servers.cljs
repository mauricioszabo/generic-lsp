(ns generic-lsp.known-servers)

(def servers
  {"Clojure" {:type :spawn
              :args ["--log-path" "/tmp/lsp.log"]
              :binary "clojure-lsp"}
   "C++" {:type :spawn
          :args []
          :binary "clangd"}
   "C" {:type :spawn
        :args []
        :binary "clangd"}
   "JavaScript" {:type :network
                 :port 2089}})
