(ns keechma-debugger.core
  (:require-macros
   [reagent.ratom :refer [reaction]])
  (:require
   [reagent.core :as reagent]
   [keechma.app-state :as app-state]
   [keechma-debugger.controllers :refer [controllers]]
   [keechma-debugger.subscriptions :refer [subscriptions]]
   [keechma-debugger.ui-system :as ui-system]
   [keechma-debugger.stylesheets.core :refer [stylesheet]]
   [keechma.toolbox.css.core :refer [update-page-css]]))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce debug?
  ^boolean js/goog.DEBUG)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(def app-definition
  {:components    ui-system/system 
   :controllers   controllers
   :subscriptions subscriptions
   :html-element  (.getElementById js/document "app")})

(defonce running-app (clojure.core/atom))

(defn start-app! []
  (reset! running-app (app-state/start! app-definition))
  (update-page-css (stylesheet)))

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
