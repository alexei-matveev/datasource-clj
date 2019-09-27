;;
;; A backend for SimpleJson Plugin needs to implement 4 urls [1]:
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
  {:range {:from "2019-09-27T15:45:46.373Z",
           :to "2019-09-27T21:45:46.376Z",
           :raw {:from "now-6h", :to "now"}},
   :targets [{:target "xyz", :refId "A", :hide false, :type "timeserie"}],
   :maxDataPoints 480,
   :panelId 2,
   :cacheTimeout nil,
   :timezone "",
   :startTime 1569620746383,
   :rangeRaw {:from "now-6h", :to "now"},
   :intervalMs 60000,
   :adhocFilters [],
   :requestId "Q116",
   :dashboardId nil,
   :interval "1m",
   :scopedVars {:__interval {:text "1m", :value "1m"},
                :__interval_ms {:text "60000", :value 60000}}})
(defn query [request]
  (pprint request)
  (let [body (:body request)
        targets (:targets body)
        data (for [t targets]
               {:target (:target t)
                :datapoints []})]
    (pprint data)
    (response data)))

(defn not-implemented [request]
  (pprint request)
  (response nil))

(cc/defroutes routes
  ;; / should return 200 ok. Used for "Test connection" on the
  ;; datasource config page.
  (cc/ANY "/" [] "Hello from Clojure data source!")
  ;; /search used by the find metric options on the query tab in
  ;; panels.
  (cc/ANY "/search" request (search request))
  ;; /query should return metrics based on input.
  (cc/ANY "/query" request (query request))
  ;; /annotations should return annotations.
  (cc/ANY "/annotations" request (not-implemented request))
  ;; /tag-keys should return tag keys for ad hoc filters.
  (cc/ANY "/tag-keys" request (not-implemented request))
  ;; /tag-values should return tag values for ad hoc filters.
  (cc/ANY "/tag-values" request (not-implemented request))
  (route/not-found "Page not found"))

(def app
  (-> routes
      (wrap-json-body {:keywords? true :bigdecimals? true})
      wrap-json-response))

(defn go []
  (jetty/run-jetty app {:port 8080}))

;; For your C-c C-e pleasure:
(comment
  (def server (go))
  (.stop server))

(defn -main [& args]
  (go))
