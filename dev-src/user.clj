(ns user
  (:require [xtdb.api :as xt]
            [xtdb-inspector.core :as inspector]))

(defonce xtdb (atom nil))
(defonce server (atom nil))

(defn start []
  (println "Starting dev instance with in-memory XTDB in port 3000")
  (swap! xtdb #(or % (xt/start-node {})))
  (swap! server
         (fn [old-server]
           (when old-server
             (old-server))
           (inspector/start {:xtdb-node @xtdb
                             :port 3000}))))

(defn some-docs []
  ;; insert some docs for testing
  (xt/submit-tx
   @xtdb
   [[::xt/put {:xt/id :hello :greeting "hello xtdb inspector world!"
               :number 420.69M :link "over-there"}]
    [::xt/put {:xt/id "over-there"
               :message "you are now over here!"
               :number 666M
               :go-deeper {:foo :bar}}]
    [::xt/put {:xt/id {:foo :bar}
               :message "even deeper now"
               :number 3.1415M
               :back-to-start :hello}]]))
