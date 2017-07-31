(ns top-ladder.core
    (:require
      [reagent.core :as r]))

(def player-self (r/atom {:position 6 :in-challenge true}))

(def names '["William" "Kaho" "Michael" "Tom" "Trevor" "Clive" "Colin"])

(defn challenge-player [id name] (js/confirm (str "Do you want to challenge " name "?")))

(defn can-challenge? [my-pos other-pos] (< 0 (- my-pos other-pos) 5))

(def player-store
  (r/atom
    (map
      (fn [name id]
        (let [pos (- (count names) id)] {:name name :id id :position pos}))
      names
      (range (count names)))))

;; -------------------------
;; Views

(defn challenge-list [players]
  [:ul
   (for [p players]
     ^{:key (:id p)}
     [:li
      (:name p)
      (if (can-challenge? (:position @player-self) (:position p))
        [:button {:on-click #(challenge-player (:id p) (:name p))} "Challenge"]
        "")])])

(defn ladder-list [players]
  [:ul
   (for [p players]
     ^{:key (:id p)} [:li (:name p)])])

(defn ladder-display []
  [:div
   "Players"
   (if (:in-challenge @player-self)
     [ladder-list (sort-by :position @player-store)]
     [challenge-list (sort-by :position @player-store)])])

(defn challenge-info [] [:div "You are currently engaged in a challenge"])

(defn challenge-display []
  (if (:in-challenge @player-self) [challenge-info]))

(defn home-page []
  [:div [:h2 "Top Ladder"] [ladder-display] [challenge-display]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
