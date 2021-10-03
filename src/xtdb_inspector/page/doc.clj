(ns xtdb-inspector.page.doc
  "Page that displays a single document."
  (:require [ripley.html :as h]
            [xtdb-inspector.id :as id]
            [xtdb.api :as xt]
            [xtdb-inspector.page :as page]))

(defn render [{:keys [xtdb-node request] :as ctx}]
  ;;(println "REQ: " (pr-str req))
  (let [id (some-> request :params :doc-id id/read-doc-id)
        db (xt/db xtdb-node)
        entity (xt/entity db id)
        id-str (pr-str id)]
    (h/html
     [:div
      [:h3 id-str]
      [:table
       [::h/for [[k v] (dissoc entity :xt/id)
                 :let [key-name (pr-str k)
                       value (pr-str v)
                       link (when (id/valid-id? db v)
                              (str "/doc/" (id/doc-id-param v)))]]
        [:tr
         [:td.px-2.py-2.font-semibold key-name]
         [:td.px-2.py-2
          [::h/if link
           [:a.underline.bg-blue-200 {:href link} value]
           value]]]]]])))
