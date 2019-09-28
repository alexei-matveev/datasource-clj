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
            [compojure.route :as cr]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]))

;; A map from target names to example functions of time:
(def database
  {"sine"
   (fn [x] (Math/sin x)) ; Math/sin would look for statie *Field*
   "cosine"
   (fn [x] (Math/cos x))
   "Can it be arbitrary text? Surprise me!"
   (fn [x]
     (let [y (- x 436005.0)] ; epoch in hours
       (if (= y 0.0) 1.0 (/ (Math/sin y) y))) )
   "noise"
   (fn [x] (rand-int 100))})

;; First  /search request  appears to  be {:target  ""}. Then,  as you
;; start typing e.g. "cosine" letter  for letter, more specific search
;; requests such  as {:target  "c"}, {:target "co"},  {:target "cos"},
;; etc are coming ...
(defn search [request]
  (pprint request)
  ;; FIXME: actually search?
  (let [metrics (keys database)    ; ["sine" "cosine" ...]
        ;; FIXME: this form I dont get yet:
        metricX [{:text "sine", :value 1}
                 {:text "cosine", :value 2}
                 {:text "Can it be an arbitrary text?" :value 3}]]
    (pprint metrics)
    (response metrics)))

;; Example Query:
(comment
  (def example-query
    {:range {:from "2019-09-27T15:00:12.345Z",
             :to   "2019-09-27T21:00:12.345Z",
             :raw {:from "now-6h", :to "now"}},
     :targets [{:target "cosine", :refId "A", :hide false, :type "timeserie"}],
     :intervalMs 6000000, ; intentionally increased x 100 for use in tests
     :interval "1m",      ; originally 1m = 60000 ms
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
                  :__interval_ms {:text "60000", :value 60000}}}))

;; Extract time range in Milliseconds as a tuple from Grafana query:
(defn time-range [q]
  (let [r (:range q)]
    (for [ts [(:from r) (:to r)]]
      (.getTime (inst/read-instant-timestamp ts)))))

(comment
  (time-range example-query)
  => (1569596412345 1569618012345))

(defn fake-data-poins [f start end step]
  (let [scale (* 3600 1000.0)]          ; 1h in ms
    (for [t (range start end step)]
      [(f (/ t scale)) t])))

;; q = Body of the /query:
(defn make-query-response [q]
  (let [targets (:targets q)
        [start end] (time-range q)
        step (:intervalMs q)
        ;; FIXME: queries for tabular data?
        data (for [t targets
                   :let [target (:target t)
                         function (database target (database "noise"))]
                   :when (= "timeserie" (:type t))]
               {:target target
                :datapoints (fake-data-poins function start end step)})]
    data))

(comment
  (make-query-response example-query)
  => ({:target "cosine",
       :datapoints ([-0.7961963581657523 1569596412345]
                    [-0.5260452104331543 1569602412345]
                    [0.8969061860681147 1569608412345]
                    [0.3543351256655463 1569614412345])}))

(defn query [request]
  (pprint request)
  (let [q (:body request)
        data (make-query-response q)]
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
  (cr/not-found "Page not found"))

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
