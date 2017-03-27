(ns keechma-debugger.controllers
  (:require [keechma-debugger.controllers.collector :as collector]
            [keechma-debugger.controllers.event :as event]))

(def controllers
  (-> {:collector (collector/->Collector)
       :event event/controller}))
