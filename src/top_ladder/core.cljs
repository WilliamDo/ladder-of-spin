(ns top-ladder.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.reader]
    [reagent.core :as r]
    [cljs-http.client :as http]
    [cljs.core.async :refer [<!]]))

(def player-store (r/atom {}))

(def player-self (r/atom {:id 4}))

(defn challenge-player [id name]
  (let [confirm-challenge (js/confirm (str "Do you want to challenge " name "?"))]
    (if confirm-challenge
      (go (let [response (<! (http/post "http://localhost:8080/challenge" {:form-params {:challenger (:id @player-self) :challengee id}}))]
          (prn response)
          (prn (:body response))
          (swap! player-self #(assoc-in % [:challenge :opponent] id)))))))

(defn can-challenge? [my-pos other-pos] (< 0 (- my-pos other-pos) 5))

(defn clear-challenge [] (swap! player-self #(dissoc % :challenge)))

(defn challenge-win [id]
  (go (let [response (<! (http/post "http://localhost:8080/confirm-result" {:form-params {:player (:id @player-self) :opponent id :result :win}}))]
      (reset! player-store (get-in response [:body :players]))
      (prn (get-in response [:body :challenges (:id @player-self)]))
      (swap! player-self assoc :challenge (get-in response [:body :challenges (:id @player-self)])))))

(defn challenge-loss [id]
  (go (let [response (<! (http/post "http://localhost:8080/confirm-result" {:form-params {:player (:id @player-self) :opponent id :result :loss}}))]
      (reset! player-store (get-in response [:body :players]))
      (prn (get-in response [:body :challenges (:id @player-self)]))
      (swap! player-self assoc :challenge (get-in response [:body :challenges (:id @player-self)])))))

(defn refresh []
  (go (let [response (<! (http/get "http://localhost:8080/ladder"))]
      (prn response)
      (prn (:body response))
      (reset! player-store (get-in response [:body :players]))
      (swap! player-self assoc :challenge (get-in response [:body :challenges (:id @player-self)])))))

;; -------------------------
;; Views

(defn challenge-list [players]
  [:ol
   (for [p players]
     ^{:key (:id p)}
     [:li
      (:name p)
      (if (can-challenge? (:position (get @player-store (:id @player-self))) (:position p))
        [:button {:on-click #(challenge-player (:id p) (:name p))} "Challenge"]
        "")])])

(defn ladder-list [players]
  (let [current-player @player-self]
    [:table {:class "table table-hover"}
     [:thead {:class "thead-inverse"} [:tr [:th "#"] [:th "Name"] [:th "Challenge"]]]
     [:tbody (for [p players]
               [:tr {:class (if (= (:id p) (:id @player-self)) "table-info")}
                [:th {:scope "row"} (:position p)]
                [:td (:name p)]
                [:td (if (and (nil? (:challenge current-player)) (can-challenge? (:position (get @player-store (:id current-player))) (:position p)))
                  [:button {:on-click #(challenge-player (:id p) (:name p)) :class "btn btn-outline-primary btn-sm"} "Challenge"])]])]]))

(defn ladder-display []
  [:div [ladder-list (sort-by :position (vals @player-store))]])

(defn challenge-info []
  (let [challenge-opponent (get-in @player-self [:challenge :opponent])
        claim (get-in @player-self [:challenge :result])]
  [:div
   "You have a pending challenge with "
   (:name (get @player-store challenge-opponent))
   [:div
    [:button {:on-click #(challenge-win challenge-opponent) :class "btn btn-outline-success btn-sm"} "Win"]
    [:button {:on-click #(challenge-loss challenge-opponent) :class "btn btn-outline-secondary btn-sm"} "Lose"]]
   [:div (if claim (str "You are claiming " claim) "You have not entered a result")]]))

(defn challenge-display []
  (if (:challenge @player-self) [challenge-info]))

(defn challenge-modal []
  (let [challenge-opponent (get-in @player-self [:challenge :opponent])
        claim (get-in @player-self [:challenge :result])]
    [:div {:class "modal fade" :id "challengeModal" :tabIndex "-1" :role "dialog" :aria-labelledby "challengeModalLabel" :aria-hidden "true"}
     [:div {:class "modal-dialog" :role "document"}
      [:div {:class "modal-content"}
       [:div {:class "modal-header"} "Challenge"]
       [:div {:class "modal-body"} "You have a pending challenge with " (:name (get @player-store challenge-opponent)) [:div (if claim (str "You are claiming " claim) "You have not entered a result")]]
       [:div {:class "modal-footer"}
        [:button {:on-click #(challenge-win challenge-opponent) :class "btn btn-outline-success btn-sm"} "Win"]
        [:button {:on-click #(challenge-loss challenge-opponent) :class "btn btn-outline-secondary btn-sm"} "Lose"]]]]]))

(defn home-page []
  [:div
   [:nav {:class "navbar navbar-dark bg-primary"}
    [:span {:class "navbar-brand mb-0"} "Top Ladder"]
    (if (:challenge @player-self) [:button {:class "btn btn-primary btn-sm" :data-toggle "modal" :data-target "#challengeModal"} "Challenge"])
    [:button {:on-click #(refresh) :class "btn btn-primary btn-sm"} "Refresh"]]

   [:div {:class "container"} [ladder-display] [challenge-modal]]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
