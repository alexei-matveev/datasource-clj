(ns datasource-clj.core
  (:require [ring.adapter.jetty :as j]))

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello, World!"})

(defn -main [& args]
  (j/run-jetty handler {:port 8080}))
