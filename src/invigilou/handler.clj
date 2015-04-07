(ns invigilou.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :as json-middleware]
            [ring.util.response :refer [response]]
            [clj-jade.core :as jade]
            [clj-http.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as sql]
            [cheshire.core :as cheshire]))

(jade/configure {:template-dir "src/views/"
                 :pretty-print true})

(def db {:subprotocol "sqlite"
         :subname "data/exam-schedule.sqlite"})

(defn- setup-db! []
  (sql/db-do-commands db
                      (sql/create-table-ddl :buildings
                                            [:code :text "PRIMARY KEY"]
                                            [:name :text]
                                            [:address :text]
                                            [:lat "DECIMAL(9,6)"]
                                            [:lng "DECIMAL(9,6)"])))

(defn- geocode
  "Return lat/lng map from given address string."
  [addr]
  (let [endpoint "https://maps.googleapis.com/maps/api/geocode/json"
        res (http/get endpoint
                      {:query-params {:address addr}})
        data (cheshire/parse-string (:body res) true)]
    (get-in data [:results 0 :geometry :location])))

(defn add-coords!
  "Geocode all buildings in db that are missing lat/lng.
  NOTE: this only did ~2/3 of them on the
  first run, I think Google was throttling or something."
  []
  (http/with-connection-pool {}
    (doseq [b (sql/query db ["SELECT DISTINCT address
                             FROM buildings
                             WHERE lat is null OR lng is null"])]
      (-> b
          (#(merge % (geocode (str (:address %) ", Vancouver BC"))))
          (#(sql/update! db :buildings % ["address = ?" (:address %)]))
          (#(println %))))))



(defn- get-addresses! []
  (let [url "http://www.students.ubc.ca/classroomservices/buildings-and-classrooms/"
        rows (-> (html/html-resource (clojure.java.io/as-url url))
                 (html/select [:.dataTable :tr]))]
    (apply sql/insert! db :buildings
           (map #(let [tds (html/select % [:td])]
                   {:code (-> (first tds) :content first :content first)
                    :name (-> (second tds) :content first :content first)
                    :address (-> (nth tds 2) :content first)})
                rows))))

(defn building-name
  "Lookup full name from SIS building shortcode"
  [code]
  (let [res (sql/query db
                       ["SELECT name
                        FROM buildings
                        WHERE code = ?"
                        code])]
    (if (> (count res) 0)
      (:name (first res))
      code)))


(defn- building-address
  "Return street address of SIS building shortcode"
  [code]
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

(defn api-building-coords [code]
  (first
    (sql/query db ["SELECT lat, lng FROM buildings
                   WHERE code = ?" code])))

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

(defn echo [s]
  (println s)
  s)

(defn next-exams
  "Get all exams at the next `N` > 0 exam times after `after`. Eg., if
  now is 07:00 and next exam times are 08:30, 12:00, 15:30, etc., and
  we ask for next 2 exam blocks after 10:15, get all exams at 12:00 and
  15:30. With no arguments, N=1 and after=now."
  ([] (next-exams "now"))
  ([after] (next-exams after 1))
  ([after N]
   (sql/query db
              ["SELECT
                   ifnull(b.name, s.building) as building,
                   s.coursecode,
                   s.datetime,
                   s.building as shortcode,
                   b.lat,
                   b.lng
               FROM schedule_2014w2 s
               JOIN (SELECT datetime FROM schedule_2014w2
                     WHERE datetime >= datetime(?)
                     GROUP BY datetime LIMIT ?) f
                    ON f.datetime = s.datetime
               LEFT JOIN buildings b ON b.code = s.building
               ORDER BY s.datetime, coursecode"
               after N])))

(defn ordinal-suffix
  "Ordinal suffix for days-of-month"
  [n]
  (if (<= 11 n 13)
    "th"
    (case (mod n 10)
      1 "st"
      2 "nd"
      3 "rd"
      "th")))

(defn render-home [req]
  (let [now (java.util.Date.)
        timefmt (java.text.SimpleDateFormat. "K:mm a")
        datefmt (java.text.SimpleDateFormat. "EEEE, MMMM d")]
  (jade/render "index.jade"
               {:time (.format timefmt now)
                :date (str (.format datefmt now) (ordinal-suffix (.getDate now)))
                :exams (cheshire/encode (next-exams "now" 999))})))

(defroutes app-routes
  (GET "/sis/:code" [code] (building-address code))
  (GET "/geo" [] (fn [req] (response (geocode "2211 Wesbrook Mall, Vancouver BC"))))
  ;(GET "/coords" [] (fn [req] (add-coords!)))
  (GET "/api/building-coords/:code" [code] (response (api-building-coords code)))
  (GET "/api/calendar" [] (response (api-calendar)))
  ;(GET "/fetchaddresses" [] (fn [req] (get-addresses!)))
  ;(GET "/createdb" [] (fn [req] (setup-db!)))
  (GET "/" [] render-home)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (json-middleware/wrap-json-response)
      (wrap-defaults site-defaults)))
