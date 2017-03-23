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
  {:collector-value collector-value})
