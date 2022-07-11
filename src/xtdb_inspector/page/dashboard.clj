(ns xtdb-inspector.page.dashboard
  "Dashboard page.
  Shows a dashboard that is configured in the database.

  A dashboard is a set of widgets that display query results.
  This can be things like charts, stats, gauges etc.

  Dashboard can update on a timed interval, or after each
  new transaction."
  (:require [ripley.html :as h]
            [xtdb.api :as xt]
            [xtdb-inspector.ui :as ui]
            xtdb.query
            [ripley.live.source :as source]
            [xtdb-inspector.ui.chart :as ui.chart]
            [xtdb-inspector.page.query :as page.query]
            [clojure.tools.logging :as log]))

(defmulti render-widget
  "Render a widget's HTML.
  Receives the widget configuration map along with
  the current :query-results.

  The query results is a live source that updates based
  on the dashboard update interval.
  "
  :type)

(defn- format-number
  "Format number with at most single digit accuracy in US locale.
  (CSS needs decimal separator to be \".\")"
  [num]
  (let [f (doto (java.text.NumberFormat/getInstance java.util.Locale/US)
            (.setMaximumFractionDigits 1))]
    (.format f num)))

(defmethod render-widget :stat
  [{:keys [description query-results]}]
  (h/html
   [:div.stat
    [::h/live query-results
     #(let [v (some->> % ffirst format-number)]
        (h/html [:div.stat-value v]))]
    [::h/when description
     [:div.stat-desc description]]]))

(defmethod render-widget :radial-progress
  [{:keys [query-results]}]
  (h/html
   ;; <div class="radial-progress" style="--value:70;">70%</div>
   [:span
    [::h/live query-results
     #(let [value (some->> % ffirst format-number)
            style (format "--value:%s;" value)]
        (h/html
         [:div.radial-progress {:style style} value "%"]))]]))

(defmethod render-widget :pie
  [{:keys [query-results] :as w}]
  (ui.chart/pie
   (merge
    {:label-accessor first
     :value-accessor second}
    (select-keys w [:width :height :value-accessor :label-accessor
                    :max-items :other-label]))

   query-results))

(defmethod render-widget :query
  [{:keys [db query-results query]}]
  (page.query/query-results-table
   db
   (page.query/unpack-find-defs query)
   query-results))


(defn- widget-container
  [{:keys [label col-span db] :as widget
    :or {col-span 1}} widget-source]
  (let [class (str "card bg-base-100 shadow-xl "
                   (case col-span
                     1 ""
                     2 "col-span-2"
                     3 "col-span-3"
                     4 "col-span-4"))]
    (h/html
     [:div {:class class}
      [:div.card-body
       [:div.card-title label]
       (render-widget (assoc widget
                             :db db
                             :query-results
                             (source/computed :query-results widget-source)))]])))

(defn- update-widgets [xtdb-node widgets state]
  (let [db (xt/db xtdb-node)]
    (doseq [{:keys [id query query-params]} widgets
            :let [params (when query-params
                           (try
                             (eval query-params)
                             (catch Throwable t
                               (log/warn t "Query parameter evaluation threw exception")
                               nil)))]]
      (swap! state assoc-in [:widgets id :query-results]
             (apply xt/q db query params)))))

(defn render [{:keys [xtdb-node request]}]
  (let [dashboard-name (get-in request [:route-params :dashboard])
        dashboard (ffirst
                   (xt/q (xt/db xtdb-node)
                         '[:find (pull d [*])
                           :where [d :xtdb-inspector.dashboard/name name]
                           :in name]
                         dashboard-name))
        state (atom {:widgets {}})
        widget-source (fn [{id :id}]
                        (source/computed
                         #(get-in % [:widgets id])
                         state))
        configured-widgets (:widgets (:xtdb-inspector.dashboard/config dashboard))
        db (xt/db xtdb-node)]
    (future
      (update-widgets xtdb-node configured-widgets state))
    (h/html
     [:div.grid.grid-cols-4.gap-2
      [::h/for [w configured-widgets]
       ;; PENDING: when source updates, widget should use later db as well
       (widget-container (assoc w :db db) (widget-source w))]])))

(defn render-listing [{:keys [xtdb-node]}]
  (let [dashboards (xt/q (xt/db xtdb-node)
                         '{:find [(pull d [:xtdb-inspector.dashboard/name
                                           :xtdb-inspector.dashboard/description])]
                           :where [[d :xtdb-inspector.dashboard/name]]})]
    (h/html
     [:div "Available dashboards:"
      [:ul
       [::h/for [[{:xtdb-inspector.dashboard/keys [name description]}] dashboards]
        [:li (ui/link (str "/dashboard/" name) name) " " description]]
       [::h/when (empty? dashboards)
        [:div.alert.alert-warning
         "No dashboards defined."]]]])))
