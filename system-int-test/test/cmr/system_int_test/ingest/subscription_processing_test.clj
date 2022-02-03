(ns cmr.system-int-test.ingest.subscription-processing-test
  "CMR subscription processing tests."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac-util]
   [cmr.ingest.services.subscriptions-helper :as jobs]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as data-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.subscription-util :as subscription-util]
   [cmr.transmit.access-control :as access-control]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"})
    (subscription-util/grant-all-subscription-fixture
     {"provguid1" "PROV1" "provguid2" "PROV2"}
     [:read :update]
     [:read :update])
    (subscription-util/grant-all-subscription-fixture {"provguid1" "PROV3"}
                                                      [:read]
                                                      [:read :update])
    (dev-system/freeze-resume-time-fixture)]))

(defn- create-granule-and-index
  "A utility function to reduce common code in these tests, Create a test granule and then wait for it to be indexed."
  [provider collection granule-ur]
  (let [concept-id (:concept-id collection)
        attribs {:granule-ur granule-ur :access-value 1}
        gran (data-granule/granule-with-umm-spec-collection collection concept-id attribs)
        options {:token "mock-echo-system-token"}
        result (data-core/ingest provider gran options)]
    (index/wait-until-indexed)
    result))

(defn- mock-send-subscription-emails
  "This function is used along with with-redefs to avoid sending emails in
   integration tests. If send-subscription-emails is called in tests without being mocked,
   errors while be returned when attempting to connect to the mail server in 
   postal-core/send-message."
  [context subscriber-filtered-gran-refs-list]
  (let
   [send-update-subscription-notification-time! #'jobs/send-update-subscription-notification-time!]
    (doseq [subscriber-filtered-gran-refs-tuple subscriber-filtered-gran-refs-list
            :let [[sub-id subscriber-filtered-gran-refs subscriber-id subscription] subscriber-filtered-gran-refs-tuple]]
      (when (seq subscriber-filtered-gran-refs)
        (send-update-subscription-notification-time! context sub-id)))
    subscriber-filtered-gran-refs-list))

(deftest ^:oracle subscription-job-manual-time-constraint-test
  "This test is used to validate that email-subscription-processing will use a
   valid revision-date-range when one is provided.  To set this test up, several
   granules are created on individual days and a subscription is created over
   them.  This allows the test to verify that it is pulling only the correct
   granules in a given time window."
  (system/only-with-real-database
   (with-redefs
    [jobs/send-subscription-emails mock-send-subscription-emails]
     (let [user2-group-id (echo-util/get-or-create-group (system/context) "group2")
           _user2-token (echo-util/login (system/context) "user2" [user2-group-id])
           _ (echo-util/ungrant (system/context)
                                (-> (access-control/search-for-acls (system/context)
                                                                    {:provider "PROV1"
                                                                     :identity-type "catalog_item"}
                                                                    {:token "mock-echo-system-token"})
                                    :items
                                    first
                                    :concept_id))
           _ (echo-util/grant (system/context)
                              [{:group_id user2-group-id
                                :permissions [:read]}]
                              :catalog_item_identity
                              {:provider_id "PROV1"
                               :name "Provider collection/granule ACL"
                               :collection_applicable true
                               :granule_applicable true
                               :granule_identifier {:access_value {:include_undefined_value true
                                                                   :min_value 1 :max_value 50}}})
        ;;  Create coll1 granules
           coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll1"
                                                                               :EntryTitle "entry-title1"})
                                                       {:token "mock-echo-system-token"})
           coll1-concept-id (:concept-id coll1)
           c1g1 (data-granule/granule-with-umm-spec-collection
                 coll1 coll1-concept-id {:day-night "NIGHT"})
           c1g2 (data-granule/granule-with-umm-spec-collection
                 coll1 coll1-concept-id {:day-night "DAY"})
           c1g3 (data-granule/granule-with-umm-spec-collection
                 coll1 coll1-concept-id {:day-night "DAY"})
           c1g4 (data-granule/granule-with-umm-spec-collection
                 coll1 coll1-concept-id {:day-night "DAY"})
           _coll1_granule1 (subscription-util/save-umm-granule "PROV1" c1g1 {:revision-date "2016-01-01T10:00:00Z"})
           coll1_granule2 (subscription-util/save-umm-granule "PROV1" c1g2 {:revision-date "2016-01-02T10:00:00Z"})
           coll1_granule3 (subscription-util/save-umm-granule "PROV1" c1g3 {:revision-date "2016-01-03T10:00:00Z"})
           _coll1_granule4 (subscription-util/save-umm-granule "PROV1" c1g4 {:revision-date "2016-01-04T10:00:00Z"})
        ;;  Create coll2 granules
           coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll2"
                                                                               :EntryTitle "entry-title2"})
                                                       {:token "mock-echo-system-token"})
           coll2-concept-id (:concept-id coll2)
           c2g1 (data-granule/granule-with-umm-spec-collection
                 coll2 coll2-concept-id {:day-night "DAY"})
           c2g2 (data-granule/granule-with-umm-spec-collection
                 coll2 coll2-concept-id {:day-night "NIGHT"})
           _coll2_granule1 (subscription-util/save-umm-granule "PROV1" c2g1 {:revision-date "2015-12-30T10:00:00Z"})
           _coll2_granule2 (subscription-util/save-umm-granule "PROV1" c2g2 {:revision-date "2015-12-31T10:00:00Z"})
           _ (index/wait-until-indexed)
         ;; Setup subscriptions
           _sub1 (subscription-util/create-subscription-and-index coll1 "test_sub_prov1_coll1" "user2" "day_night_flag=day")
           _sub2 (subscription-util/create-subscription-and-index coll2 "test_sub_prov1_coll2" "user2" "day_night_flag=day")]

       (testing "given a valid time constraint, return the correct granules"
         (let [time-constraint "2016-01-02T00:00:00Z,2016-01-04T00:00:00Z"
               system-context (system/context)
               result (->> (jobs/email-subscription-processing system-context time-constraint)
                           (first)
                           (second))]
           (is (= (count result) 2))
           (is (= (:concept-id coll1_granule2) (:concept-id (first result))))
           (is (= (:concept-id coll1_granule3) (:concept-id (second result))))))))))

