(ns xtdb-inspector.core
  "Core XTDB inspector ns, start new inspector server or
  get routes to integrate into an existing ring app."
  (:require [org.httpkit.server :as http-kit]
            [compojure.core :refer [routes GET]]
            [compojure.route :as route]
            [xtdb-inspector.page :as page]
            [xtdb-inspector.page.doc :as page.doc]
            [xtdb-inspector.page.query :as page.query]
            [xtdb-inspector.page.attr :as page.attr]
            [xtdb-inspector.page.tx :as page.tx]
            [ripley.live.context :as context]))


(defn- page [ctx req page-fn]
  (let [ctx (assoc ctx :request req)]
    (page/page-response
     ctx
     #(page-fn ctx))))

(defn inspector-handler [xtdb-node]
  (let [ctx {:xtdb-node xtdb-node}]
    (routes
     (context/connection-handler "/__ripley-live" :ping-interval 45)
     (GET "/doc" req
          (page ctx req #'page.doc/render-form))
     (GET "/doc/:doc-id" req
          (page ctx req #'page.doc/render))
     (GET "/query" req
          (page ctx req #'page.query/render))
     (GET "/query/:query" req
          (page ctx req #'page.query/render))
     (GET "/attr" req
          (page ctx req #'page.attr/render))
     (GET "/attr/:keyword" req
          (page ctx req #'page.attr/render))
     (GET "/attr/:namespace/:keyword" req
          (page ctx req #'page.attr/render))
     (GET "/tx" req
          (page ctx req #'page.tx/render))
     (route/resources "/"))))


(defn start [{:keys [port xtdb-node]
              :or {port 3000}}]
  {:pre [(some? xtdb-node)]}
  (http-kit/run-server
   (inspector-handler xtdb-node)
   {:port port}))
