(ns xtdb-inspector.ui.chart
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.live.collection :as collection]
            [ripley.template :as template]))


(defn bar-chart
  "Simple top to bottom bar chart for showing relative
  counts of different items.

  Options:
  :width       Width of the SVG image. Defaults to \"100%\".
  :bar-height  Height of a single bar. Defaults to 30.
               The SVG height will be :bar-height * (count bars).

  :label-accessor
               Accessor to get the label for an item.
               Defaults to :label.
  :value-accessor
               Accessor to get the value for an item.
               Defaults to :value.

  Bars-source must be a source that provides a collection
  of bars, which are maps.
  Each item must be a map contain a value and a label obtained
  by calling value-accessor and label-accessor respectively.

  eg.
  (bar-chart
   {:width 600 :bar-height 20}
   [{:label \"Yes\" :value 420}
    {:label \"No\" :value 67}
    {:label \"Undecided\" :value 10}])
  "
  [{:keys [width bar-height
           value-accessor label-accessor]
    :or {width "100%"
         bar-height 30
         value-accessor :value
         label-accessor :label}}
   bars-source]
  (let [max-source (source/computed
                    #(reduce max 1 (map value-accessor %))
                    bars-source)
        top 30
        height (source/computed #(+ top (* bar-height (count %))) bars-source)
        viewbox (source/computed #(str "0 0 600 " (+ top (* bar-height (count %)))) bars-source)
        ;; Add indexes to our bars so that we can calculate y position
        bars-source (source/computed
                     (fn [bars]
                       (let [max (reduce max 1 (map value-accessor bars))]
                         (into []
                               (map-indexed
                                (fn [i item]
                                   (let [value (value-accessor item)
                                         label (label-accessor item)
                                         y (double (+ top (* bar-height (+ i 0.1))))
                                         w (double (* 300 (/ value max)))
                                         value-and-label (str value " " label)]
                                     {:i i
                                      :y y
                                      :w w
                                      :text-y (+ y (/ bar-height 2))
                                      :value-and-label value-and-label})))
                               bars)))
                     bars-source)
        tick-source (fn [pct]
                      (source/computed
                       (fn [max height]
                         {:value (Math/round (* pct max))
                          :max max
                          :height height})
                       max-source height))
        tick (fn [{:keys [value max height]}]
               (let [x (double (* 300 (/ value max)))]
                 (h/html
                  [:g
                   [:text {:text-anchor "middle"
                           :x x :y 15
                           :font-size "0.5em"}
                    value]
                   [:line {:x1 x :x2 x
                           :y1 25 :y2 height
                           :stroke-dasharray "3 3"
                           :stroke-width "3"
                           :stroke "black"}]])))
        id (gensym "barchart")]
    (h/html
     [:span
      (template/use-template
       (fn [{:keys [y w text-y value-and-label]}]
         (h/html
          [:g
           [:rect.text-primary {:y y
                                :width w
                                :height (* 0.8 bar-height)}]
           [:text.text-info {:x 310
                             :y text-y}
            value-and-label]]))
       (str "#" id)
       bars-source)
      [:svg {:width width
             :viewBox [::h/live viewbox]
             :fill "currentColor"
             :preserveAspectRatio "xMinYMin"}
       [:g {:id id}]
       #_(collection/live-collection
          {:source bars-source
           :key (comp label-accessor ::item)
           :container-element :g
           :item-source-fn #(source/computed
                             (fn [max item]
                               (assoc item ::max max))
                             max-source %)
           :render (fn [{::keys [index max item]}]
                     (let [value (value-accessor item)
                           label (label-accessor item)
                           y (double (+ top (* bar-height (+ index 0.1))))
                           w (double (* 300 (/ value max)))
                           value-and-label (str value " " label)]
                       (h/html
                        [:g
                         [:rect.text-primary {:y y
                                              :width w
                                              :height (* 0.8 bar-height)}]
                         [:text.text-info {:x 310 ;(+ w (* 0.05 width))
                                           :y (+ y (/ bar-height 2))}
                          value-and-label]])))})
       [:g.ticks
        ;; add 25%, 50% and 75% ticks
        [::h/live (tick-source 0.25) tick]
        [::h/live (tick-source 0.50) tick]
        [::h/live (tick-source 0.75) tick]]]])))