(deftest ^:oracle subscription-email-processing-time-constraint-test
  (system/only-with-real-database
   (with-redefs
    [jobs/send-subscription-emails mock-send-subscription-emails]
     (let [user2-group-id (echo-util/get-or-create-group (system/context) "group2")
           _user2-token (echo-util/login (system/context) "user2" [user2-group-id])
           _ (echo-util/ungrant (system/context)
                                (-> (access-control/search-for-acls (system/context)
                                                                    {:provider "PROV1"
                                                                     :identity-type "catalog_item"}
                                                                    {:token "mock-echo-system-token"})
                                    :items
                                    first
                                    :concept_id))

           _ (echo-util/grant (system/context)
                              [{:group_id user2-group-id
                                :permissions [:read]}]
                              :catalog_item_identity
                              {:provider_id "PROV1"
                               :name "Provider collection/granule ACL"
                               :collection_applicable true
                               :granule_applicable true
                               :granule_identifier {:access_value {:include_undefined_value true
                                                                   :min_value 1 :max_value 50}}})

         ;; Setup collection
           coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll1"
                                                                               :EntryTitle "entry-title1"})
                                                       {:token "mock-echo-system-token"})

           coll2 (data-core/ingest-umm-spec-collection "PROV1"
                                                       (data-umm-c/collection {:ShortName "coll2"
                                                                               :EntryTitle "entry-title2"})
                                                       {:token "mock-echo-system-token"})
           _ (index/wait-until-indexed)
         ;; Setup subscriptions
           sub1 (subscription-util/create-subscription-and-index coll1 "test_sub_prov1" "user2" "provider=PROV1")]

       (testing "First query executed does not have a last-notified-at and looks back 24 hours"
         (let [gran1 (create-granule-and-index "PROV1" coll1 "Granule1")
               results (->> (system/context)
                            (jobs/email-subscription-processing)
                            (map #(nth % 1))
                            flatten
                            (map :concept-id))]
           (is (= (:concept-id gran1) (first results)))))

       (dev-system/advance-time! 10)

       (testing "Second run finds only granules created since the last notification"
         (let [gran2 (create-granule-and-index "PROV1" coll1 "Granule2")
               response (->> (system/context)
                             (jobs/email-subscription-processing)
                             (map #(nth % 1))
                             flatten
                             (map :concept-id))]
           (is (= [(:concept-id gran2)] response))))

       (testing "Deleting the subscription purges the subscription notification entry"
       ;; Delete and reingest the subscription. If the sub-notification was purged, then it
       ;; should look back 24 hours, as if the subscription were new.
         (let [concept {:provider-id "PROV1" :concept-type :subscription :native-id "test_sub_prov1"}]
           (ingest/delete-concept concept))
         (subscription-util/create-subscription-and-index coll1 "test_sub_prov1" "user2" "provider=PROV1")
         (is (= 2
                (->> (system/context)
                     (jobs/email-subscription-processing)
                     (map #(nth % 1))
                     flatten
                     (map :concept-id)
                     count))))))))

