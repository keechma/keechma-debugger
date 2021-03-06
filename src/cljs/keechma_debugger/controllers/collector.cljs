(ns keechma-debugger.controllers.collector
  (:require-macros
   [cljs.core.async.macros :refer [go-loop]])
  (:require
   [keechma.controller     :as controller]
   [taoensso.sente         :as sente :refer (cb-success?)]
   [fipp.edn :as fipp-edn]
   [clojure.string :as str]
   [cljs.core.async        :as async :refer (<! >! put! chan close!)]
   [oops.core :refer [ocall]]))

(def event-store-layout
  {:events {}
   :apps-order []
   :controllers {}
   :apps-status {}})

(defn get-app-events [events app-name]
  (or (get events app-name) []))

(defn build-event [[app-name type direction topic ev-name payload severity]]
  {:id (name (gensym "evid"))
   :type type
   :direction direction
   :topic topic
   :name ev-name
   :payload (str/trim (with-out-str (fipp-edn/pprint payload)))
   :severity severity
   :created-at (.getTime (js/Date.))})

(defn process-start-event [store event]
  (let [[app-name type direction _ name payload _] event]
    (if (and (= :app type)
             (= :in direction)
             (= :start name))
      (assoc store
             :apps-order (conj (:apps-order store) app-name)
             :apps-status (assoc (:apps-status store) app-name :start)
             :controllers (assoc (:controllers store) app-name payload))
      store)))

(defn process-stop-event [store event]
  (let [[app-name type direction _ name _ _] event]
    (if (and (= :app type)
             (= :in direction)
             (= :stop name))
      (assoc store
             :apps-status (assoc (:apps-status store) app-name :stop))
      store)))

(defn add-pause-event [prev-events event]
  (let [[app-name type direction topic ev-name payload severity] event
        prev-event (last prev-events)
        prev-created-at (:created-at prev-event)
        batch-num (inc (count (filter #(= :pause (:type %)) prev-events)))
        now (.getTime (js/Date.))]
    (if (and prev-created-at (> (- now prev-created-at) 2000))
      [(build-event [app-name :pause nil nil [batch-num now] nil :info])
       (build-event event)]
      [(build-event event)])))

(defn process-event [store event]
  (let [[app-name _ _ _ _ _ _] event
        prev-events (get-app-events (:events store) app-name)
        ]
    (assoc-in store [:events app-name]
              (concat prev-events (add-pause-event prev-events event)))))


(defn store-event [app-db-atom event]
  (let [app-db @app-db-atom
        events (or (get-in app-db [:kv :events]) event-store-layout)]
    (swap! app-db-atom
           assoc-in [:kv :events]
           (-> events
               (process-start-event event)
               (process-stop-event event)
               (process-event event)))))

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
