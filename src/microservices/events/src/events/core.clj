(ns events.core
  (:require [org.httpkit.server :as server]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response created]]
            [cheshire.core :as json]
            [jackdaw.client :as jc]
            [jackdaw.serdes :refer [string-serde]]
            [clojure.tools.logging :as log])
  (:gen-class))

(def config
  {:port (or (some-> (System/getenv "PORT") Integer/parseInt) 8082)
   :kafka-brokers (or (System/getenv "KAFKA_BROKERS") "localhost:9092")})

(def kafka-config
  {"bootstrap.servers" (:kafka-brokers config)
   "key.serializer" "org.apache.kafka.common.serialization.StringSerializer"
   "value.serializer" "org.apache.kafka.common.serialization.StringSerializer"
   "acks" "all"})

(defonce kafka-producer (atom nil))

(defn init-kafka!
  "Initialize Kafka producer and create topics if needed"
  []
  (try
    (log/info "Initializing Kafka producer with brokers:" (:kafka-brokers config))

    ; Initialize producer
    (reset! kafka-producer (jc/producer kafka-config))

    (log/info "Kafka producer initialized successfully")
    (catch Exception e
      (log/error "Failed to initialize Kafka:" (.getMessage e))
      (throw e))))

(defn close-kafka!
  "Close Kafka connections"
  []
  (when @kafka-producer
    ;; (jc/close @kafka-producer)
    (reset! kafka-producer nil)))

(defn topic-config [topic]
  {:topic-name topic
   :key-serde (string-serde)
   :value-serde (string-serde)})

(defn send-to-kafka
  "Send event to Kafka topic"
  [topic event]
  (try
    (when-let [producer @kafka-producer]
      (let [metadata @(jc/produce! producer (topic-config topic) (json/generate-string event))]
        {:status "success"
         :partition (:partition metadata)
         :offset (:offset metadata)
         :event event}))
    (catch Exception e
      (log/error "Kafka error:" (.getMessage e))
      {:status "error" :error "Failed to send event to Kafka"})))

(defn validate-movie-event [event]
  (and (get event "movie_id")
       (get event "title")
       (get event "action")))

(defn validate-user-event [event]
  (and (get event "user_id")
       (get event "action")
       (get event "timestamp")))

(defn validate-payment-event [event]
  (and (get event "payment_id")
       (get event "user_id")
       (get event "amount")
       (get event "status")
       (get event "timestamp")))

(defn create-event-response [result]
  (if (= "success" (:status result))
    (created "" (json/generate-string result)) ; HTTP 201 for successful creation
    {:status 500 :body (json/generate-string {:error "Failed to process event"})}))

(defroutes app-routes
  (GET "/api/events/health" []
    (log/info "Health check requested")
    (response {:status true}))

  (POST "/api/events/movie" {:keys [body]}
    (log/info "Received movie event:" body)
    (if (validate-movie-event body)
      (let [event (assoc body
                         "type" "movie"
                         "id" (str "movie-" (get body "movie_id") "-" (get body "action") "-" (System/currentTimeMillis))
                         "timestamp" (str (java.time.Instant/now)))]
        (->> event
            (send-to-kafka "movie-events")
            create-event-response))
      {:status 400 :body (json/generate-string {:error "Invalid movie event data: missing required fields"})}))

  (POST "/api/events/user" {:keys [body]}
    (log/info "Received user event:" body)
    (if (validate-user-event body)
      (let [event (assoc body
                         "type" "user"
                         "id" (str "user-" (get body "user_id") "-" (get body "action") "-" (System/currentTimeMillis)))]
        (->> event
            (send-to-kafka "user-events")
            create-event-response))
      {:status 400 :body (json/generate-string {:error "Invalid user event data: missing required fields"})}))

  (POST "/api/events/payment" {:keys [body]}
    (log/info "Received payment event:" body)
    (if (validate-payment-event body)
      (let [event (assoc body
                         "type" "payment"
                         "id" (str "payment-" (get body "payment_id") "-" (get body "status") "-" (System/currentTimeMillis)))]
        (->> event
            (send-to-kafka "payment-events")
            create-event-response))
      {:status 400 :body (json/generate-string {:error "Invalid payment event data: missing required fields"})}))

  (route/not-found
   {:status 404 :body (json/generate-string {:error "Endpoint not found"})}))

(def app
  (-> app-routes
      (wrap-json-body {:keywords? false :bigdecimals? true})
      (wrap-json-response)
      (wrap-params)))

(defn -main [& args]
  (try
    (init-kafka!)
    (log/info "Starting Events Service on port" (:port config))
    (log/info "Kafka brokers:" (:kafka-brokers config))

    (server/run-server app {:port (:port config)})

    (catch Exception e
      (log/error "Failed to start application:" (.getMessage e))
      (System/exit 1))))

; Add shutdown hook for graceful shutdown
(.addShutdownHook (Runtime/getRuntime)
                  (Thread. ^Runnable (fn []
                                       (log/info "Shutting down Events Service...")
                                       (close-kafka!))))