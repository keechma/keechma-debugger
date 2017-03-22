(ns keechma-debugger.components.main
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma.toolbox.ui :refer [sub>]]
   [reagent.core :as r]
   [oops.core :refer [oget oset!]]
   [keechma-debugger.util.json-renderer :as jr]))

(defelement main-wrap)

(defn scroll-to-bottom [this]
  (let [dom-node (r/dom-node this)]
    (oset! dom-node "scrollTop" (oget dom-node "scrollHeight"))))

(defn render-list [ctx events]
  (r/create-class
   {:reagent-render (fn [ctx events]
                     [:div {:style {:height "90vh"
                                   :overflow-y "auto"}}
                      [:table.table.table-sm.table-bordered
                       [:tbody
                        (doall (map (fn [e]
                                      [:tr {:key (:id e)
                                            :class (when (and (= :router (:type e))
                                                              (= :route-changed (:name e)))
                                                     "table-success")}
                                       [:td (:type e)]
                                       [:td (:direction e)]
                                       [:td (:topic e)]
                                       [:td (:name e)]
                                       [:td [jr/render (:payload e)]]]) events))]]])
    :component-did-mount scroll-to-bottom
    :component-did-update scroll-to-bottom}))

(defn collector [ctx measurements]
  (let [events (sub> ctx :collector-value)] 
    [main-wrap
     (doall (map (fn [[app-name events]]
                   [:div {:key (str app-name)}
                    [:h1 (str app-name)]
                    [render-list ctx events]]) events))]))

(def component
  (ui/constructor
   {:renderer          collector
    :topic             :Collector
    :subscription-deps [:collector-value]}))
