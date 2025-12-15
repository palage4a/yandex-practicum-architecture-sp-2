(defproject proxy "0.1.0-SNAPSHOT"
  :description "Simple API Gateway for CinemaAbyss"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [http-kit "2.6.0"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-json "0.5.1"]
                 [compojure "1.6.2"]
                 [cheshire "5.11.0"]
                 [clj-http "3.13.1"]
                 [org.clojure/tools.logging "1.2.4"]]
  :main ^:skip-aot proxy.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})