(ns ^:figwheel-no-load top-ladder.dev
  (:require
    [top-ladder.core :as core]
    [devtools.core :as devtools]))


(enable-console-print!)

(devtools/install!)

(core/init!)
