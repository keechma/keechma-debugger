(ns keechma-debugger.components.main
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma.toolbox.ui :refer [sub>]]
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

(defelement -event-wrap
  :class [:monospaced :h6 :bg-h-clouds :bd-clouds]
  :style [{:height (str (:height-factor base-graph-config) "px")
           :line-height (str (:height-factor base-graph-config) "px")
           :border-bottom-width "1px"
           :border-bottom-style "solid"}
          [:&.route-changed {:border-top "2px solid black"
                             :line-height (str (dec (:height-factor base-graph-config)) "px")}]
          [:&.pause {:background "linear-gradient(to right top, transparent 33%, #bdc3c7 33%, #bdc3c7 66%, transparent 66%)"
                     :background-size "3px 3px"}]])

(defelement -lifecycle-label
  :tag :span
  :class [:c-white :rounded :mr1 :inline-block :relative]
  :style {:line-height "16px"
          :font-weight "bold"
          :font-size "9px"
          :padding "0 5px"
          :top "-2px"})

(defelement -event-batch-label
  :tag :span
  :class [:bg-white :c-asbestos :rounded :inline-block :relative]
  :style {:line-height "17px"
          :font-weight "bold"
          :font-size "11px"
          :padding "0 5px"
          :top "11px"})

(defn calculate-graph-config [app-events]
  (assoc base-graph-config
         :width (* (:width-factor base-graph-config)
                   (inc (count (:controllers app-events))))
         
         :height (* (:height-factor base-graph-config)
                    (count (:events app-events)))))

(defn render-component-ev [e]
  (let [topic (:topic e)]
    (let [controller-name (first (:name e))
          controller-action (last (:name e))]
      [:span 
       [:b "component/" (str topic)]
       [:i.fa.fa-chevron-circle-right.mx1]
       [:span {:style {:color (graph/generate-color-from-term controller-name)}}
        "controller/"
        [:b (str controller-name)]
        "/" (str controller-action)]])))

(defn render-app-ev [e]
  (let [topic (:topic e)]
    (if (= :controller topic)
      (let [controller-name (first (:name e))
            controller-action (last (:name e))]
        [:span 
         [:b "app"]
         [:i.fa.fa-chevron-circle-right.mx1]
         [:span {:style {:color (graph/generate-color-from-term controller-name)}}
          "controller/"
          [:b (str controller-name)]
          "/" (str controller-action)]])
      [:span.c-asbestos (str (:type e) "/" (or (:topic e) "-") "/" (:name e))])))

(defn get-controller-lifecycle-or-action [e]
  (let [name (:name e)]
    (if (and (vector? name) (= :lifecycle (first name)))
      name
      [nil name])))

(defn render-controller-ev [e]
  (let [direction (:direction e)
        topic (:topic e)
        controller-color (graph/generate-color-from-term topic)
        [lifecycle action] (get-controller-lifecycle-or-action e)]
    (if (= :in direction)
      [:span {:style {:color controller-color}}
       (when lifecycle
         [-lifecycle-label {:style {:background controller-color}} "LIFECYCLE"])
       [:i.fa.fa-chevron-circle-right.mr1]
       "controller/"
       [:b (str topic)]
       "/"
       (str action)]
      (let [target-ev (:name e)
            target-controller (first target-ev)
            target-action (last target-ev)]
        [:span
         [:span {:style {:color controller-color}}
          "controller/"
          [:b (str topic)]
          [:i.fa.fa-chevron-circle-right.mx1]]
         [:span {:style {:color (graph/generate-color-from-term target-controller)}}
          "controller/"
          [:b (str target-controller)]
          "/"
          (str target-action)]
         ]))))

(defn render-pause-ev [e]
  (let [[batch-num timestamp] (:name e)]
    [-event-batch-label "BATCH #" batch-num]))

(defn render-router-ev [e]
  [:span
   [:b "router"
    [:i.fa.fa-chevron-circle-right.mx1]
    ":app/" (:name e)]])

(defn render-list-renderer [ctx app-events]
  (let [graph-config (calculate-graph-config app-events)
        events (:events app-events)]
    [-events-graph-wrap
     [-graph-wrap {:style {:width (str (:width graph-config) "px")}}
      [graph/render app-events graph-config]]
     [-events-wrap
      (doall (map (fn [e]
                    [-event-wrap {:key (:id e)
                                  :style {:padding-left (str (+ 40 (:width graph-config)) "px")}
                                  :class (class-names {:route-changed (and (= :router (:type e))
                                                                           (= :route-changed (:name e)))
                                                       :pause (= :pause (:type e))})}
                     (case (:type e)
                       :controller (render-controller-ev e)
                       :component (render-component-ev e)
                       :app (render-app-ev e)
                       :router (render-router-ev e)
                       :pause (render-pause-ev e)
                       (str (:type e) (:direction e) (:topic e) (:name e)))
                     ]) events))]
     ]))

(defn render-list [ctx app-events]
  (r/create-class
   {:reagent-render (fn [ctx app-events] (render-list-renderer ctx app-events))
    :component-did-mount scroll-to-bottom
    :component-did-update scroll-to-bottom}))

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
    :topic             :collector
    :subscription-deps [:collector-value]}))
