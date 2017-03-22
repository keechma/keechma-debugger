(ns keechma-debugger.subscriptions
  (:require-macros
   [reagent.ratom :refer [reaction]]))

(defn collector-value [app-db]
  (reaction
   (let [events-store (get-in @app-db [:kv :events])
         apps-order (:apps-order events-store)
         events (:events events-store)
         apps-status (:apps-status events-store)]
     (reduce (fn [acc app-name]
               (if (= :start (get apps-status app-name))
                 (conj acc [app-name (get events app-name)]))) [] apps-order))))

(def subscriptions
  {:collector-value collector-value})
