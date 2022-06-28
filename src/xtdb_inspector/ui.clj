(ns xtdb-inspector.ui
  "Common UI components for inspector."
  (:require [xtdb-inspector.id :as id]
            [ripley.html :as h]
            [clojure.string :as str]
            [ripley.js :as js])
  (:import (java.time LocalDate LocalTime LocalDateTime Duration Instant)
           (java.time.format DateTimeFormatter FormatStyle)))


(defn format-inst [inst]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS") inst))


(defn link [href title]
  (h/html
   [:a.underline.bg-blue-200.px-1 {:href href} title]))

;; Method to display (as human readable), should return string
(defmulti display (fn [value] (type value)))

(defmethod display LocalDate
  [date]
  (.format date (DateTimeFormatter/ofLocalizedDate FormatStyle/MEDIUM)))

(defmethod display LocalTime
  [time]
  (.format time (DateTimeFormatter/ofLocalizedTime FormatStyle/MEDIUM)))

(defmethod display LocalDateTime
  [date-time]
  (.format date-time (DateTimeFormatter/ofLocalizedDateTime FormatStyle/MEDIUM)))

(defmethod display Duration
  [d]
  (str d))

(defmethod display Instant [i] (str i))

(defmethod display :default [_] ::no-custom-display)

(def short-class-name (comp last #(str/split % #"\.") str))
(def class-of (comp short-class-name type))

(defn format-value
  "Format a given value, if it is a valid id, render a link to view it."
  [is-id? value]
  (if (vector? value)
    (h/html
     [:ul
      [::h/for [v value]
       [:li (format-value is-id? v)]]])
    (let [href (when (is-id? value)
                 (str "/doc/" (id/doc-id-param value)))
          disp (display value)
          stringified (if (= ::no-custom-display disp)
                        (pr-str value)
                        disp)
          type (when (not= disp ::no-custom-display)
                 (class-of value))]
      (h/html
       [::h/if href
        (link href stringified)
        [:div.inline-block
         stringified
         [::h/when type
          [:span.text-xs " (" type ")"]]]]))))

(defmulti editor-widget-for
  (fn [value-type _initial-value _set-value!]
    value-type))

(defn input [type initial-value set-value! & {:keys [placeholder]}]
  (let [id (str (gensym "edit"))]
    (h/html
     [:div
      [:input.w-full
       {:autofocus true
        :type type
        :value initial-value
        :placeholder (or placeholder "")
        :id id
        :on-key-press "if(event.keyCode==13) event.target.blur()"
        :on-blur (js/js set-value! (js/input-value id))}]])))

(defn- format-for-edit [formatter value]
  (if (= value ::empty)
    ""
    (formatter value)))

;; Create an editor widget for the given value by type
(defmethod editor-widget-for LocalDate
  [_ date set-value!]
  (input "date" (format-for-edit str date)
         #(-> % LocalDate/parse set-value!)))

(defmethod editor-widget-for LocalTime
  [_ date set-value!]
  (input "time" (format-for-edit str date)
         #(-> % LocalTime/parse set-value!)))

(defmethod editor-widget-for LocalDateTime
  [_ datetime set-value!]
  (input "datetime-local" (format-for-edit str datetime)
         #(-> % LocalDateTime/parse set-value!)))

(defmethod editor-widget-for Duration
  [_ duration set-value!]
  (input "text" (format-for-edit str duration)
         #(-> % Duration/parse set-value!)))

(defn parse-edn [edn-string]
  (binding [*read-eval* false]
    (read-string edn-string)))

(defmethod editor-widget-for Instant
  [_ instant set-value!]
  (input "text" (format-for-edit str instant)
         #(-> % Instant/parse set-value!)))

(defmethod editor-widget-for :default [_ v set-value!]
  (input "text" (format-for-edit pr-str v) (comp set-value! parse-edn)
         :placeholder "EDN"))
