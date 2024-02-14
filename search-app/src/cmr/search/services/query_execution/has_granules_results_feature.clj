(ns cmr.search.services.query-execution.has-granules-results-feature
  "This enables the :has-granules feature for collection search results. When it is enabled
  collection search results will include a boolean flag indicating whether the collection has
  any granules at all as indicated by provider holdings."
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.common.jobs :refer [defjob]]
            [cmr.common.cache :as cache]
            [cmr.redis-utils.redis-cache :as redis-cache]
            [cmr.search.data.elastic-search-index :as idx]))

(def REFRESH_HAS_GRANULES_MAP_JOB_INTERVAL
  "The frequency in seconds of the refresh-has-granules-map-job"
  ;; default to 1 hour
  3600)

(def has-granule-cache-key
  :has-granules-map)

(defn create-has-granules-map-cache
  "Returns a 'cache' which will contain the cached has granules map."
  []
  (redis-cache/create-redis-cache))

(defn- collection-granule-counts->has-granules-map
  "Converts a map of collection ids to granule counts to a map of collection ids to true or false
  of whether the collection has any granules"
  [coll-gran-counts]
  (into {} (for [[coll-id num-granules] coll-gran-counts]
             [coll-id (> num-granules 0)])))

(defn refresh-has-granules-map
  "Gets the latest provider holdings and updates the has-granules-map stored in the cache."
  [context]
  (let [has-granules-map (collection-granule-counts->has-granules-map
                           (idx/get-collection-granule-counts context nil))]
    (cache/set-value (cache/context->cache context has-granule-cache-key)
                     has-granule-cache-key
                     has-granules-map)))

;; TODO need to continue to trace back to see if a full map is necessary. What is it really being used for anyway? Something related to ES queries.
(defn get-has-granules-map
  "Gets the cached has granules map from the context which contains collection ids to true or false
  of whether the collections have granules or not. If the has-granules-map has not yet been cached
  it will retrieve it and cache it.
  Example) {\"C0000000002-PROV1\" true, \"C0000000003-PROV1\" false}"
  [context]
  (let [has-granules-map-cache (cache/context->cache context has-granule-cache-key)]
    (cache/get-value has-granules-map-cache
                     has-granule-cache-key
                     (fn []
                       (collection-granule-counts->has-granules-map
                         (idx/get-collection-granule-counts context nil))))))

;; This returns a boolean flag with collection results if a collection has any granules in provider holdings
(defmethod query-execution/post-process-query-result-feature :has-granules
  [context query elastic-results query-results feature]
  (assoc query-results :has-granules-map (get-has-granules-map context)))

(defjob RefreshHasGranulesMapJob
  [ctx system]
  (refresh-has-granules-map {:system system}))

(defn refresh-has-granules-map-job
  [job-key]
  {:job-type RefreshHasGranulesMapJob
   :job-key job-key
   :interval REFRESH_HAS_GRANULES_MAP_JOB_INTERVAL})
