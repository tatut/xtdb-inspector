(ns xtdb-inspector.page
  "Main page template that sets up styles and app bar"
  (:require [ripley.html :as h]
            [xtdb-inspector.metrics :as metrics]
            [ripley.js :as js]
            [xtdb.api :as xt]
            [ripley.live.source :as source]
            [clojure.string :as str]
            [xtdb-inspector.id :as id]
            [xtdb-inspector.ui :as ui]
            [ripley.live.protocols :as p]))

(defn search [search!]
  (h/html
   [:div.form-control
    [:dic.input-group.input-group-sm
     [:input#lucene-search.input.input-bordered.input-sm
      {:placeholder "Search...", :name "search", :type "search"
       :on-input (js/js-debounced 500 search! (js/input-value "lucene-search"))}]
     [:button.btn.btn-square.btn-sm
      {:type "submit"}
      [:svg
       {:width "20px" :height "20px"
        :fill "currentColor"
        :y "0px"
        :x "0px"
        :version "1.1",
        :viewbox "0 0 56.966 56.966",}
       [:path
        {:d "M55.146,51.887L41.588,37.786c3.486-4.144,5.396-9.358,5.396-14.786c0-12.682-10.318-23-23-23s-23,10.318-23,23  s10.318,23,23,23c4.761,0,9.298-1.436,13.177-4.162l13.661,14.208c0.571,0.593,1.339,0.92,2.162,0.92  c0.779,0,1.518-0.297,2.079-0.837C56.255,54.982,56.293,53.08,55.146,51.887z M23.984,6c9.374,0,17,7.626,17,17s-7.626,17-17,17  s-17-7.626-17-17S14.61,6,23.984,6z"}]]]]]))

(def links
  [["/query" "Query"]
   ["/doc" "Documents"]
   ["/attr" "Attributes"]
   ["/tx" "Transactions"]])

(defn app-bar [ctx search!]
  ;; HTML adapted from https://tailwindcomponents.com/component/responsive-navbar-2
  (h/html
   [:nav.navbar.bg-base-100
    [:div.flex-1
     [:span.font-semibold "XTDB Inspector"]]

    [:div.navbar-start
     [:ul.menu.menu-compact.lg:menu-horizontal.md:menu-horizontal
      [::h/for [[href label] links]
       [:li
        [:a {:href href} label]]]]]

    [:div.navbar-end; flex-none.gap-2
     (search search!)]]))


(defn lucene-search! [xtdb-node set-results! text]
  (set-results!
   (if (str/blank? text)
     []
     (xt/q (xt/db xtdb-node)
           '{:find [e v a s]
             :where [[(wildcard-text-search text) [[e v a s]]]]
             :limit 10
             :order-by [[s :desc]]
             :in [text]} text))))

(defn render-lucene-results [results]
  (let [class (if (empty? results)
                "hidden"
                "z-50 fixed w-9/12 bg-gray-100 p-2 border-2")]
    (h/html
     [:div {:class class :style "left: 12.5%;"}
      [:table
       [:thead
        [:tr
         [:td "Document"]
         [:td "Attribute"]
         [:td "Value"]]]
       [:tbody
        [::h/for [[e v a] results
                  :let [attr (pr-str a)]]
         [:tr
          [:td (ui/format-value (constantly true) e)]
          [:td attr]
          [:td (ui/format-value (constantly false) v)]]]]]])))

(defn render-page [{:keys [xtdb-node] :as ctx} page-content-fn]
  (let [[results set-results!] (source/use-state nil)
        [show-metrics? set-show-metrics!] (source/use-state false)]
    (h/out! "<!DOCTYPE html>\n")
    (h/html
     [:html {:data-theme "light"}
      [:head
       [:meta {:charset "UTF-8"}]
       [:link {:rel "stylesheet" :href "/xtdb-inspector.css"}]
       [:style
        ".hover-trigger .hover-target { display: none; }"
        ".hover-trigger:hover .hover-target { display: block; }"]
       (h/live-client-script "/__ripley-live")]
      [:body
       (app-bar ctx (partial lucene-search! xtdb-node set-results!))
       [::h/live results render-lucene-results]
       [:div.page
        (page-content-fn)]
       [::h/live show-metrics?
        (fn [show?]
          (h/html
           [:div.flex.flex-col.justify-end.items-end.fixed
            {:style "bottom: 0.2rem; right: 0.2rem;"}
            [::h/when show?
             (metrics/metrics-ui ctx)]
            [:button {:on-click #(set-show-metrics! (not show?))}
             [::h/if show? "- hide metrics" "+ show metrics"]]]))]]])))

(defn page-response [ctx page-content-fn]
  (h/render-response
   #(render-page ctx page-content-fn)))
