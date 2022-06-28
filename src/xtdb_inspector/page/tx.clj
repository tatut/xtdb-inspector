(ns xtdb-inspector.page.tx
  "Transaction log page"
  (:require [xtdb.api :as xt]
            [ripley.html :as h]
            [xtdb-inspector.ui :as ui]
            [xtdb-inspector.ui.table :as ui.table]
            [ripley.live.source :as source]
            [ripley.live.protocols :as p]))

(defn latest-tx-source [xtdb-node]
  (let [source-atom (atom nil)
        listener (xt/listen xtdb-node {::xt/event-type ::xt/indexed-tx}
                            (fn [{tx-id ::xt/tx-id}]
                              (when-let [source @source-atom]
                                (p/write! source tx-id))))
        [source _ _] (source/source-with-listeners
                      #(::xt/tx-id (xt/latest-completed-tx xtdb-node))
                      #(.close listener))]
    (reset! source-atom source)))

(defn transactions [xtdb-node]
  (source/computed
   #(with-open [log (xt/open-tx-log xtdb-node (max 0 (- % 100)) false)]
      (vec (iterator-seq log)))
   (latest-tx-source xtdb-node)))

(defn tx-table [tx-log-source on-tx-selected]
  (ui.table/table
   {:key ::xt/tx-id
    :columns [{:label "#" :accessor ::xt/tx-id}
              {:label "Timestamp" :accessor ::xt/tx-time
               :render #(h/out! (ui/format-inst %))}
              {:label "Events" :accessor ::xt/tx-events
               :render #(h/out! (count %))}]
    :on-row-click on-tx-selected}
   tx-log-source))

(defn tx-op->maps [[op & data :as tx-op]]
  (case op
    ::xt/put
    (let [[{id :xt/id :as doc}
           valid-time-start valid-time-end] data]
      [{:operation op
        :id id
        :payload doc
        :valid-time-start valid-time-start
        :valid-time-end valid-time-end}])

    ::xt/delete
    (let [[document valid-time-start valid-time-end] data]
      [{:operation op
        :payload document
        :valid-time-start valid-time-start
        :valid-time-end valid-time-end}])

    ::xt/match
    (let [[id valid-time & ops] data]
      (into [{:operation op
              :id id
              :payload "<match>"
              :valid-time-start valid-time
              :valid-time-end valid-time}]
            (tx-op->maps ops)))

    ::xt/evict
    [{:operation op
      :id (first data)}]

    ;; Fallback
    {:operation op
     :payload tx-op}))

(defn- fetch-tx-ops [xtdb-node tx-id]
  (with-open [log (xt/open-tx-log xtdb-node (dec tx-id) true)]
    (::xt/tx-ops (.next log))))

(defn tx-details [xtdb-node tx-details-source]
  (ui.table/table
   {:columns [{:label "Op" :accessor :operation :render #(h/out! (name %))}
              {:label "Document" :accessor :id
               :render #(ui/format-value (constantly true) %)}
              {:label "Payload" :accessor :payload}
              {:label "vt-start" :accessor :valid-time-start}
              {:label "vt-end" :accessor :valid-time-end}]
    :empty-message "Click on transaction to show details"}
   (source/computed
    #(let [{id ::xt/tx-id} %]
       (when id
         (into []
               (mapcat tx-op->maps)
               (fetch-tx-ops xtdb-node (::xt/tx-id %)))))
    tx-details-source)))

(defn render [{:keys [xtdb-node] :as _ctx}]
  (let [[tx-details-source set-tx!] (source/use-state {})]
    (h/html
     [:div.transactions.flex
      [:div {:class "w-2/6"}
       [:h3 "Latest transactions"]
       (tx-table (transactions xtdb-node) set-tx!)]
      [:div {:class "w-4/6"}
       [:h3 "Transaction details"]
       (tx-details xtdb-node tx-details-source)]])))
