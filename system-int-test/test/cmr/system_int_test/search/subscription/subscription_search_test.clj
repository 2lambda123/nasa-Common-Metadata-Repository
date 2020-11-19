(ns cmr.system-int-test.search.subscription.subscription-search-test
  "This tests searching subscriptions."
  (:require
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac-util]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.subscription-util :as subscriptions]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"
                                       "provguid2" "PROV2"
                                       "provguid3" "PROV3"
                                       "provguid4" "PROV4"})
                (subscriptions/grant-all-subscription-fixture
                  {"provguid1" "PROV1" "provguid2" "PROV2"} [:read :update] [:read :update])
                ;; No read permission granted for guest for PROV3
                (subscriptions/grant-all-subscription-fixture
                  {"provguid3" "PROV3"} [:update] [:read :update])
                ;; No read permission granted for any user_types for PROV4.
                (subscriptions/grant-all-subscription-fixture
                  {"provguid4" "PROV4"} [:update] [:update])]))

(deftest search-for-subscriptions-test-with-subscriber
  (doseq [acl (:items
               (ac-util/search-for-acls (system/context)
                                        {:provider "PROV1"
                                         :target ["SUBSCRIPTION_MANAGEMENT" "INGEST_MANAGEMENT_ACL"]}
                                        {:token "mock-echo-system-token"}))
          :let [concept-id (:concept_id acl)]]
    (echo-util/ungrant (system/context) concept-id))
  (ac-util/wait-until-indexed)
  (let [subscriber-token (echo-util/login (system/context) "subscriber")
        guest-token (echo-util/login-guest (system/context))
        subscription (subscriptions/ingest-subscription (subscriptions/make-subscription-concept
                                                         {:Name "test subscription"
                                                          :SubscriberId "subscriber"
                                                          :native-id "test-subscription-native-id"})
                                                        {:token "mock-echo-system-token"})]
    (index/wait-until-indexed)
    (is (= 201 (:status subscription)))
    (are3 [expected-subscriptions query]
      (do
        (testing "XML references format"
          (data2-core/assert-refs-match expected-subscriptions (subscriptions/search-refs query)))
        (testing "JSON format"
          (subscriptions/assert-subscription-search expected-subscriptions (subscriptions/search-json query))))

      "Find all returns nothing for guest"
      [] {:token guest-token}

      "Find all returns all for subscriber"
      [subscription] {:token subscriber-token}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match returns nothing for guest"
      [] {:name "test subscription" :token guest-token}

      "By name case sensitive - exact match returns the right subscription for registered user"
      [subscription] {:name "test subscription" :token subscriber-token})))

(deftest search-for-subscriptions-user-read-permission-test
  ;; SUBSCRIPTION_MANAGEMENT ACL grants only update permission for guest on PROV3,
  ;; so subscription for PROV3 can be ingested but can't be searched by guest.
  ;; but it's searchable by registered users because read permission is granted.
  (let [subscription3 (subscriptions/ingest-subscription-with-attrs {:native-id "Sub3"
                                                                     :Name "Subscription3"
                                                                     :SubscriberId "SubId3"
                                                                     :CollectionConceptId "C1-PROV3"
                                                                     :provider-id "PROV3"})
        guest-token (echo-util/login-guest (system/context))
        user1-token (echo-util/login (system/context) "user1")]
    (index/wait-until-indexed)
    (is (= 201 (:status subscription3)))
    (are3 [expected-subscriptions query]
      (do
        (testing "XML references format"
          (data2-core/assert-refs-match expected-subscriptions (subscriptions/search-refs query)))
        (testing "JSON format"
          (subscriptions/assert-subscription-search expected-subscriptions (subscriptions/search-json query))))

      "Find all returns nothing for guest"
      [] {:token guest-token}

      "Find all returns all for registered user"
      [subscription3] {:token user1-token}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match returns nothing for guest"
      [] {:name "Subscription3" :token guest-token}

      "By name case sensitive - exact match returns the right subscription for registered user"
      [subscription3] {:name "Subscription3" :token user1-token})))

