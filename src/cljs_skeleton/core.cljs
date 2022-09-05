(ns cljs-skeleton.core
  (:require ["atom" :refer [CompositeDisposable]]))

(def subscriptions (atom (CompositeDisposable.)))

(defonce atom-state (atom nil))

(defn- info! [message]
  (.. js/atom -notifications (addInfo message)))

(defn activate [state]
  (reset! atom-state state)
  (.add @subscriptions (.. js/atom -commands
                           (add "atom-text-editor"
                                "cljs-skeleton:activate"
                                #(info! "CLJS Plugin Activated!")))))

(defn deactivate [state]
  (.dispose ^js @subscriptions))

(defn ^:dev/before-load reset-subs []
  (deactivate @atom-state))

(defn ^:dev/after-load re-activate []
  (reset! subscriptions (CompositeDisposable.))
  (activate @atom-state)
  (info! "Reloaded plug-in"))
