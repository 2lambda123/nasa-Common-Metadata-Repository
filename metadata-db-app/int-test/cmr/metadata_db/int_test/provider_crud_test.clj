(ns cmr.metadata-db.int-test.provider-crud-test
  "Contains integration tests for CRUD of providers. Tests CRUD with various
   configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [cmr.metadata-db.int-test.utility :as test-util]
            [cmr.common.util :as util]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (test-util/reset-database-fixture))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-provider-test
  (testing "successful saves"
    (util/are3
     [provider-map]
     (let [{:keys [status]} (test-util/save-provider provider-map)]
       (do
         (is (= status 201) "Status Check")
         (is (test-util/verify-provider-was-saved provider-map) "Save Check")))

     "cmr-only false small false"
     {:provider-id "PROV1" :short-name "PROV1" :cmr-only false :small false}

     "cmr-only true small false"
     {:provider-id "PROV2" :short-name "PROV2" :cmr-only true :small false}

     "cmr-only false small true"
     {:provider-id "PROV3" :short-name "PROV3" :cmr-only false :small true}

     "cmr-only true small true"
     {:provider-id "PROV4" :short-name "PROV4" :cmr-only true :small true}

     "cmr-only and small default to false"
     {:provider-id "PROV5" :short-name "PROV5"}

     "only provider-id is present"
     {:provider-id "PROV6"}))

  (testing "save provider twice"
    (let [{:keys [status errors]} (test-util/save-provider {:provider-id "PROV1"
                                                         :short-name "S1"
                                                         :cmr-only false
                                                         :small false})]
      (is (= [409 ["Provider with provider id [PROV1] already exists."]]
             [status errors]))))
  (testing "save reserved provder is not allowed"
    (let [{:keys [status errors]} (test-util/save-provider {:provider-id "SMALL_PROV"
                                                         :short-name "S5"
                                                         :cmr-only false
                                                         :small false})]
      (is (= [400 ["Provider Id [SMALL_PROV] is reserved"]]
             [status errors]))))
  (testing "save CMR provider is not allowed"
    (let [{:keys [status errors]} (test-util/save-provider {:provider-id "CMR"
                                                         :short-name "CMR"
                                                         :cmr-only true
                                                         :small false})]
      (is (= [400 ["Provider Id [CMR] is reserved"]]
             [status errors])))))

(deftest update-provider-test
  (testing "basic update"
    (let [provider {:provider-id "PROV1"
                    :short-name "PROV1"
                    :cmr-only false
                    :small false}
          updated-provider (assoc provider :cmr-only true)]
      (test-util/save-provider provider)
      (is (test-util/verify-provider-was-saved provider) "before update")
      (test-util/update-provider updated-provider)
      (is (test-util/verify-provider-was-saved updated-provider) "after update")))
  (testing "cannot modify small field of a provider"
    (let [{:keys [status errors]} (test-util/update-provider {:provider-id "PROV1"
                                                           :short-name "S1"
                                                           :cmr-only true
                                                           :small true})]
      (is (= [400 ["Provider [PROV1] small field cannot be modified."]]
             [status errors]))))
  (testing "ignore request to modify short name of a provider"
    (let [provider {:provider-id "PROV1"
                    :short-name "S5"
                    :cmr-only true
                    :small false}
          expected (-> provider (assoc :short-name "PROV1"))]
      (test-util/update-provider provider)
      (is (test-util/verify-provider-was-saved expected) "expected provider to not change")))
  (testing "modify short name of a provider with conflict is not OK"
    (test-util/save-provider {:provider-id "PROV6"
                           :short-name "S6"
                           :cmr-only false
                           :small false})
    (let [{:keys [status errors]} (test-util/update-provider {:provider-id "PROV1"
                                                           :short-name "S6"
                                                           :cmr-only true
                                                           :small false})]
      (is (= [200 nil]
             [status errors]))))
  (testing "update non-existent provider"
    (is (= 404 (:status (test-util/update-provider {:provider-id "PROV2"
                                                 :short-name "S2"
                                                 :cmr-only true
                                                 :small false})))))
  (testing "update reserved provider is not allowed"
    (let [{:keys [status errors]} (test-util/update-provider {:provider-id "SMALL_PROV"
                                                           :short-name "S5"
                                                           :cmr-only false
                                                           :small false})]
      (is (= [400 ["Provider Id [SMALL_PROV] is reserved"]]
             [status errors]))))
  (testing "bad parameters"
    (is (= 400 (:status (test-util/update-provider {:provider-id nil
                                                 :short-name "S1"
                                                 :cmr-only true
                                                 :small false})))
        "required provider-id test")
    (is (= 400 (:status (test-util/update-provider {:provider-id "PROV1"
                                                 :short-name "S1"
                                                 :cmr-only "non-boolean-value"
                                                 :small false})))
        "cmr-only boolean test")))

(deftest get-providers-test
  (test-util/save-provider {:provider-id "PROV1"
                         :short-name "S1"
                         :cmr-only false
                         :small false})
  (test-util/save-provider {:provider-id "PROV2"
                         :cmr-only true
                         :small true})
  (let [{:keys [status providers]} (test-util/get-providers)]
    (is (= status 200))
    (is (= [{:provider-id "PROV1" :short-name "PROV1" :cmr-only false :small false}
            {:provider-id "PROV2" :short-name "PROV2" :cmr-only true :small true}]
           (sort-by :provider-id providers)))))

(deftest delete-provider-test
  (testing "delete provider"
    (test-util/save-provider {:provider-id "PROV1"
                           :short-name "S1"
                           :cmr-only false
                           :small false})
    (test-util/save-provider {:provider-id "PROV2"
                           :short-name "S2"
                           :cmr-only false
                           :small true})
    (test-util/delete-provider "PROV1")
    (let [{:keys [status providers]} (test-util/get-providers)]
      (is (= status 200))
      (is (= [{:provider-id "PROV2" :short-name "PROV2" :cmr-only false :small true}] providers))))
  (testing "delete SMALL_PROV provider"
    (let [{:keys [status errors]} (test-util/delete-provider "SMALL_PROV")]
      (is (= [400 ["Provider [SMALL_PROV] is a reserved provider of CMR and cannot be deleted."]]
             [status errors]))))
  (testing "delete non-existent provider"
    (let [{:keys [status errors]} (test-util/delete-provider "NOT_PROV")]
      (is (= [404 ["Provider with provider-id [NOT_PROV] does not exist."]]
             [status errors])))))
