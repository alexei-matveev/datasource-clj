;;
;; Your backend needs to implement 4 urls [1]:
;;
;; / should return 200 ok. Used for "Test connection" on the
;; datasource config page.
;;
;; /search used by the find metric options on the query tab in panels.
;;
;; /query should return metrics based on input.
;;
;; /annotations should return annotations.
;;
;; /tag-keys should return tag keys for ad hoc filters.
;;
;; /tag-values should return tag values for ad hoc filters.
;;
;; [1] https://grafana.com/grafana/plugins/grafana-simple-json-datasource
;;
(ns datasource-clj.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :as cc]
            [compojure.route :as route]))

(cc/defroutes app
  (cc/GET "/" [] "Hello from Clojure data source!")
  (route/not-found "Page not found"))

(defn go []
  (jetty/run-jetty app {:port 8080}))

;; For your C-c C-e pleasure:
(comment
  (def server (go))
  (.stop server))

(defn -main [& args]
  (go))
