(ns cmr.search.test.api.tags
  "Tests to verify functionality in cmr.search.api.tags namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.search.api.tags :as tags]))

(def dictionary-with-error [
    {
        "tag_association" {
            "concept_id" "TA1200000009-CMR"
            "revision_id" 1
        }
        "tagged_item" {
            "concept_id" "C1200000005-PROV1"
        }
    }

    {
        "errors" [
        "Collection [C1200000006-PROV1] does not exist or is not visible."
        ]

        "tagged_item" {
        "concept_id" "C1200000006-PROV1"
        }
    }
])

(deftest make-sure-tag-api-response-error-400-test
  (testing "Making sure `tag-api-response` returns status code 400 when there's errors"
      (is
        (= 400 (:status (tags/tag-api-response dictionary-with-error))))))
