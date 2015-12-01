(ns invigilou.handler
  (:require [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
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

(def ^:private query
  (partial sql/query db))

(defn- setup-db! []
  (->>
    (sql/create-table-ddl :buildings
                          [:code :text "PRIMARY KEY"]
                          [:name :text]
                          [:address :text]
                          [:lat "DECIMAL(9,6)"]
                          [:lng "DECIMAL(9,6)"])
    (sql/db-do-commands db)))

(defn- geocode
  "Return lat/lng map from given address string."
  [addr]
  (let [res (http/get "https://maps.googleapis.com/maps/api/geocode/json"
                      {:query-params {:address addr}})
        data (cheshire/parse-string (:body res) true)]
    (get-in data [:results 0 :geometry :location])))

(defn add-coords!
  "Geocode all buildings in db that are missing lat/lng.
  NOTE: this only did ~2/3 of them on the
  first run, I think Google was throttling or something."
  []
  (http/with-connection-pool {}
    (doseq [b (query ["SELECT DISTINCT address
                      FROM buildings
                      WHERE lat is null OR lng is null"])]
      (-> b
          (#(merge % (geocode (str (:address %) ", Vancouver BC"))))
          (#(sql/update! db :buildings % ["address = ?" (:address %)]))
          println))))



(def enliven-url
  (comp html/html-resource clojure.java.io/as-url))

(defn- get-addresses! []
  (let [rows (-> "http://www.students.ubc.ca/classroomservices/buildings-and-classrooms/"
                 enliven-url
                 (html/select [:.dataTable :tr]))
        parse-row (fn [[code name addr]]
                    {:code (get-in code [:content 0 :content 0])
                     :name (get-in name [:content 0 :content 0])
                     :address (get-in addr [:content 0])})]
    (->> rows
         (map #(-> (html/select [:td])
                   parse-row))
         (apply sql/insert! db :buildings))))

(defn building-name
  "Lookup full name from SIS building shortcode"
  [code]
  (let [[name] (query ["SELECT name
                       FROM buildings
                       WHERE code = ?"
                       code])]
    (or name code)))


(defn- building-address
  "Return street address of SIS building shortcode"
  [code]
  (let [url (str "http://www.students.ubc.ca/classroomservices/buildings-and-classrooms/"
                 "?code=" code)]
    (-> (enliven-url url)
        (html/select [:p :b])
        (get-in [0 :content]))))

(defn api-building-coords [code]
  (first
    (query ["SELECT lat, lng FROM buildings
            WHERE code = ?" code])))

(defn api-calendar []
  (->> (query ["SELECT
                   strftime('%s', datetime) as datetime,
                   count(*) as n
               FROM schedule_2014w2
               GROUP BY datetime"])
       ; json: {"2015-01-01 12:01:02": 45, ...}
       (reduce #(assoc %1 (keyword (:datetime %2)) (:n %2))
               {})))

(defn coursecode->year
  "Eg., return 3 when given \"ASIA 342 001\"."
  [cc]
  (-> cc
      (str/split #"[A-Z\s]+" 3) ; yields eg. ["" "342" "001"]
      second
      first
      str
      Integer/parseInt))

(defn addyear [exams]
  (->> exams
       (map #(-> %
                 (update :coursecode coursecode->year)
                 (rename-keys {:coursecode :year})))))

(defn next-exams
  "Get all exams at the next `N` > 0 exam times after `after`. Eg., if
  now is 07:00 and next exam times are 08:30, 12:00, 15:30, etc., and
  we ask for next 2 exam blocks after 10:15, get all exams at 12:00 and
  15:30. With no arguments, N=1 and after=now."
  ([] (next-exams "now"))
  ([after] (next-exams after 1))
  ([after N]
   (query [(str "SELECT "
                   ;ifnull(b.name, s.building) as building,
                   "s.coursecode,
                   cast(strftime('%s', s.datetime) as integer) as datetime,
                   s.building as shortcode "
                   ;b.lat,
                   ;b.lng
                "FROM schedule_2014w2 s
                JOIN (SELECT datetime FROM schedule_2014w2
                      WHERE datetime >= datetime(?)
                      GROUP BY datetime LIMIT ?) f
                     ON f.datetime = s.datetime
                LEFT JOIN buildings b ON b.code = s.building
                ORDER BY s.datetime, coursecode")
               after N])))

(defn keyshrinker [exams]
  (->> exams
       (map #(clojure.set/rename-keys % {;:building :b
                                         :coursecode :c
                                         :datetime :d
                                         :shortcode :s
                                         ;:lat :t
                                         ;:lng :g
                                         :year :y}))))

(defn hashbuildings [exams]
  (let [rows (query
               ["SELECT DISTINCT s.building as code,
                ifnull(b.name, s.building) as name,
                b.lat,
                b.lng
                FROM schedule_2014w2 s
                LEFT JOIN buildings b ON b.code = s.building"])]
    {:exams exams
     :buildings (zipmap (map (comp keyword :code) rows)
                        (map #(dissoc % :code) rows))}))

(defn home-data []
  (-> (next-exams "2015-04-04" 999)
      addyear
      keyshrinker
      hashbuildings
      cheshire/encode))

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
  (jade/render "index.jade"
                {:exams (home-data)}))

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
      json-middleware/wrap-json-response
      (wrap-defaults site-defaults)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty app
                     {:port port
                      :join? false})))
