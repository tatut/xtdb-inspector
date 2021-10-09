(ns user
  (:require [xtdb.api :as xt]
            [xtdb-inspector.core :as inspector]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defonce xtdb (atom nil))
(defonce server (atom nil))

(defn start []
  (println "Starting dev instance with in-memory XTDB in port 3000")
  (swap! xtdb #(or % (xt/start-node {:xtdb.metrics/metrics {}
                                     :xtdb-inspector.metrics/reporter {}})))
  (swap! server
         (fn [old-server]
           (when old-server
             (old-server))
           (inspector/start {:xtdb-node @xtdb
                             :port 3000}))))

(defn some-docs []
  ;; insert some docs for testing
  (xt/submit-tx
   @xtdb
   [[::xt/put {:xt/id :hello :greeting "hello xtdb inspector world!"
               :number 420.69M :link "over-there"}]
    [::xt/put {:xt/id "over-there"
               :message "you are now over here!"
               :number 666M
               :go-deeper {:foo :bar}}]
    [::xt/put {:xt/id {:foo :bar}
               :message "even deeper now"
               :number 3.1415M
               :back-to-start :hello}]
    [::xt/put {:xt/id "thing1"
               :name "The first thing"}]
    [::xt/put {:xt/id "thing2"
               :name "Another thing"}]
    [::xt/put {:xt/id "thing3"
               :name "Yet another thing here"}]
    ])

  ;; insert some changes for testing history
  (xt/submit-tx @xtdb [[::xt/put {:xt/id :hello
                                  :greeting "hello XTDB inspector world!"
                                  :number 420.666M
                                  :link "over-there"
                                  :new-key "this one is"
                                  :things ["thing2"]}]])

  (xt/submit-tx @xtdb [[::xt/put {:xt/id :hello
                                  :greeting "hello XTDB inspector world!"
                                  :number 42M
                                  :link "over-there"
                                  :new-key "this one is"
                                  :things ["thing1" "thing3"]}]])

  ;; Insert some test people data
  (xt/submit-tx
   @xtdb
   (for [{:keys [id first_name last_name email gender job_title address]}
         (read-string (slurp (io/resource "testdata/people.edn")))]
     [::xt/put
      {:xt/id {:person-id id}
       :first-name first_name
       :last-name last_name
       :email email
       :gender (-> gender str/lower-case keyword)
       :job-title job_title
       :address address}]))

  ;; Insert a saved query
  (xt/submit-tx
   @xtdb
   [[::xt/put {:xt/id (java.util.UUID/randomUUID)
               :xtdb-inspector.saved-query/name "job-title counts"
               :xtdb-inspector.saved-query/query
               ;; Stored as text so they can have formatting and
               ;; comments
               (str "{:find [?jt (count ?p)] ; title and its count \n"
                    " :where [[?p :job-title ?jt]]\n"
                    "            ;; order most popular titles first\n"
                    " :order-by [[(count ?p) :desc]]}")}]
    [::xt/put {:xt/id (java.util.UUID/randomUUID)
               :xtdb-inspector.saved-query/name "users with name"
               :xtdb-inspector.saved-query/query
               (str "{:find [?u (pull ?u [:first-name :last-name])]\n"
                    " :where [[?u :first-name]]}")}]])

  )

(defn db [] (xt/db @xtdb))
