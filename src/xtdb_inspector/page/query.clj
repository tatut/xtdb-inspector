(ns xtdb-inspector.page.query
  "Edit and run a query"
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.js :as js]
            [xtdb.api :as xt]
            [xtdb-inspector.ui :as ui]
            [ripley.integration.xtdb :as rx]
            [ripley.live.protocols :as p]
            xtdb.query
            [xtdb-inspector.id :as id]
            [clojure.string :as str]
            [xtdb-inspector.ui.tabs :as ui.tabs]
            [xtdb-inspector.ui.table :as ui.table]
            [xtdb-inspector.ui.chart :as ui.chart]))

(def last-query (atom "{:find []\n :where []\n :limit 100}"))

(def codemirror-js
  ["https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.63.1/codemirror.min.js"
   "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.63.1/mode/clojure/clojure.min.js"])

(def codemirror-css "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.63.1/codemirror.min.css")

(defn safe-read [x]
  (try
    (binding [*read-eval* false]
      (let [item (read-string x)]
        (if-not (map? item)
          {:error (str "Expected map query definition. Got: " (type item))}
          {:q item})))
    (catch Throwable t
      {:error (.getMessage t)})))

(defn query-result-and-count-sources [db q on-error!]
  (let [[results set-results!] (source/use-state [])
        result-count (atom 0)]
    (future
      (try
        (with-open [res (xt/open-q db q)]
          (doseq [p (->> res iterator-seq (partition-all 100))]
            (swap! result-count + (count p))
            (set-results! (into (p/current-value results) p))))
        (catch Throwable t
          (on-error! (str "Error in query: " (.getMessage t))))))
    [results result-count]))

(defn- query! [xtdb-node set-state! query-str]
  (reset! last-query query-str)
  (let [{:keys [q error]} (safe-read query-str)]
    (if error
      (set-state! {:error? true
                   :error-message error
                   :running? false
                   :results nil})
      (do
        (set-state! {:running? true
                     :error? false
                     :results nil})
        (try
          (let [db (xt/db xtdb-node)]
            (set-state! {:running? false
                         :error? false
                         :results (query-result-and-count-sources
                                   db q
                                   #(set-state! {:error? true
                                                 :error-message %
                                                 :running? false
                                                 :results nil}))
                         :query q
                         :timing (System/nanoTime)
                         :basis (xt/db-basis db)}))
          (catch Throwable e
            (set-state! {:error? true
                         :error-message (str "Error in query: " (.getMessage e))
                         :running? false
                         :results nil})))))))

