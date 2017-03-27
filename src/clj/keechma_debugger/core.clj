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

(def json-render-css "
.jh-root, .jh-type-object, .jh-type-array, .jh-key, .jh-value, .jh-root tr{
 -webkit-box-sizing: border-box; /* Safari/Chrome, other WebKit */
 -moz-box-sizing: border-box;    /* Firefox, other Gecko */
 box-sizing: border-box;         /* Opera/IE 8+ */
}

.jh-key, .jh-value{
 margin: 0;
 padding: 0.2em;
}

.jh-empty-collection:before {
  content: '[]';
}

.jh-empty-map:before {
  content: '{}';
}

.jh-empty-set:before {
  content: '#{}';
}

.jh-empty-string:before {
  content: '\"\"';
}

.jh-value{
    border-left: 1px solid #ddd;
}

.jh-type-bool, .jh-type-number{
    font-weight: bold;
    text-align: center;
    color: #5286BC;
}

.jh-type-string{
    font-style: italic;
    color: #839B00;
}

.jh-type-date{
    font-style: italic;
    color: #839C00;
}

.jh-array-key{
    font-style: italic;
    font-size: small;
    text-align: center;
}

.jh-object-key, .jh-array-key{
    color: #444;
    vertical-align: top;
}

.jh-type-object > tbody > tr:nth-child(odd), .jh-type-array > tbody > tr:nth-child(odd){
    background-color: #f5f5f5;
}

.jh-type-object > tbody > tr:nth-child(even), .jh-type-array > tbody > tr:nth-child(even){
    background-color: #fff;
}

.jh-type-object, .jh-type-array{
    width: 100%;
    border-collapse: collapse;
}

.jh-root{
 border: 1px solid #ccc;
 margin: 0.2em;
}

th.jh-key{
 text-align: left;
}

.jh-type-object > tbody > tr, .jh-type-array > tr{
 border: 1px solid #ddd;
 border-bottom: none;
}

.jh-type-object > tbody > tr:last-child, .jh-type-array > tbody > tr:last-child{
 border-bottom: 1px solid #ddd;
}

.jh-type-object > tbody > tr:hover, .jh-type-array > tbody > tr:hover{
 border: 1px solid #F99927;
}

.jh-empty{
 font-style: italic;
 color: #999;
 font-size: small;
}
")

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
         [:title "Keechma Debugger"]
         [:style {:type "text/css"} json-render-css]
         [:script {:src"https://use.fontawesome.com/25b123c46b.js"}]]
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

