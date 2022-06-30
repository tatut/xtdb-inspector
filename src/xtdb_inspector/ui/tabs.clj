(ns xtdb-inspector.ui.tabs
  "Tabbed panel"
  (:require [ripley.html :as h]
            [ripley.live.source :as source]))

(defn- tab-button [label select! selected?]
  (let [cls (str "cursor-pointer py-2 px-4 text-gray-500 border-b-8"
                 (when selected?
                   " text-green-500 border-green-500"))]
    (h/html
     [:li {:on-click select!
           :class cls}
      label])))

(defn tabs [& tabs]
  (let [[selected-idx set-selected-idx!] (source/use-state 0)
        tab-count (count tabs)]
    (h/html
     [:div.tabs
      [:ul.flex.items-center.my-4
       [::h/for [i (range tab-count)
                 :let [{:keys [label render]} (nth tabs i)]]
        [::h/live
         (source/computed #(= i %) selected-idx)
         (partial tab-button label #(set-selected-idx! i))]]]
      [::h/live selected-idx
       #(h/html
         [:div.tab-content
          ((:render (nth tabs %)))])]])))
