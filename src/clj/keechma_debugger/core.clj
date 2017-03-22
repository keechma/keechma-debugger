(ns keechma-debugger.core
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [compojure.core :refer [GET POST defroutes wrap-routes]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [hiccup.core :as h]
            [ring.util.response :as resp]
            [clojure.core.async :refer [go-loop <!]]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(go-loop []
  (when-let [cmd (<! ch-chsk)]
    (let [[ev payload] (:event cmd)]
      (when (= ev :keechma/debugger)
        (doseq [uid (:any @connected-uids)]
          (chsk-send! uid [ev payload]))))
    (recur)))

(defroutes routes
  ;; <other stuff>
  (GET "/debugger" req
       (h/html
        [:head {:charset "utf-8"}
         [:link {:href "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/css/bootstrap.min.css" :rel "stylesheet"}]]
        [:body
         [:div {:id "app"}]
         [:script {:src "js/compiled/app.js"}]
         [:script "keechma_debugger.core.main();"]]))
  ;;; Add these 2 entries: --->
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  )

(def handler
  (wrap-defaults routes site-defaults)) 

