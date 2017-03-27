(ns keechma-debugger.components.graph
  (:require
   [keechma.ui-component :as ui]
   [keechma.toolbox.css.core :refer-macros [defelement]]
   [keechma-debugger.util :refer [index-of]]
   [garden.color :as color]
   [goog.string :as gstring]
   [keechma.toolbox.ui :refer [<cmd sub>]]))

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
      (inc idx))
    0))

(defn build-current-controller-line [current-line idx x e]
  (if (:y1 current-line)
    (assoc current-line :y2 idx :ev2-id (:id e))
    {:key (:id e) :stroke-width 2 :stroke (generate-color-from-term (:topic e)) :x1 x :x2 x :y1 idx :ev1-id (:id e)}))

(defn build-current-connector-line [line idx controllers e open?]
  (if open?
    {:x1 0
     :y1 idx
     :ev1-id (:id e)
     :key (:id e)}
    (if (and (:x1 line) (:y1 line) (nil? (:x2 line)) (nil? (:y2 line)))
      (assoc line
             :x2 (calculate-x e controllers)
             :y2 idx
             :ev2-id (:id e)
             :stroke-color-term (name (:topic e))
             :stroke (generate-color-from-term (:topic e)))
      nil)))

(defn build-current-send-command-connector-line [line idx controllers e open?]
  (if open?
    {:x1 (calculate-x e controllers)
     :y1 idx
     :ev1-id (:id e)
     :stroke-color-term (name (:topic e))
     :stroke (generate-color-from-term (:topic e))
     :key (:id e)}
    (let [x2 (calculate-x e controllers)]
      (when (not= (:x1 line) x2)
        (assoc line
               :x2 x2
               :ev2-id (:id e)
               :y2 idx)))))

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

(defn calculate-send-command-connectors [events controllers]
  (let [indexed-events (map-indexed (fn [idx e] [idx e]) events)]
    (:closed
     (reduce
      (fn [acc [idx e]]
        (if (= :controller (:type e))
          (let [direction (:direction e)]
            (if (= :out direction)
              (let [connector-key (:name e)
                    prev-lines (or (get-in acc [:open connector-key]) [])]
                (assoc-in acc [:open connector-key]
                          (vec (conj prev-lines
                                     (build-current-send-command-connector-line {} idx controllers e true)))))
              (let [connector-key [(:topic e) (:name e)]
                    open-lines (get-in acc [:open connector-key])
                    current-line (first open-lines)
                    rest-lines (rest open-lines)
                    closed-lines (or (get-in acc [:closed connector-key]) [])]
                (if current-line
                  (-> acc
                      (assoc-in [:open connector-key] rest-lines)
                      (assoc-in [:closed connector-key]
                                (vec (conj closed-lines
                                           (build-current-send-command-connector-line current-line idx controllers e false)))))
                  acc))))
          acc)) {} indexed-events))))

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
  (let [indexed-events (map-indexed (fn [idx e] [idx e]) events)]
    (:closed
     (reduce (fn [acc [idx e]]
               (if (main->controller-event? e)
                 (let [connector-key (main->controller-line-name e)
                       open? (opening-main->controller-event? e)]
                   (if open?
                     (let [prev-lines (or (get-in acc [:open connector-key]) [])]
                       (assoc-in acc [:open connector-key]
                                 (vec (conj prev-lines
                                            (build-current-connector-line {} idx controllers e open?)))))
                     (let [open-lines (get-in acc [:open connector-key])
                           current-line (first open-lines)
                           rest-lines (rest open-lines)
                           closed-lines (or (get-in acc [:closed connector-key]) [])]
                       (if current-line
                         (-> acc
                             (assoc-in [:open connector-key] rest-lines)
                             (assoc-in [:closed connector-key]
                                       (vec (conj closed-lines
                                                  (build-current-connector-line current-line idx controllers e false)))))
                         acc))))
                 acc)
                 ) {} indexed-events))))

(defn signum [val]
  (if (neg? val) -1 1))

(defn make-connector-path [{:keys [x1 x2 y1 y2]}]
  (let [delta-factor 0.25
        delta-x (* delta-factor (- x2 x1))
        delta-y (* delta-factor (- y2 y1))
        delta 5

        x-diff (- x2 x1)
        y-diff (- y2 y1)

        arc-1 0
        arc-2 1]
    (str "M" x1 " " y1
         " H" (- x2 (* delta (signum delta-x)))
         " A" delta " " delta " 0 0 " (if (> x1 x2) arc-1 arc-2) " " x2 " " (+ y1 (* 1 delta))
         " V"  y2)))

