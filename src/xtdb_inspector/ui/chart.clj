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
       [:g.ticks
        ;; add 25%, 50% and 75% ticks
        [::h/live (tick-source 0.25) tick]
        [::h/live (tick-source 0.50) tick]
        [::h/live (tick-source 0.75) tick]]]])))


(defn- pie-items
  [{:keys [value-accessor label-accessor max-items other-label]
    :or {value-accessor :value
         label-accessor :label
         max-items 4
         other-label "Other"}} items]
  (let [items (reverse
               (sort-by second (mapv (juxt label-accessor value-accessor) items)))]
    (if (> (count items) (inc max-items))
      ;; If too many items, summarize rest
      (conj (vec (take max-items items))
            [other-label
             (reduce + (map second (drop max-items items)))])

      ;; Return items as is
      items)))

(defn pie
  "Render a pie chart.

  Options:
  :width     Width in CSS (defaults to 100%)
  :height    Height in CSS (defaults to 100%)
  :label-accessor
             Function to get label from item (defaults to :label)
  :value-accessor
             Function to get value from item (defaults to :value)
  :max-items How many items to render (defaults to 4).
             Shows top N items as own slices and summarizes
             extra items as the \"other\" slice.
  :other-label
             Label to show for items that are summed (defaults to \"Other\").
  :colors    Optional vector of colors to use.
             If there are less colors than max-items+1
             the same color will be repeated.

  "
  [{:keys [width height colors] :as config
    :or {width "100%" height "100%"
         colors ["#6050DC" "#D52DB7" "#FF2E7E" "#FF6B45" "#FFAB05"]}}
   slices-source]
  (let [x (fn x
            ([ang] (x 1.0 ang))
            ([r ang] (* r (Math/cos ang))))
        y (fn y
            ([ang] (y 1.0 ang))
            ([r ang] (* r (Math/sin ang))))
        pos (fn pos
              ([ang] (pos 1.0 ang))
              ([r ang]
               (str (x r ang) "," (y r ang))))
        slices (source/computed
                (fn [items]
                  (let [items (pie-items config items)
                        sum (reduce + 0 (map second items))
                        rads #(- (* 2 Math/PI (/ % sum)) (/ Math/PI 2))]
                    (loop [[s & items] items
                           acc []
                           total 0
                           [c & colors] (cycle colors)
                           ]
                      (if-not s
                        acc
                        (let [[label value] s
                              start (rads total)
                              end (rads (+ total value))
                              large (if (< (- end start) Math/PI) "0" "1")
                              half-ang (+ start (/ (- end start) 2))
                              tx (x 0.7 half-ang)
                              ty (y 0.7 half-ang)]
                          (recur items
                                 (conj acc {:label label
                                            :start start
                                            :end end
                                            :d (str "M " (pos start)
                                                    " A 1 1 0 " large " 1 "
                                                    (pos end)
                                                    " L 0,0")
                                            :color c
                                            :text-x tx :text-y ty
                                            :percentage (format "%.1f%%"
                                                                (* 100.0 (/ value sum)))
                                            :legend-style (str "background-color:" c ";")})
                                 (+ total value)
                                 colors))))))
                slices-source)
        slices-id (gensym "pie")
        legend-id (gensym "lg")]

    ;; Render SVG pie slices using template
    (template/use-template
     (fn [{:keys [d color text-x text-y percentage]}]
       (h/html
        [:g.slice
         [:path {:d d
                 :fill color
                 :stroke "black"
                 :stroke-width 0.01}]
         [:text {:x text-x :y text-y
                 :text-anchor "middle"
                 :alignment-baseline "middle"
                 :font-size "0.1"} percentage]]))
     (str "#" slices-id)
     slices)

    ;; Render legend using template
    (template/use-template
     (fn [{:keys [legend-style label]}]
       (h/html
        [:div.whitespace-nowrap
         [:div.inline-block.w-4.h-4 {:style legend-style}]
         [:span.mx-2 label]]))
     (str "#" legend-id)
     slices)

    (h/html
     [:div.flex.items-center
      [:svg {:viewBox "-1.1 -1.1 2.2 2.2"}
       [:g.pie.text-primary {:id slices-id}]]
      [:div.flex.flex-col {:id legend-id}]])))
