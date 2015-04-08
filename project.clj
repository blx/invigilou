(defproject invigilou "0.1.0-SNAPSHOT"
  :description "Analysis of UBC exam schedule"
  :url "https://github.com/blx/invigilou"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [environ "0.5.0"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [clj-http "1.1.0"]
                 [enlive "1.1.5"]
                 [clj-jade "0.1.5"]
                 [ring/ring-json "0.3.1"]]
  :plugins [[lein-ring "0.8.13" :exclusions [org.clojure/clojure]]
            [lein-marginalia "0.8.0" :exclusions [org.clojure/clojure]]]
  :ring {:handler invigilou.handler/app}
  :uberjar-name "invigilou-standalone.jar"
  :profiles
  {:uberjar {:aot :all}
   :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
