(ns cmr.ingest.api.subscriptions-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.subscriptions :as subscriptions]))

(deftest ingest-subscription-test
  (testing "creating a subscription"
    (let [subscription {}]
      (testing "with native-id provided uses the native-id"
        (with-redefs-fn {#'subscriptions/perform-subscription-ingest (constantly nil)
                         #'subscriptions/common-ingest-checks (constantly nil)
                         #'api-core/body->concept! (constantly {:native-id "tmp"
                                                                :metadata " {\"Name\": \"some name\"}"})
                         #'subscriptions/check-subscription-ingest-permission (fn [request-context concept provider-id]
                                                                                (is (= "given-native-id" (:native-id concept))))}
          #(subscriptions/ingest-subscription "test-provider" "given-native-id" subscription)))

      (testing "with native-id not provided generates a native-id"
        (with-redefs-fn {#'subscriptions/perform-subscription-ingest (constantly nil)
                         #'subscriptions/common-ingest-checks (constantly nil)
                         #'api-core/body->concept! (constantly {:native-id "tmp"
                                                                :metadata " {\"Name\": \"some name\"}"})
                         #'subscriptions/check-subscription-ingest-permission
                         (fn [request-context concept provider-id]
                           (is (string/starts-with? (:native-id concept) "some_name")))}
          #(subscriptions/ingest-subscription "test-provider" nil subscription))))))

(deftest generate-native-id-test
  (let [concept {:metadata
                 "{\"Name\":\"the beginning\",\"SubscriberId\":\"post-user\",\"EmailAddress\":\"someEmail@gmail.com\",\"CollectionConceptId\":\"C1200000018-PROV1\",\"Query\":\"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.0"
                 :native-id nil
                 :concept-type :subscription
                 :provider-id "PROV1"}
        native-id (subscriptions/generate-native-id concept)]
    (is (string? native-id))

    (testing "name is used as the prefix"
      (is (string/starts-with? native-id "the_beginning")))))
