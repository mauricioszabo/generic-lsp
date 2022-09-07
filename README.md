# Generic LSP - connect into anything!

LSP (Language Server Protocol) is a generic way for any editor to interact with any programming language. Originally drafted for VSCode, LSP is a protocol that allows any editor to have the same capabilities like Autocomplete, Go To Var Definition/Declaration, etc.

Atom/Pulsar does not have the same capabilities for LSP than other editor, so this package tries to fix that gap by adding "Generic LSP" capabilities: with some easy configurations, it is possible to fire up a LSP server (via command line for now) and have autocomplete, go to var definition, linter, and other niceties that other editors take for granted.

This package is meant to be a "generic" LSP. It will **not install** any LSP server for you (you need to install yourself, and have it on your PATH, or configure the path inside the package's configuration) and it will **not install** additional packages for you (but it does integrate with existing known packages like Linter and Linter UI for example)

## What is implemented

- Linter (needs the `linter` package)
- Go To Definition / Declaration / Type Declaration (each one is a specific command for now)
- Autocomplete (needs the `autocomplete-plus` package, it's probably already installed)
- Known LSP servers can be started (we still don't have configuration wired up)

![A screenshot of your package](https://f.cloud.github.com/assets/69169/2290250/c35d867a-a017-11e3-86be-cd7c5bf3ff9b.gif)
