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

(def db-b {:subprotocol "sqlite"
           :subname "data/ubc-buildings.sqlite"
           :classname "org.sqlite.JDBC"})
(def db-e {:subprotocol "sqlite"
           :subname "data/exam-schedule.sqlite"
           :classname "org.sqlite.JDBC"})

(defn- setup-db []
  (sql/db-do-commands db-b
                      (sql/create-table-ddl :buildings
                                            [:code :text "PRIMARY KEY"]
                                            [:name :text]
                                            [:address :text])))

(defn- get-addresses []
  (let [url "http://www.students.ubc.ca/classroomservices/buildings-and-classrooms/"
        rows (-> (html/html-resource (clojure.java.io/as-url url))
                 (html/select [:.dataTable :tr]))]
    (apply sql/insert! db-b :buildings
           (map #(let [tds (html/select % [:td])]
                   {:code (-> (first tds) :content first :content first)
                    :name (-> (second tds) :content first :content first)
                    :address (-> (nth tds 2) :content first)})
                rows))))

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
  (->> (sql/query db-e
                  ["SELECT
                       strftime('%s', datetime) as datetime,
                       count(*) as n
                   FROM schedule_2014w2
                   GROUP BY datetime"])
       ; json: {"2015-01-01 12:01:02": 45, ...}
       (reduce #(assoc %1 (keyword (:datetime %2)) (:n %2))
               {})))


(defn render-home [req]
  (jade/render "src/index.jade"))

(defroutes app-routes
  (GET "/sis/:code" [code] (building-address code))
  (GET "/api/calendar" [] (response (api-calendar)))
  ;(GET "/fetchaddresses" [] (fn [req] (get-addresses)))
  ;(GET "/createdb" [] (fn [req] (setup-db)))
  (GET "/" [] render-home)
  ;(GET "/" [] "Hello World")
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (json-middleware/wrap-json-response)
      (wrap-defaults site-defaults)))
