(ns cmr.umm-spec.location-keywords
  "Helper utilities for converting Spatial or Location Keywords to UMM LocationKeywords."
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [cmr.common-app.services.kms-lookup :as kms-lookup]
    [cmr.common.util :as util]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]))

(def cache-location-keywords->umm-location-keywords
  "Mapping for renaming values generated by the kms fetcher to LocationKeyword UMM keys"
  {:category :Category
   :type :Type
   :subregion-1 :Subregion1
   :subregion-2 :Subregion2
   :subregion-3 :Subregion3
   :detailed-location :DetailedLocation})

(defn- find-spatial-keyword
  "Finds spatial keywords in the hierarchy and pick the one with the fewest keys (e.g. shortest
  hierarchical depth.) Takes the kms-index and a location string as parameters, and returns
  the map of hierarichies which contain the location string (treated case insensitive)."
  [kms-index location-string]
  (or (kms-lookup/lookup-by-location-string kms-index location-string)
      {:category "OTHER" :type location-string}))

(defn spatial-keywords->location-keywords
  "Takes the kms-index and a list of Spatial Keywords and returns a list of location keyword maps
  for that spatial keyword."
  [kms-index spatial-keywords]
  (map (fn [keyword]
         (dissoc
          (set/rename-keys
            (find-spatial-keyword kms-index keyword)
            cache-location-keywords->umm-location-keywords)
          :uuid))
       spatial-keywords))

(defn- location-values
  "Returns the location keyword values so they can be indexed for searching"
  [location-keyword]
  (as-> location-keyword keyword
        (vals keyword)
        (remove nil? keyword)))

(defn location-keywords->spatial-keywords
  "Converts a list of LocationKeyword maps to a list of SpatialKeywords"
  [location-keyword-list]
  (distinct (mapcat location-values location-keyword-list)))

(defn translate-spatial-keywords
  "Translates a list of spatial keywords into an array of LocationKeyword type objects"
  [kms-index spatial-keywords]
  (let [location-keyword-maps (spatial-keywords->location-keywords kms-index spatial-keywords)
        umm-location-keyword-maps (seq
                                   (map
                                    #(dissoc
                                      (set/rename-keys % cache-location-keywords->umm-location-keywords)
                                      :uuid)
                                    location-keyword-maps))]
    umm-location-keyword-maps))
