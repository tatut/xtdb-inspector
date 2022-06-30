(ns xtdb-inspector.ui.chart
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.live.collection :as collection]))

(defn bar-chart
  "Simple top to bottom bar chart for showing relative
  counts of different items.

  Options must contain :width and :bar-height of the chart.
  The SVG height will be :bar-height * (count bars).

  Bars-source must be a source that provides a collection
  of bars, which are maps.
  Each item must be a map containing :label and :value.

  eg.
  (bar-chart
   {:width 600 :bar-height 20}
   [{:label \"Yes\" :value 420}
    {:label \"No\" :value 67}
    {:label \"Undecided\" :value 10}])
  "
  [{:keys [width bar-height] :as _opts} bars-source]
  {:pre [(number? width)
         (number? bar-height)]}

  (let [;; Add indexes to our bars so that we can calculate y position
        bars-source (source/computed #(into [] (map-indexed
                                                (fn [i item]
                                                  (assoc item :index i))) %)
                                     bars-source)
        max-source (source/computed #(reduce max (map :value %)) bars-source)
        height (source/computed #(* bar-height (count %)) bars-source)]
    (h/html
     [:svg {:width width :height [::h/live height]}
      (collection/live-collection
       {:source bars-source
        :key :label
        :container-element :g
        :item-source-fn #(source/computed
                          (fn [max item]
                            (assoc item :max max))
                          max-source %)
        :render (fn [{:keys [label value max index]}]
                  (let [y (* bar-height (+ index 0.1))
                        w (* 0.75 (/ value max) width)
                        value-and-label (str value " " label)]
                    (h/html
                     [:g
                      [:rect {:y y
                              :width w
                              :height (* 0.8 bar-height)
                              :fill "red"}]
                      [:text {:x (+ w (* 0.05 width))
                              :y (+ y (/ bar-height 2))}
                       value-and-label]])))})
      ])
    )
  )
