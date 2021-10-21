(ns xtdb-inspector.page.query
  "Edit and run a query"
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.js :as js]
            [xtdb.api :as xt]
            [xtdb-inspector.ui :as ui]
            [ripley.integration.xtdb :as rx]
            [cheshire.core :as cheshire]
            [ripley.live.protocols :as p]
            [ripley.impl.dynamic :as dyn]
            xtdb.query))


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

(defn- query! [xtdb-node set-state! query-str]
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
          (let [db (xt/db xtdb-node)
                ts (System/nanoTime)
                res (xt/q db q)
                te (System/nanoTime)]
            (set-state! {:running? false
                         :error? false
                         :results res
                         :query q
                         :timing (/ (- te ts) 1e6)
                         :db db}))
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
             {:name (str (when column-name
                           (str column-name ": ")) (name k))
              :accessor [column-accessor k]
              :id? false})

           ;; any other result
           [{:name (or column-name (pr-str header))
             :accessor [column-accessor]
             :id? (id? header)}])))
     (:find query) column-accessors)))

(defn- duration [ms]
  (cond
    (> ms 1000)
    (format "%.2fs" (/ ms 1000.0))

    (> ms 100)
    (format "%.0fms" ms)

    :else
    (format "%.2fms" ms)))

(defn render-results [xtdb-node {:keys [running? results query timing db] :as r}]
  (cond
    ;; Query is running
    running?
    (loading "Querying...")

    ;; No query has been made yet and no query is running
    (nil? results)
    (h/html [:span])

    ;; Query has been run and results are available
    :else
    (let [headers (unpack-find-defs db query)]
      (with-open [db (xt/open-db xtdb-node)]
        (h/html
         [:div
          [:div.text-sm
           (h/dyn! (count results)) " results in " (h/dyn! (duration  timing))]
          [::h/when (seq results)
           [:table.w-full
            [:thead.bg-gray-200
             [:tr
              [::h/for [header-name (map :name headers)]
               [:td header-name]]]]
            [:tbody
             [::h/for [row results]
              [:tr
               [::h/for [{:keys [accessor id?]} headers
                         :let [item (get-in row accessor)]]
                [:td
                 (ui/format-value (constantly id?) item)]]]]]]]])))))

(defn saved-queries [db]
  (xt/q db '{:find [?e ?n]
             :keys [id name]
             :where [[?e :xtdb-inspector.saved-query/name ?n]]}))

(defn save-query! [xtdb-node name query]
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
                 :xtdb-inspector.saved-query/query query}]])))

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
       [:div.border-2.p-2
        "Save query as: "
        [:input#save-query-as {:type :text :placeholder "save query as"}]
        [:button.p-1.bg-blue-200
         {:on-click (js/js (partial save-query! xtdb-node)
                           (js/input-value "save-query-as")
                           "editor.getDoc().getValue()")}
         "Save"]]
       [:div.border-2.p-2.ml-1
        "Load saved query: "
        [::h/live (rx/q {:node xtdb-node :should-update? (constantly true)} saved-queries)
         (fn [queries]
           (h/html
            [:select {:name "saved-query"
                      :on-change (js/js load-query! js/change-value)}
             [:option {:disabled true :selected true} "--  saved query --"]
             [::h/for [{:keys [id name]} queries
                       :let [id (str id)]]
              [:option {:value id} name]]]))]]]])))


(defn render [{:keys [xtdb-node request]}]
  (let [[state set-state!] (source/use-state {:query nil
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
       [:textarea#query
        "{:find []\n :where []}"]]
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
       [:button.p-1.bg-blue-200
        {:on-click (js/js query!
                          "editor.getDoc().getValue()"
                          ""
                          )}
        "Run query"]
       #_[:div.ml-4
        [:input#live {:name "live" :type "checkbox"}]
        [:label {:for "live"} "Update live"]]]

      [::h/live (source/c= (select-keys %state
                                        [:running? :results :query :timing :db]))
       (partial render-results xtdb-node)]])))
