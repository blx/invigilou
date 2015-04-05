(defproject invigilou "0.1.0-SNAPSHOT"
  :description "Analysis of UBC exam schedule"
  :url "https://github.com/blx/exam-traffic"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [clj-http "1.1.0"]
                 [enlive "1.1.5"]
                 [clj-jade "0.1.5"]
                 [ring/ring-json "0.3.1"]]
  :plugins [[lein-ring "0.8.13" :exclusions [org.clojure/clojure]]
            [lein-bower "0.5.1" :exclusions [org.clojure/clojure]]
            [lein-coffee "0.2.1" :exclusions [org.clojure/clojure]]
            [lein-marginalia "0.8.0" :exclusions [org.clojure/clojure]]]
  :bower-dependencies [[cal-heatmap "3.5.2"]
                       [moment "2.9.0"]
                       [event-drops "0.1.1"]]
  :bower {:directory "resources/public/js/lib"}
  :lein-coffee
  {:compile-hook true
   :jar-hook true
   :coffee-version "1.9.0"
   :coffee {:sources ["src/coffee/exams.coffee"]
            :output "resources/public/js"
            :bare true}}
  :ring {:handler invigilou.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
