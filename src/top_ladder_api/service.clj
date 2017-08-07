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

(def player-store (atom (reduce (fn [m p] (assoc m (:id p) p)) {} player-all)))

(def challenge-store (atom {}))

(defn all-players [request]
  {:status 200
   :body @player-store
   :headers {"Access-Control-Allow-Origin" "http://localhost:3449"
             "Access-Control-Allow-Credentials" "true"}})

(defn new-challenge
  [challenger challengee]
  (let [challenges @challenge-store]
    (if (not (or
               (contains? challenges challenger)
               (contains? challenges challengee)))
      (swap!
        challenge-store
        #(assoc % challenger challengee challengee challenger)))))

(defn issue-challenge [request]
  (let [challenger (get-in request [:form-params :challenger])
        challengee (get-in request [:form-params :challengee])]
    (do
      (prn request)
      (new-challenge challenger challengee)
      {:status 200
       :body @challenge-store
       :headers {"Access-Control-Allow-Origin" "http://localhost:3449"
                 "Access-Control-Allow-Credentials" "true"}})))

(defn challenge-complete-win [id challenger-id]
  (let [players @player-store
       opponent-pos (:position (get players id))
       curr-player challenger-id
       self (get players curr-player)]
   (compare-and-set!
     player-store
     players
     (merge
       players
       (reduce
         (fn [m [k v]] (assoc m k (assoc v :position (inc (:position v)))))
         {}
         (filter (fn [[k v]] (<= opponent-pos (:position v) (dec (:position self)))) players))
       {curr-player (assoc (get players curr-player) :position opponent-pos)}))))

(defn challenge-win [request]
  (let [challenger (get-in request [:form-params :challenger])
        challengee (get-in request [:form-params :challengee])]
    (do
      (challenge-complete-win (read-string challengee) (read-string challenger))
      {:status 200
       :body @player-store
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
              ["/challenge" :post (conj [(body-params/body-params)] `issue-challenge)]
              ["/challenge-win" :post (conj [(body-params/body-params)] `challenge-win)]})

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
