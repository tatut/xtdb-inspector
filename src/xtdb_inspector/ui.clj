(ns xtdb-inspector.ui
  "Common UI components for inspector."
  (:require [xtdb-inspector.id :as id]
            [ripley.html :as h]))


(defn format-inst [inst]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS") inst))

(defn format-value
  "Format a given value, if it is a valid id, render a link to view it."
  [is-id? value]
  (if (vector? value)
    (h/html
     [:ul
      [::h/for [v value]
       [:li (format-value is-id? v)]]])
    (let [link (when (is-id? value)
                 (str "/doc/" (id/doc-id-param value)))
          stringified (pr-str value)]
      (h/html
       [::h/if link
        [:a.underline.bg-blue-200 {:href link} stringified]
        stringified]))))
