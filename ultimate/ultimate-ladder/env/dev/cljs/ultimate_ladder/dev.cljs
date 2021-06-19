(ns ^:figwheel-no-load ultimate-ladder.dev
  (:require
    [ultimate-ladder.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)
