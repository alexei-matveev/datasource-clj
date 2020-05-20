(defproject datasource-clj "0.1.0-SNAPSHOT"
  :description "Grafana JSON data source backend"
  :url "https://github.com/alexei-matveev/datasource-clj"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [bidi "2.1.6"]
                 [ring/ring-core "1.8.1"]
                 [ring/ring-jetty-adapter "1.8.1"] ; same as ring-core
                 [ring/ring-json "0.5.0"]]
  :repl-options {:init-ns datasource-clj.core}
  :main datasource-clj.core
  :aot [datasource-clj.core])
