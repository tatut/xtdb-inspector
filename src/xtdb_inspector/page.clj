(ns xtdb-inspector.page
  "Main page template that sets up styles and app bar"
  (:require [ripley.html :as h]
            [xtdb-inspector.metrics :as metrics]
            [xtdb.api :as xt]
            [ripley.live.source :as source]
            [clojure.string :as str]
            [xtdb-inspector.id :as id]
            [ripley.alpine :refer [x-data] :as x]))

(defn search [search! results]
  (x-data
   {:data {:term "" :search false}
    :source (source/computed
             (fn [{:keys [term results]}]
               {:term term
                :results (mapv
                          (fn [[e v a]]
                            ;; id, href, attr, value
                            [(pr-str e)
                             (str "/doc/" (id/doc-id-param e))
                             (pr-str a)
                             (pr-str v)]) results)})
             results)
    :container-element :div.form-control}
   (fn [{:keys [term results]}]
     (h/html
      [:div.form-control
       [:div.input-group.input-group-sm
        [:input#lucene-search.input.input-bordered.input-sm
         {:placeholder "Search...", :name "search", :type "search"
          :x-model.debounce.500ms "term"}]
        [:button.btn.btn-square.btn-sm
         {:type "submit"
          :x-bind:disabled "search == true"
          :x-effect "term; $dispatch('click')"
          :x-on:click (str
                       "if(term.length > 2) { "
                       "search = true; "
                       (x/callback search! "term")
                       "} else { " results " = []; }")}
         [:svg
          {:width "20px" :height "20px"
           :fill "currentColor"
           :y "0px"
           :x "0px"
           :version "1.1",
           :viewbox "0 0 56.966 56.966",}
          [:path
           {:d "M55.146,51.887L41.588,37.786c3.486-4.144,5.396-9.358,5.396-14.786c0-12.682-10.318-23-23-23s-23,10.318-23,23  s10.318,23,23,23c4.761,0,9.298-1.436,13.177-4.162l13.661,14.208c0.571,0.593,1.339,0.92,2.162,0.92  c0.779,0,1.518-0.297,2.079-0.837C56.255,54.982,56.293,53.08,55.146,51.887z M23.984,6c9.374,0,17,7.626,17,17s-7.626,17-17,17  s-17-7.626-17-17S14.61,6,23.984,6z"}]]]]

       [:template {:x-if (str "search || " results ".length > 0")
                   :x-effect (str term "; search = false")}
        [:table.lucene-results {:style "left:12.5%;top:50px;" :class "z-50 fixed w-9/12 bg-gray-100 p-2 border-2"}
         [:template {:x-if "search"}
          [:tr
           [:td {:colspan 3}
            [:div.flex.justify-center.m-4
             [:svg.animate-spin.-ml-1.mr-3.h-5.w-5.text-primary
              {:fill "none" :viewBox "0 0 24 24"}
              [:circle.opacity-25 {:cx 12 :cy 12 :r 10 :stroke "currentColor" :stroke-width 4}]
              [:path.opacity-75 {:fill "currentColor"
                                 :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
             "Searching..."]]]]

         [:template {:x-if (str term " == term")}
          [:template {:x-for (str "r in " results)}
           [:tr
            [:td [:a.underline.bg-blue-200.px-1 {:x-bind:href "r[1]"
                                                 :x-html "r[0]"}]]
            [:td {:x-text "r[2]"}]
            [:td {:x-html "r[3]"}]]]]]]]))))

(def links
  [["/query" "Query"]
   ["/doc" "Documents"]
   ["/attr" "Attributes"]
   ["/tx" "Transactions"]])

(defn app-bar [ctx search! results]
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
     (search search! results)]]))


(defn lucene-search! [xtdb-node set-results! text]
  (set-results!
   {:term text
    :results
    (if (str/blank? text)
      []
      (xt/q (xt/db xtdb-node)
            '{:find [e v a s]
              :where [[(wildcard-text-search text) [[e v a s]]]]
              :limit 10
              :order-by [[s :desc]]
              :in [text]} text))}))

(defn render-page [{:keys [xtdb-node] :as ctx} page-content-fn]
  (let [[results set-results!] (source/use-state [])
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
       (ripley.alpine/alpine-js-script)
       (h/live-client-script "/__ripley-live")]
      [:body
       (app-bar ctx (partial lucene-search! xtdb-node set-results!) results)
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
