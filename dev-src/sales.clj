(ns sales
  "Load fictional US sales data database.
  This is for testing the dashboards.

  Download the file: https://data.world/dataman-udit/us-regional-sales-data"
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [xtdb.api :as xt]))

(defn- ->id [key]
  (fn [id] {key (if (number? id)
                  (long id)
                  id)}))

(def import-config
  {"Sales Orders Sheet"
   {:columns
    {:A :xt/id
     :B :order/channel
     :C :order/warehouse
     :D :order/procure-date
     :E :order/order-date
     :F :order/ship-date
     :G :order/delivery-date
     :H :order/currency
     :I :order/sales-team
     :J :order/customer
     :K :order/store
     :L :order/product
     :M :order/quantity
     :N :order/discount
     :O :order/unit-price
     :P :order/unit-cost}
    :process
    {:xt/id (->id :order)
     :order/product (->id :product)
     :order/sales-team (->id :sales-team)
     :order/customer (->id :customer)
     :order/store (->id :store)}}

   "Customers Sheet"
   {:columns
    {:A :xt/id
     :B :customer/name}
    :process
    {:xt/id (->id :customer)}}

   "Store Locations Sheet"
   {:columns
    {:A :xt/id
     :B :store/city
     :C :store/county
     :E :store/state
     :G :store/lat
     :H :store/lng}
    :process
    {:xt/id (->id :store)}}

   "Products Sheet"
   {:columns
    {:A :xt/id
     :B :product/name}
    :process {:xt/id (->id :product)}}

   "Sales Team Sheet"
   {:columns
    {:A :xt/id
     :B :sales-team/name}
    :process
    {:xt/id (->id :sales-team)}}})

(defn load-sales-data [xtdb-node file]
  (let [wb (sheet/load-workbook-from-file file)]
    (doseq [s (sheet/sheet-seq wb)
            :let [{:keys [columns process]} (import-config (sheet/sheet-name s))]
            :when columns]
      (let [data (mapv (fn [row]
                         (reduce-kv (fn [row field process]
                                      (update row field process))
                                    row process))
                       (drop 1 (sheet/select-columns columns s)))]
        (xt/submit-tx xtdb-node
                      (for [d data]
                        [::xt/put d]))))))

(defn add-dashboard [xtdb-node]
  (xt/submit-tx
   xtdb-node
   [[::xt/put {:xt/id "sales-dashboard"
               :xtdb-inspector.dashboard/name "sales"
               :xtdb-inspector.dashboard/description "Sales dashboard"
               :xtdb-inspector.dashboard/config
               {:update-duration :manual
                :widgets
                [{:id :top-products
                  :label "Top sellers"
                  :col-span 2
                  :type :pie
                  :query '{:find [pn (sum (* qnt up))]
                           :where [[o :order/product p]
                                   [p :product/name pn]
                                   [o :order/quantity qnt]
                                   [o :order/unit-price up]]
                           :group-by [pn]}}
                 {:id :top-states
                  :type :pie
                  :col-span 2
                  :label "Top states"
                  :query '{:find [state (sum (* quantity price))]
                           :where [[o :order/quantity quantity]
                                   [o :order/unit-price price]
                                   [o :order/store store]
                                   [store :store/state state]]}}
                 {:id :quarterly-sales
                  :label "Quarterly sales"
                  :type :bars
                  :col-span 2
                  :query '{:find [quarter (sum (* quantity price))]
                           :where [[o :order/order-date date]
                                   [o :order/quantity quantity]
                                   [o :order/unit-price price]
                                   [(sales/quarter date) quarter]]
                           :group-by [quarter]
                           :order-by [[quarter :desc]]}}
                 {:id :quarterly-sales-by-state
                  :label "Quarterly sales by state"
                  :type :query
                  :col-span 2
                  :query '{:find [state quarter (sum (* quantity price))]
                           :where [[o :order/order-date date]
                                   [o :order/store store]
                                   [store :store/state state]
                                   [(sales/quarter date) quarter]
                                   [o :order/quantity quantity]
                                   [o :order/unit-price price]]
                           :group-by [state quarter]
                           :order-by [[quarter :desc] [(sum (* quantity price)) :desc]]}}]}}]]))

(defn quarter [date]
  (let [y (+ 1900 (.getYear date))
        m (inc (.getMonth date))]
    (format "%d Q%d"
            y
            (case m
              (1 2 3) 1
              (4 5 6) 2
              (7 8 9) 3
              (10 11 12) 4))))

(comment
  (load-sales-data @user/xtdb (str (System/getenv "HOME")
                                   "/Downloads/US_Regional_Sales_data.xlsx"))
  (add-dashboard @user/xtdb))
