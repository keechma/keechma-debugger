(ns keechma-debugger.subscriptions
  (:require-macros
   [reagent.ratom :refer [reaction]]))

(defn collector-value [app-db]
  (reaction
   (let [events-store (get-in @app-db [:kv :events])
         {:keys [apps-order events apps-status controllers]} events-store] 
     (reduce (fn [acc app-name]
               (if (= :start (get apps-status app-name))
                 (conj acc {:name app-name
                            :events (get events app-name)
                            :controllers (get controllers app-name)} ))) [] apps-order))))

(def subscriptions
  {:collector-value collector-value
   :event-expanded? (fn [app-db-atom id]
                      (reaction
                       (contains? (set (get-in @app-db-atom [:kv :expanded-event-ids])) id)))
   :row-dimensions (fn [app-db-atom]
                     (reaction
                      (get-in @app-db-atom [:kv :row-dimensions])))})
