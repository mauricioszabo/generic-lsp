(ns generic-lsp.known-servers)

(def servers
  {"Clojure" {:args []
              :binary "clojure-lsp"}
   "C++" {:args []
          :binary "clangd"}
   "C" {:args []
        :binary "clangd"}
   "JavaScript" {:args ["--stdio"]
                 :binary "typescript-language-server"}
   "TypeScript" {:args ["--stdio"]
                 :binary "typescript-language-server"}
   "TypeScriptReact" {:args ["--stdio"]
                      :binary "typescript-language-server"}
   "Ruby" {:args ["exec" "solargraph" "stdio"]
           :binary "bundle"}
   "Java" {:binary "jdtls"
           :args []}
   "Rust" {:binary "rust-analyzer"
           :args []}
   "Lua" {:binary "lua-language-server"
          :args []}})
