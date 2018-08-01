(ns cmr.umm-spec.test.gran-spatial-rep-alt-xpath-test
  "Tests getting GranuleSpatialRepresentation from alternative xpath for ISO19115." 
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.models.umm-common-models :as cmn]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.util :as util]))

(def test-context (lkt/setup-context-for-test))

(defn alt-xpath-example-file
  "Returns an example ISO19115 metadata file with alternative GranuleSpatialRepresentation xpath."
  []
  (io/file (io/resource "example-data/iso19115/ISOExample-GranSpatialRep-Alt-XPath.xml")))

(defn mixed-example-file
  "Returns an example ISO19115 metadata file with both regular and alternative GranuleSpatialRepresentation xpath."
  []
  (io/file (io/resource "example-data/iso19115/ISOExample-Mixed-GranSpatialRep-XPath.xml")))

(deftest test-alt-xpath-example-file 
  "Verify the returned GranuleSpatialRepresentation is CARTESIAN"
  (let [metadata (slurp (alt-xpath-example-file))
        umm (js/parse-umm-c (core/parse-metadata test-context :collection :iso19115 metadata))]
    (is (= "HORIZONTAL" (get-in umm [:SpatialExtent :SpatialCoverageType])))
    (is (= "CARTESIAN" (get-in umm [:SpatialExtent :GranuleSpatialRepresentation]))))) 
         
(deftest test-mixed-example-file 
  "Verify that the returned GranuleSpatialRepresentation is GEODETIC."
  (let [metadata (slurp (mixed-example-file))
        umm (js/parse-umm-c (core/parse-metadata test-context :collection :iso19115 metadata))]
    (is (= "HORIZONTAL" (get-in umm [:SpatialExtent :SpatialCoverageType])))
    (is (= "GEODETIC" (get-in umm [:SpatialExtent :GranuleSpatialRepresentation])))))
