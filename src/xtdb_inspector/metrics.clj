(ns xtdb-inspector.metrics
  "Metrics reporting UI"
  (:require [xtdb.metrics :as metrics]
            [xtdb.system :as sys])
  (:import (com.codahale.metrics MetricRegistry Gauge Meter)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit
                                 Future)))

(set! *warn-on-reflection* true)

(def metrics (atom {}))

(def ^:private executor (Executors/newScheduledThreadPool 1))

(defn- report [^MetricRegistry registry]
  {:gauges
   (into {}
         (let [g (.getGauges registry)]
           (for [k (keys g)
                 :let [v (.getValue ^Gauge (get g k))]]
             [k v])))
   :meters
   (into {}
         (let [ms (.getMeters registry)]
           (for [k (keys ms)
                 :let [^Meter m (get ms k)]]
             [k {:min1 (.getOneMinuteRate m)
                 :min5 (.getFiveMinuteRate m)
                 :min15 (.getFifteenMinuteRate m)
                 :mean (.getMeanRate m)
                 :count (.getCount m)}])))})

(defn ->reporter {::sys/deps {:registry ::metrics/registry
                              :metrics ::metrics/metrics}
                  :sys/args {}}
  [{reg :registry}]
  (let [^Future task
        (.scheduleAtFixedRate ^ScheduledExecutorService executor
                              #(reset! metrics (report reg))
                              1 1 TimeUnit/SECONDS)]
    (reify java.lang.AutoCloseable
      (close [_]
        (.cancel task true)
        (reset! metrics {})))))
