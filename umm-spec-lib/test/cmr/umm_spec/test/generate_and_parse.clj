(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
            [cmr.common.util :refer [update-in-each]]
            [cmr.common.test.test-check-ext :as ext :refer [defspec]]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.test.umm-record-sanitizer :as sanitize]
            [cmr.umm-spec.core :as core]
            [cmr.umm-spec.simple-xpath :refer [select context]]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso-xml-to-umm]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as iso-umm-to-xml]
            [cmr.umm-spec.iso-keywords :as kws]
            [cmr.umm-spec.iso19115-2-util :as iu]
            [cmr.umm-spec.umm-to-xml-mappings.echo10 :as echo10]
            [cmr.common.util :refer [are2]]
            [cmr.umm-spec.test.umm-generators :as umm-gen]))

(def tested-formats
  "Seq of formats to use in round-trip conversion and XML validation tests."
  [:dif :dif10 :echo10 :iso19115 :iso-smap])

; TODO Add these one by one as the format-to-format conversions get fixed
; (def format-examples
;   "Map of format type to example file"
;   {:dif "dif.xml"
;    :dif10 "dif10.xml"
;    :echo10 "echo10.xml"
;    :iso19115 "iso19115.xml"
;    :iso-smap "iso_smap.xml"})

(def format-examples
  "Map of format type to example file"
  {})

(defn xml-round-trip
  "Returns record after being converted to XML and back to UMM through
  the given to-xml and to-umm mappings."
  [record format]
  (let [metadata-xml (core/generate-metadata :collection format record)]
    ;; validate against xml schema
    (is (empty? (core/validate-xml :collection format metadata-xml)))
    (core/parse-metadata :collection format metadata-xml)))

(defn- generate-and-validate-xml
  "Returns a vector of errors (empty if none) from attempting to convert the given UMM record
  to valid XML in the given format."
  [record metadata-format concept-type]
  (let [metadata-xml (core/generate-metadata concept-type metadata-format record)]
    (vec (core/validate-xml concept-type metadata-format metadata-xml))))

(deftest roundrobin-collection-example-record
  (doseq [[origin-format filename] format-examples
          dest-format tested-formats
          :when (not= origin-format dest-format)]
    (testing (str origin-format " to " dest-format)
      (let [metadata (slurp (io/resource (str "example_data/" filename)))
            umm-c-record (core/parse-metadata :collection origin-format metadata)]
        (is (= [] (generate-and-validate-xml umm-c-record dest-format :collection)))))))

(deftest roundtrip-example-record
  (doseq [metadata-format tested-formats]
    (testing (str metadata-format)
      (is (= (expected-conversion/convert expected-conversion/example-record metadata-format)
             (xml-round-trip expected-conversion/example-record metadata-format))))))

(defspec roundtrip-generated-records 100
  (for-all [umm-record (gen/no-shrink umm-gen/umm-c-generator)
            metadata-format (gen/elements tested-formats)]
    (is (= (expected-conversion/convert umm-record metadata-format)
           (xml-round-trip umm-record metadata-format)))))

(defn- parse-iso19115-projects-keywords
  "Returns the parsed projects keywords for the given ISO19115-2 xml"
  [metadata-xml]
  (let [md-data-id-el (first (select (context metadata-xml) iso-xml-to-umm/md-data-id-base-xpath))]
    (seq (#'kws/descriptive-keywords md-data-id-el "project"))))

;; Info in UMM Projects field is duplicated in ISO191152 xml in two different places.
;; We parse UMM Projects from the gmi:MI_Operation, not from gmd:descriptiveKeywords.
;; This test is to verify that we populate UMM Projects in gmd:descriptiveKeywords correctly as well.
(defspec iso19115-projects-keywords 100
  (for-all [umm-record umm-gen/umm-c-generator]
    (let [metadata-xml (core/generate-metadata :collection :iso19115 umm-record)
          projects (:Projects (core/parse-metadata :collection :iso19115 metadata-xml))
          expected-projects-keywords (seq (map iu/generate-title projects))]
      (is (= expected-projects-keywords
             (parse-iso19115-projects-keywords metadata-xml))))))

(defspec umm-c-to-xml 100
  (for-all [umm-record (gen/no-shrink umm-gen/umm-c-generator)
            metadata-format (gen/elements tested-formats)]
    (empty? (generate-and-validate-xml umm-record metadata-format :collection))))

(comment

  (println (core/generate-metadata :collection :iso-smap user/failing-value))

  (is (= (expected-conversion/convert user/failing-value :iso-smap)
         (xml-round-trip user/failing-value :iso-smap)))

  ;; random XML gen
  (def metadata-format :dif)
  (def metadata-format :iso19115)
  (def metadata-format :echo10)
  (def metadata-format :dif)
  (def metadata-format :dif10)
  (def metadata-format :iso-smap)

  (def sample-record (first (gen/sample (gen/such-that :DataDates umm-gen/umm-c-generator) 1)))

  (def sample-record user/failing-value)

  (def sample-record expected-conversion/example-record)

  ;; generated xml
  (println (core/generate-metadata :collection metadata-format sample-record))

  ;; our simple example record
  (core/generate-metadata :collection metadata-format expected-conversion/example-record)

  ;; round-trip
  (xml-round-trip sample-record metadata-format)

  ;; generated test case
  (is (= (expected-conversion/convert sample-record metadata-format)
         (xml-round-trip sample-record metadata-format)))

  ;; for generated test failures
  (is (= (expected-conversion/convert user/failing-value metadata-format)
         (xml-round-trip user/failing-value metadata-format))))