(deftest ^:oracle subscription-email-processing-filtering
  (system/only-with-real-database
   (with-redefs
    [jobs/send-subscription-emails mock-send-subscription-emails]
     (testing "Tests subscriber-id filtering in subscription email processing job"
       (let [user1-group-id (echo-util/get-or-create-group (system/context) "group1")
           ;; User 1 is in group1
             user1-token    (echo-util/login (system/context) "user1" [user1-group-id])
             _              (echo-util/ungrant (system/context)
                                               (-> (access-control/search-for-acls (system/context)
                                                                                   {:provider      "PROV1"
                                                                                    :identity-type "catalog_item"}
                                                                                   {:token "mock-echo-system-token"})
                                                   :items
                                                   first
                                                   :concept_id))
             _              (echo-util/ungrant (system/context)
                                               (-> (access-control/search-for-acls (system/context)
                                                                                   {:provider      "PROV2"
                                                                                    :identity-type "catalog_item"}
                                                                                   {:token "mock-echo-system-token"})
                                                   :items
                                                   first
                                                   :concept_id))
             _              (echo-util/grant (system/context)
                                             [{:group_id    user1-group-id
                                               :permissions [:read]}]
                                             :catalog_item_identity
                                             {:provider_id           "PROV1"
                                              :name                  "Provider collection/granule ACL"
                                              :collection_applicable true
                                              :granule_applicable    true
                                              :granule_identifier    {:access_value {:include_undefined_value true
                                                                                     :min_value               1
                                                                                     :max_value               50}}})
             _              (echo-util/grant (system/context)
                                             [{:user_type   :registered
                                               :permissions [:read]}]
                                             :catalog_item_identity
                                             {:provider_id           "PROV2"
                                              :name                  "Provider collection/granule ACL registered users"
                                              :collection_applicable true
                                              :granule_applicable    true
                                              :granule_identifier    {:access_value {:include_undefined_value true
                                                                                     :min_value               100
                                                                                     :max_value               200}}})
             _              (ac-util/wait-until-indexed)
           ;; Setup collections
             coll1          (data-core/ingest-umm-spec-collection "PROV1"
                                                                  (data-umm-c/collection {:ShortName  "coll1"
                                                                                          :EntryTitle "entry-title1"})
                                                                  {:token "mock-echo-system-token"})
             coll2          (data-core/ingest-umm-spec-collection "PROV2"
                                                                  (data-umm-c/collection {:ShortName  "coll2"
                                                                                          :EntryTitle "entry-title2"})
                                                                  {:token "mock-echo-system-token"})
             _              (index/wait-until-indexed)
           ;; Setup subscriptions for each collection, for user1
             _              (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                                    {:Name                "test_sub_prov1"
                                                                     :SubscriberId        "user1"
                                                                     :EmailAddress        "user1@nasa.gov"
                                                                     :CollectionConceptId (:concept-id coll1)
                                                                     :Query               " "})
                                                                   {:token "mock-echo-system-token"})
             _              (subscription-util/ingest-subscription (subscription-util/make-subscription-concept
                                                                    {:provider-id         "PROV2"
                                                                     :Name                "test_sub_prov1"
                                                                     :SubscriberId        "user1"
                                                                     :EmailAddress        "user1@nasa.gov"
                                                                     :CollectionConceptId (:concept-id coll2)
                                                                     :Query               " "})
                                                                   {:token "mock-echo-system-token"})
             _              (index/wait-until-indexed)
           ;; Setup granules, gran1 and gran3 with acl matched access-value
           ;; gran 2 does not match, and should not be readable by user1
             gran1          (data-core/ingest "PROV1"
                                              (data-granule/granule-with-umm-spec-collection coll1
                                                                                             (:concept-id coll1)
                                                                                             {:granule-ur   "Granule1"
                                                                                              :access-value 33})
                                              {:token "mock-echo-system-token"})
             gran2          (data-core/ingest "PROV1"
                                              (data-granule/granule-with-umm-spec-collection coll1
                                                                                             (:concept-id coll1)
                                                                                             {:granule-ur   "Granule2"
                                                                                              :access-value 66})
                                              {:token "mock-echo-system-token"})
             gran3          (data-core/ingest "PROV2"
                                              (data-granule/granule-with-umm-spec-collection coll2
                                                                                             (:concept-id coll2)
                                                                                             {:granule-ur   "Granule3"
                                                                                              :access-value 133})
                                              {:token "mock-echo-system-token"})
             _              (index/wait-until-indexed)
             expected       (set [(:concept-id gran1) (:concept-id gran3)])
             actual         (->> (system/context)
                                 (jobs/email-subscription-processing)
                                 (map #(nth % 1))
                                 flatten
                                 (map :concept-id)
                                 set)]
         (is (= expected actual)))))))
