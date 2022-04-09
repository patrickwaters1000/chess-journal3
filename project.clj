(defproject example "0.1.0-SNAPSHOT"
  :description "App for studying chess"
  :url "http://example.com/"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [cheshire "5.10.0"]
                 [compojure "1.1.8"]
                 [org.postgresql/postgresql "42.2.10"]
                 [http-kit "2.1.16"]
                 [clj-time "0.15.2"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [com.github.bhlangonijr/chesslib "1.1.20"]]
  :plugins [[lein-ring "0.12.4"]]
  :ring {:handler chess-journal3.api/app}
  :repositories [["jitpack" "https://jitpack.io"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
