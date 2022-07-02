(ns xtdb-inspector.ui.chart
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.live.collection :as collection]))

(defn bar-chart
  "Simple top to bottom bar chart for showing relative
  counts of different items.

  Options:
  :width       Required width of the SVG image.
  :bar-height  Required height of a single bar.
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
    :or {value-accessor :value
         label-accessor :label}}
   bars-source]
  {:pre [(number? width)
         (number? bar-height)]}

  (let [max-source (source/computed
                    #(reduce max (map value-accessor %))
                    bars-source)
        height (source/computed #(* bar-height (count %)) bars-source)
        ;; Add indexes to our bars so that we can calculate y position
        bars-source (source/computed
                     #(into []
                            (map-indexed
                             (fn [i item]
                               {::index i ::item item}))
                            %)
                     bars-source)]
    (try
      (h/html
       [:svg {:width width :height [::h/live height]}
        (collection/live-collection
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
                          y (* bar-height (+ index 0.1))
                          w (* 0.66 (/ value max) width)
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
      (catch Throwable t
        (println "EXC: " t))))
  )