(deftest search-for-subscriptions-group-read-permission-test
  (let [subscription4 (subscriptions/ingest-subscription-with-attrs {:native-id "Sub4"
                                                                     :Name "Subscription4"
                                                                     :SubscriberId "SubId4"
                                                                     :CollectionConceptId "C1-PROV4"
                                                                     :provider-id "PROV4"})
        ;; create two groups
        group1-concept-id (echo-util/get-or-create-group (system/context) "group1")
        group2-concept-id (echo-util/get-or-create-group (system/context) "group2")
        ;; create two user tokens, associated with two groups
        user1g-token (echo-util/login (system/context) "user1g" [group1-concept-id])
        user2g-token (echo-util/login (system/context) "user2g" [group2-concept-id])]
    (index/wait-until-indexed)

    ;; grant one group read permission
    (echo-util/grant-all-subscription-group-sm (system/context) "PROV4" group1-concept-id [:read])

    (is (= 201 (:status subscription4)))
    (are3 [expected-subscriptions query]
      (do
        (testing "XML references format"
          (data2-core/assert-refs-match expected-subscriptions (subscriptions/search-refs query)))
        (testing "JSON format"
          (subscriptions/assert-subscription-search expected-subscriptions (subscriptions/search-json query))))

      "Find all returns nothing for user2g"
      [] {:token user2g-token}

      "Find all returns all for user1g"
      [subscription4] {:token user1g-token}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match returns nothing for user2g"
      [] {:name "Subscription4" :token user2g-token}

      "By name case sensitive - exact match returns the right subscription for user1g"
      [subscription4] {:name "Subscription4" :token user1g-token})))

(deftest search-for-subscriptions-validation-test
  (testing "Unrecognized parameters"
    (is (= {:status 400
            :errors ["Parameter [foo] was not recognized."]}
           (subscriptions/search-refs {:foo "bar"}))))

  (testing "Unsupported sort-key parameters"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting subscriptions."]}
           (subscriptions/search-refs {:sort-key "concept_id"}))))

  (testing "Search with wildcards in concept_id param not supported."
    (is (= {:status 400
            :errors ["Concept-id [SUB*] is not valid."
                     "Option [pattern] is not supported for param [concept_id]"]}
           (subscriptions/search-refs {:concept-id "SUB*" "options[concept-id][pattern]" true}))))

  (testing "Search with ignore_case in concept_id param not supported."
    (is (= {:status 400
            :errors ["Option [ignore_case] is not supported for param [concept_id]"]}
           (subscriptions/search-refs
            {:concept-id "SUB1000-PROV1" "options[concept-id][ignore-case]" true}))))

  (testing "Default subscription search result format is XML"
    (let [{:keys [status headers]} (search/find-concepts-in-format nil :subscription {})]
      (is (= 200 status))
      (is (= "application/xml; charset=utf-8" (get headers "Content-Type")))))

  (testing "Unsuported result format in headers"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for subscriptions."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format :atom+xml :subscription {})))))

  (testing "Unsuported result format in url extension"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for subscriptions."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format
             nil :subscription {} {:url-extension "atom"}))))))

