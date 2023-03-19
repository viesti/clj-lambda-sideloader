(ns demo.handler
  (:require [clj-lambda-reload.core :as core]
            [demo.app :as app]))

(gen-class
  :name "demo.handler"
  :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler
               org.crac.Resource]
  :post-init register-crac)

(defn -handleRequest [this in out ctx]
  (core/sideload 'demo.app)
  (let [result (app/main)]
    (spit out result)))

;; crac stuff

(defn -register-crac [this]
  (.register (org.crac.Core/getGlobalContext) this))

(defn -beforeCheckpoint [this context]
  (println "Before checkpoint")
  #_(core/sideload)
  (app/warmup)
  (println "Before checkpoint done"))

(defn -afterRestore [this context]
  (println "After restore")
  (println "After restore done"))
