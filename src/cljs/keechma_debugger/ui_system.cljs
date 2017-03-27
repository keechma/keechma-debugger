(ns keechma-debugger.ui-system
  (:require [keechma-debugger.components.main :as main]
            [keechma-debugger.components.event :as event]))

(def system
  {:main main/component
   :event event/component})
