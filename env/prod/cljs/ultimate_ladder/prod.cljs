(ns ultimate-ladder.prod
  (:require [ultimate-ladder.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