(defn- loading [label]
  (h/html
   [:div.flex
    [:svg.animate-spin.-ml-1.mr-3.h-5.w-5.text-black
     {:xmlns "http://www.w3.org/2000/svg"
      :fill "none"
      :viewBox "0 0 24 24"}
     [:circle.opacity-25 {:cx 12 :cy 12 :r 10 :stroke "currentColor"
                          :stroke-width 4}]
     [:path.opacity-75
      {:fill "currentColor"
       :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
    label]))

(defn unpack-find-defs
  "Unpack the :find definitions into header name and accessor patterns.
  This separates keywords specified in pull patterns to own columns
  in the result table.

  Also determines which vars are ids based on query plan."
  [db query]
  (let [id? (into #{}
                  (comp
                   (filter #(= (:attr (second %)) :crux.db/id))
                   (map first))
                  (:var->bindings (xtdb.query/query-plan-for db query)))
        column-accessors
        (or (some->> query :keys (map keyword))
            (range))]
    (mapcat
     (fn [header column-accessor]
       (let [column-name (when (not (number? column-accessor))
                           (name column-accessor))]
         (if (and (coll? header)
                  (= 'pull (first header))
                  (every? #(not= '* %) (nth header 2)))
           ;; pull pattern that has no star, generate column for each
           (for [k (nth header 2)]
             {:label (str (when column-name
                            (str column-name ": ")) (name k))
              :accessor #(get-in % [column-accessor k])})

           ;; any other result
           [{:label (or column-name (pr-str header))
             :accessor #(get-in % [column-accessor])
             :id? (when (id? header) true)}])))
     (:find query) column-accessors)))

(defn- bar-chartable? [find-defs]
  (and (= 2 (count find-defs))
       (some #(str/starts-with? (:label %) "(count ") find-defs)))


(defn- duration [ms]
  (cond
    (> ms 1000)
    (format "%.2fs" (/ ms 1000.0))

    (> ms 100)
    (format "%.0fms" ms)

    :else
    (format "%.2fms" ms)))

(defn render-results [xtdb-node {:keys [basis running? results query timing] :as r}]
  (cond
    ;; Query is running
    running?
    (loading "Querying...")

    ;; No query has been made yet and no query is running
    (nil? results)
    (h/html [:span])

    ;; Query has been run and results are available
    :else
    (let [[result-source count-source] results
          db (xt/db xtdb-node)
          headers (unpack-find-defs db query)]
      (h/html
       [:div
        [:div.text-sm
         [::h/live count-source
          #(h/html [:span
                    (h/dyn! %)
                    " results in "
                    (h/dyn! (duration (/ (- (System/nanoTime) timing) 1e6)))])]]
        (ui.tabs/tabs
         {:label "Table"
          :render
          (fn []
            (h/html
             (ui.table/table
              {:key identity
               ;; Set render method that uses format value
               :columns headers}
              result-source)))}
         (when (bar-chartable? headers)
           {:label "Bar chart"
            :render
            (fn []
              (let [{[label-header] false
                     [value-header] true}
                    (group-by #(str/starts-with? (:label %) "(count ")
                                          headers)
                    value-accessor (:accessor value-header)
                    label-accessor (:accessor label-header)]
                (h/html
                 [:div
                  (ui.chart/bar-chart
                   {:width 500
                    :bar-height 30
                    :value-accessor value-accessor
                    :label-accessor label-accessor}
                   result-source)])))}))]))))

(defn saved-queries [db]
  (xt/q db '{:find [?e ?n]
             :keys [id name]
             :where [[?e :xtdb-inspector.saved-query/name ?n]]}))

(defn save-query! [xtdb-node name query]
  (when (and (not (str/blank? name))
             (not (str/blank? query)))
    (let [existing-query-id (ffirst
                             (xt/q (xt/db xtdb-node)
                                   '{:find [?q]
                                     :where [[?q :xtdb-inspector.saved-query/name ?n]]
                                     :in [?n]}
                                   name))]
      (xt/submit-tx
       xtdb-node
       [[::xt/put {:xt/id (or existing-query-id (java.util.UUID/randomUUID))
                   :xtdb-inspector.saved-query/name name
                   :xtdb-inspector.saved-query/query query}]]))))

(defn saved-queries-ui [xtdb-node]
  (let [[query load-query!] (source/use-state nil)
        query-source (source/computed
                      #(when-let [q (some->> %
                                             (java.util.UUID/fromString)
                                             (xt/entity (xt/db xtdb-node)))]
                         (str "editor.getDoc().setValue("
                              (pr-str (:xtdb-inspector.saved-query/query q))
                              ")"))
                      query)]
    (h/html
     [:div
      (js/eval-js-from-source query-source)

      [:div.flex.flex-row
       [:div.form-control
        [:div.input-group.input-group-md
         [:input#save-query-as.input.input-bordered.input-md {:placeholder "Save query as"}]
         [:button.btn.btn-square.btn-md
          {:on-click (js/js (partial save-query! xtdb-node)
                            (js/input-value "save-query-as")
                            "editor.getDoc().getValue()")}
          "Save"]]]
       [:div.divider.divider-horizontal]
       [::h/live (rx/q {:node xtdb-node :should-update? (constantly true)} saved-queries)
        (fn [queries]
          (h/html
           [:select.select.select-bordered.w-full.max-w-xs
            {:name "saved-query"
             :on-change (js/js load-query! js/change-value)}
            [:option {:disabled true :selected true} "Load saved query"]
            [::h/for [{:keys [id name]} queries
                      :let [id (str id)]]
             [:option {:value id} name]]]))]]])))

(defn- saved-query-by-name [db name]
  (ffirst
   (xt/q db '{:find [q]
              :where [[query :xtdb-inspector.saved-query/name name]
                      [query :xtdb-inspector.saved-query/query q]]
              :in [name]}
         name)))


(defn render [{:keys [xtdb-node request]}]
  (let [query-text (or
                    (some->> request :params :query
                             (saved-query-by-name (xt/db xtdb-node)))
                    @last-query)
        [state set-state!] (source/use-state {:query nil
                                              :running? false
                                              :results nil
                                              :error? false})
        query! (partial query! xtdb-node set-state!)]
    (h/html
     [:div
      [::h/for [js codemirror-js]
       [:script {:src js}]]
      [:link {:rel "stylesheet" :href codemirror-css}]
      [:h2 "Query"]
      [:div.unreset
       [:textarea#query query-text]]
      [:script
       (h/out!
        "var editor = CodeMirror.fromTextArea(document.getElementById('query'), {"
        "lineNumbers: true,"
        "autoCloseBrackets: true,"
        "matchBrackets: true,"
        "mode: 'text/x-clojure'"
        "})")]
      (saved-queries-ui xtdb-node)
      [::h/live (source/c= (select-keys %state [:error? :error-message]))
       (fn [{:keys [error? error-message] :as f}]
         (h/html
          [:span
           [::h/if error?
            [:div.bg-red-300.border-2 error-message]
            [:span]]]))]
      [:div.flex
       [:button.btn.btn-primary
        {:on-click (js/js query!
                          "editor.getDoc().getValue()"
                          ""
                          )}
        "Run query"]]

      [:div.divider.divider-vertical]

      [::h/live (source/c= (select-keys %state
                                        [:basis :running? :results :query :timing]))
       (partial render-results xtdb-node)]])))
