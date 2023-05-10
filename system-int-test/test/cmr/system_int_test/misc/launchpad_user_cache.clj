(ns cmr.system-int-test.misc.launchpad-user-cache
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.cache-util :as cache-util]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(deftest launchpad-user-cache-test
  (testing "launchpad cache initial value"
    (let [launchpad-token (echo-util/login-with-launchpad-token (system/context) "user1")]
      (is (empty? (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token)))
      (let [concept (data-umm-c/collection-concept {})
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept {:token launchpad-token})]
        (index/wait-until-indexed)

        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))
        (is (some #{"launchpad-user"} (cache-util/list-caches-for-app (url/ingest-read-caches-url) transmit-config/mock-echo-system-token)))
        (is (= (:uid (cache-util/get-cache-value
                      (url/ingest-read-caches-url)
                      "launchpad-user"
                      launchpad-token
                      transmit-config/mock-echo-system-token
                      200))
               "user1"))

        (testing "Launchpad token cache value mantains expiration date"
          (dev-sys-util/advance-time! 20)
          (let [expiration-time (:expiration-time (cache-util/get-cache-value
                                                   (url/ingest-read-caches-url)
                                                   "launchpad-user"
                                                   launchpad-token
                                                   transmit-config/mock-echo-system-token
                                                   200))]
            (ingest/ingest-concept concept {:token launchpad-token})
            (index/wait-until-indexed)

            (is (= expiration-time (:expiration-time (cache-util/get-cache-value
                                                      (url/ingest-read-caches-url)
                                                      "launchpad-user"
                                                      launchpad-token
                                                      transmit-config/mock-echo-system-token
                                                      200))))
            (testing "token cache value expires"
              (dev-sys-util/advance-time! 2000)
              (ingest/ingest-concept concept {:token launchpad-token})
              (index/wait-until-indexed)
              (is (not= expiration-time (cache-util/list-cache-keys (url/ingest-read-caches-url) "launchpad-user" transmit-config/mock-echo-system-token))))))))))
