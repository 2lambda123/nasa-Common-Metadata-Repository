(ns cmr.umm-spec.test.umm-g.granule
  "Tests parsing and generating UMM-G granule."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.date-time-parser :as p]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm.test.generators.granule :as gran-gen]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-lib-g]))

(def umm-g-coll-refs
  "Generator for UMM-G granule collection ref. It does not support entry-id,
  only entry title, short name & version."
  (gen/one-of [gran-gen/coll-refs-w-entry-title gran-gen/coll-refs-w-short-name-version]))

(def umm-g-granules
  "Generator for UMM-G granule in umm-lib Granule model."
  (gen/fmap #(assoc % :collection-ref (gen/generate umm-g-coll-refs)) gran-gen/granules))

(defn- umm->expected-parsed
  "Modifies the UMM record for testing UMM-G. As the fields are added to UMM-G support for
  parsing and generating in cmr.umm-spec.umm-g.granule, the fields should be taken off the
  excluded list below."
  [gran]
  (-> gran
      (dissoc :data-granule)
      (dissoc :access-value)
      (dissoc :spatial-coverage)
      (dissoc :related-urls)
      (dissoc :orbit-calculated-spatial-domains)
      (dissoc :product-specific-attributes)
      (dissoc :cloud-cover)
      (dissoc :two-d-coordinate-system)
      (dissoc :measured-parameters)
      umm-lib-g/map->UmmGranule))

(defspec generate-granule-is-valid-umm-g-test 100
  (for-all [granule umm-g-granules]
    (let [metadata (core/generate-metadata {} granule :umm-json)]
      (empty? (core/validate-metadata :granule :umm-json metadata)))))

(defspec generate-and-parse-umm-g-granule-test 100
  (for-all [granule umm-g-granules]
    (let [umm-g-metadata (core/generate-metadata {} granule :umm-json)
          parsed (core/parse-metadata {} :granule :umm-json umm-g-metadata)
          expected-parsed (umm->expected-parsed granule)]
      (= parsed expected-parsed))))

(def sample-umm-g-granule
  (slurp (io/file (io/resource "example-data/umm-json/granule/v1.4/GranuleExample.json"))))

(def expected-granule
  (umm-lib-g/map->UmmGranule
   {:granule-ur "Unique_Granule_UR"
    :data-provider-timestamps (umm-lib-g/map->DataProviderTimestamps
                               {:insert-time (p/parse-datetime "2018-08-19T01:00:00Z")
                                :update-time (p/parse-datetime "2018-09-19T02:00:00Z")
                                :delete-time (p/parse-datetime "2030-08-19T03:00:00Z")})
    :collection-ref (umm-lib-g/map->CollectionRef
                     {:entry-title nil
                      :short-name "CollectionShortName"
                      :version-id "Version"})
    :data-granule nil
    :access-value nil
    :temporal (umm-lib-g/map->GranuleTemporal
               {:range-date-time (umm-c/map->RangeDateTime
                                  {:beginning-date-time (p/parse-datetime "2018-07-17T00:00:00.000Z")
                                   :ending-date-time (p/parse-datetime "2018-07-17T23:59:59.999Z")})})
    :platform-refs [(umm-lib-g/map->PlatformRef
                     {:short-name "Aqua"
                      :instrument-refs
                      [(umm-lib-g/map->InstrumentRef
                        {:short-name "AMSR-E"
                         :characteristic-refs [(umm-lib-g/map->CharacteristicRef
                                                {:name "InstrumentCaracteristicName1",
                                                 :value "150"})
                                               (umm-lib-g/map->CharacteristicRef
                                                {:name "InstrumentCaracteristicName2",
                                                 :value "22F"})]
                         :sensor-refs [(umm-lib-g/map->SensorRef
                                        {:short-name "AMSR-E_ChildInstrument",
                                         :characteristic-refs
                                         [(umm-lib-g/map->CharacteristicRef
                                           {:name "ChildInstrumentCharacteristicName3",
                                            :value "250"})]})]
                         :operation-modes ["Mode1" "Mode2"]})]})]
    :project-refs [(umm-lib-g/map->ProjectRef
                    {:short-name "Project1"
                     :campaigns ["Campaign1"]})
                   (umm-lib-g/map->ProjectRef
                    {:short-name "Project2"
                     :campaigns ["Campaign2" "Campaign3"]})]
    :spatial-coverage nil
    :related-urls nil}))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-granule (core/parse-metadata {} :granule :umm-json sample-umm-g-granule)))))

(comment (def sample-umm-g-granule #cmr.umm.umm_granule.UmmGranule{:granule-ur "!", :data-provider-timestamps #cmr.umm.umm_granule.DataProviderTimestamps{:insert-time #=(cmr.common.joda-time/date-time 0 "UTC"), :update-time #=(cmr.common.joda-time/date-time 0 "UTC"), :delete-time nil}, :collection-ref #cmr.umm.umm_granule.CollectionRef{:entry-title nil, :short-name "p", :version-id "pAk1f", :entry-id nil}, :data-granule nil, :access-value nil, :temporal #cmr.umm.umm_granule.GranuleTemporal{:range-date-time #cmr.umm.umm_collection.RangeDateTime{:beginning-date-time #=(cmr.common.joda-time/date-time 0 "UTC"), :ending-date-time nil}, :single-date-time nil}, :spatial-coverage nil, :orbit-calculated-spatial-domains nil, :measured-parameters nil, :platform-refs nil, :project-refs [#cmr.umm.umm_granule.ProjectRef{:short-name "!", :campaigns nil}], :related-urls nil, :product-specific-attributes nil, :cloud-cover nil, :two-d-coordinate-system nil}))
