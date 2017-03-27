(ns keechma-debugger.components.event
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma.toolbox.ui :refer [sub> <cmd]]
   [reagent.core :as r]
   [oops.core :refer [oget oset!]]
   [keechma-debugger.util.json-renderer :as jr]
   [keechma-debugger.components.graph :as graph]
   [keechma.toolbox.util :refer [class-names]]))

(defelement -event-wrap
  :class [:monospaced :h5 :bd-clouds :bg-h-clouds :py2]
  :style [{;;:height "40px"
           ;;:line-height "40px"
           :border-bottom-width "1px"
           :border-bottom-style "solid"}
          [:&.route-changed {:border-top "2px solid black"
                             ;;:line-height "39px"
                             }]
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

(defn inner-render [ctx props e]
  (let [type (:type e)
        graph-config (:graph-config props)]
    [-event-wrap 
     {:key (:key props)
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
     [:div
      [jr/render (:payload e)]]]))

(defn measure-row [ctx e this]
  (let [dom-node (r/dom-node this)
        client-rect (.getBoundingClientRect dom-node)
        offset-top (.-offsetTop dom-node)]
    (<cmd ctx :row-dimensions {:id (:id e)
                               :dimensions {:y1 offset-top :y2 (+ offset-top (.-height client-rect))}})))

(defn render [ctx props e]
  (r/create-class
   {:reagent-render (fn [props e] [inner-render ctx props e])
    :component-did-mount #(measure-row ctx e %)
    :component-did-update #(measure-row ctx e %)}))

(def component
  (ui/constructor
   {:renderer render
    :topic :event}))
