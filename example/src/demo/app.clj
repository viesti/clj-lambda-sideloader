(ns demo.app)

(defn main []
  (let [message "Hello from application entrypoint! 18"]
    (println message)
    message))

(defn warmup []
  (let [message "Warming up"]
    (println message)
    message))
