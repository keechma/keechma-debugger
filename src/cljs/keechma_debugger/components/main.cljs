(ns keechma-debugger.components.main
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.ui :refer [sub>]]))

(defn collector-render [ctx]
  (let [events (sub> ctx :collector-value)] 
    [:div
     (doall (map (fn [[app-name events]]
                   [:div {:key (str app-name)}
                    [:h1 (str app-name)]
                    [:ul
                     (doall (map (fn [e]
                                   [:li {:key (:id e)} (str e)]) events))]]) events))]))

(def component
  (ui/constructor
   {:renderer          collector-render
    :topic             :Collector
    :subscription-deps [:collector-value]}))
