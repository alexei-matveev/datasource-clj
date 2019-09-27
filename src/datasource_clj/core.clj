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
            [clojure.pprint :refer [pprint]]
            [compojure.core :as cc]
            [compojure.route :as route]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]))

;; First request appears to be {"target" ""} ...
(defn search [request]
  (pprint request)
  (let [metrics ["aaa" "bbb" "ccc"]
        metricX [{:text "upper_25", :value 1}
                 {:text "upper_75", :value 2}]]
    (response metrics)))

;; Example Query:
(comment
  {"range" {"from" "2019-09-27T15:29:36.141Z",
            "to" "2019-09-27T21:29:36.141Z",
            "raw" {"from" "now-6h", "to" "now"}},
   "scopedVars" {"__interval" {"text" "1m", "value" "1m"},
                 "__interval_ms" {"text" "60000", "value" 60000}},
   "panelId" 2,
   "maxDataPoints" 480,
   "timezone" "",
   "cacheTimeout" nil,
   "requestId" "Q114",
   "interval" "1m",
   "startTime" 1569619776148,
   "targets" [{"target" "aaa", "refId" "A", "hide" false, "type" "timeserie"}],
   "intervalMs" 60000,
   "rangeRaw" {"from" "now-6h", "to" "now"},
   "dashboardId" nil,
   "adhocFilters" []})
(defn query [request]
  (pprint request)
  (let [body (:body request)
        targets (body "targets")
        data (for [t targets]
               {:target (t "target")
                :datapoints []})]
    (pprint data)
    (response data)))

(defn not-implemented [request]
  (pprint request)
  (response nil))

(cc/defroutes routes
  (cc/ANY "/" [] "Hello from Clojure data source!")
  (cc/ANY "/search" request (search request))
  (cc/ANY "/query" request (query request))
  (cc/ANY "/annotations" request (not-implemented request))
  (cc/ANY "/tag-keys" request (not-implemented request))
  (cc/ANY "/tag-values" request (not-implemented request))
  (route/not-found "Page not found"))

(def app
  (-> routes
      wrap-json-body
      wrap-json-response))

(defn go []
  (jetty/run-jetty app {:port 8080}))

;; For your C-c C-e pleasure:
(comment
  (def server (go))
  (.stop server))

(defn -main [& args]
  (go))
