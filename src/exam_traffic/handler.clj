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

(defn next-exams []
  (sql/query db
             ["SELECT
                  ifnull(b.name, s.building) as building,
                  s.coursecode,
                  s.datetime,
                  s.building as shortcode
              FROM schedule_2014w2 s
              LEFT JOIN buildings b ON b.code = s.building
              WHERE datetime >= datetime('now')
              ORDER BY datetime"]))

(defn render-home [req]
  (jade/render "src/index.jade"
               {:time "1:35 AM"
                :date "Wednesday, April 1st"
                :ee (take 10 (next-exams))
                :exams [" 9:00 AM: CPSC 210 in DMP 110"
                        "11:00 AM: ECON 101 in ANGU 200"]}))

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
