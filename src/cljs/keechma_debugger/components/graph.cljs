(ns keechma-debugger.components.graph
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma-debugger.util :refer [index-of]]))

(defn calculate-x [event controllers]
  (if (= :controller (:type event))
    (let [topic (:topic event)
          idx (index-of controllers topic)]
      (+ 10 (* 20 idx)))
    10))


(defn build-current-line [current-line idx x e]
  (if (:y1 current-line)
    (assoc current-line :y2 (+ 13.5 (* idx 27)))
    {:key (:id e) :stroke-width 2 :stroke "black" :x1 x :x2 x :y1 (+ 13.5 (* idx 27))}))

(defn calculate-controllers-connectors [events controllers]
  (reduce (fn [acc [idx e]]
            (let [type (:type e)]
              (if (= :controller type)
                (let [topic (:topic e)
                      ev-name (:name e)
                      start? (= [:lifecycle :start] ev-name)
                      lines (get acc topic)
                      prev-lines (if start? lines (drop-last lines))
                      current-line (if start? {} (or (last lines) {}))
                      x (calculate-x e controllers)]
                  (assoc acc topic (conj (vec prev-lines) (build-current-line current-line idx x e))))
                acc)
              )) {} (map-indexed (fn [idx e] [idx e]) events)))

(defn signum [val]
  (if (neg? val) -1 1))

(defn make-connector-path [{:keys [x1 x2 y1 y2]}]
  (let [delta-factor 0.25
        delta-x (* delta-factor (- x2 x1))
        delta-y (* delta-factor (- y2 y1))
        delta (min delta-x delta-y)
        arc-1 0
        arc-2 1]
    (str "M" x1 " " y1
         " V" (+ y1 delta)
         " A" delta " " delta " 0 0 " arc-1 " " (+ x1 (* delta (signum delta-x))) " " (+ y1 (* 2 delta))
         " H" (- x2 (* delta (signum delta-x)))
         " A" delta " " delta " 0 0 " arc-2 " " x2 " " (+ y1 (* 3 delta))
         " V" y2)))


(defn opening-main->controller-event? [e]
  (let [type (:type e)
        topic (:topic e)]
    (or (and (= :app type)
             (= :controller topic))
        (= :component type))))

(defn closing-main->controller-event? [e]
  (= :controller (:type e)))

(defn main->controller-event? [e]
  (or (opening-main->controller-event? e)
      (closing-main->controller-event? e)))

(defn main->controller-line-name [e]
  (let [topic (:topic e)
        ev-name (:name e)]
    (case (:type e)
      :app [(first ev-name) :lifecycle (last ev-name)]
      :component ev-name
      :controller (flatten [topic ev-name]))))

(defn build-current-connector-line [line idx controllers e open?]
  (if open?
    {:x1 10
     :y1 (+ 13.5 (* idx 27))
     :key (:id e) :stroke-width 2 :stroke "black"}
    (if (and (:x1 line) (:y1 line) (nil? (:x2 line)) (nil? (:y2 line)))
      (assoc line
             :x2 (calculate-x e controllers)
             :y2 (+ 13.5 (* idx 27)))
      nil)))

(defn calculate-main->controllers-connectors [events controllers]
  (reduce (fn [acc [idx e]]
            (let [type (:type e)]
              (if (main->controller-event? e)
                (let [connector-name (main->controller-line-name e)
                      open? (opening-main->controller-event? e)
                      close? (closing-main->controller-event? e)
                      lines (get acc connector-name)
                      prev-lines (if open? lines (drop-last lines))
                      current-line (if open? {} (or (last lines) {}))]
                  (assoc acc connector-name (conj (vec prev-lines) (build-current-connector-line current-line idx controllers e open?))))
                acc)
              )) {} (map-indexed (fn [idx e] [idx e]) events)))

(defn render [app-events]
  (let [events (:events app-events)
        controllers (:controllers app-events)
        height-factor 27
        lines (calculate-controllers-connectors events controllers)
        connectors (calculate-main->controllers-connectors events controllers)]
    [:svg {:height (str (* height-factor (count events)) "px")
           :width (str (* 20 (count controllers)) "px")}
     [:line {:x1 10
             :x2 10
             :y1 13.5
             :y2 (+ 13.5 (* (count events) height-factor))
             :stroke "black"
             :stroke-width 2}]     
     (doall (map-indexed (fn [idx e]
                           [:circle {:key (:id e)
                                     :r 5
                                     :cy (+ 13.5 (* idx height-factor))
                                     :cx (calculate-x e controllers)}]) events))
     (doall (map (fn [line]
                   [:path {:d (make-connector-path line) :key (:key line) :stroke (:stroke line) :stroke-width (:stroke-width line) :fill "transparent"}]) (remove nil? (flatten (vals connectors)))))
     (doall (map (fn [line]
                   [:line line]) (flatten (vals lines))))]))
