(defproject keechma-debugger "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [ring "1.6.0-RC1"]
                 [ring/ring-defaults "0.2.3"]
                 [compojure "1.5.2"]
                 [com.taoensso/sente "1.11.0"]
                 [reagent "0.6.0"]
                 [hiccup "1.0.5"]
                 [keechma "0.2.0-SNAPSHOT-10" :exclusions [cljsjs/react-with-addons]]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]

  :plugins [[lein-cljsbuild "1.1.4"]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "target"]

  :figwheel {:css-dirs ["resources/public/css"]
             :reload-clj-files {:clj true :cljc true}
             :server-port 1337
             :ring-handler keechma-debugger.core/handler}

  :profiles
  {:dev
   {:dependencies []

    :plugins      [[lein-figwheel "0.5.8"]]
    }}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     {:on-jsload "keechma-debugger.core/reload"}
     :compiler     {:main                 keechma-debugger.core
                    :optimizations        :none
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/dev"
                    :asset-path           "js/compiled/dev"
                    :source-map-timestamp true}}

    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            keechma-debugger.core
                    :optimizations   :advanced
                    :output-to       "resources/public/js/compiled/app.js"
                    :output-dir      "resources/public/js/compiled/min"
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}

    ]})