(defn render [ctx app-events {:keys [height-factor width-factor stroke-width width]}]
  (let [events (:events app-events)
        controllers (:controllers app-events)
        controller-connectors (calculate-controllers-connectors events controllers)
        connectors (calculate-main->controllers-connectors events controllers)
        send-command-connectors (calculate-send-command-connectors events controllers)
        row-dimensions (sub> ctx :row-dimensions)
        get-top (fn [ev-id]
                  (+ 25 (or (get-in row-dimensions [ev-id :y1]) 0)))
        height (or (apply max (map :y2 (vals row-dimensions))) 0)]
    [:svg {:height (str height "px")
           :width (str width "px")}
     [:defs
      (doall (map (fn [c]
                    [:marker {:id (str "arrow-" (name c))
                              :key (str "arrow-" (name c))
                              :orient "auto"
                              :marker-width (* 1.5 stroke-width)
                              :marker-height (* 3 stroke-width)
                              :ref-x "5"
                              :ref-y "3"}
                     [:path {:d "M0,0 V6 L3,3 Z" :fill (generate-color-from-term (name c))}]])
                  controllers))]
     
     [:line {:x1 (/ width-factor 2)
             :x2 (/ width-factor 2)
             :y1 0
             :y2 height
             :stroke "black"
             :stroke-width stroke-width}] 
     [:g (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke ev1-id ev2-id]}]
                       [:line {:key key
                               :x1 (+ (/ width-factor 2) (* width-factor x1))
                               :x2 (+ (/ width-factor 2) (* width-factor x2))
                               :y1 (get-top ev1-id)
                               :y2 (get-top ev2-id)
                               :stroke-width stroke-width
                               :stroke stroke}])
                     (flatten (vals controller-connectors))))]
     [:g (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke ev1-id ev2-id]}]
                       [:path
                        {:d (make-connector-path {:x1 (/ width-factor 2)
                                                  :x2 (+ (/ width-factor 2) (* width-factor x2))
                                                  :y1 (get-top ev1-id)
                                                  :y2 (get-top ev2-id)})
                         :key key
                         :stroke "white"
                         :stroke-width (* 3 stroke-width)
                         :fill "transparent"}])
                     (remove nil? (flatten (vals connectors)))))]

     [:g (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke stroke-color-term ev1-id ev2-id]}]
                       [:path
                        {:d (make-connector-path {:x1 (/ width-factor 2)
                                                  :x2 (+ (/ width-factor 2) (* width-factor x2))
                                                  :y1 (get-top ev1-id)
                                                  :y2 (get-top ev2-id)})
                         :key key
                         :stroke stroke
                         :stroke-width stroke-width
                         :fill "transparent"
                         :marker-end (str "url(#arrow-" stroke-color-term ")")}])
                     (remove nil? (flatten (vals connectors)))))]

     [:g (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke stroke-color-term ev1-id ev2-id]}]
                       [:path
                        {:d (make-connector-path {:x1 (+ (/ width-factor 2) (* width-factor x1))
                                                  :x2 (+ (/ width-factor 2) (* width-factor x2))
                                                  :y1 (get-top ev1-id)
                                                  :y2 (get-top ev2-id)})
                         :key key
                         :stroke "white"
                         :stroke-width (* 3 stroke-width)
                         :fill "transparent"}])
                     (remove nil? (reverse (flatten (vals send-command-connectors))))))]

     [:g (doall (map (fn [{:keys [x1 x2 y1 y2 key stroke stroke-color-term ev1-id ev2-id]}]
                       [:path
                        {:d (make-connector-path {:x1 (+ (/ width-factor 2) (* width-factor x1))
                                                  :x2 (+ (/ width-factor 2) (* width-factor x2))
                                                  :y1 (get-top ev1-id)
                                                  :y2 (get-top ev2-id)
                                                  })
                         :key key
                         :stroke stroke
                         :stroke-width stroke-width
                         :fill "transparent"
                         :marker-end (str "url(#arrow-" stroke-color-term ")")}])
                     (remove nil? (reverse (flatten (vals send-command-connectors))))))]

     [:g (doall (map-indexed (fn [idx e]
                               (when (not= :pause (:type e))
                                 [:circle {:key (:id e)
                                           :stroke (if (= :controller (:type e)) (generate-color-from-term (:topic e)) "black")
                                           :stroke-width stroke-width
                                           :fill "white"
                                           :r (/ width-factor 4)
                                           :cy (get-top (:id e))
                                           :cx (+ (/ width-factor 2) (* width-factor (calculate-x e controllers)))}]))
                             events))]]))
