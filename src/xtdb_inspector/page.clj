(ns xtdb-inspector.page
  "Main page template that sets up styles and app bar"
  (:require [ripley.html :as h]
            [xtdb-inspector.metrics :as metrics]))

(defn render-page [ctx page-content-fn]
  (h/out! "<!DOCTYPE html>\n")
  (h/html
   [:html
    [:head
     [:link {:rel "stylesheet" :href "/xtdb-inspector.css"}]
     (h/live-client-script "/__ripley-live")]
    [:body
     [:div.flex
      [:div.flex-grow
       (page-content-fn)]
      [:div {:class "w-3/12"}
       (metrics/metrics-ui ctx)]]]]))

(defn page-response [ctx page-content-fn]
  (h/render-response
   #(render-page ctx page-content-fn)))
