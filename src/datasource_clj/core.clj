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
            ;; [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]))

;; Time series DB.  Here a map from target names  to example functions
;; of time:
(def database
  {"sine"
   (fn [x] (Math/sin x)) ; pointfree Math/sin would look for static *Field*
   "cosine"
   (fn [x] (Math/cos x))
   "Can it be arbitrary text? Surprise me!"
   (fn [x]
     (let [y (- x 436005.0)] ; epoch in hours
       (if (= y 0.0) 1.0 (/ (Math/sin y) y))) )
   "noise"
   (fn [x] (rand-int 100))})

;;
;; First /search request appears to  be {:target ""} whenn looking vor
;; the possible time series names (targets). Then, as you start typing
;; e.g. "cosine" letter for letter, more specific search requests such
;; as {:target "c"}, {:target "co"}, {:target "cos"}, etc are coming.
;;
;; Another  use of  the /search  Endpoint is  to ask  vor the  possble
;; values of the variable of type "Query".  The "Query" field from the
;; variable  config page  gets  passed as  the  usual {:target  "..."}
;; here.  The returned "metrics" are  then the possible values for the
;; var.
;;
(defn search [request]
  (pprint request)
  (let [metrics (keys database)    ; ["sine" "cosine" ...]
        q (:body request)
        ;; FIXME: schuld the search be rather cases insensitive?
        regex (re-pattern (:target q))
        matches (filter (fn [s] (re-find regex s)) metrics)
        ;; FIXME: this form I dont get yet:
        matcheX [{:text "sine", :value 1}
                 {:text "cosine", :value 2}
                 {:text "Can it be an arbitrary text?" :value 3}]]
    (pprint matches)
    matches))

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
    data))

;; Tag database for ad hoc filters:
(def tags
  {"country" ["Germany" "New Zealand"]
   "city"  ["Berlin" "Paris" "London" "New York"]
   "district" []
   "street" []
   "age" [10 20 30]})

;;
;; Values  of  the  custom  Dashboard variables  of  type  "Interval",
;; "Query",   etc.   do   not   get  passed   down   to  the   backend
;; automatically. Ad hoc filters, like "city = Berlin", do.
;;
;; Requests for /tag-keys happen e.g.  when clicking on the LHS of the
;; ad hoc filter to get the list of keywords for the dropdown. FIXME:
;; waht is the body when content-type = nil.
(defn tag-keys [request]
  (pprint request)
  ;; Response should be an array of maps like this. FIXME: type string
  ;; ist not suitable for age ...
  (let [resp (for [k (keys tags)]
               {:type "string" :text k})]
    (pprint resp)
    resp))

;; Requests for  /tag-values happen e.g.  when clicking on the  RHS of
;; the  ad   hoc  filter   to  get   the  list   of  values   for  the
;; dropdown. Example request body: {:key "city"}.
(defn tag-values [request]
  (pprint request)
  (let [q (:body request)
        k (:key q)
        ;; Response should be an array of maps:
        resp (for [v (get tags k)]
               {:text v})]
    (pprint resp)
    resp))

(defn not-implemented [request]
  (pprint request)
  nil)

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
  (cc/ANY "/tag-keys" request (tag-keys request))
  ;; /tag-values should return tag values for ad hoc filters.
  (cc/ANY "/tag-values" request (tag-values request))
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
