(ns cmr.redis-utils.redis-hash-cache
  "An implementation of the CMR hash cache protocol on top of Redis. Mutliple applications
  have multiple caches which use one instance of Redis. Therefore calling reset on
  a Redis cache should not delete all of the data stored. Instead when creating a Redis
  cache the caller must provide a list of keys which should be deleted when calling
  reset on that particular cache. TTL MUST be set if you expect your keys to be evicted automatically."
  (:require
   [cmr.common.hash-cache :as cache]
   [cmr.common.log :as log :refer [info]]
   [cmr.redis-utils.redis-cache :as rc]
   [cmr.redis-utils.redis :as redis :refer [wr-wcar*]]
   [taoensso.carmine :as carmine]))

;; Implements the CmrHashCache protocol by saving data in Redis.
(defrecord RedisHashCache
  [
   ;; A collection of keys used by this cache. Only these keys will be deleted from the backend
   ;; store on calls to reset.
   keys-to-track

   ;; The time to live for the key in seconds.
   ttl

   ;; Refresh the time to live when GET operations are called on key.
   refresh-ttl?]

  cache/CmrHashCache
  (get-map
    [this key]
    ;; key is the cache-key 
    ;; hgetall returns a vector structure [[field1 value1 field2 value2 fieldN valueN]]
    ;; First pulls out the inner vector then conver it to {field value field value} hash 
    ;; map so that callers can process it.
    (let [start (System/currentTimeMillis)
          ;; true is for reading the cache.
          result (-> (wr-wcar* key true :as-pipeline (carmine/hgetall (rc/serialize key)))
                     first)
          result (when-not (or (nil? result)
                               (empty? result))
                   (into {} (for [[a b] (partition 2 result)]
                              {a b})))]
      (info (format "Redis timed function hash cache get map for %s time [%s] ms" key (- (System/currentTimeMillis) start)))
      result))

  (key-exists
    [this key]
    ;; key is the cache-key. Retuns true if the cache key exists in redis nil otherwise
    (let [start (System/currentTimeMillis)
          exists (wr-wcar* key true (carmine/exists (rc/serialize key)))]
      (info (format "Redis timed function hash cache key-exists for %s time [%s] ms" key (- (System/currentTimeMillis) start)))
      (when exists
        (> exists 0))))

  (get-keys
    [this key]
    ;; key is the cache-key 
    ;; hkeys returns a vector structure [[key1 key2 ... keyn]] First pulls out the inner vector
    ;; returns a vector of keys.
   (let [start (System/currentTimeMillis)
         result (-> (wr-wcar* key true :as-pipeline (carmine/hkeys (rc/serialize key)))
                    first)]
     (info (format "Redis timed function hash cache get keys for %s time [%s] ms" key (- (System/currentTimeMillis) start)))
     result))

  (get-value
    [this key field]
    ;; key is the cache-key. Returns the value of the passed in field.
    (-> (wr-wcar* key true :as-pipeline (carmine/hget (rc/serialize key) field))
        first))
  
  (get-values
    ;; key is the cache-key. Fields is either a vector or a list of fields.
    ;; returns a vector of values.
    [this key fields]
    (map #(-> (wr-wcar* key true :as-pipeline (carmine/hget (rc/serialize key) %1))
              first)
         fields))

  (reset
    [this]
    (doseq [the-key keys-to-track]
      (wr-wcar* the-key false (carmine/del (rc/serialize the-key)))))

  (reset
    [this key]
    (wr-wcar* key false (carmine/del (rc/serialize key))))

  (set-value
    [this key field value]
    ;; Store value in map to aid deserialization of numbers.
    (wr-wcar* (str key "-" field) false (carmine/hset (rc/serialize key) field value)))
  
  (set-values
   [this key field-value-map]
   (doall (map #(wr-wcar* key false (carmine/hset (rc/serialize key) %1 (get field-value-map %1)))
               (keys field-value-map))))

  (cache-size
    [this key]
    ;; Return 0 if the cache is empty or does not yet exist. This is for cmr.common-app.services.cache-info.
    (let [start (System/currentTimeMillis)
          size (if-let [size (wr-wcar* key false (carmine/memory-usage (rc/serialize key)))]
                 size
                 0)]
      (info (format "Redis timed function hash cache cache-size for %s time [%s] ms" key (- (System/currentTimeMillis) start)))
      size)))

(defn create-redis-hash-cache
  "Creates an instance of the redis cache.
  options:
      :keys-to-track
       The keys that are to be managed by this cache.
      :ttl
       The time to live for the key in seconds. If nil assumes key will never expire. NOTE:
       The key is not guaranteed to stay in the cache for up to ttl. If the
       cache becomes full any key that is not set to persist will
       be a candidate for eviction.
      :refresh-ttl?
       When a GET operation is called called on the key then the ttl is refreshed
       to the time to live set by the initial cache."
  ([]
   (create-redis-hash-cache nil))
  ([options]
   (->RedisHashCache (:keys-to-track options)
                     (get options :ttl)
                     (get options :refresh-ttl? false))))