(deftest search-for-subscriptions-test
  (let [subscription1 (subscriptions/ingest-subscription-with-attrs {:native-id "Sub1"
                                                                     :Name "Subscription1"
                                                                     :SubscriberId "SubId1"
                                                                     :CollectionConceptId "C1-PROV1"
                                                                     :provider-id "PROV1"})
        subscription2 (subscriptions/ingest-subscription-with-attrs {:native-id "sub2"
                                                                     :Name "Subscription2"
                                                                     :SubscriberId "SubId2"
                                                                     :CollectionConceptId "C2-PROV1"
                                                                     :provider-id "PROV1"})
        subscription3 (subscriptions/ingest-subscription-with-attrs {:native-id "sub3"
                                                                     :Name "Subscrition3"
                                                                     :SubscriberId "SubId3"
                                                                     :CollectionConceptId "C3-PROV2"
                                                                     :provider-id "PROV2"})
        subscription4 (subscriptions/ingest-subscription-with-attrs {:native-id "sb4"
                                                                     :Name "Subother"
                                                                     :SubscriberId "SubId4"
                                                                     :CollectionConceptId "C4-PROV2"
                                                                     :provider-id "PROV2"})
        prov1-subscriptions [subscription1 subscription2]
        prov2-subscriptions [subscription3 subscription4]
        all-subscriptions (concat prov1-subscriptions prov2-subscriptions)]
    (index/wait-until-indexed)

    (are3 [expected-subscriptions query]
      (do
        (testing "XML references format"
          (data2-core/assert-refs-match expected-subscriptions (subscriptions/search-refs query)))
        (testing "JSON format"
          (subscriptions/assert-subscription-search expected-subscriptions (subscriptions/search-json query))))

      "Find all"
      all-subscriptions {}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match"
      [subscription1]
      {:name "Subscription1"}

      "By name case sensitive, default ignore-case true"
      [subscription1]
      {:name "subscription1"}

      "By name ignore case false"
      []
      {:name "subscription1" "options[name][ignore-case]" false}

      "By name ignore case true"
      [subscription1]
      {:name "subscription1" "options[name][ignore-case]" true}

      "By name Pattern, default false"
      []
      {:name "*other"}

      "By name Pattern true"
      [subscription4]
      {:name "*other" "options[name][pattern]" true}

      "By name Pattern false"
      []
      {:name "*other" "options[name][pattern]" false}

      "By multiple names"
      [subscription1 subscription2]
      {:name ["Subscription1" "subscription2"]}

      "By multiple names with options"
      [subscription1 subscription4]
      {:name ["Subscription1" "*other"] "options[name][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; subscriberid Param
      "By subscriberid case sensitive - exact match"
      [subscription1]
      {:subscriber-id "SubId1"}

      "By subscriberid case sensitive, default ignore-case true"
      [subscription1]
      {:subscriber-id "subId1"}

      "By subscriberid ignore case false"
      []
      {:subscriber-id "subId1" "options[subscriber-id][ignore-case]" false}

      "By subscriberid ignore case true"
      [subscription1]
      {:subscriber-id "subId1" "options[subscriber-id][ignore-case]" true}

      "By subscriberid Pattern, default false"
      []
      {:subscriber-id  "*4"}

      "By subscriberid Pattern true"
      [subscription4]
      {:subscriber-id "*4" "options[subscriber-id][pattern]" true}

      "By subscriberid Pattern false"
      []
      {:subscriber-id "*4" "options[subscriber-id][pattern]" false}

      "By multiple subscriberids"
      [subscription1 subscription2]
      {:subscriber-id ["SubId1" "subId2"]}

      "By multiple subscriberids with options"
      [subscription1 subscription4]
      {:subscriber-id ["SubId1" "*4"] "options[subscriber-id][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; collection-concept-id Param
      "By name case sensitive - exact match"
      [subscription1]
      {:collection-concept-id "C1-PROV1"}

      "By collection-concept-id case sensitive, default ignore-case true"
      [subscription1]
      {:collection-concept-id "c1-prov1"}

      "By collection-concept-id ignore case false"
      []
      {:collection-concept-id "c1-prov1" "options[collection-concept-id][ignore-case]" false}

      "By collection-concept-id ignore case true"
      [subscription1]
      {:collection-concept-id "c1-prov1" "options[collection-concept-id][ignore-case]" true}

      "By collection-concept-id Pattern, default false"
      []
      {:collection-concept-id  "c4*"}

      "By collection-concept-id Pattern true"
      [subscription4]
      {:collection-concept-id "c4*" "options[collection-concept-id][pattern]" true}

      "By collection-concept-id Pattern false"
      []
      {:collection-concept-id "c4*" "options[collection-concept-id][pattern]" false}

      "By multiple collection-concept-ids"
      [subscription1 subscription2]
      {:collection-concept-id ["C1-PROV1" "c2-prov1"]}

      "By multiple collection-concept-ids with options"
      [subscription1 subscription4]
      {:collection-concept-id ["C1-prov1" "c4*"] "options[collection-concept-id][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; provider Param
      "By provider - exact match"
      prov1-subscriptions
      {:provider "PROV1"}

      "By provider, default ignore-case true"
      prov1-subscriptions
      {:provider "prov1"}

      "By provider ignore case false"
      []
      {:provider "prov1" "options[provider][ignore-case]" false}

      "By provider ignore case true"
      prov1-subscriptions
      {:provider "prov1" "options[provider][ignore-case]" true}

      "By provider Pattern, default false"
      []
      {:provider "PROV?"}

      "By provider Pattern true"
      all-subscriptions
      {:provider "PROV?" "options[provider][pattern]" true}

      "By provider Pattern false"
      []
      {:provider "PROV?" "options[provider][pattern]" false}

      "By multiple providers"
      prov2-subscriptions
      {:provider ["PROV2" "PROV3"]}

      "By multiple providers with options"
      all-subscriptions
      {:provider ["PROV1" "*2"] "options[provider][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; native-id Param
      "By native-id case sensitive - exact match"
      [subscription1]
      {:native-id "SUB1"}

      "By native-id case sensitive, default ignore-case true"
      [subscription1]
      {:native-id "sub1"}

      "By native-id ignore case false"
      []
      {:native-id "sub1" "options[native-id][ignore-case]" false}

      "By native-id ignore case true"
      [subscription1]
      {:native-id "sub1" "options[native-id][ignore-case]" true}

      "By native-id Pattern, default false"
      []
      {:native-id "sub*"}

      "By native-id Pattern true"
      [subscription1 subscription2 subscription3]
      {:native-id "sub*" "options[native-id][pattern]" true}

      "By native-id Pattern false"
      []
      {:native-id "sub*" "options[native-id][pattern]" false}

      "By multiple native-ids"
      [subscription1 subscription2]
      {:native-id ["SUB1" "sub2"]}

      "By multiple native-ids with options"
      [subscription1 subscription4]
      {:native-id ["SUB1" "sb*"] "options[native-id][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; concept-id Param
      "By concept-id - single"
      [subscription1]
      {:concept-id (:concept-id subscription1)}

      "By concept-id - multiple"
      [subscription1 subscription2]
      {:concept-id [(:concept-id subscription1) (:concept-id subscription2)]}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; Combination of params
      "Combination of params"
      [subscription3]
      {:native-id "sub*" :provider "PROV2" "options[native-id][pattern]" true})))

(deftest subscription-search-sort
  (let [subscription1 (subscriptions/ingest-subscription-with-attrs {:native-id "sub1"
                                                                     :Name "subscription"
                                                                     :provider-id "PROV2"})
        subscription2 (subscriptions/ingest-subscription-with-attrs {:native-id "sub2"
                                                                     :Name "Subscription 2"
                                                                     :provider-id "PROV1"})
        subscription3 (subscriptions/ingest-subscription-with-attrs {:native-id "sub3"
                                                                     :Name "a subscription"
                                                                     :provider-id "PROV1"})
        subscription4 (subscriptions/ingest-subscription-with-attrs {:native-id "sub4"
                                                                     :Name "subscription"
                                                                     :provider-id "PROV1"})]
    (index/wait-until-indexed)

    (are3 [sort-key expected-subscriptions]
      (is (data2-core/refs-match-order?
           expected-subscriptions
           (subscriptions/search-refs {:sort-key sort-key})))

      "Default sort"
      nil
      [subscription3 subscription4 subscription1 subscription2]

      "Sort by name"
      "name"
      [subscription3 subscription4 subscription1 subscription2]

      "Sort by name descending order"
      "-name"
      [subscription2 subscription4 subscription1 subscription3]

      "Sort by provider id"
      "provider"
      [subscription2 subscription3 subscription4 subscription1]

      "Sort by provider id descending order"
      "-provider"
      [subscription1 subscription2 subscription3 subscription4]

      "Sort by name ascending then provider id ascending explicitly"
      ["name" "provider"]
      [subscription3 subscription4 subscription1 subscription2]

      "Sort by name ascending then provider id descending order"
      ["name" "-provider"]
      [subscription3 subscription1 subscription4 subscription2]

      "Sort by name then provider id descending order"
      ["-name" "-provider"]
      [subscription2 subscription1 subscription4 subscription3])))
