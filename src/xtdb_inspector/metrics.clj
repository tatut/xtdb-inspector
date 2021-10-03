(ns xtdb-inspector.metrics
  "Metrics reporting UI"
  (:require [xtdb.metrics :as metrics]
            [xtdb.system :as sys]
            [ripley.html :as h]
            [ripley.live.source :as source])
  (:import (com.codahale.metrics MetricRegistry Gauge Meter)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit
                                 Future)))

(set! *warn-on-reflection* true)

(def metrics (atom {}))

(def ^:private executor (Executors/newScheduledThreadPool 1))

(defn- report [^MetricRegistry registry]
  {:updated (java.util.Date.)
   :gauges
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


;;;;;;;;;;;
;; UI for showing metrics, will listen to the atom
;; and update all metrics live to connected clients.
;;

(def idx-meters-to-render
  [["bytes" "xtdb.index-store.indexed-bytes"]
   ["attr vals" "xtdb.index-store.indexed-avs"]
   ["docs" "xtdb.index-store.indexed-docs"]])

(defn render-meters [title meters-to-render meter-values]
  (letfn [(fmt [n]
            (h/dyn! (format "%.1f" n)))]
    (h/html
     [:div.meters
      [:table.w-full.text-right
       [:tr.bg-gray-300
        [:td title]
        [:td "1m"]
        [:td "5m"]
        [:td "15m"]
        [:td "cnt"]]
       [::h/for [[label meter-name] meters-to-render
                 :let [m (get meter-values meter-name)
                       c (str (:count m))]]
        [:tr
         [:td label]
         [:td (fmt (:min1 m))]
         [:td (fmt (:min5 m))]
         [:td (fmt (:min15 m))]
         [:td.font-semibold c]]]]])))


(defn metrics-ui [ctx]
  (let [ms (source/source metrics)]
    (h/html
     [:div.metrics.flex-col.bg-gray-50.rounded-md.border-2.border-black.m-3.p-3.text-sm
      [:div
       [::h/live (source/c= (:meters %ms))
        (partial render-meters "Indexed" idx-meters-to-render)]

       [:div {:class "px-4 py-2 mt-4 text-lg text-gray-900 bg-gray-200 rounded-lg sm:mt-0 hover:text-gray-900 focus:text-gray-900 hover:bg-gray-300"}
        [::h/live (source/c= (get-in %ms [:gauges "xtdb.query.currently-running"]))
         #(h/html [:span
                   {:class "badge mb-3 bg-red-800 rounded-full px-2 py-1 text-center object-right-top text-white text-sm mr-1"} %])]
        "running queries"]]])))
