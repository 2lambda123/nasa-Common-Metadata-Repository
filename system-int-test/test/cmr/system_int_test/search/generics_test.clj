(ns cmr.system-int-test.search.generics-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.system-int-test.utils.generic-util :as gen-util]
   [cmr.transmit.config :as transmit-config])
  (:import
   [java.util UUID]))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})]))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type provider-id native-id] (generic-request concept-type provider-id native-id nil :get))
  ([concept-type provider-id native-id document method]
  (-> {:method method
       :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
       :connection-manager (system/conn-mgr)
       :body (when document (json/generate-string document))
       :throw-exceptions false
       :headers {"Accept" "application/json"
                 transmit-config/token-header
                 (transmit-config/echo-system-token)}}
      (clj-http.client/request))))

(defn search-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type-ext params]
   (-> {:method :get
        :url (format "%s%s?%s" (url-helper/search-root) concept-type-ext params)
        :connection-manager (system/conn-mgr)
        :throw-exceptions false
        :headers { transmit-config/token-header
                  (transmit-config/echo-system-token)}}
       (clj-http.client/request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-search-results
  "Test that a Generic can be searched and have the search results use XML."

  (let [native-id (format "Generic-Test-%s" (UUID/randomUUID))
        ;generic-requester (partial generic-request "grid" "PROV1" native-id)
        generic-requester (partial gen-util/generic-request
                                   (transmit-config/echo-system-token)
                                   "PROV1"
                                   native-id
                                   "grid")
        good-generic-requester (partial generic-requester gen-util/grid-good)
        post-results (good-generic-requester :post)]
    (index/wait-until-indexed)

    (testing "Check that test the document ingested before going forward with tests"
      (is (= 201 (:status post-results))) "failed to ingest test record")

    (testing "Test that generics can use XML search results."
      (let [results (search-request "grids" "name=Grid-A7-v1")
            status (:status results)
            body (:body results)]
        (is (string/includes? body "<name>Grid-A7-v1</name>") "record not found")
        (is (= 200 status) "wrong http status")))

    (testing "Test that generics can use JSON search results."
      (let [results (search-request "grids.json" "name=Grid-A7-v1")
            status (:status results)
            body (json/parse-string (:body results) true)]
        (is (some? (:concept_id (first (:items body)))) "no concept id")
        (is (= 200 status) "wrong http status")))

    ;; TODO: Generic work: add tests for dynamic fields
    (comment testing "Test that generics can use JSON search results."
             (let [results (search-request "grids.json" "longname=Grid A7")
                   status (:status results)
                   body (json/parse-string (:body results) true)]
               (is (some? (:concept_id (first (:items body)))) "no concept id")
               (is (= 200 status) "wrong http status")))

    (testing "Test that generics can use UMM_JSON search results."
      (let [results (search-request "grids.umm_json" "name=Grid-A7-v1")
            status (:status results)
            body (json/parse-string (:body results) true)]
        (is (some? (:meta (first (:items body)))) "did not find a meta tag")
        (is (= 200 status) "wrong http status")))

    (testing "Test that generics will not work with bad parameters"
      (let [results (search-request "grids.json" "fake=parameter")
            status (:status results)
            body (json/parse-string (:body results) true)]
        (is (= 500 status) "wrong http status")))))
