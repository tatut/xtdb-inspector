(ns xtdb-inspector.page.doc
  "Page that displays a single document."
  (:require [ripley.html :as h]
            [xtdb-inspector.id :as id]
            [xtdb.api :as xt]
            [ripley.live.source :as source]
            [clojure.set :as set]))

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

(defn format-tx-time [tx-time]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS") tx-time))

(defn entity-history [db id ]
  (let [history (xt/entity-history db id :asc {:with-docs? true})
        changes (reverse (changes history))
        initial (last changes)]
    (h/html
     [:div.entity-history.pt-4
      [:h3 "History"]
      [::h/for [{:keys [time changes] :as chg}  changes
                :let [time (format-tx-time time)]]
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
          [:tr.hover:bg-gray-100
           [:td.px-2.py-2.font-semibold {:class "w-1/3"} key-name]
           [:td.px-2.py-2
            [::h/when old-val
             [:div.line-through old-val]]
            [::h/when new-val
             [:div new-val]]]]]]]]])))

(defn render [{:keys [xtdb-node request] :as ctx}]
  (let [id (some-> request :params :doc-id id/read-doc-id)
        db (xt/db xtdb-node)
        entity (xt/entity db id)
        id-str (pr-str id)
        [show-history-source set-show-history!] (source/use-state false)]
    (h/html
     [:div
      [:h3 "Document (" [:span.font-mono id-str] ")"]
      [:table.font-mono {:class "w-9/12"}
       [::h/for [[k v] (dissoc entity :xt/id)
                 :let [key-name (pr-str k)
                       value (pr-str v)
                       link (when (id/valid-id? db v)
                              (str "/doc/" (id/doc-id-param v)))]]
        [:tr.hover:bg-gray-100
         [:td.px-2.py-2.font-semibold {:class "w-1/3"} key-name]
         [:td.px-2.py-2
          [::h/if link
           [:a.underline.bg-blue-200 {:href link} value]
           value]]]]]
      [::h/live show-history-source
       (fn [show?]
         (h/html
          [:div
           [::h/if show?
            (entity-history db id)
            [:button {:on-click #(set-show-history! true)}
             "Show history"]]]))]])))
