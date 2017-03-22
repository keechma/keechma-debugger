(ns keechma-debugger.controllers
  (:require [keechma-debugger.controllers.collector :as collector]))

(def controllers
  (-> {:collector (collector/->Collector)}))
