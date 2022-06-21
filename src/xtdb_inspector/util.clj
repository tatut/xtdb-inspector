(ns xtdb-inspector.util
  (:import (java.net URLEncoder))
  (:require [clojure.string :as str]))

(defn enc
  "URLEncode a thing"
  [x]
  (-> x
      URLEncoder/encode
      (str/replace "+" "%20")))
