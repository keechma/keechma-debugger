(ns keechma-debugger.components.main
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma.toolbox.ui :refer [sub>]]
   [reagent.core :as r]
   [oops.core :refer [oget oset!]]
   [keechma-debugger.util.json-renderer :as jr]
   [keechma-debugger.components.graph :as graph]))

(defelement main-wrap)

(defn scroll-to-bottom [this]
  (let [dom-node (r/dom-node this)]
    (oset! dom-node "scrollTop" (oget dom-node "scrollHeight"))))

(defn render-list [ctx events]
  (r/create-class
   {:reagent-render (fn [ctx app-events]
                      (let [events (:events app-events)]
                        [:div {:style {:height "90vh"
                                       :overflow-y "auto"
                                       :display "flex"}}
                         [:div {:style {:flex 1}}
                          [graph/render app-events]]
                         [:table.table.table-sm.table-bordered
                          [:tbody
                           (doall (map (fn [e]
                                         [:tr {:key (:id e)
                                               :class (when (and (= :router (:type e))
                                                                 (= :route-changed (:name e)))
                                                        "table-success")}
                                          [:td (str (:type e))]
                                          [:td (str (:direction e))]
                                          [:td (str (:topic e))]
                                          [:td (str (:name e))]
                                          ;;[:td [jr/render (:payload e)]]
                                          ]) events))]]]))
    :component-did-mount scroll-to-bottom
    :component-did-update scroll-to-bottom}))

(defn collector [ctx measurements]
  (let [collected-events (sub> ctx :collector-value)] 
    [main-wrap
     (doall (map (fn [app-events]
                   [:div {:key (str (:name app-events))}
                    [:h1 (str (:name app-events))]
                    [render-list ctx app-events]]) collected-events))]))

(def component
  (ui/constructor
   {:renderer          collector
    :topic             :Collector
    :subscription-deps [:collector-value]}))
