(ns user
  (:require [xtdb.api :as xt]
            [xtdb-inspector.core :as inspector]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defonce xtdb (atom nil))
(defonce server (atom nil))

(defn start []
  (println "Starting dev instance with in-memory XTDB in port 3000")
  (swap! xtdb #(or % (xt/start-node {:xtdb.lucene/lucene-store {}
                                     :xtdb.metrics/metrics {}
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
   (for [{:keys [id first_name last_name email gender job_title address date_of_birth]}
         (read-string (slurp (io/resource "testdata/people.edn")))
         :let [[year month day] (map #(Long/parseLong %)
                                     (str/split date_of_birth #"-"))]]
     [::xt/put
      {:xt/id {:person-id id}
       :first-name first_name
       :last-name last_name
       :email email
       :gender (-> gender str/lower-case keyword)
       :job-title job_title
       :address address
       :birthday (format "%02d-%02d" month day) ; to quickly fetch "today's birthdays"
       :date-of-birth (java.time.LocalDate/of year month day)}]))

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

  ;; Insert a dashboard
  (xt/submit-tx
   @xtdb
   [[::xt/put {:xt/id {:dashboard "demo"}
               :xtdb-inspector.dashboard/name "demo"
               :xtdb-inspector.dashboard/description "Demonstrates dashboard widgets"
               :xtdb-inspector.dashboard/config
               {:update-duration (java.time.Duration/ofSeconds 60)
                :widgets
                [{:id :dev-count
                  :type :stat
                  :label "Developers"
                  :description "# people any developer job title"
                  :query '{:find [(count d)]
                           :where [[(text-search :job-title "developer") [[d]]]]}}

                 {:id :male-percentage
                  :type :radial-progress
                  :label "Male %"
                  :query '{:find [(* 100.0 (/ (count e) total))]
                           :where [[e :gender :male]
                                   [(q [:find (count g) :where [g :gender]])
                                    [[total]]]]}}
                 {:id :genders
                  :type :pie
                  :col-span 2
                  :max-items 4
                  :label "Gender distribution"
                  :query '{:find [g (count g)]
                           :where [[_ :gender g]]
                           :group-by [g]}}

                 {:id :todays-birthdays
                  :type :query
                  :label "Upcoming birthdays"
                  :col-span 4
                  :query '{:find [u
                                  (if (= 0 days-to-birthday)
                                    (str fn " " ln " turns " age " today. HAPPY BIRTHDAY!")
                                    (str fn " " ln " turns " age " in " days-to-birthday " days"))
                                  days-to-birthday]
                           :keys [user celebrate in-days]
                           :where [[u :birthday bd]
                                   [u :first-name fn]
                                   [u :last-name ln]
                                   [(>= bd today)]
                                   [(<= bd week-from-now)]
                                   [u :date-of-birth dob]
                                   [(user/days-until-birthday dob) days-to-birthday]
                                   [(user/birthday-age dob) age]]
                           :order-by [[days-to-birthday :asc]]
                           :in [today week-from-now]}
                  :query-params '(letfn [(fmt [d]
                                           (.format (java.text.SimpleDateFormat. "MM-dd") d))]
                                   [(fmt (java.util.Date.))
                                    (fmt (java.util.Date.
                                          (+ (System/currentTimeMillis)
                                             (* 1000 60 60 24 7))))])}]}}]]))

(defn days-until-birthday [dob]
  (let [now (java.time.LocalDate/now)
        this-year-birthday (.withYear dob (.getYear now))]
    (.getDays (.until now this-year-birthday))))

(defn birthday-age [dob]
  (inc (.getYears (.until dob (java.time.LocalDate/now)))))

(defn db [] (xt/db @xtdb))

(defn shutdown []
  (when-let [server @server]
    (server))
  (when-let [node @xtdb]
    (.close node))
  (shutdown-agents))

(defn -main [& _args]
  (println "Starting main for testing")
  (start)
  (some-docs)
  ;; We are in the background, so sleep until tests are done
  (Thread/sleep (* 1000 60 15))
  (shutdown))


;; generate some test data for barcharts
(comment
  (def devs (map first (xt/q (db) '[:find e :where [(text-search :job-title "developer") [[e]]]])))
  (def langs [:clojure :haskell :rust :typescript :clojure :clojure :smalltalk]) ; stack the odds :D
  (xt/submit-tx
   @xtdb
   (for [d devs
         :let [l (rand-nth langs)]]
     [::xt/put (assoc (xt/entity (db) d) :favorite-language l)])))
