;;
;; A backend for SimpleJson Plugin needs  to implement just a few urls
;; [1]. The minimum is  likely a / and a /query  Endpoints. A few more
;; examples see Ref. [2]. For anything but / Grafana seems to use HTTP
;; POST, but  docs appear to  allow both.  There is als  an "enhanced"
;; version of Simple Json Plugin bei "simpod" [3].
;;
;; * "/" should return 200 ok. Used for "Test connection" on the
;;   datasource config page.
;;
;; * "/search" used by the find metric options on the query tab in
;;   panels.
;;
;; * "/query" should return metrics based on input.
;;
;; * "/annotations" should return annotations.
;;
;; * "/tag-keys" should return tag keys for ad hoc filters.
;;
;; * "/tag-values" should return tag values for ad hoc filters.
;;
;; [1] https://grafana.com/grafana/plugins/grafana-simple-json-datasource
;; [2] https://github.com/grafana/grafana/blob/master/docs/sources/plugins/developing/datasources.md
;; [3] https://grafana.com/grafana/plugins/simpod-json-datasource
;;
(ns datasource-clj.core
  (:require [ring.adapter.jetty :as jetty]
            [clojure.pprint :refer [pprint]]
            [clojure.instant :as inst]
            [ring.util.response :as rr]
            [bidi.ring :as br]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]])
  (:gen-class))

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

;; Debug helper, print and return:
(defn dbg [x]
  (pprint x)
  x)

;;
;; First /search request  appears to be {:target ""}  when looking vor
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
(defn search [q]
  (let [metrics (keys database)    ; ["sine" "cosine" ...]
        ;; FIXME: schuld the search be rather cases insensitive?
        regex (re-pattern (:target q))
        matches (filter (fn [s] (re-find regex s)) metrics)
        ;; FIXME: this form I dont get yet:
        matcheX [{:text "sine", :value 1}
                 {:text "cosine", :value 2}
                 {:text "Can it be an arbitrary text?" :value 3}]]
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

(defn fake-timeserie [target start end step]
  (let [fun (database target (database "noise"))
        scale (* 3600 1000.0)          ; 1h in ms
        datapoints (for [t (range start end step)]
                     [(fun (/ t scale)) t])]
    {:target target
     :datapoints datapoints}))

(defn fake-table []
  ;; FIXME:  Earlier examples  did  not use  sort/desc,  is it  really
  ;; understood by all Grafana versions? "Time" column seems to always
  ;; be sorted ...
  {:columns [{:text "Time", :type "time", :sort false, :desc false}
             {:text "Country", :type "string"},
             {:text "Number", :type "number", :sort true}],
   ;; The  rows  are  intentionally  not sorted,  see  the  digit  for
   ;; seconds:
   :rows [[1569618012345, "SE", 123],
          [1569618011345, "DE", 231],
          [1569618013345, "US", 321]],
   :type "table"})

;; q = Body of the /query:
(defn query [q]
  (let [targets (:targets q)
        [start end] (time-range q)
        step (:intervalMs q)]
    (for [t targets]
      (case (:type t)
        "timeserie"
        (fake-timeserie (:target t) start end step)
        "table"
        (fake-table)))))

(comment
  (query example-query)
  => ({:target "cosine",
       :datapoints ([-0.7961963581657523 1569596412345]
                    [-0.5260452104331543 1569602412345]
                    [0.8969061860681147 1569608412345]
                    [0.3543351256655463 1569614412345])}))

;; Tag database for ad hoc filters:
(def tags
  {"country" ["Germany" "New Zealand"]
   "city"  ["Berlin" "Paris" "London" "New York"]
   "district" []
   "street" []
   "age" [10 20 30]})

;; Values  of  the  custom  Dashboard variables  of  type  "Interval",
;; "Query",   etc.   do   not   get  passed   down   to  the   backend
;; automatically. Ad hoc filters, like "city = Berlin", do.
;;
;; Requests for /tag-keys happen e.g.  when clicking on the LHS of the
;; ad hoc filter to get the list of keywords for the dropdown. FIXME:
;; waht is the body when content-type = nil.
(defn tag-keys [q]
  ;; Response should be an array of maps like this. FIXME: type string
  ;; ist not suitable for age ...
  (for [k (keys tags)]
    {:type "string" :text k}))

;; Requests for /tag-values  happen e.g.  when clicking on  the RHS of
;; the  ad   hoc  filter   to  get   the  list   of  values   for  the
;; dropdown. Example request body:  {:key "city"}.  Response should be
;; an array of maps.
(defn tag-values [q]
  (let [k (:key q)]
    (for [v (get tags k)]
      {:text v})))

(defn annotations [q]
  ;; Does  not need  to be  passed back,  contrary to  what some  docs
  ;; claim:
  (let [annotation (:annotation q)
        time 1569614412345]             ; random
    (for [t [time (+ time (* 60 60 1000)) ]]
      {:text "Joe cases brain split."  ; required
       :time t                         ; required
       ;; Some docs seem to claim it is required, no it is not:
       ;; :annotation annotation
       :title "Cluster outage."        ; optional
       :tags ["joe", "cluster", "failure"]
       ;; Annotations for  regions seem to be  poorly documented, see
       ;; example  [1].  Time  End   alone  does  not  suffice.
       ;; [1] https://github.com/simPod/grafana-json-datasource
       :isRegion true
       :timeEnd (+ t (* 40 60 1000))})))

(defn not-implemented [request]
  {:status 404
   :body "Not implemented!"})

(def routes
  (let [impl [["" (fn [_] "ok")]
              ["search" search]
              ["query" query]
              ["annotations" annotations]
              ["tag-keys" tag-keys]
              ["tag-values" tag-values]
              [true not-implemented]]]
    ;; With Bidi  you need  to use  rr/response to  get a  proper HTTP
    ;; response. Decorate endpoint  handlers with to keep  most of the
    ;; HTTP boilerplate off the impl handlers:
    (br/make-handler
     ["/"
      (vec
       (for [[endpoint handler] impl]
         [endpoint
          (fn [request]
            (-> request
                ;; dbg  ; <- debug prints
                :body
                handler
                ;; dbg
                rr/response))]))])))

(def app
  (-> routes
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-json-response)))

(defn -main [& args]
  (jetty/run-jetty app {:port 8080}))

;; For your C-c C-e pleasure:
(comment
  (def server (-main))
  (.stop server))
