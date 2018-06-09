(ns ultimate-ladder.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [ultimate-ladder.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.format-params :refer [wrap-restful-params wrap-clojure-params]]))

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta/css/bootstrap.min.css")])

(defn loading-page []
  (html5
    (head)
    [:body {:class "body-container"}
     mount-target
     (include-js "/js/app.js")
     (include-js "https://code.jquery.com/jquery-3.2.1.slim.min.js")
     (include-js "https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.11.0/umd/popper.min.js")
     (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta/js/bootstrap.min.js")]))

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
  (let [challenger (get-in request [:edn-params :challenger])
        challengee (get-in request [:edn-params :challengee])]
    (do
      (prn request)
      (new-challenge (read-string challenger) (read-string challengee))
      {:status 200
       :body (:challenges ladder-store)
       :headers {"Content-Type" "application/edn"}})))



(defroutes routes
  (GET "/" [] (loading-page))
  (GET "/about" [] (loading-page))

  (wrap-restful-format (GET "/ladder" [] {:status 200 :headers {"Content-Type" "application/edn"} :body @ladder-store}) :formats [:edn])
  (POST "/challenge" request (str request))

  (resources "/")
  (not-found "Not Found"))


(def app (wrap-middleware #'routes))
