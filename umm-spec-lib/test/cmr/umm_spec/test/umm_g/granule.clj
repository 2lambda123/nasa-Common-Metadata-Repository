(ns cmr.umm-spec.test.umm-g.granule
  "Tests parsing and generating UMM-G granule."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.date-time-parser :as p]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.common.util :as util]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.util :as su]
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

(defn- sanitize-operation-modes
  "Sanitizer for operation-modes, if sequence it removes duplicates, if nil it inserts a not provided."
  [operation-modes]
  (when (seq operation-modes)
    (distinct operation-modes)))

(defn- sanitize-granule
  "Sanitizes umm-lib generated granule."
  [umm]
  (-> umm
      (update :project-refs (fn [x] (when (seq x) (distinct x))))
      (update :platform-refs (fn [x] (when (seq x) (distinct x))))
      (util/update-in-each
       [:platform-refs]
       #(util/update-in-each % [:instrument-refs] update :operation-modes sanitize-operation-modes))))

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
      (dissoc :two-d-coordinate-system)
      (dissoc :measured-parameters)
      umm-lib-g/map->UmmGranule))

(defspec generate-granule-is-valid-umm-g-test 100
  (for-all [granule umm-g-granules]
    (let [granule (sanitize-granule granule)
          metadata (core/generate-metadata {} granule :umm-json)]
      (empty? (core/validate-metadata :granule :umm-json metadata)))))

(defspec generate-and-parse-umm-g-granule-test 100
  (for-all [granule umm-g-granules]
    (let [granule (sanitize-granule granule)
          umm-g-metadata (core/generate-metadata {} granule :umm-json)
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
    :cloud-cover 60
    :project-refs ["Campaign1" "Campaign2" "Campaign3"]
    :spatial-coverage nil
    :related-urls nil}))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-granule (core/parse-metadata {} :granule :umm-json sample-umm-g-granule)))))
