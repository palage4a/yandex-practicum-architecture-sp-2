(defproject events "0.1.0-SNAPSHOT"
  :description "Microservice for handling events in CinemaAbyss system"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [http-kit "2.6.0"]
                 [compojure "1.7.0"]
                 [ring/ring-json "0.5.1"]
                 [cheshire "5.11.0"]
                 [fundingcircle/jackdaw "0.9.12"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.slf4j/slf4j-simple "1.7.36"] ; NOTE: depedency for kafka logging.
                 [org.apache.kafka/kafka-streams-test-utils "3.3.2"]] ; FIXME: https://github.com/FundingCircle/jackdaw/issues/378.
  :main ^:skip-aot events.core
       :target-path "target/%s"
       :profiles {:uberjar {:aot :all
                            :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})