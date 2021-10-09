(ns xtdb-inspector.page.query
  "Edit and run a query"
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.js :as js]
            [xtdb.api :as xt]
            [xtdb-inspector.ui :as ui]))


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
      (do
        (set-state! {:error? true
                     :error-message error
                     :running? false
                     :results nil}))
      (do
        (set-state! {:running? true
                     :error? false
                     :results nil})
        (try
          (let [res (xt/q (xt/db xtdb-node) q)]
            (set-state! {:running? false
                         :error? false
                         :results res
                         :query q}))
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
  in the result table."
  [query]
  (let [column-accessors
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
              :accessor [column-accessor k]})

           ;; any other result
           [{:name (or column-name (pr-str header))
             :accessor [column-accessor]}])))
     (:find query) column-accessors)))

(defn render-results [xtdb-node {:keys [running? results query]}]
  (if running?
    (loading "Querying...")
    (let [db (xt/db xtdb-node)
          headers (unpack-find-defs query)]
      (h/html
       [:div
        [::h/when (seq results)
         [:table.w-full
          [:thead.bg-gray-200
           [:tr
            [::h/for [header-name (map :name headers)]
             [:td header-name]]]]
          [:tbody
           [::h/for [row results]
            [:tr
             [::h/for [{:keys [accessor]} headers
                       :let [item (get-in row accessor)]]
              [:td
               (ui/format-value db item)]]]]]]]]))))

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
      [::h/live (source/c= (select-keys %state [:error? :error-message]))
       (fn [{:keys [error? error-message] :as f}]
         (h/html
          [:span
           [::h/if error?
            [:div.bg-red-300.border-2 error-message]
            [:span]]]))]
      [:button.p-1.bg-blue-200
       {:on-click (js/js query! "editor.getDoc().getValue()")}
       "Run query"]

      [::h/live (source/c= (select-keys %state [:running? :results :query]))
       (partial render-results xtdb-node)]])))
