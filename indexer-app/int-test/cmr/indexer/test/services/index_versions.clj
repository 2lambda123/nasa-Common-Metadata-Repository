(ns cmr.indexer.test.services.index-versions
  "Integration test for index versions during save and delete in elasticsearch"
  (:require
   [clojure.data.codec.base64 :as b64]
   [clojure.string :as s]
   [clojure.test :refer :all]
   [clojurewerkz.elastisch.rest :as esr]
   [clojurewerkz.elastisch.rest.document :as doc]
   [clojurewerkz.elastisch.rest.index :as esi]
   [cmr.common.cache :as cache]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.test.test-util :as tu]
   [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.data.index-set :as idx-set]))

(defn- es-doc
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "C1234-PROV1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        short-name "DummyShort"
        version-id "1"
        project-short-names ["ESI" "EPI" "EVI"]]
    {:concept-id concept-id
     :entry-title entry-title
     :entry-title.lowercase (s/lower-case entry-title)
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :short-name short-name
     :short-name.lowercase (s/lower-case short-name)
     :version-id version-id
     :version-id.lowercase (s/lower-case version-id)
     :project-sn2 project-short-names
     :project-sn2.lowercase (map s/lower-case project-short-names)}))

(def test-config
  "Return the configuration for elasticsearch"
  {:host "localhost"
   :port 9213
   :admin-token (str "Basic " (b64/encode (.getBytes "password")))})

(def context (atom nil))

(defn server-setup
  "Fixture that starts an instance of elastic in the JVM runs the tests and then shuts it down."
  [f]
  (let [http-port (:port test-config)
        server (lifecycle/start (elastic-server/create-server http-port 9215 "es_data/indexer_test") nil)]
    (reset! context {:system {:db {:config test-config
                                   :conn (esr/connect (str "http://localhost:" http-port))}}})
    (try
      (f)
      (finally
        (lifecycle/stop server nil)))))

;; Run once for the whole test suite
(use-fixtures
  :once
  (join-fixtures [
                  ;; Disables standard out logging during testing because it breaks the JUnit parser in bamboo.
                  tu/silence-logging-fixture
                  server-setup]))

(defn- assert-version
  "Assert the retrieved document for the given id is of the given version"
  [id version]
  (let [result (es/get-document @context "tests" "collection" id)]
    (is (= version (:_version result)))))

(defn- assert-delete
  "Assert the document with the given id is deleted"
  [id]
  (let [result (es/get-document @context "tests" "collection" id)]
    (is (nil? result))))

(defn index-setup
  "Fixture that creates an index and drops it."
  [f]
  (let [conn (get-in @context [:system :db :conn])]
    (esi/create conn "tests" :settings idx-set/collection-setting-v2 :mappings idx-set/collection-mapping)
    (try
      (f)
      (finally
        (esi/delete conn "tests")))))
;; Run once for each test to clear out data.
(use-fixtures :each index-setup)

(defn- save-document-in-elastic
  "Helper function to call elasticsearch save-document-in-elastic"
  ([context es-index es-type es-doc concept-id revision-id]
   (save-document-in-elastic context es-index es-type es-doc concept-id revision-id nil))
  ([context es-index es-type es-doc concept-id revision-id options]
   (es/save-document-in-elastic
     context [es-index] es-type es-doc concept-id revision-id revision-id options)))

(defn- delete-document-in-elastic
  ([context es-index es-type concept-id revision-id]
   (delete-document-in-elastic context es-index es-type concept-id revision-id nil))
  ([context es-index es-type concept-id revision-id options]
   (es/delete-document context es-index es-type concept-id revision-id revision-id options)))

(deftest save-with-increment-versions-test
  (testing "Save with increment versions"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 1)
    (assert-version "C1234-PROV1" 1)
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 2)
    (assert-version "C1234-PROV1" 2)
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 10)
    (assert-version "C1234-PROV1" 10)))

(deftest save-with-equal-versions-test
  (testing "Save with equal versions"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 1)
    (assert-version "C1234-PROV1" 1)
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 1)
    (assert-version "C1234-PROV1" 1)))

(deftest save-with-earlier-versions-test
  (testing "Save with earlier versions with ignore-conflict false"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 3)
    (assert-version "C1234-PROV1" 3)
    (try
      (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 2)
      (catch clojure.lang.ExceptionInfo e
        (let [type (:type (ex-data e))
              err-msg (first (:errors (ex-data e)))]
          (is (= :conflict type))
          (is (re-find #"version conflict, current \[3\], provided \[2\]" err-msg))))))
  (testing "Save with earlier versions with ignore-conflict true"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 3
                              {:ignore-conflict? true})
    (assert-version "C1234-PROV1" 3)
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 2
                              {:ignore-conflict? true})
    (assert-version "C1234-PROV1" 3)))

(deftest delete-with-increment-versions-test
  (testing "Delete with increment versions"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 1)
    (delete-document-in-elastic @context ["tests"] "collection" "C1234-PROV1" "2")
    (assert-delete "C1234-PROV1")
    (delete-document-in-elastic @context ["tests"] "collection" "C1234-PROV1" "8")
    (assert-delete "C1234-PROV1")))

(deftest delete-with-equal-versions-test
  (testing "Delete with equal versions"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 1)
    (delete-document-in-elastic @context ["tests"] "collection" "C1234-PROV1" "1")
    (assert-delete "C1234-PROV1")))

(deftest delete-with-earlier-versions-test
  (testing "Delete with earlier versions ignore-conflict false"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 2)
    (try
      (delete-document-in-elastic @context ["tests"] "collection" "C1234-PROV1" "1")
      (catch java.lang.Exception e
        (is (re-find #"version conflict, current \[2\], provided \[1\]" (.getMessage e))))))
  (testing "Delete with earlier versions ignore-conflict true"
    (save-document-in-elastic @context "tests" "collection" (es-doc) "C1234-PROV1" 2
                              {:ignore-conflict? true})
    (delete-document-in-elastic @context ["tests"] "collection" "C1234-PROV1" "1" {:ignore-conflict? true})
    (assert-version "C1234-PROV1" 2)))
