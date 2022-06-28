(ns xtdb-inspector.page
  "Main page template that sets up styles and app bar"
  (:require [ripley.html :as h]
            [xtdb-inspector.metrics :as metrics]
            [ripley.js :as js]
            [xtdb.api :as xt]
            [ripley.live.source :as source]
            [clojure.string :as str]
            [xtdb-inspector.id :as id]
            [xtdb-inspector.ui :as ui]))

(defn app-bar [ctx search!]
  ;; HTML adapted from https://tailwindcomponents.com/component/responsive-navbar-2
  (h/html
   [:nav.flex.items-center.justify-between.flex-wrap.bg-white.py-4.lg:px-12.shadow.border-solid.border-t-2.border-blue-700
    [:div.flex.justify-between.lg:w-auto.w-full.lg:border-b-0.pl-6.pr-2.border-solid.border-b-2.border-gray-300.pb-5.lg:pb-0
     [:div.flex.items-center.flex-shrink-0.text-gray-800.mr-16
      [:span.font-semibold.text-xl.tracking-tight "XTDB Inspector"]]]
    [:div.menu.w-full.lg:block.flex-grow.lg:flex.lg:items-center.lg:w-auto.lg:px-3.px-8
     [:div.text-md.font-bold.text-blue-700.lg:flex-grow
      [:a.block.mt-4.lg:inline-block.lg:mt-0.hover:text-white.px-4.py-2.rounded.hover:bg-blue-700.mr-2
       {:href "/query"}
       "Query"]
      [:a.block.mt-4.lg:inline-block.lg:mt-0.hover:text-white.px-4.py-2.rounded.hover:bg-blue-700.mr-2
       {:href "/doc"}
       "Documents"]
      [:a.block.mt-4.lg:inline-block.lg:mt-0.hover:text-white.px-4.py-2.rounded.hover:bg-blue-700.mr-2
       {:href "/attr"}
       "Attributes"]
      [:a.block.mt-4.lg:inline-block.lg:mt-0.hover:text-white.px-4.py-2.rounded.hover:bg-blue-700.mr-2
       {:href "/tx"}
       "Transactions"]]

     [:div.relative.mx-auto.text-gray-600.lg:block.hidden
      [:input#lucene-search.border-2.border-gray-300.bg-white.h-10.pl-2.pr-8.rounded-lg.text-sm.focus:outline-none
       {:placeholder "Search lucene index", :name "search", :type "search"
        :on-input (js/js-debounced 500 search! (js/input-value "lucene-search"))}]
      [:button.absolute.right-0.top-0.mt-3.mr-2
       {:type "submit"}
       [:svg#Capa_1.text-gray-600.h-4.w-4.fill-current
        {:width "512px",
         :y "0px",
         :x "0px",
         :xml:space "preserve",
         :style "enable-background:new 0 0 56.966 56.966;",
         :version "1.1",
         :viewbox "0 0 56.966 56.966",
         :height "512px"}
        [:path
         {:d "M55.146,51.887L41.588,37.786c3.486-4.144,5.396-9.358,5.396-14.786c0-12.682-10.318-23-23-23s-23,10.318-23,23  s10.318,23,23,23c4.761,0,9.298-1.436,13.177-4.162l13.661,14.208c0.571,0.593,1.339,0.92,2.162,0.92  c0.779,0,1.518-0.297,2.079-0.837C56.255,54.982,56.293,53.08,55.146,51.887z M23.984,6c9.374,0,17,7.626,17,17s-7.626,17-17,17  s-17-7.626-17-17S14.61,6,23.984,6z"}]]]]]]))


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
  (let [[results set-results!] (source/use-state nil)]
    (h/out! "<!DOCTYPE html>\n")
    (h/html
     [:html
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
       [:div.flex
        [:div.flex-grow
         (page-content-fn)]
        #_[:div {:class "w-2/6"}
         (metrics/metrics-ui ctx)]]]])))

(defn page-response [ctx page-content-fn]
  (h/render-response
   #(render-page ctx page-content-fn)))
