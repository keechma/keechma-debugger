(ns keechma-debugger.components.main
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma.toolbox.ui :refer [sub> <cmd]]
   [reagent.core :as r]
   [oops.core :refer [oget oset!]]
   [keechma-debugger.util.json-renderer :as jr]
   [keechma-debugger.components.graph :as graph]
   [keechma.toolbox.util :refer [class-names]]))

(defn scroll-to-bottom [this]
  (let [dom-node (r/dom-node this)]
    (oset! dom-node "scrollTop" (oget dom-node "scrollHeight"))))

(def base-graph-config
  {:height-factor 40
   :width-factor 16
   :stroke-width 2})

(defelement -main-wrap
  :class [:absolute :left-0 :right-0 :bottom-0 :top-0])

(defelement -app-events-wrap
  :class [:absolute :left-0 :right-0 :bottom-0 :top-0])

(defelement -app-title-wrap
  :class [:absolute :left-0 :right-0 :top-0 :c-midnight-blue :px2 :h1]
  :style {:height "50px"
          :line-height "50px"})

(defelement -events-graph-wrap
  :class [:absolute :left-0 :right-0 :bottom-0 :bd-silver]
  :style {:top "60px"
          :border-top-style "solid"
          :border-top-width "1px"
          :overflow-y "auto"})

(defelement -graph-wrap
  :class [:absolute :left-0 :top-0 :bottom-0]
  :style {:z-index 1
          :padding "0px 20px"})

(defelement -events-wrap
  :class [:absolute :left-0 :right-0 :top-0 :bottom-0])



(defn calculate-graph-config [app-events]
  (assoc base-graph-config
         :width (* (:width-factor base-graph-config)
                   (inc (count (:controllers app-events))))
         
         :height (* (:height-factor base-graph-config)
                    (count (:events app-events)))))


(defn render-list-renderer [ctx app-events]
  (let [graph-config (calculate-graph-config app-events)
        events (:events app-events)]
    [-events-graph-wrap
     [-graph-wrap {:style {:width (str (:width graph-config) "px")}}
      [graph/render ctx app-events graph-config]]
     [-events-wrap
      (doall
       (map (fn [e] [(ui/component ctx :event) {:key (:id e) :graph-config graph-config} (:name app-events) e])
            events))]]))

(defn render-list [ctx app-events]
  (let [ev-count (atom 0)]
    (r/create-class
     {:reagent-render (fn [ctx app-events] (render-list-renderer ctx app-events))
      :component-did-mount scroll-to-bottom
      :component-did-update scroll-to-bottom 
      })))

(defn collector [ctx measurements]
  (let [collected-events (sub> ctx :collector-value)] 
    [-main-wrap
     (doall (map (fn [app-events]
                   [-app-events-wrap {:key (str (:name app-events))}
                    [-app-title-wrap (str (:name app-events))]
                    [render-list ctx app-events]]) collected-events))]))

(def component
  (ui/constructor
   {:renderer          collector
    :component-deps [:event]
    :subscription-deps [:collector-value :row-dimensions]}))
