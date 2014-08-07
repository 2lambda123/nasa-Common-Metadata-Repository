(ns cmr.system-int-test.search.granule-spatial-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.arc :as a]
            [cmr.spatial.line-segment :as s]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec :as codec]
            [cmr.spatial.messages :as smsg]
            [clojure.string :as str]
            [cmr.spatial.dev.viz-helper :as viz-helper]
            [cmr.spatial.serialize :as srl]
            [cmr.common.dev.util :as dev-util]
            [cmr.spatial.lr-binary-search :as lbs]
            [cmr.umm.spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order.
  The polygon will be closed automatically."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn search-poly
  "Returns a url encoded polygon for searching"
  [& ords]
  (codec/url-encode (umm-s/set-coordinate-system :geodetic (apply polygon ords))))

(def spatial-viz-enabled
  "Set this to true to debug test failures with the spatial visualization."
  false)

(defn display-indexed-granules
  "Displays the spatial areas of granules on the map."
  [granules]
  (let [geometries (mapcat (comp :geometries :spatial-coverage) granules)
        geometries (map derived/calculate-derived geometries)
        geometries (mapcat (fn [g]
                             [g
                              (srl/shape->mbr g)
                              (srl/shape->lr g)])
                           geometries)]
    (viz-helper/add-geometries geometries)))

(defn display-search-area
  "Displays a spatial search area on the map"
  [geometry]
  (let [geometry (derived/calculate-derived geometry)]
    (viz-helper/add-geometries [geometry
                                (srl/shape->mbr geometry)
                                (srl/shape->lr geometry)])))


;; Tests that invalid spatial areas are detected and error messages are returned.
(deftest spatial-search-validation-test
    (testing "invalid encoding"
      (is (= {:errors [(smsg/shape-decode-msg :polygon "0,ad,d,0")] :status 422}
             (search/find-refs :granule {:polygon "0,ad,d,0"})))
      (is (= {:errors [(smsg/shape-decode-msg :bounding-box "0,ad,d,0")] :status 422}
             (search/find-refs :granule {:bounding-box "0,ad,d,0"})))
      (is (= {:errors [(smsg/shape-decode-msg :point "0,ad")] :status 422}
             (search/find-refs :granule {:point "0,ad"}))))

    (testing "invalid polygons"
      (is (= {:errors [(smsg/ring-not-closed)] :status 422}
             (search/find-refs :granule
                               {:polygon (codec/url-encode
                                           (poly/polygon :geodetic [(rr/ords->ring :geodetic 0 0, 1 0, 1 1, 0 1)]))}))))
    (testing "invalid bounding box"
      (is (= {:errors [(smsg/br-north-less-than-south 45 46)] :status 422}
             (search/find-refs
               :granule
               {:bounding-box (codec/url-encode (m/mbr -180 45 180 46))}))))

    (testing "invalid point"
      (is (= {:errors [(smsg/point-lon-invalid -181)] :status 422}
             (search/find-refs :granule {:point "-181.0,5"}))))

    (testing "invalid lines"
      (is (= {:errors [(smsg/duplicate-points [[1 (p/point 1 1)] [3 (p/point 1 1)]])] :status 422}
             (search/find-refs :granule
                               {:line "0,0,1,1,2,2,1,1"})))))


(comment

  (viz-helper/clear-geometries)

  (viz-helper/add-geometries [(l/ords->line-string :geodetic 22.681,-8.839, 18.309,-11.426)
                              (l/ords->line-string :cartesian 16.439,-13.463,  31.904,-13.607)])


  ;; todo multiple point lines

  (a/point-at-lon (a/ords->arc 22.681,-8.839, 18.309,-11.426) 20.0)
  (s/segment+lon->lat (s/ords->line-segment 16.439,-13.463,  31.904,-13.607) 20.0)

  (l/covers-point? (derived/calculate-derived
                     (l/ords->line-string :cartesian 16.439,-13.463,  31.904,-13.607))
                   (p/point 20.0 -13.496157710960231))
  (l/covers-point? (derived/calculate-derived
                     (l/ords->line-string :cartesian 16.439,-13.463,  31.904,-13.607))
                   (p/point 20.0 -13.4961577))

  (m/covers-point? :geodetic
                   (:mbr (derived/calculate-derived
                           (l/ords->line-string :cartesian 16.439,-13.463,  31.904,-13.607)))
                   (p/point 20.0 -13.496157710960231))


  (viz-helper/add-geometries [(p/point 2.185,-11.161)])
  (viz-helper/add-geometries [(rr/ords->ring :geodetic 20.16,-13.7,21.64,12.43,12.47,11.84,-22.57,7.06,20.16,-13.7)])

  (viz-helper/add-geometries [(apply m/mbr [-10 90 10 -90])])

  )


