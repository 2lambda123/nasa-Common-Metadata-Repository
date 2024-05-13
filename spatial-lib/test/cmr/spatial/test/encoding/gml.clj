(ns cmr.spatial.test.encoding.gml
  "Tests for the GML spatial encoding lib."
  (:require
   [clojure.data.xml :as xml]
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.xml :as cx]
   [cmr.spatial.encoding.gml :as gml]
   [cmr.spatial.line-string :as line]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.test.generators :as spatial-gen]))

;; example XML document with valid GML elements

(def gml-xml
  "<root xmlns:gml=\"http://www.opengis.net/gml\">
     <gml:Point>
       <gml:pos>45.256 -110.45</gml:pos>
     </gml:Point>
     <gml:LineString>
       <gml:posList>
         45.256 -110.45 46.46 -109.48 43.84 -109.86 45.8 -109.2
       </gml:posList>
     </gml:LineString>
     <gml:Polygon>
       <gml:exterior>
         <gml:LinearRing>
           <gml:posList>
             45.256 -110.45 46.46 -109.48 43.84 -109.86 45.256 -110.45
           </gml:posList>
         </gml:LinearRing>
       </gml:exterior>
     </gml:Polygon>
     <gml:Polygon srsName=\"http://www.opengis.net/def/crs/EPSG/0/4326\">
       <gml:exterior>
         <gml:LinearRing>
           <gml:posList>
             45.256 -110.45 46.46 -109.48 43.84 -109.86 45.256 -110.45
           </gml:posList>
         </gml:LinearRing>
       </gml:exterior>
     </gml:Polygon>
   </root>")

(defn- emit-gml-str
  "Helper for emitting an XML document string with an xmlns attribtue
  for the gml prefix."
  [element]
  (xml/emit-str (assoc-in element [:attrs :xmlns:gml] "http://www.opengis.net/gml")))

(deftest test-parse-lat-lon-string
  (testing "one point"
    (is (= (gml/parse-lat-lon-string "9 10")
           [(p/point 10 9 true)])))
  (testing "multiple points"
    (is (= (gml/parse-lat-lon-string "2 1.03 -4 3")
           [(p/point 1.03 2 true) (p/point 3 -4 true)]))))

(deftest test-decode-point
  (testing "decoding points from GML"
    (is (= (p/point -110.45 45.256)
           (gml/decode (cx/element-at-path (xml/parse-str gml-xml) [:Point]))))))

(deftest test-decode-line-string
  (testing "decoding GML line strings"
    (is (= (line/ords->line-string :cartesian [-110.45 45.256, -109.48 46.46, -109.86 43.84, -109.2 45.8])
           (gml/decode (cx/element-at-path (xml/parse-str gml-xml) [:LineString]))))))

(deftest test-decode-polygon
  (testing "decoding GML polygons"
    (is (= (poly/polygon :cartesian [(rr/ords->ring :cartesian [-110.45 45.256, -109.48 46.46, -109.86 43.84, -110.45 45.256])])
           (gml/decode (first (cx/elements-at-path (xml/parse-str gml-xml) [:Polygon])))))
    (is (= (poly/polygon :geodetic [(rr/ords->ring :geodetic [-110.45 45.256, -109.48 46.46, -109.86 43.84, -110.45 45.256])])
           (gml/decode (second (cx/elements-at-path (xml/parse-str gml-xml) [:Polygon])))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Property-Based Tests

(declare check-gml-point-round-trip)
(defspec check-gml-point-round-trip 100
  (for-all [p spatial-gen/points]
    (let [element (-> (gml/encode p) emit-gml-str xml/parse-str)]
      (= p (gml/decode element)))))

(declare check-gml-line-string-round-trip)
(defspec check-gml-line-string-round-trip 100
  (for-all [l spatial-gen/cartesian-lines]
    (let [element (-> (gml/encode l) emit-gml-str xml/parse-str)]
      (= l (gml/decode element)))))

(declare check-gml-polygon-round-trip)
(defspec check-gml-polygon-round-trip 100
  (for-all [polygon spatial-gen/polygons-with-holes]
    (let [element (-> (gml/encode polygon) emit-gml-str xml/parse-str)]
      (and (seq (map :points (:rings polygon)))
           (= (map :points (:rings polygon))
              (map :points (:rings (gml/decode element))))))))
