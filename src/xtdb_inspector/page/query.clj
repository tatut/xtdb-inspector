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
            [xtdb-inspector.ui.chart :as ui.chart]
            [clojure.set :as set]
            [ring.util.io :as ring-io]
            [clojure.java.io :as io]))

(def last-query (atom {:query-text "{:find []\n :where []\n :limit 100}"
                       :in-args {}}))

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

(defn- state-val [[state _]]
  (p/current-value state))

(defn- swap-state! [[_ set-state! :as state] update-fn & args]
  (set-state! (apply update-fn (state-val state) args)))

(defn query-result-and-count-sources [db q args on-error!]
  (let [[results set-results!] (source/use-state [])
        result-count (atom 0)]
    (future
      (try
        (with-open [res (apply xt/open-q db q args)]
          (doseq [p (->> res iterator-seq (partition-all 100))]
            (swap! result-count + (count p))
            (set-results! (into (p/current-value results) p))))
        (catch Throwable t
          (on-error! (str "Error in query: " (.getMessage t))))))
    [results result-count]))

(defn- query! [xtdb-node state query-str]
  (let [merge-state! (partial swap-state! state merge)
        {:keys [q error]} (safe-read query-str)
        {:keys [in-args in-args-names]} (state-val state)
        args (when in-args
               (map in-args in-args-names))]
    (reset! last-query {:query-text query-str
                        :in-args in-args})
    (if error
      (merge-state! {:error? true
                     :error-message error
                     :running? false
                     :results nil})
      (do
        (merge-state! {:running? true
                       :error? false
                       :results nil})
        (try
          (let [db (xt/db xtdb-node)]
            (merge-state! {:running? false
                           :error? false
                           :results (query-result-and-count-sources
                                     db q args
                                     #(merge-state! {:error? true
                                                     :error-message %
                                                     :running? false
                                                     :results nil}))
                           :query q
                           :timing (System/nanoTime)
                           :basis (xt/db-basis db)}))
          (catch Throwable e
            (merge-state! {:error? true
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

  Also determines which vars are ids based on query."
  [query]
  (let [normalized (xtdb.query/normalize-query query)
        id? (set/union
             ;; All symbols in the E position in EAV where triples
             (into #{}
                   (for [w (:where normalized)
                         :when (and (vector? w)
                                    (symbol? (first w)))]
                     (first w)))
             ;; All symbols in first position of pull finds
             (into #{}
                   (for [f (:find normalized)
                         :when (and (list? f)
                                    (= 'pull (first f)))]
                     (second f))))
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
           (for [k (nth header 2)
                 :let [k (if (map? k)
                           ;; nested pull
                           (first (keys k))
                           ;; attribute pull
                           k)]]
             {:label (str (when column-name
                            (str column-name ": ")) (name k))
              :accessor #(get-in % [column-accessor k])
              :id? (when (= :xt/id k)
                     true)})

           ;; any other result
           [{:label (or column-name (pr-str header))
             :accessor #(get-in % [column-accessor])
             :id? (when (id? header) true)}])))
     (:find normalized) column-accessors)))

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

(defn query-results-table [db headers result-source]
  (ui.table/table
   {:key identity
    ;; Set render method that uses format value
    :columns (mapv (fn [{id? :id? :as hdr}]
                     (let [is-id? (fn [v]
                                    (if (some? id?)
                                      id?
                                      (id/valid-id? db v)))]
                       (assoc hdr
                              :render (partial ui/format-value is-id?))))
                   headers)}
   result-source))

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
          headers (unpack-find-defs query)]
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
            (query-results-table db headers result-source))}
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
                   {:value-accessor value-accessor
                    :label-accessor label-accessor}
                   result-source)])))}))]))))

(defn saved-queries [db]
  (xt/q db '{:find [?e ?n]
             :keys [id name]
             :where [[?e :xtdb-inspector.saved-query/name ?n]]}))

(defn save-query! [xtdb-node in-args name query]
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
                   :xtdb-inspector.saved-query/query query
                   :xtdb-inspector.saved-query/in-args in-args}]]))))

(defn saved-queries-ui [xtdb-node state]
  (let [[query load-query!] (source/use-state nil)
        query-source
        (source/computed
         #(str "window.location.search='?query='+encodeURIComponent('" % "')")
         query)]
    (h/html
     [:div
      (js/eval-js-from-source query-source)

      [:div.flex.flex-row
       [:div.form-control
        [:div.input-group.input-group-sm
         [:input#save-query-as.input.input-bordered.input-sm {:placeholder "Save query as"}]
         [:button.btn.btn-square.btn-sm
          {:on-click (js/js (fn [name query-text]
                              (save-query! xtdb-node
                                           (:in-args (state-val state))
                                           name query-text))
                            (js/input-value "save-query-as")
                            "editor.getDoc().getValue()")}
          "Save"]]]
       [:div.divider.divider-horizontal]
       [::h/live (rx/q {:node xtdb-node :should-update? (constantly true)} saved-queries)
        (fn [queries]
          (h/html
           [:select.select.select-bordered.w-full.max-w-xs.select-sm
            {:name "saved-query"
             :on-change (js/js load-query! js/change-value)}
            [:option {:disabled true :selected true} "Load saved query"]
            [::h/for [{:keys [name]} queries]
             [:option {:value name} name]]]))]]])))

(defn- saved-query-by-name [db name]
  (ffirst
   (xt/q db '{:find [(pull query [*])]
              :where [[query :xtdb-inspector.saved-query/name name]]
              :in [name]}
         name)))

