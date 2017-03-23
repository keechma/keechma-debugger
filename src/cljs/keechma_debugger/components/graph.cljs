(ns keechma-debugger.components.graph
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma-debugger.util :refer [index-of]]))

(defn calculate-x [event controllers]
  (if (= :controller (:type event))
    (let [topic (:topic event)
          idx (index-of controllers topic)]
      idx)
    0))

(defn build-current-controller-line [current-line idx x e]
  (if (:y1 current-line)
    (assoc current-line :y2 idx)
    {:key (:id e) :stroke-width 2 :stroke "black" :x1 x :x2 x :y1 idx}))

(defn build-current-connector-line [line idx controllers e open?]
  (if open?
    {:x1 0
     :y1 idx
     :key (:id e)}
    (if (and (:x1 line) (:y1 line) (nil? (:x2 line)) (nil? (:y2 line)))
      (assoc line
             :x2 (calculate-x e controllers)
             :y2 idx)
      nil)))

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
                  (assoc acc topic (conj (vec prev-lines) (build-current-controller-line current-line idx x e))))
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
        width-factor 20
        controller-connectors (calculate-controllers-connectors events controllers)
        connectors (calculate-main->controllers-connectors events controllers)]
    [:svg {:height (str (* height-factor (count events)) "px")
           :width (str (* width-factor (count controllers)) "px")}
     [:line {:x1 (/ width-factor 2)
             :x2 (/ width-factor 2)
             :y1 (/ height-factor 2)
             :y2 (+ (/ height-factor 2) (* (count events) height-factor))
             :stroke "black"
             :stroke-width 2}]     
     (doall (map (fn [{:keys [x1 x2 y1 y2 key]}]
                   [:path
                    {:d (make-connector-path {:x1 (/ width-factor 2)
                                              :x2 (+ (/ width-factor 2) (* width-factor x2))
                                              :y1 (+ (/ height-factor 2) (* height-factor y1))
                                              :y2 (+ (/ height-factor 2) (* height-factor y2))})
                     :key key
                     :stroke "black"
                     :stroke-width 2
                     :fill "transparent"}])
                 (remove nil? (flatten (vals connectors)))))
     (doall (map (fn [{:keys [x1 x2 y1 y2 key]}]
                   [:line {:key key
                           :x1 (+ (/ width-factor 2) (* width-factor x1))
                           :x2 (+ (/ width-factor 2) (* width-factor x2))
                           :y1 (+ (/ height-factor 2) (* height-factor y1))
                           :y2 (+ (/ height-factor 2) (* height-factor y2))
                           :stroke-width 2
                           :stroke "black"}])
                 (flatten (vals controller-connectors))))
     (doall (map-indexed (fn [idx e]
                           [:circle {:key (:id e)
                                     :r (/ width-factor 4)
                                     :cy (+ (/ height-factor 2) (* idx height-factor))
                                     :cx  (+ (/ width-factor 2) (* width-factor (calculate-x e controllers)))}])
                         events))]))