(deftest spatial-search-test
  (let [geodetic-coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial :geodetic)}))
        cartesian-coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial :cartesian)}))
        make-gran (fn [ur & shapes]
                    (d/ingest "PROV1" (dg/granule geodetic-coll
                                                  {:granule-ur ur
                                                   :spatial-coverage (apply dg/spatial shapes)})))
        make-cart-gran (fn [ur & shapes]
                         (d/ingest "PROV1" (dg/granule cartesian-coll
                                                       {:granule-ur ur
                                                        :spatial-coverage (apply dg/spatial shapes)})))

        ;; Lines
        normal-line (make-gran "normal-line" (l/ords->line-string :geodetic 22.681 -8.839, 18.309 -11.426, 22.705 -6.557))
        normal-line-cart (make-cart-gran "normal-line-cart" (l/ords->line-string :cartesian 16.439 -13.463,  31.904 -13.607, 31.958 -10.401))

        ;; Bounding rectangles
        whole-world (make-gran "whole-world" (m/mbr -180 90 180 -90))
        touches-np (make-gran "touches-np" (m/mbr 45 90 55 70))
        touches-sp (make-gran "touches-sp" (m/mbr -160 -70 -150 -90))
        across-am-br (make-gran "across-am-br" (m/mbr 170 10 -170 -10))
        normal-brs (make-gran "normal-brs"
                              (m/mbr 10 10 20 0)
                              (m/mbr -20 0 -10 -10))

        ;; Geodetic Polygons
        wide-north (make-gran "wide-north" (polygon -70 20, 70 20, 70 30, -70 30, -70 20))
        wide-south (make-gran "wide-south" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        across-am-poly (make-gran "across-am-poly" (polygon 170 35, -175 35, -170 45, 175 45, 170 35))
        on-np (make-gran "on-np" (polygon 45 85, 135 85, -135 85, -45 85, 45 85))
        on-sp (make-gran "on-sp" (polygon -45 -85, -135 -85, 135 -85, 45 -85, -45 -85))
        normal-poly (make-gran "normal-poly" (polygon -20 -10, -10 -10, -10 10, -20 10, -20 -10))

        ;; polygon with holes
        outer (umm-s/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (umm-s/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (umm-s/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (make-gran "polygon-with-holes" (poly/polygon [outer hole1 hole2]))

        ;; Cartesian Polygons
        wide-north-cart (make-cart-gran "wide-north-cart" (polygon -70 20, 70 20, 70 30, -70 30, -70 20))
        wide-south-cart (make-cart-gran "wide-south-cart" (polygon -70 -30, 70 -30, 70 -20, -70 -20, -70 -30))
        very-wide-cart (make-cart-gran "very-wide-cart" (polygon -180 40, -180 35, 180 35, 180 40, -180 40))
        very-tall-cart (make-cart-gran "very-tall-cart" (polygon -160 90, -160 -90, -150 -90, -150 90, -160 90))
        normal-poly-cart (make-cart-gran "normal-poly-cart" (polygon 1.534 -16.52, 6.735 -14.102, 3.745 -9.735, -1.454 -11.802, 1.534 -16.52))

        outer-cart (umm-s/ords->ring -5.26 -22.59 11.56 -22.77 10.47 -11.29 -5.86 -11.37 -5.26 -22.59)
        hole1-cart (umm-s/ords->ring 6.95 -17.95 2.98 -17.94 3.92 -20.08 6.95 -17.95)
        hole2-cart (umm-s/ords->ring 5.18 -13.08 -1.79 -12.99 -2.65 -15 4.29 -14.95 5.18 -13.08)
        polygon-with-holes-cart (make-cart-gran "polygon-with-holes-cart" (poly/polygon [outer-cart hole1-cart hole2-cart]))

        ;; Points
        north-pole (make-gran "north-pole" (p/point 0 90))
        south-pole (make-gran "south-pole" (p/point 0 -90))
        normal-point (make-gran "normal-point" (p/point 10 22))
        am-point (make-gran "am-point" (p/point 180 22))]
    (index/refresh-elastic-index)

    (testing "line searches"
      (are [ords items]
           (let [found (search/find-refs
                         :granule
                         {:line (codec/url-encode (apply l/ords->line-string :geodetic ords))
                          :page-size 50})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (->> items (map :granule-ur) sort pr-str))
               (println "Actual:" (->> found :refs (map :name) sort pr-str)))
             matches?)

           ;; normal two points
           [-24.28,-12.76,10,10] [whole-world polygon-with-holes normal-poly normal-brs]

           ;; normal multiple points
           [-0.37,-14.07,4.75,1.27,25.13,-15.51] [whole-world polygon-with-holes
                                                  polygon-with-holes-cart normal-line-cart
                                                  normal-line normal-poly-cart]
           ;; across antimeridian
           [-167.85,-9.08,171.69,43.24] [whole-world across-am-br across-am-poly very-wide-cart]

           ;; across north pole
           [0 85, 180 85] [whole-world north-pole on-np touches-np]

           ;; across north pole where cartesian polygon touches it
           [-155 85, 25 85] [whole-world north-pole on-np very-tall-cart]

           ;; across south pole
           [0 -85, 180 -85] [whole-world south-pole on-sp]

           ;; across north pole where cartesian polygon touches it
           [-155 -85, 25 -85] [whole-world south-pole on-sp touches-sp very-tall-cart]))

    (testing "point searches"
        (are [lon_lat items]
             (let [found (search/find-refs :granule {:point (codec/url-encode (apply p/point lon_lat))
                                                     :page-size 50})
                   matches? (d/refs-match? items found)]
               (when-not matches?
                 (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                 (println "Actual:" (->> found :refs (map :name) sort pr-str)))
               matches?)

             ;; north pole
             [0 90] [whole-world north-pole on-np touches-np]

             ;; south pole
             [0 -90] [whole-world south-pole on-sp touches-sp]

             ;; in hole of polygon with a hole
             [4.83 1.06] [whole-world]
             ;; in hole of polygon with a hole
             [1.67 5.43] [whole-world]
             ;; and not in hole
             [1.95 3.36] [whole-world polygon-with-holes]

             ;; in mbr
             [17.73 2.21] [whole-world normal-brs]

             ;;matches exact point on polygon
             [-5.26 -2.59] [whole-world polygon-with-holes]

             ;; Matches a granule point
             [10 22] [whole-world normal-point wide-north-cart]

             [-154.821 37.84] [whole-world very-wide-cart very-tall-cart]

             ;; Near but not inside the cartesian normal polygon
             ;; and also insid the polygon with holes (outside the holes)
             [-2.212,-12.44] [whole-world polygon-with-holes-cart]
             [0.103,-15.911] [whole-world polygon-with-holes-cart]
             ;; inside the cartesian normal polygon
             [2.185,-11.161] [whole-world normal-poly-cart]

             ;; inside a hole in the cartesian polygon
             [4.496,-18.521] [whole-world]

             ;; point on geodetic line
             [20.0 -10.437310310746927] [whole-world normal-line]
             ;; point on cartesian line
             [20.0 -13.496157710960231] [whole-world normal-line-cart]))

    (testing "bounding rectangle searches"
        (are [wnes items]
             (let [found (search/find-refs :granule {:bounding-box (codec/url-encode (apply m/mbr wnes))
                                                     :page-size 50})
                   matches? (d/refs-match? items found)]
               (when-not matches?
                 (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                 (println "Actual:" (->> found :refs (map :name) sort pr-str)))
               matches?)

             [-23.43 5 25.54 -6.31] [whole-world polygon-with-holes normal-poly normal-brs]

             ;; inside hole in geodetic
             [4.03,1.51,4.62,0.92] [whole-world]
             ;; corner points inside different holes
             [4.03,5.94,4.35,0.92] [whole-world polygon-with-holes]

             ;; inside hole in cartesian polygon
             [-0.54,-13.7,3.37,-14.45] [whole-world normal-poly-cart]
             ;; inside different holes in cartesian polygon
             [3.57,-14.38,3.84,-18.63] [whole-world normal-poly-cart polygon-with-holes-cart]

             ;; just under wide north polygon
             [-1.82,46.56,5.25,44.04] [whole-world]
             [-1.74,46.98,5.25,44.04] [whole-world wide-north]
             [-1.74 47.05 5.27 44.04] [whole-world wide-north]

             ;; vertical slice of earth
             [-10 90 10 -90] [whole-world on-np on-sp wide-north wide-south polygon-with-holes
                              normal-poly normal-brs north-pole south-pole normal-point
                              very-wide-cart wide-north-cart wide-south-cart normal-poly-cart
                              polygon-with-holes-cart]

             ;; crosses am
             [166.11,53.04,-166.52,-19.14] [whole-world across-am-poly across-am-br am-point very-wide-cart]

             ;; Matches geodetic line
             [17.67,-4,25.56,-6.94] [whole-world normal-line]

             ;; Matches cartesian line
             [23.59,-4,25.56,-15.47] [whole-world normal-line-cart]

             ;; whole world
             [-180 90 180 -90] [whole-world touches-np touches-sp across-am-br normal-brs
                                wide-north wide-south across-am-poly on-sp on-np normal-poly
                                polygon-with-holes north-pole south-pole normal-point am-point
                                very-wide-cart very-tall-cart wide-north-cart wide-south-cart
                                normal-poly-cart polygon-with-holes-cart normal-line normal-line-cart]))

    (testing "polygon searches"
        (are [ords items]
             (let [found (search/find-refs :granule {:polygon (apply search-poly ords) })
                   matches? (d/refs-match? items found)]
               (when-not matches?
                 (println "Expected:" (->> items (map :granule-ur) sort pr-str))
                 (println "Actual:" (->> found :refs (map :name) sort pr-str)))
               (when (and (not matches?) spatial-viz-enabled)
                 (println "Displaying failed granules and search area")
                 (viz-helper/clear-geometries)
                 (display-indexed-granules items)
                 (display-search-area (apply polygon ords)))
               matches?)

             [20.16,-13.7,21.64,12.43,12.47,11.84,-22.57,7.06,20.16,-13.7]
             [whole-world normal-poly normal-brs polygon-with-holes normal-line normal-line-cart]

             ;; Intersects 2nd of normal-brs
             [-16.79,-12.71,-6.32,-10.95,-5.74,-6.11,-15.18,-7.63,-16.79,-12.71]
             [whole-world normal-poly normal-brs]

             [0.53,39.23,21.57,59.8,-112.21,84.48,-13.37,40.91,0.53,39.23]
             [whole-world on-np wide-north very-wide-cart]

             ;; around north pole
             [58.41,76.95,163.98,80.56,-122.99,81.94,-26.18,82.82,58.41,76.95]
             [whole-world on-np touches-np north-pole very-tall-cart]

             ;; around south pole
             [-161.53,-69.93,25.43,-51.08,13.89,-39.94,-2.02,-40.67,-161.53,-69.93]
             [whole-world on-sp wide-south touches-sp south-pole very-tall-cart]

             ;; Across antimeridian
             [-163.9,49.6,171.51,53.82,166.96,-11.32,-168.36,-14.86,-163.9,49.6]
             [whole-world across-am-poly across-am-br am-point very-wide-cart]

             [-2.212 -12.44, 0.103 -15.911, 2.185 -11.161 -2.212 -12.44]
             [whole-world normal-poly-cart polygon-with-holes-cart]

             ;; Interactions with lines
             ;; Covers both lines
             [15.42,-15.13,36.13,-14.29,25.98,-0.75,13.19,0.05,15.42,-15.13]
             [whole-world normal-line normal-line-cart normal-brs]

             ;; Intersects both lines
             [23.33,-14.96,24.02,-14.69,19.73,-6.81,18.55,-6.73,23.33,-14.96]
             [whole-world normal-line normal-line-cart]

             ;; Related to the geodetic polygon with the holes
             ;; Inside holes
             [4.1,0.64,4.95,0.97,6.06,1.76,3.8,1.5,4.1,0.64] [whole-world]
             [1.41,5.12,3.49,5.52,2.66,6.11,0.13,6.23,1.41,5.12] [whole-world]
             ;; Partially inside a hole
             [3.58,-1.34,4.95,0.97,6.06,1.76,3.8,1.5,3.58,-1.34] [whole-world polygon-with-holes]
             ;; Covers a hole
             [3.58,-1.34,5.6,0.05,7.6,2.33,2.41,2.92,3.58,-1.34] [whole-world polygon-with-holes]
             ;; points inside both holes
             [4.44,0.66,5.4,1.35,2.66,6.11,0.13,6.23,4.44,0.66] [whole-world polygon-with-holes]
             ;; completely covers the polygon with holes
             [-6.45,-3.74,12.34,-4.18,12,9.45,-6.69,9.2,-6.45,-3.74] [whole-world polygon-with-holes normal-brs]

             ;; Related to the cartesian polygon with the holes
             ;; Inside holes
             [-1.39,-14.32,2.08,-14.38,1.39,-13.43,-1.68,-13.8,-1.39,-14.32]
             [whole-world normal-poly-cart]
             ;; Partially inside a hole
             [-1.39,-14.32,2.08,-14.38,1.64,-12.45,-1.68,-13.8,-1.39,-14.32]
             [whole-world polygon-with-holes-cart normal-poly-cart]
             ;; Covers a hole
             [-3.24,-15.58,5.22,-15.16,6.05,-12.37,-1.98,-12.46,-3.24,-15.58]
             [whole-world polygon-with-holes-cart normal-poly-cart]
             ;; points inside both holes
             [3.98,-18.64,5.08,-18.53,3.7,-13.78,-0.74,-13.84,3.98,-18.64]
             [whole-world polygon-with-holes-cart normal-poly-cart]
             ;; completely covers the polygon with holes
             [-5.95,-23.41,12.75,-23.69,11.11,-10.38,-6.62,-10.89,-5.95,-23.41]
             [whole-world polygon-with-holes-cart wide-south-cart normal-poly-cart]))))


