(ns cmr.spatial.test.tile
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [cmr.spatial.math :refer [approx= round]]
   [cmr.spatial.tile :as t]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.derived :as d]
   [cmr.spatial.point :as p]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.line-string :as l]
   [cmr.common.util :as u]))

(deftest modis-tile-coordinates
  (testing "creation and retrieval of modis tile coordinates"
    (let [tile (t/->ModisSinTile [7 0] nil)]
         (is (= [7 0] (:coordinates tile))))))

(deftest modis-tile-geometry-intersection
  (testing "testing bouding box intersection with a modis tile"
    (let [tile (t/->ModisSinTile [7 0] (d/calculate-derived
                                        (rr/ords->ring :geodetic [0,0,10,0,10,10,0,10,0,0])))
          geom (d/calculate-derived (rr/ords->ring :geodetic [5 5,15 5,15 15,5 15,5 5]))]
         (is (t/intersects? tile geom)))))

(deftest modis-search-overalapping-tiles
  (testing "find all the tiles which intersect the given geometry"
    (declare geom tiles)
    (u/are3
     [geom tiles]
     (is (= (set tiles) (set (t/geometry->tiles geom))))

         "A large bounding box near the equator"
         (d/calculate-derived (m/mbr -20 20 20 -20))
         [[15 8] [15 9] [16 6] [16 7] [16 8] [16 9] [16 10] [16 11] [17 6] [17 7] [17 8] [17 9]
          [17 10] [17 11] [18 6] [18 7] [18 8] [18 9] [18 10] [18 11] [19 6] [19 7] [19 8] [19 9]
          [19 10] [19 11] [20 8] [20 9]]

         "A small geodetic ring completely inside a tile"
         (rr/ords->ring :geodetic [-77.205, 39.112, -77.188, 39.134, -77.221,
                                            39.143, -77.252, 39.130, -77.250,39.116,-77.205,39.112])
         [[12 5]]

         "A point"
         (p/point -84.2625 36.0133)
         [[11 5]]

         "Geodetic line string"
         (l/ords->line-string :geodetic [1 1, 10 5, 15 9])
         [[18 8][19 8]]

         "A narrow bounding box crossing over two tiles"
         (m/mbr -0.01 15.0 0.01 5.0)
         [[17 7][17 8][18 7][18 8]]

         "North Pole"
         p/north-pole
         [[17 0] [18 0]]

         "South Pole"
         p/south-pole
         [[17 17][18 17]]

         "Bounding box crossing anti-meridian"
         (m/mbr 178.16 1.32 -176.79 -4.19)
         [[0 8][0 9][35 8][35 9]]

         "Whole world"
         (m/mbr -180 90 180 -90)
         (map :coordinates @t/modis-sin-tiles))))

(defn- ring-ords->tuples
  "Get coordinate tuples from a sequence of alternating x and y coordinates of a ring."
  [ords]
  (partition 2 (drop-last 2 ords)))

(defn rotate-until-first-matches
  "Cycles through items-to-rotate until the first item matches the items list. This can be used to
  rotate through a rings points to match the expected order of another list. Returns an empty
  sequence if the items-to-rotate does not contain the first from items."
  [items items-to-rotate]
  (let [first-item (first items)
        size (count items)]
    (->> (cycle items-to-rotate)
         ;; Prevent infinite loops
         (take (* size 2))
         (drop-while #(not (approx= first-item %)))
         (take size))))

(defn- assert-rings-equal
  "Check if two rings are equivalent or not"
  [expected actual]
  (let [expected-tuples (ring-ords->tuples expected)
        actual-tuples (ring-ords->tuples (map (partial round 4) actual))
        expected-reordered (rotate-until-first-matches actual-tuples expected-tuples)]
    (is (approx= expected-reordered actual-tuples))))


(deftest computed-geometry-matches-geometry-from-file
  (testing "The geometry generated through computations in Clojure code matches with the geometry that was generated by prior Ruby code"
    ;;Note: cmr/spatial/test/modis_tiles.edn was created using echo-tiles-geometry project in ECHO
    (let [tiles (read-string
                  (slurp (io/resource "cmr/spatial/test/modis_tiles.edn")))
          ruby-tile-geometry-map (reduce #(assoc % [(:h %2) (:v %2)] (:coordinates %2)) {} tiles)
          clojure-tiles @t/modis-sin-tiles]
      (doseq [tile clojure-tiles]
        (let [tile-coordinates (:coordinates tile)
              geometry (rr/ring->ords (:geometry tile))]
          (assert-rings-equal (get ruby-tile-geometry-map tile-coordinates) geometry))))))