(defn- in-args-table [set-arg! {:keys [in-args-names in-args] :as foo}]
  (h/html
   [:div {:class "query-in-args w-1/2"}
    [::h/when (seq in-args-names)
     [:table.table.table-compact
      [:thead
       [:tr [:td "Argument"] [:td "Value"]]]
      [:tbody
       [::h/for [arg in-args-names
                 :let [arg-name (name arg)]]
        [:tr
         [:td arg-name]
         [:td (ui/input-any #(set-arg! arg %)
                            (get in-args arg ::ui/empty))]]]]]]]))

(defn validate! [swap-state! text]
  (when-let [{q :q} (safe-read text)]
    (swap-state! assoc :in-args-names (:in q))))



(defn render [{:keys [xtdb-node request]}]
  (def *r request)
  (let [{:keys [query-text in-args] :as foo}
        (or
         (some-> request :params (get "query")
                 (as-> n (saved-query-by-name (xt/db xtdb-node) n))
                 (set/rename-keys {:xtdb-inspector.saved-query/query :query-text
                                   :xtdb-inspector.saved-query/in-args :in-args}))
         @last-query)

        [src _ :as state]
        (source/use-state {:query nil
                           :in-args-names (some-> query-text safe-read :q :in)
                           :in-args (or in-args {})
                           :running? false
                           :results nil
                           :error? false})
        query!     (partial query! xtdb-node state)
        on-change! (js/js-debounced
                    500
                    (partial validate! (partial swap-state! state))
                    "editor.getDoc().getValue()")]
    (h/html
     [:div
      [::h/for [js codemirror-js]
       [:script {:src js}]]
      [:link {:rel "stylesheet" :href codemirror-css}]
      [:h2 "Query"]
      [:div.flex.flex-row.w-full
       [:div {:class "unreset w-1/2"}
        [:textarea#query query-text]]
       [:div.divider.divider-vertical]
       [::h/live (source/c= (select-keys %src [:in-args-names :in-args]))
        (partial in-args-table
                 (fn [arg val]
                   (swap-state! state update :in-args assoc arg val)))]
       [:script
        (h/out!
         "var editor = CodeMirror.fromTextArea(document.getElementById('query'), {"
         "lineNumbers: true,"
         "autoCloseBrackets: true,"
         "matchBrackets: true,"
         "mode: 'text/x-clojure'"
         "}); "
         "editor.on('change', _=> document.getElementById('validateq').click());")]
       [:button#validateq.hidden {:on-click on-change!}]]

      (saved-queries-ui xtdb-node state)
      [::h/live (source/c= (select-keys %src [:error? :error-message]))
       (fn [{:keys [error? error-message] :as f}]
         (h/html
          [:span
           [::h/if error?
            [:div.bg-red-300.border-2 error-message]
            [:span]]]))]
      [:div.flex.py-2
       [:button.btn.btn-primary.btn-sm
        {:on-click (js/js query!
                          "editor.getDoc().getValue()")}
        "Run query"]

       [:form#export-f.px-2 {:name :export
                             :action "query/export"
                             :method :POST
                             :enctype "application/x-www-form-urlencoded"}
        [:input#export-i {:type :hidden :name :query}]
        [:button.btn.btn-secondary.btn-sm
         {:on-click "document.querySelector('#export-i').value=editor.getDoc().getValue(); document.forms['export'].submit();"}
         "Export (EDN)"]]]

      [:div.divider.divider-vertical]

      [::h/live (source/c= (select-keys %src
                                        [:basis :running? :results :query :timing]))
       (partial render-results xtdb-node)]])))

(defn export-query [{xtdb-node :xtdb-node :as _ctx}
                    {{query "query"} :params :as _req}]

  (let [{:keys [q error]} (safe-read query)]
    (if q
      {:status 200
       :headers {"Content-Type" "application/edn"
                 "Content-Disposition"
                 (str "attachment; filename=\"query-"
                      (.format (java.text.SimpleDateFormat. "yyyy-MM-dd_HH:mm:ss") (java.util.Date.))
                      ".edn\"")}
       :body (ring-io/piped-input-stream
              (fn [ostream]
                (let [db (xt/db xtdb-node)]
                  (with-open [out (io/writer ostream)]
                    (.write out "{:query ")
                    (.write out (pr-str q))
                    (.write out "\n :db-basis ")
                    (.write out (pr-str (xt/db-basis db)))
                    (.write out "\n :results [\n")
                    (with-open [results (xt/open-q db q)]
                      (doseq [result (iterator-seq results)]
                        (.write out (pr-str result))
                        (.write out "\n")))
                    (.write out "]}")))))}

      {:status 400
       :body (str "Don't understand query: " error)})))
