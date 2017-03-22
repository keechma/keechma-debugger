(ns keechma-debugger.util.json-renderer
  (:require [clojure.string :as str]))

;;; Created with https://github.com/yogthos/json-html

(defn escape-html [s]
  (str/escape s
          {"&"  "&amp;"
           ">"  "&gt;"
           "<"  "&lt;"
           "\"" "&quot;"}))

(defn render-keyword [k]
  (->> k ((juxt namespace name)) (remove nil?) (str/join "/")))

(defn str-compare [k1 k2]
  (compare (str k1) (str k2)))

(defn sort-map [m]
  (try
    (into (sorted-map) m)
    (catch js/Error _
      (into (sorted-map-by str-compare) m))))

(defn sort-set [s]
  (try
    (into (sorted-set) s)
    (catch js/Error _
      (into (sorted-set-by str-compare) s))))

(declare render)

(defn render-collection [col]
  (if (empty? col)
    [:div.jh-type-object [:span.jh-empty-collection]]
    [:table.jh-type-object
     [:tbody
      (for [[i v] (map-indexed vector col)]
        ^{:key i}[:tr [:th.jh-key.jh-array-key i]
                  [:td.jh-value.jh-array-value (render v)]])]]))

(defn render-set [s]
  (if (empty? s)
    [:div.jh-type-set [:span.jh-empty-set]]
    [:ul (for [item (sort-set s)] [:li.jh-value (render item)])]))



(defn render-map [m]
  (if (empty? m)
    [:div.jh-type-object [:span.jh-empty-map]]
    [:table.jh-type-object
     [:tbody
      (for [[k v] (sort-map m)]
        ^{:key k}[:tr [:th.jh-key.jh-object-key (render k)]
                  [:td.jh-value.jh-object-value (render v)]])]]))

(defn render-string [s]
  [:span.jh-type-string
   (if (str/blank? s)
     [:span.jh-empty-string {:dangerouslyInsertHTML {:__html (escape-html s)}}])])

(defn render [v]
    (let [t (type v)]
      (cond
       (= t Keyword) [:span.jh-type-string (render-keyword v)]
       (= t Symbol) [:span.jh-type-string (str v)]
       (= t js/String) [:span.jh-type-string (escape-html v)]
       (= t js/Date) [:span.jh-type-date (.toString v)]
       (= t js/Boolean) [:span.jh-type-bool (str v)]
       (= t js/Number) [:span.jh-type-number v]
       (satisfies? IMap v) (render-map v)
       (satisfies? ISet v) (render-set v)
       (satisfies? ICollection v) (render-collection v)
       nil [:span.jh-empty nil])))
