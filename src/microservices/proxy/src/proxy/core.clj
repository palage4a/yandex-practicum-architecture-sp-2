(ns proxy.core
  (:require [org.httpkit.server :as server]
            [compojure.core :refer [GET ANY defroutes]]
            ;; [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            ;; [ring.util.response :as response]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:gen-class))

;; Configuration from environment variables
(def config
  {:port (or (some-> (System/getenv "PORT") Integer/parseInt) 8000)
   :monolith-url (or (System/getenv "MONOLITH_URL") "http://monolith:8080")
   :movies-service-url (or (System/getenv "MOVIES_SERVICE_URL") "http://movies-service:8081")
   :events-service-url (or (System/getenv "EVENTS_SERVICE_URL") "http://events-service:8082")
   :gradual-migration (Boolean/parseBoolean (or (System/getenv "GRADUAL_MIGRATION") "true"))
   :movies-migration-percent (or (some-> (System/getenv "MOVIES_MIGRATION_PERCENT") Integer/parseInt) 50)})

(defn get-service-url
  "Determine which service to use for movies based on migration configuration"
  [path method]
  (if (and (:gradual-migration config)
           (or (= path "/api/movies")
               (str/starts-with? path "/api/movies/"))
           (or (= method :get) (= method :post)))
    (if (< (rand 100) (:movies-migration-percent config))
      (:movies-service-url config)
      (:monolith-url config))
    (:monolith-url config)))

(defn proxy-request
  "Proxy request to appropriate service"
  [req service-url]
  (try
    (let [url (str service-url (:uri req))
          method (:request-method req)
          headers (-> (:headers req)
                      (dissoc "Host" "Content-Length")
                      (assoc "Content-Type" "application/json"))
          body (when-let [body-data (:body req)]
                 (slurp body-data))

          proxy-response (http/request {:method method
                                  :url url
                                  :headers headers
                                  :body body
                                  :query-params (:query-params req)
                                  :throw false
                                  :as :stream})]

      {:status (:status proxy-response)
       :headers (dissoc (:headers proxy-response) "Transfer-Encoding")
       :body (:body proxy-response)})
    (catch Exception e
      (log/error "Error proxying request:" (.getMessage e))
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:error "Internal server error"})})))

(defn handle-movies-request
  "Handle movies requests with gradual migration"
  [req]
  (let [service-url (get-service-url (:uri req) (:request-method req))]
    (log/info (str "Proxying movies request to: " service-url))
    (proxy-request req service-url)))

(defn handle-events-request
  "Handle events requests - always go to events service"
  [req]
  (proxy-request req (:events-service-url config)))

(defn handle-monolith-request
  "Handle all other requests - go to monolith"
  [req]
  (proxy-request req (:monolith-url config)))

;; Health check endpoints
(defn health-check
  "API Gateway health check"
  [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Strangler Fig Proxy is healthy"})

(defn movies-health-check
  "Movies service health check"
  [req]
  (try
    (let [response (http/get (str (:movies-service-url config) "/api/movies/health")
                             {:throw false
                              :as :json})]
      {:status (:status response)
       :headers {"Content-Type" "application/json"}
       :body (:body response)})
    (catch Exception e
      {:status 503
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:status false})})))

(defn events-health-check
  "Events service health check"
  [_]
  (try
    (let [response (http/get (str (:events-service-url config) "/api/events/health")
                             {:throw false
                              :as :json})]
      {:status (:status response)
       :headers {"Content-Type" "application/json"}
       :body (:body response)})
    (catch Exception e
      {:status 503
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:status false})})))

;; Routes definition
(defroutes app-routes
  ;; Health checks
  (GET "/health" [] health-check)
  (GET "/api/movies/health" [] movies-health-check)
  (GET "/api/events/health" [] events-health-check)

  ;; Movies endpoints with gradual migration
  (ANY "/api/movies" [] handle-movies-request)
  (ANY "/api/movies/*" [] handle-movies-request)

  ;; Events endpoints - always go to events service
  (ANY "/api/events/*" [] handle-events-request)

  ;; All other endpoints go to monolith
  (ANY "/*" [] handle-monolith-request))

;; Middleware configuration
(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (wrap-params)))

(defn -main
  "Main entry point for the API Gateway"
  [& _]
  (let [port (:port config)]
    (log/info (str "Starting API Gateway on port " port))
    (log/info (str "Configuration: " config))

    (server/run-server app {:port port})

    (log/info (str "API Gateway started successfully on port " port))))