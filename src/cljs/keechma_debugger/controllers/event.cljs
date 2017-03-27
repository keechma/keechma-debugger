(ns keechma-debugger.controllers.event
  (:require [keechma.toolbox.pipeline.core :as pp :refer-macros [pipeline!]]
            [keechma.toolbox.pipeline.controller :as controller]
            [reagent.core :as r]))

(defn toggle-expanded [[app-name id] app-db]
  (let [expanded-events (get-in app-db [:kv :expanded-event-ids])
        expanded? (contains? (set expanded-events) id)]
    (if expanded?
      (assoc-in app-db [:kv :expanded-event-ids] (filter #(not= id %) expanded-events))
      (assoc-in app-db [:kv :expanded-event-ids] (conj expanded-events id)))))

(defn update-row-dimensions [{:keys [app-name id dimensions]} app-db]
  (let [events (get-in app-db [:kv :events :events app-name])
        events-dimensions (get-in app-db [:kv :row-dimensions app-name])
        event-dimensions (get events-dimensions id)]
    (if (and event-dimensions (not= dimensions event-dimensions))
      (let [updated
            (reduce (fn [acc ev]
                      (let [seen? (:seen? acc)
                            delta (:delta acc)
                            ev-id (:id ev)
                            prev-dimensions (get events-dimensions ev-id)]
                        (if seen?
                          (assoc-in acc [:dimensions ev-id]
                                    {:y1 (+ delta (:y1 prev-dimensions))
                                     :y2 (+ delta (:y2 prev-dimensions))})
                          (if (= id ev-id)
                            (let [delta (- (:y2 dimensions) (:y2 prev-dimensions))
                                  ev-dimensions (:dimensions acc)]
                              {:seen? true
                               :delta delta
                               :dimensions (assoc ev-dimensions id dimensions)})
                            acc))
                        )) {:seen? false :dimensions {} :delta 0} events)]
        (assoc-in app-db [:kv :row-dimensions app-name]
                  (merge events-dimensions (:dimensions updated))))
      (assoc-in app-db [:kv :row-dimensions app-name id] dimensions))))

(def controller
  (controller/constructor
   (fn [_] true)
   {:row-dimensions (pipeline! [value app-db]
                      (pp/commit! (update-row-dimensions value app-db))
                      ;;(r/flush)
                      )
    :toggle-expanded (pipeline! [value app-db]
                       (pp/commit! (toggle-expanded value app-db))
                       (r/flush)
                       )}))
