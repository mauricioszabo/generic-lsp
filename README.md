# cljs-skeleton - a ClojureScript Atom package example code

This code is meant only as a skeleton to generate ClojureScript packages. It is not
meant to be used.

To start a project with this template, clone it to some folder normally, rename all entries of `cljs-skeleton` to, for example, `my-plugin` (remember to fix folder names too!) and run `apm link my-plugin` to link the local copy to the Atom package folder.

To start compilation, run: `npm install` then `npx shadow-cljs watch package`. Please wait until Shadow-CLJS compiles the first version before trying to run `Activate` command, otherwise you'll get an error and will need to restart Atom before you can make changes.

All "interactive coding" that ClojureScript is famous for works for Atom plug-ins. It also means that after saving any code, you'll be able to see changes on your commands, subscriptions, and so on (so it reloads all your callbacks too!)

## Common bugs:

### Strange errors on activation (`$cljs is not defined`):

You can only develop a SINGLE ClojureScript package per time. If you have multiple
Atom packages written in ClojureScript, compile one as a release build (with
`shadow-cljs release <build-id>` for example) then develop the other normally.

![A screenshot of your package](https://f.cloud.github.com/assets/69169/2290250/c35d867a-a017-11e3-86be-cd7c5bf3ff9b.gif)
