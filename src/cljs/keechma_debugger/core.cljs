(ns keechma-debugger.core
  (:require-macros
   [reagent.ratom :refer [reaction]]
   [cljs.core.async.macros :refer [go-loop]])
  (:require
   [reagent.core :as reagent]
   [keechma.ui-component :as ui]
   [keechma.controller :as controller]
   [keechma.app-state :as app-state]
   [taoensso.sente  :as sente :refer (cb-success?)]
   [cljs.core.async :as async :refer (<! >! put! chan close!)]
   ))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce debug?
  ^boolean js/goog.DEBUG)


(def event-store-layout
  {:events {}
   :apps-order []
   :apps-status {}})

(defn get-app-events [events app-name]
  (or (get events app-name) []))

(defn build-event [[app-name type direction topic ev-name payload severity]]
  {:id (name (gensym "evid"))
   :type type
   :direction direction
   :topic topic
   :name ev-name
   :payload payload
   :severity severity})

(defn process-start-event [store event]
  (let [[app-name type direction _ name _ _] event]
    (if (and (= :app type)
             (= :in direction)
             (= :start name))
      (assoc store
             :apps-order (conj (:apps-order store) app-name)
             :apps-status (assoc (:apps-status store) app-name :start))
      store)))

(defn process-stop-event [store event]
  (let [[app-name type direction _ name _ _] event]
    (if (and (= :app type)
             (= :in direction)
             (= :stop name))
      (assoc store
             :apps-status (assoc (:apps-status store) app-name :stop))
      store)))

(defn process-event [store event]
  (let [[app-name _ _ _ _ _ _] event]
    (assoc-in store [:events app-name] (conj (get-app-events (:events store) app-name) (build-event event)))))


(defn store-event [app-db-atom event]
  (let [app-db @app-db-atom
        events (or (get-in app-db [:kv :events]) event-store-layout)]
    (swap! app-db-atom
           assoc-in [:kv :events]
           (-> events
               (process-start-event event)
               (process-stop-event event)
               (process-event event)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Controllers

(defrecord Collector []
  controller/IController

  (params [_ _] true)

  (start [_ params app-db]
    (assoc-in app-db [:count] 0))

  (stop [this params app-db]
    (controller/execute this :stop)
    app-db)

  (handler [_ app-db-atom in-chan _]
    (let [s (sente/make-channel-socket! "/chsk" {:type :auto})
          s-recv (go-loop []
                   (when-let [event (:event (<! (:ch-recv s)))]
                     (let [[type payload] event]
                       (when (and (= :chsk/recv type)
                                  (= :keechma/debugger (first payload)))
                         (put! in-chan [:collect-ev (last payload)])))
                     (recur)))]
      (controller/dispatcher
       app-db-atom
       in-chan
       {:collect-ev (fn [app-db-atom ev] (store-event app-db-atom ev))
        :stop (fn [app-db-atom ev]
                (close! s-recv)
                (close! (:ch-recv s)))}))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Subs

(defn collector-value-sub [app-db]
  (reaction
   (let [events-store (get-in @app-db [:kv :events])
         apps-order (:apps-order events-store)
         events (:events events-store)
         apps-status (:apps-status events-store)]
     (reduce (fn [acc app-name]
               (if (= :start (get apps-status app-name))
                 (conj acc [app-name (get events app-name)]))) [] apps-order))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

(defn collector-render [ctx]
  (let [events @(ui/subscription ctx :collector-value)] 
    [:div
     (doall (map (fn [[app-name events]]
                   [:div {:key (str app-name)}
                    [:h1 (str app-name)]
                    [:ul
                     (doall (map (fn [e]
                                   [:li {:key (:id e)} (str e)]) events))]]) events))]))

(def collector-component
  (ui/constructor
   {:renderer          collector-render
    :subscription-deps [:collector-value]}))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(def app-definition
  {:components    {:main (assoc collector-component :topic :Collector)}
   :controllers   {:Collector (->Collector)}
   :subscriptions {:collector-value collector-value-sub}
   :html-element  (.getElementById js/document "app")})

(defonce running-app (clojure.core/atom))

(defn start-app! []
  (reset! running-app (app-state/start! app-definition)))

(defn dev-setup []
  (when debug?
    (enable-console-print!)
    (println "dev mode")
    ))

(defn reload []
  (let [current @running-app]
    (if current
      (app-state/stop!
       current
       (fn []
         (start-app!)
         (app-state/restore-app-db current @running-app)))
      (start-app!))))


(defn ^:export main []
  (dev-setup)
  (start-app!))
