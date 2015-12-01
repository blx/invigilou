(defproject invigilou "0.1.2-SNAPSHOT"
  :description "Analysis of UBC exam schedule"
  :url "https://github.com/blx/invigilou"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [environ "1.0.1"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.xerial/sqlite-jdbc "3.8.11"]
                 [clj-http "2.0.0"]
                 [enlive "1.1.6"]
                 [clj-jade "0.1.7"]
                 [ring/ring-json "0.4.0"]]
  :plugins [[lein-ring "0.9.7" :exclusions [org.clojure/clojure]]]
  :ring {:handler invigilou.handler/app}
  :uberjar-name "invigilou-standalone.jar"
  :profiles
  {:uberjar {:aot :all}
   :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
