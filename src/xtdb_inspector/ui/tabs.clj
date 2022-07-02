(ns xtdb-inspector.ui.tabs
  "Tabbed panel"
  (:require [ripley.html :as h]
            [ripley.live.source :as source]))

(defn- tab-button [label select! selected?]
  (let [cls (source/computed
             #(str "tab tab-bordered"
                   (when %
                     " tab-active"))
             selected?)]
    (h/html
     [:a {:on-click select!
          :class [::h/live cls]}
      label])))

(defn tabs [& tabs]
  (let [[selected-idx set-selected-idx!] (source/use-state 0)
        tabs (remove nil? tabs)
        tab-count (count tabs)]
    (h/html
     [:div
      [:div.tabs
       (doseq [i (range tab-count)
               :let [{:keys [label]} (nth tabs i)]]
         (tab-button label
                     #(set-selected-idx! i)
                     (source/computed #(= i %) selected-idx)))]
      [::h/live selected-idx
       #(h/html
         [:div.tab-content
          ((:render (nth tabs %)))])]])))
