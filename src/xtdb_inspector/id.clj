(ns xtdb-inspector.id
  "Handle document ids"
  (:require [xtdb.api :as xt]
            [clojure.string :as str])
  (:import (java.net URLEncoder)))


(defn id-type?
  "Check if x can be an id."
  [x]
  (or
   (keyword? x)
   (string? x)
   (integer? x)
   (map? x)
   (uuid? x)
   (instance? java.net.URL x)
   (instance? java.net.URI x)))

(defn valid-id?
  "Check if given id is a valid id that actually
  has information in the database."
  [db id]
  (and (id-type? id)
       (some? (xt/entity-tx db id))))


(defn read-doc-id
  "Read doc id from URL parameter.
  doc-id must already be URL decoded."
  [doc-id]
  (cond
    ;; Keyword id
    (str/starts-with? doc-id ":")
    (keyword (subs doc-id 1))

    ;;
    (Character/isDigit (.charAt doc-id 0))
    (Long/parseLong doc-id)

    ;; Any edn (prefixed with _)
    (str/starts-with? doc-id "_")
    (binding [*read-eval* false]
      (read-string (subs doc-id 1)))

    ;; Otherwise it's a string
    :else doc-id))

(defn- enc [x]
  (-> x
      URLEncoder/encode
      (str/replace "+" "%20")))

(defn doc-id-param
  "Output doc-id as URL parameter"
  [id]
  (enc
   (cond
     (keyword? id)
     (pr-str id)

     (or
      (integer? id)
      (and (string? id)
           (not (str/starts-with? id "_"))
           (not (Character/isDigit (.charAt id 0)))))
     (str id)

     :else
     (str "_" (pr-str id)))))
