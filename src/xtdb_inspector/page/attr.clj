(ns xtdb-inspector.page.attr
  "Page to show attributes and browse their values."
  (:require [xtdb.api :as xt]
            [ripley.html :as h]
            [ripley.js :as js]
            [xtdb-inspector.ui :as ui]
            [xtdb-inspector.ui.table :as ui.table]
            [ripley.live.source :as source]
            [ripley.integration.xtdb :as rx]
            [xtdb-inspector.id :as id]))

(defn- request-attr [request]
  (let [{kw' :keyword
         ns' :namespace} (:params request)]
    (when kw'
      (keyword ns' kw'))))

(defn- render-attr-values [{:keys [xtdb-node]} attr]
  (let [values (rx/q {:node xtdb-node}
                     {:find '[e v]
                      :where [['e attr 'v]]
                      :limit 1001})]
    (h/html
     [:div
      [:table
       [:thead
        [:tr
         [:td "Document"]
         [:td "Value"]]]
       [::h/live values
        (fn [attr-values]
          (with-open [db (xt/open-db xtdb-node)]
            (h/html
             [:tbody
              [::h/for [[e v] (take 1000 attr-values)]
               [:tr
                [:td (ui/format-value (constantly true) e)]
                [:td (ui/format-value (partial id/valid-id? db) v)]]]])))]]
      [::h/live (source/c= (count %values))
       (fn [c]
         (h/html
          [:div
           [::h/when (> c 1000)
            [:div.text-xs "Query limited to 1000 items"]]]))]])))

(defn- render-attr-listing [{:keys [xtdb-node]}]
  (ui.table/table
   {:columns [{:label "Attribute" :accessor first
               :render #(ui/link (str "/attr/" (subs (str %) 1))
                                 (pr-str %))}
              {:label "Values count" :accessor second}]
    :key first
    :order [first :asc]}
   (future
     (xt/attribute-stats xtdb-node))))

(defn render [{:keys [xtdb-node request] :as ctx}]
  (if-let [attr (request-attr request)]
    (render-attr-values ctx attr)
    (render-attr-listing ctx)))
