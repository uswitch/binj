(defproject uswitch/binj "0.5.0-SNAPSHOT"
  :description "Clojure library to interact with the Bing Ads API"
  :url "https://github.com/uswitch/binj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :pom-plugins  [[org.apache.maven.plugins/maven-shade-plugin "3.1.0"]]
  :plugins [[s3-wagon-private "1.3.1"]]
  :uberjar-merge-with {"META-INF/cxf/bus-extensions.txt" [slurp str spit] }
  :deploy-repositories {"releases"  {:url           "s3p://uswitch-internal-jars/releases"
                                     :sign-releases false
                                     :no-auth       true}
                        "snapshots" {:url           "s3p://uswitch-internal-jars/snapshots"
                                     :sign-releases false
                                     :no-auth       true}}
  :dependencies [[org.clojure/clojure                     "1.8.0"]
                 [com.microsoft.bingads/microsoft.bingads "12.0.1"]
                 [commons-io/commons-io                   "2.5"]
                 [clj-http-lite                           "0.3.0"]
                 [clj-time                                "0.14.2"]
                 [org.clojure/data.csv                    "0.1.4"]])
