(ns keechma-debugger.components.graph
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma-debugger.util :refer [index-of]]
   [garden.color :as color]
   [goog.string :as gstring]))

(defn generate-color-from-term [t]
  (let [term (name t)
        term-length (count term)
        modifier (* term-length term-length)
        hash-code (gstring/hashCode term) 
        term-hue (mod (+ modifier hash-code) 360)]
    (color/as-hex (color/hsl term-hue 100 35))))

(defn calculate-x [event controllers]
  (if (= :controller (:type event))
    (let [topic (:topic event)
          idx (index-of controllers topic)]
      idx)
    0))

(defn build-current-controller-line [current-line idx x e]
  (if (:y1 current-line)
    (assoc current-line :y2 idx)
    {:key (:id e) :stroke-width 2 :stroke (generate-color-from-term (:topic e)) :x1 x :x2 x :y1 idx}))

(defn build-current-connector-line [line idx controllers e open?]
  (if open?
    {:x1 0
     :y1 idx
     :key (:id e)}
    (if (and (:x1 line) (:y1 line) (nil? (:x2 line)) (nil? (:y2 line)))
      (assoc line
             :x2 (calculate-x e controllers)
             :y2 idx
             :stroke-color-term (name (:topic e))
             :stroke (generate-color-from-term (:topic e)))
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

        x-diff (- x2 x1)
        y-diff (- y2 y1)

        arc-1 0
        arc-2 1]
    (str "M" x1 " " y1
         " H" (+ x1 delta) 
         " H" (- x2 (* delta (signum delta-x)))
         " A" delta " " delta " 0 0 " arc-2 " " x2 " " (+ y1 (* 1 delta))
         " V"  y2)
    ))


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
        width-factor 16
        stroke-width 2
        controller-connectors (calculate-controllers-connectors events controllers)
        connectors (calculate-main->controllers-connectors events controllers)
        height (str (* height-factor (count events)) "px")
        width (str (* width-factor (count controllers)) "px")]
    [:svg {:height height
           :width width}
     [:defs
      (doall (map (fn [c]
                    [:marker {:id (str "arrow-" (name c))
                              :key (str "arrow-" (name c))
                              :orient "auto"
                              :marker-width stroke-width
                              :marker-height (* 2 stroke-width)
                              :ref-x "4"
                              :ref-y "2"}
                     [:path {:d "M0,0 V4 L2,2 Z" :fill (generate-color-from-term (name c))}]])
                  controllers))]
     
     [:line {:x1 (/ width-factor 2)
             :x2 (/ width-factor 2)
             :y1 (/ height-factor 2)
             :y2 (+ (/ height-factor 2) (* (count events) height-factor))
             :stroke "black"
             :stroke-width stroke-width}] 
     (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke]}]
                   [:line {:key key
                           :x1 (+ (/ width-factor 2) (* width-factor x1))
                           :x2 (+ (/ width-factor 2) (* width-factor x2))
                           :y1 (+ (/ height-factor 2) (* height-factor y1))
                           :y2 (+ (/ height-factor 2) (* height-factor y2))
                           :stroke-width stroke-width
                           :stroke stroke}])
                 (flatten (vals controller-connectors))))
     (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke]}]
                   [:path
                    {:d (make-connector-path {:x1 (/ width-factor 2)
                                              :x2 (+ (/ width-factor 2) (* width-factor x2))
                                              :y1 (+ (/ height-factor 2) (* height-factor y1))
                                              :y2 (+ (/ height-factor 2) (* height-factor y2))})
                     :key key
                     :stroke "white"
                     :stroke-width (* 3 stroke-width)
                     :fill "transparent"}])
                 (remove nil? (flatten (vals connectors)))))

     (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke stroke-color-term]}]
                   [:path
                    {:d (make-connector-path {:x1 (/ width-factor 2)
                                              :x2 (+ (/ width-factor 2) (* width-factor x2))
                                              :y1 (+ (/ height-factor 2) (* height-factor y1))
                                              :y2 (+ (/ height-factor 2) (* height-factor y2))})
                     :key key
                     :stroke stroke
                     :stroke-width stroke-width
                     :fill "transparent"
                     :marker-end (str "url(#arrow-" stroke-color-term ")")}])
                 (remove nil? (flatten (vals connectors)))))
     (doall (map-indexed (fn [idx e]
                           [:circle {:key (:id e)
                                     :stroke (if (= :controller (:type e)) (generate-color-from-term (:topic e)) "black")
                                     :stroke-width stroke-width
                                     :fill "white"
                                     :r (/ width-factor 4)
                                     :cy (+ (/ height-factor 2) (* idx height-factor))
                                     :cx  (+ (/ width-factor 2) (* width-factor (calculate-x e controllers)))}])
                         events))]))
