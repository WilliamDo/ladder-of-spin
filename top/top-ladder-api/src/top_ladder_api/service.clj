(ns top-ladder-api.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/response "Hello Pedestal User"))

(def names '["William" "Kaho" "Michael" "Tom" "Trevor" "Clive" "Colin"])

(def player-all
  (map
    (fn [name id]
      (let [pos (- (count names) id)] {:name name :id id :position pos}))
    names
    (range (count names))))

(def ladder-store
  (atom
    {:players (reduce (fn [m p] (assoc m (:id p) p)) {} player-all)
     :challenges {}}))

(defn all-players [request]
  {:status 200
   :body (:players @ladder-store)
   :headers {"Access-Control-Allow-Origin" "http://localhost:3449"
             "Access-Control-Allow-Credentials" "true"}})

(defn ladder-info [request]
  {:status 200
   :body @ladder-store
   :headers {"Access-Control-Allow-Origin" "http://localhost:3449"
             "Access-Control-Allow-Credentials" "true"}})

(defn new-challenge
  [challenger challengee]
  (let [ladder @ladder-store challenges (:challenges ladder)]
    (if (not (or
               (contains? challenges challenger)
               (contains? challenges challengee)))
      (swap!
        ladder-store
        #(update-in % [:challenges] assoc
          challenger {:opponent challengee :challenger true}
          challengee {:opponent challenger :challenger false})))))

(defn issue-challenge [request]
  (let [challenger (get-in request [:form-params :challenger])
        challengee (get-in request [:form-params :challengee])]
    (do
      (prn request)
      (new-challenge (read-string challenger) (read-string challengee))
      {:status 200
       :body (:challenges ladder-store)
       :headers {"Access-Control-Allow-Origin" "http://localhost:3449"
                 "Access-Control-Allow-Credentials" "true"}})))

(defn confirm-challenge-win [winner opponent]
  (swap! ladder-store #(assoc-in % [:challenges winner :result] :win)))

(defn confirm-challenge-loss [loser opponent]
  (swap! ladder-store #(assoc-in % [:challenges loser :result] :loss)))

(defn challenge-success [ladder challenger challengee]
  (let [players (:players ladder)
        challenges (:challenges ladder)
        opponent-pos (:position (get players challengee))
        self (get players challenger)]
    (compare-and-set!
      ladder-store
      ladder
      {:players (merge players
                  (reduce
                    (fn [m [k v]] (assoc m k (assoc v :position (inc (:position v)))))
                    {}
                    (filter (fn [[k v]] (<= opponent-pos (:position v) (dec (:position self)))) players))
                  {challenger (assoc (get players challenger) :position opponent-pos)})
       :challenges (dissoc challenges challengee challenger)})))

(defn challenge-fail [ladder challenger challengee]
  (let [players (:players ladder) challenges (:challenges ladder)]
    (compare-and-set!
      ladder-store
      ladder
      {:players players
       :challenges (dissoc challenges challengee challenger)})))

(defn resolve-challenge [challenger challengee]
  (let [ladder @ladder-store
        challenges (:challenges ladder)
        challenger-claim (get-in challenges [challenger :result])
        challengee-claim (get-in challenges [challengee :result])]
    (cond
      (and (= challenger-claim :loss) (= challengee-claim :win)) (challenge-fail ladder challenger challengee)
      (and (= challenger-claim :win) (= challengee-claim :loss)) (challenge-success ladder challenger challengee))))

(defn confirm-result [request]
  (let [player (get-in request [:form-params :player])
        opponent (get-in request [:form-params :opponent])
        result (read-string (get-in request [:form-params :result]))]
    (do
      (prn request)
      (cond
        (= result :win) (confirm-challenge-win (read-string player) (read-string opponent))
        (= result :loss) (confirm-challenge-loss (read-string player) (read-string opponent)))
      {:status 200
       :body @ladder-store
       :headers {"Access-Control-Allow-Origin" "http://localhost:3449"
                 "Access-Control-Allow-Credentials" "true"}})))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/about" :get (conj common-interceptors `about-page)]
              ["/players" :get all-players :route-name :players]
              ["/ladder" :get ladder-info :route-name :ladder]
              ["/challenge" :post (conj [(body-params/body-params)] `issue-challenge)]
              ["/confirm-result" :post (conj [(body-params/body-params)] `confirm-result)]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
;(def routes
;  `[[["/" {:get home-page}
;      ^:interceptors [(body-params/body-params) http/html-body]
;      ["/about" {:get about-page}]]]])


;; Consumed by top-ladder-api.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})
