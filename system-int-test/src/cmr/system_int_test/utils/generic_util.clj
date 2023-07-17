(ns cmr.system-int-test.utils.generic-util
  "Utility functions and definitions for use by generic document pipeline tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.config :as transmit-config]))

(def grid-good (-> "schemas/grid/v0.0.1/metadata.json"
                   (jio/resource)
                   (slurp)
                   (json/parse-string true)))

(def data-quality-summary (-> "schemas/data-quality-summary/v1.0.0/metadata.json"
                              (jio/resource)
                              (slurp)
                              (json/parse-string true)))

(def order-option (-> "schemas/order-option/v1.0.0/metadata.json"
                      (jio/resource)
                      (slurp)
                      (json/parse-string true)))

(def collection-draft (-> "schemas/collection-draft/v1.0.0/metadata.json"
                          (jio/resource)
                          (slurp)
                          (json/parse-string true)))

(defn grant-all-drafts-fixture
  "A test fixture that grants all users the ability to create and modify drafts."
  [providers guest-permissions registered-permissions]
  (fn [f]
    (let [providers (for [[provider-guid provider-id] providers]
                      {:provider-guid provider-guid
                       :provider-id provider-id})]
      (doseq [provider-map providers]
        ;; grant PROVIDER_CONTEXT permission for each provider.
        (echo-util/grant-provider-context (sys/context)
                                          (:provider-id provider-map)
                                          guest-permissions
                                          registered-permissions)))
    (f)))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([token provider-id native-id concept-type]
   (generic-request token provider-id native-id concept-type nil :get))
  ([token provider-id native-id concept-type document method]
   (let [headers (if token
                   {"Accept" "application/json"
                    transmit-config/token-header token}
                   {"Accept" "application/json"})]
     (client/request
      {:method method
       :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
       :connection-manager (sys/conn-mgr)
       :body (when document (json/generate-string document))
       :throw-exceptions false
       :headers headers}))))

(defn ingest-generic-document
  "A wrapper function for generic-request, and returns the concept ingested."
  ([token provider-id native-id concept-type document]
   (ingest-generic-document token provider-id native-id concept-type document :get))
  ([token provider-id native-id concept-type document method]
   (json/parse-string
    (:body (generic-request
            token provider-id native-id (name concept-type) document method))
    true)))
