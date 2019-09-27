;;
;; A backend for SimpleJson Plugin needs to implement 4 urls [1]:
;;
;; [1] https://grafana.com/grafana/plugins/grafana-simple-json-datasource
;;
(ns datasource-clj.core
  (:require [ring.adapter.jetty :as jetty]
            [clojure.pprint :refer [pprint]]
            [clojure.instant :as inst]
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

;; Extract time range in Milliseconds as a tuple from Grafana query:
(defn time-range [q]
  (let [r (:range q)]
    (for [ts [(:from r) (:to r)]]
      (.getTime (inst/read-instant-timestamp ts)))))

(defn fake-data-poins [start end step]
  (let [scale (* 3600 1000.0)]          ; 1h in ms
    (for [t (range start end step)]
      [(Math/cos (/ t scale)) t])))

;; Example Query:
(comment
  (let [q {:range {:from "2019-09-27T15:00:12.345Z",
                   :to   "2019-09-27T21:00:12.345Z",
                   :raw {:from "now-6h", :to "now"}},
           :targets [{:target "xyz", :refId "A", :hide false, :type "timeserie"}],
           :intervalMs 60000,
           :interval "1m",
           :adhocFilters [{:key "age", :operator "<", :value "42", :condition "AND"}
                          {:key "city", :operator "=", :value "Berlin"}],
           :maxDataPoints 480,
           :panelId 2,
           :cacheTimeout nil,
           :timezone "",
           :startTime 1569620746383,
           :rangeRaw {:from "now-6h", :to "now"},
           :requestId "Q116",
           :dashboardId nil,
           :scopedVars {:__interval {:text "1m", :value "1m"},
                        :__interval_ms {:text "60000", :value 60000}}}]
    (time-range q)))

(defn query [request]
  (pprint request)
  (let [q (:body request)
        targets (:targets q)
        [from to] (time-range q)
        interval (:intervalMs q)
        ;; FIXME: queries for tabular data?
        data (for [t targets
                   :when (= "timeserie" (:type t))]
               {:target (:target t)
                :datapoints (fake-data-poins from to interval)})]
    #_(pprint data)
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
