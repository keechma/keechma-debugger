(ns keechma-debugger.controllers.event
  (:require [keechma.toolbox.pipeline.core :as pp :refer-macros [pipeline!]]
            [keechma.toolbox.pipeline.controller :as controller]))

(def controller
  (controller/constructor
   (fn [_] true)
   {:row-dimensions (pipeline! [value app-db]
                      (pp/commit! (assoc-in app-db [:kv :row-dimensions (:id value)] (:dimensions value))))}))
