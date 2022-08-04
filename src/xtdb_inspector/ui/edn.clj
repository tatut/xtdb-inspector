(ns xtdb-inspector.ui.edn
  "Pretty HTML rendering of arbitrary EDN."
  (:require [ripley.html :as h]))

(defmulti render (fn [_ctx item] (type item)))

(defmethod render :default [_ item]
  (let [str (pr-str item)]
    (h/html [:span str])))

(defmethod render java.lang.String [_ctx str]
  (h/html [:span.text-lime-500 "\"" str "\""]))

(defmethod render java.lang.Number [_ctx num]
  (let [str (pr-str num)]
    (h/html [:span.text-red-300 str])))

(defmethod render clojure.lang.Keyword [_ctx kw]
  (let [str (pr-str kw)]
    (h/html [:span.text-emerald-700 str])))

(defmethod render clojure.lang.PersistentVector [{render-item :render-item :as ctx
                                                  :or {render-item render}} vec]
  (h/html
   [:div.flex
    "["
    [:div.inline-flex.space-x-2
     [::h/for [v vec]
      [:div.inline-block (render-item (dissoc ctx :render-item) v)]]]
    "]"]))

(defmethod render clojure.lang.IPersistentMap [ctx m]
  (if (empty? m)
    (h/html [:div.inline-block "{}"])
    (let [entries (seq m)
          normal-entries (butlast entries)
          last-entry (last entries)]
      (h/html
       [:div.inline-block.flex
        "{"
        [:table
         [::h/for [[key val] normal-entries]
          [:tr.whitespace-pre
           [:td.align-top.py-0.pl-0.pr-2 (render ctx key)]
           [:td.align-top.p-0 (render ctx val)]]]
         [:tr.whitespace-pre
          [:td.align-top.py-0.pl-0.pr-2 (render ctx (key last-entry))]
          [:td.align-top.p-0 [:div.inline-block (render ctx (val last-entry))] "}"]]]]))))

(defn edn
  ([thing] (edn {} thing))
  ([ctx thing]
   (render ctx thing)))
