(ns xtdb-inspector.page
  "Main page template that sets up styles and app bar"
  (:require [ripley.html :as h]))

(defn render-page [ctx page-content-fn]
  (h/out! "<!DOCTYPE html>\n")
  (h/html
   [:html
    [:head
     [:link {:rel "stylesheet" :href "/xtdb-inspector.css"}]
     (h/live-client-script "/__ripley-live")]
    [:body
     (page-content-fn)]]))

(defn page-response [ctx page-content-fn]
  (h/render-response
   #(render-page ctx page-content-fn)))
