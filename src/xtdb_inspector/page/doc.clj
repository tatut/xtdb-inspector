(ns xtdb-inspector.page.doc
  "Page that displays a single document."
  (:require [ripley.html :as h]
            [xtdb-inspector.id :as id]
            [xtdb-inspector.ui :as ui]
            [xtdb.api :as xt]
            [ripley.live.source :as source]
            [clojure.set :as set]
            [ripley.js :as js]))

;; PENDING: we could have a live collection
;; for the history and listen to changes as they happen.
;;
;; The whole page could be live and show the current doc
;; and the history. Any changes in db could be listened to
;; and automatically reflected here.

(defn changes [history]
  (loop [old nil
         acc []
         [h & history] history]
    (if-not h
      acc
      (let [new (dissoc (::xt/doc h) :xt/id)
            all-keys (set/union (set (keys old))
                                (set (keys new)))
            history-entry
            {:time (::xt/tx-time h)
             :changes (into {}
                            (keep (fn [k]
                                   (let [old-value (get old k ::no-value)
                                         new-value (get new k ::no-value)]
                                     (when (not= old-value new-value)
                                       [k {::from old-value ::to new-value}]))))
                            all-keys)}]
        (recur new
               (conj acc history-entry)
               history)))))


(defn attr-val-row [attr val-fn]
  (h/html
   [:tr.hover:bg-gray-100
    [:td.px-2.py-2.font-semibold {:class "w-1/3"} attr]
    [:td.px-2.py-2
     (val-fn)]]))

(defn entity-history [db id ]
  (let [history (xt/entity-history db id :asc {:with-docs? true})
        changes (reverse (changes history))
        initial (last changes)]
    (h/html
     [:div.entity-history.pt-4
      [:h3 "History"]
      [::h/for [{:keys [time changes] :as chg}  changes
                :let [time (ui/format-inst time)]]
       [:div.history-entry
        [:hr]
        [:div.font-bold time
         [::h/when (= chg initial)
          " (initial document)"]]
        [:table.font-mono {:class "w-9/12"}
         [::h/for [[key val] (sort-by first changes)
                   :let [key-name (pr-str key)
                         old-val (when (not= ::no-value (::from val))
                                   (pr-str (::from val)))
                         new-val (when (not= ::no-value (::to val))
                                   (pr-str (::to val)))]]
          (attr-val-row
           key-name
           (fn []
             (h/html
              [:span
               [::h/when old-val
                [:div.line-through old-val]]
               [::h/when new-val
                [:div new-val]]])))]]]]])))

(defn links-to [xtdb id]
  (let [attrs
        ;; PENDING: we could also keep a set of all
        ;; attributes of id values we encounter when
        ;; displaying docs and persisting it.
        ;; But perhaps this is fast enough?
        (disj (into #{}
                    (map key)
                    (xt/attribute-stats xtdb))
              :xt/id)]
    (with-open [db (xt/open-db xtdb)]
      (into []
            (mapcat
             (fn [attr]
               (for [[from]
                     (xt/q db {:find ['?e]
                               :where [['?e attr 'id]]
                               :in ['id]} id)]
                 [attr from])))
            attrs))))

(defn render-links-to [db links]
  (h/html
   [:div
    [:table.font-mono {:class "w-9/12"}
     [:thead
      [:tr
       [:td "Attribute"]
       [:td "Document"]]]
     [:tbody
      [::h/for [[attr from] links
                :let [attr-name (pr-str attr)]]
       (attr-val-row attr-name
                     #(ui/format-value (partial id/valid-id? db) from))]]]]))

(defn render [{:keys [xtdb-node request] :as ctx}]
  (with-open [db (xt/open-db xtdb-node)]
    (let [id (some-> request :params :doc-id id/read-doc-id)
          entity (xt/entity db id)
          id-str (pr-str id)
          [show-history-source set-show-history!] (source/use-state false)]
      (h/html
       [:div
        [:h3.bg-gray-300 "Document   " [:span.font-mono id-str]]
        [:table.font-mono {:class "w-9/12"}
         [:thead
          [:tr
           [:td "Attribute"]
           [:td "Value"]]]
         [:tbody
          [::h/for [[k v] (dissoc entity :xt/id)
                    :let [key-name (pr-str k)]]
           [:tr.hover:bg-gray-100
            [:td.px-2.py-2.font-semibold {:class "w-1/3"} key-name]
            [:td.px-2.py-2
             (ui/format-value (partial id/valid-id? db) v)]]]]]

        [:h3.bg-gray-300 "Links from other documents"]
        [::h/live (future (links-to xtdb-node id))
         (partial render-links-to db)]

        [::h/live show-history-source
         (fn [show?]
           (h/html
            [:div
             [::h/if show?
              (entity-history db id)
              [:button {:on-click #(set-show-history! true)}
               "Show history"]]]))]]))))

(defn render-form [ctx]
  (let [doc (atom nil)
        set-doc! (fn [value]
                   (println "value ois: " value)
                   (binding [*read-eval* false]
                     (reset! doc (read-string value))))]
    (h/html
     [:div.flex.flex-col.m-4
      [:div "Insert XTDB document id (" [:span.font-mono ":xt/id"] ") as EDN:"]
      [:div
       [:input#doc {:name "doc"}]
       [:button.border-2.rounded-2.p-1
        {:on-click (js/js set-doc! (js/input-value "doc"))}
        "Go"]]
      [:span.font-light
       "Examples: 123  :hello  \"some-doc\" "]
      (js/eval-js-from-source
       (source/c= (str "window.location.pathname += \"/"
                       (id/doc-id-param %doc) "\"")))]))  )
