(ns exam-traffic.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :as json-middleware]
            [ring.util.response :refer [response]]
            [clj-jade.core :as jade]
            [clj-http.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as sql]))

(jade/configure {:template-dir "src/views/"})

(def db {:subprotocol "sqlite"
         :subname "data/exam-schedule.sqlite"
         :classname "org.sqlite.JDBC"})

(defn- setup-db []
  (sql/db-do-commands db
                      (sql/create-table-ddl :buildings
                                            [:code :text "PRIMARY KEY"]
                                            [:name :text]
                                            [:address :text])))

(defn- get-addresses []
  (let [url "http://www.students.ubc.ca/classroomservices/buildings-and-classrooms/"
        rows (-> (html/html-resource (clojure.java.io/as-url url))
                 (html/select [:.dataTable :tr]))]
    (apply sql/insert! db :buildings
           (map #(let [tds (html/select % [:td])]
                   {:code (-> (first tds) :content first :content first)
                    :name (-> (second tds) :content first :content first)
                    :address (-> (nth tds 2) :content first)})
                rows))))

(defn building-name [code]
  "Lookup full name from SIS building shortcode"
  (let [res (sql/query db
                       ["SELECT name
                        FROM buildings
                        WHERE code = ?"
                        code])]
    (if (> (count res) 0)
      (:name (first res))
      code)))


(defn- building-address [code]
  "Return street address of SIS building shortcode"
  (let [url (str "http://www.students.ubc.ca/classroomservices/buildings-and-classrooms/"
                 "?code=" code)]
    (-> (html/html-resource (clojure.java.io/as-url url))
        (html/select [:p :b])
        first
        :content)))

(defn- find-building [code]
  (http/get "http://www.students.ubc.ca/classroomservices/buildings-and-classrooms/"
            {:query-params {:code code}})
  (str "Hello " code))

(defn api-calendar []
  (->> (sql/query db
                  ["SELECT
                       strftime('%s', datetime) as datetime,
                       count(*) as n
                   FROM schedule_2014w2
                   GROUP BY datetime"])
       ; json: {"2015-01-01 12:01:02": 45, ...}
       (reduce #(assoc %1 (keyword (:datetime %2)) (:n %2))
               {})))

(defn next-exams
  "Get all exams scheduled at or after `after` (defaults to 'now')."
  ([] (next-exams "now"))
  ([after]
   (sql/query db
              ["SELECT
                   ifnull(b.name, s.building) as building,
                   s.coursecode,
                   s.datetime,
                   s.building as shortcode
               FROM schedule_2014w2 s
               LEFT JOIN buildings b ON b.code = s.building
               WHERE datetime >= datetime(?)
               ORDER BY datetime, coursecode"
               after])))

(defn next-exams-time
  "Get all exams at the next `N` > 0 exam times. Eg., if now is 07:00 and
  next exam times are 08:30, 12:00, 15:30, etc., and we ask for 2, get
  all exams at 08:30 and 12:00."
  ([] (next-exams-time 1))
  ([N] (->> (next-exams)
            (reduce #(let [exams (:exams %1)
                           n (:n %1)
                           this %2]
                       (if (or (empty? exams)
                               (= (:datetime this)
                                  (:datetime (peek exams))))
                         (assoc %1 :exams (conj exams this))
                         (if (< n N)
                           (assoc %1
                                  :n (inc n)
                                  :exams (conj exams this))
                           (reduced %1))))
                    {:exams [] :n 1})
            :exams)))
(comment
       (reduce #(if (or (empty? %1)
                        (= (:datetime %2)
                           (:datetime (last %1))))
                  (conj %1 %2)
                  (reduced %1))
               [])
       )

(defn ordinal-suffix [n]
  "Ordinal suffix for days-of-month"
  (if (<= 11 n 13)
    "th"
    (case (mod n 10)
      1 "st"
      2 "nd"
      3 "rd"
      "th")))

(defn render-home [req]
  (let [now (java.util.Date.)
        timefmt (java.text.SimpleDateFormat. "k:mm a")
        datefmt (java.text.SimpleDateFormat. "EEEE, MMMM d")]
  (jade/render "index.jade"
               {:time (.format timefmt now)
                :date (str (.format datefmt now) (ordinal-suffix (.getDate now)))
                :exams (next-exams-time)})))

(defroutes app-routes
  (GET "/sis/:code" [code] (building-address code))
  (GET "/api/calendar" [] (response (api-calendar)))
  ;(GET "/fetchaddresses" [] (fn [req] (get-addresses)))
  ;(GET "/createdb" [] (fn [req] (setup-db)))
  (GET "/" [] render-home)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (json-middleware/wrap-json-response)
      (wrap-defaults site-defaults)))
