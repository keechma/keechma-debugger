(ns keechma-debugger.util)

(defn index-of [coll item]
 (loop [c coll 
        idx 0]
   (if-let [first-item (first c)]
     (if (= first-item item) 
       idx
       (recur (rest c) (inc idx)))
     nil)))
