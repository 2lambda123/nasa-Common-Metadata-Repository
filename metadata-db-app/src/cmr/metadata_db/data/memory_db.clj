(ns cmr.metadata-db.data.memory-db
  "An in memory implementation of the metadata database."
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [cmr.common.concepts :as cc]
   [cmr.common.date-time-parser :as p]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.memorydb.connection :as connection]
   [cmr.common.time-keeper :as tk]
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.data.oracle.concepts.tag :as tag]
   [cmr.metadata-db.data.oracle.concepts]
   [cmr.metadata-db.data.providers :as providers]
   [cmr.metadata-db.services.provider-validation :as pv])
  (:import
   (cmr.common.memorydb.connection MemoryStore)))

;; XXX find-latest-concepts is used by after-save which is defined before
;;     find-latest-concepts is. This bears closer examination, since the
;;     need for a declare due to issues like this is often a signifier
;;     in an API that hasn't been fully ironed out.
(declare find-latest-concepts)

(defn- association->tombstone
  "Returns the tombstoned revision of the given association concept"
  [association]
  (-> association
      (assoc :metadata "" :deleted true)
      (update :revision-id inc)))

(defmulti after-save
  "Handler for save calls. It will be passed the list of concepts and the
  concept that was just saved. Implementing mehods should manipulate anything
  required and return the new list of concepts."
  (fn [db concepts concept]
    (:concept-type concept)))

(defmethod after-save :collection
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (filter #(not= (:concept-id concept)
                   (get-in % [:extra-fields :parent-collection-id]))
            concepts)))

;; CMR-2520 Readdress this case when asynchronous cascaded deletes are implemented.
(defmethod after-save :tag
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    ;; XXX The use of tag/ here is calling into the Oracle implementation; the
    ;;     in-mem db needs its own implementation of that function (they can't
    ;;     share a utility version of it due to the fact that it depends upon
    ;;     a call to another function which is implementation specific).
    (let [tag-associations (tag/get-tag-associations-for-tag-tombstone db concept)
          tombstones (map association->tombstone tag-associations)]
      ;; publish tag-association delete events
      (doseq [tombstone tombstones]
        (ingest-events/publish-event
         (:context db)
         (ingest-events/concept-delete-event tombstone)))
      (concat concepts tombstones))))

;; CMR-2520 Readdress this case when asynchronous cascaded deletes are implemented.
(defmethod after-save :variable
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (let [variable-associations (find-latest-concepts
                                 db
                                 {:provider-id "CMR"}
                                 {:concept-type :variable-association
                                  :variable-concept-id (:concept-id concept)})
          tombstones (map association->tombstone variable-associations)]
      ;; publish variable-association delete events
      (doseq [tombstone tombstones]
        (ingest-events/publish-event
         (:context db)
         (ingest-events/concept-delete-event tombstone)))
      (concat concepts tombstones))))

(defmethod after-save :service
  [db concepts concept]
  (if-not (:deleted concept)
    concepts
    (let [service-associations (find-latest-concepts
                                db
                                {:provider-id "CMR"}
                                {:concept-type :service-association
                                 :service-concept-id (:concept-id concept)})
          tombstones (map association->tombstone service-associations)]
      ;; publish service-association delete events
      (doseq [tombstone tombstones]
        (ingest-events/publish-event
         (:context db)
         (ingest-events/concept-delete-event tombstone)))
      (concat concepts tombstones))))

(defmethod after-save :default
  [db concepts concept]
  concepts)

(defn- concept->tuple
  "Converts a concept into a concept id revision id tuple"
  [concept]
  [(:concept-id concept) (:revision-id concept)])

(defn validate-concept-id-native-id-not-changing
  "Validates that the concept-id native-id pair for a concept being saved is not changing. This
  should be done within a save transaction to avoid race conditions where we might miss it.
  Returns nil if valid and an error response if invalid."
  [db provider concept]
  (let [{:keys [concept-id native-id concept-type provider-id]} concept
        {existing-concept-id :concept-id
         existing-native-id :native-id} (->> (deref (:concepts db))
                                             (filter #(= concept-type (:concept-type %)))
                                             (filter #(= provider-id (:provider-id %)))
                                             (filter #(or (= concept-id (:concept-id %))
                                                          (= native-id (:native-id %))))
                                             first)]
    (when (and (and existing-concept-id existing-native-id)
               (or (not= existing-concept-id concept-id)
                   (not= existing-native-id native-id)))
      {:error :concept-id-concept-conflict
       :error-message (format (str "Concept id [%s] and native id [%s] to save do not match "
                                   "existing concepts with concept id [%s] and native id [%s].")
                              concept-id native-id existing-concept-id existing-native-id)
       :existing-concept-id existing-concept-id
       :existing-native-id existing-native-id})))

(defn- delete-time
  "Returns the parsed delete-time extra field of a concept."
  [concept]
  (some-> concept
          (get-in [:extra-fields :delete-time])
          p/parse-datetime))

(defn- expired?
  "Returns true if the concept is expired (delete-time in the past)"
  [concept]
  (when-let [t (delete-time concept)]
    (t/before? t (tk/now))))

(defn- latest-revisions
  "Returns only the latest revisions of each concept in a seq of concepts."
  [concepts]
  (map last
       (map (partial sort-by :revision-id)
            (vals (group-by :concept-id concepts)))))

(defn- concepts->find-result
  "Returns the given concepts in the proper find result format based on the find params."
  [concepts params]
  (let [exclude-metadata? (= "true" (:exclude-metadata params))
        map-fn (fn [concept]
                 (let [concept (if (and (= :granule (:concept-type concept))
                                        (nil? (get-in concept [:extra-fields :granule-ur])))
                                 (assoc-in concept [:extra-fields :granule-ur]
                                           (:native-id concept))
                                 concept)]
                   (if exclude-metadata?
                     (dissoc concept :metadata)
                     concept)))]
    (map map-fn concepts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB ConceptSearch Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-concepts
  [db providers params]
  ;; XXX Looking at search-with-params, seems like it might need to be
  ;;     in a utility ns for use by all impls
  (let [found-concepts (mapcat #(concepts/search-with-params
                                @(:concepts db)
                                (assoc params :provider-id (:provider-id %)))
                              providers)]
    (concepts->find-result found-concepts params)))

(defn find-latest-concepts
  [db provider params]
  (let [latest-concepts (latest-revisions @(:concepts db))
        found-concepts (concepts/search-with-params
                        latest-concepts
                        (assoc params :provider-id (:provider-id provider)))]
    (concepts->find-result found-concepts params)))

(def concept-search-behaviour
  {:find-concepts find-concepts
   :find-latest-concepts find-latest-concepts})

(extend MemoryStore
        concepts/ConceptSearch
        concept-search-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB ConceptsStore Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-concept-id
  [db concept]
  (let [{:keys [concept-type provider-id]} concept
       num (swap! (:next-id db) inc)]
   (cc/build-concept-id {:concept-type concept-type
                         :sequence-number num
                         :provider-id provider-id})))

(defn get-concept-id
  [db concept-type provider native-id]
  (let [provider-id (:provider-id provider)
       concept-type (if (keyword? concept-type) concept-type (keyword concept-type))]
   (->> @(:concepts db)
        (filter (fn [c]
                  (and (= concept-type (:concept-type c))
                       (= provider-id (:provider-id c))
                       (= native-id (:native-id c)))))
        first
        :concept-id)))

(defn get-granule-concept-ids
  [db provider native-id]
  (let [provider-id (:provider-id provider)
        matched-gran (->> @(:concepts db)
                          (filter (fn [c]
                                   (and (= :granule (:concept-type c))
                                        (= provider-id (:provider-id c))
                                        (= native-id (:native-id c)))))
                          (sort-by :revisoin-id)
                          last)
        {:keys [concept-id deleted]} matched-gran
        parent-collection-id (get-in matched-gran [:extra-fields :parent-collection-id])]
    [concept-id parent-collection-id deleted]))

(defn- -get-concept
  [db concept-type provider concept-id]
  (let [revisions (filter
                   (fn [c]
                    (and (= concept-type (:concept-type c))
                         (= (:provider-id provider) (:provider-id c))
                         (= concept-id (:concept-id c))))
                   @(:concepts db))]
    (->> revisions
         (sort-by :revision-id)
         last)))

(defn- -get-concept-with-revision
  [db concept-type provider concept-id revision-id]
  (if-not revision-id
    (-get-concept db concept-type provider concept-id)
    (first (filter
            (fn [c]
             (and (= concept-type (:concept-type c))
                  (= (:provider-id provider) (:provider-id c))
                  (= concept-id (:concept-id c))
                  (= revision-id (:revision-id c))))
            @(:concepts db)))))

(defn get-concept
  ([db concept-type provider concept-id]
    (-get-concept db concept-type provider concept-id))
  ([db concept-type provider concept-id revision-id]
    (-get-concept-with-revision
     db concept-type provider concept-id revision-id)))

(defn get-concepts
  [db concept-type provider concept-id-revision-id-tuples]
  (filter
   identity
   (map (fn [[concept-id revision-id]]
          (get-concept db concept-type provider concept-id revision-id))
        concept-id-revision-id-tuples)))

(defn get-latest-concepts
[db concept-type provider concept-ids]
  (let [concept-id-set (set concept-ids)
        concept-map (reduce (fn [concept-map {:keys [concept-id revision-id] :as concept}]
                             (if (contains? concept-id-set concept-id)
                               (cond

                                 (nil? (get concept-map concept-id))
                                 (assoc concept-map concept-id concept)

                                 (> revision-id (:revision-id (get concept-map concept-id)))
                                 (assoc concept-map concept-id concept)

                                 :else
                                 concept-map)
                               concept-map))
                            {}
                            @(:concepts db))]
   (keep (partial get concept-map) concept-ids)))

(defn get-transactions-for-concept
  [db provider con-id]
  (keep (fn [{:keys [concept-id revision-id transaction-id]}]
         (when (= con-id concept-id)
           {:revision-id revision-id :transaction-id transaction-id}))
       @(:concepts db)))

(defn save-concept
  [db provider concept]
  {:pre [(:revision-id concept)]}

  (if-let [error (validate-concept-id-native-id-not-changing db provider concept)]
   ;; There was a concept id, native id mismatch with earlier concepts
   error
   ;; Concept id native id pair was valid
   (let [{:keys [concept-type provider-id concept-id revision-id]} concept
         concept (update-in concept
                            [:revision-date]
                            #(or % (f/unparse (f/formatters :date-time) (tk/now))))
         ;; Set the created-at time to the current timekeeper time for concepts which have
         ;; the created-at field and do not already have a :created-at time set.
         concept (if (some #{concept-type} [:collection :granule :service :variable])
                   (update-in concept
                              [:created-at]
                              #(or % (f/unparse (f/formatters :date-time) (tk/now))))
                   concept)
         concept (assoc concept :transaction-id (swap! (:next-transaction-id db) inc))
         concept (if (= concept-type :granule)
                   (-> concept
                       (dissoc :user-id)
                       ;; This is not stored in the real db.
                       (update-in [:extra-fields] dissoc :parent-entry-title))
                   concept)]
     (if (or (nil? revision-id)
             (get-concept db concept-type provider concept-id revision-id))
       {:error :revision-id-conflict}
       (do
         (swap! (:concepts db) (fn [concepts]
                                     (after-save db (conj concepts concept)
                                                 concept)))
         nil)))))

(defn force-delete
  [db concept-type provider concept-id revision-id]
  (swap! (:concepts db)
         #(filter
           (fn [c]
            (not (and (= concept-type (:concept-type c))
                      (= (:provider-id provider) (:provider-id c))
                      (= concept-id (:concept-id c))
                      (= revision-id (:revision-id c)))))
           %)))

(defn force-delete-concepts
  [db provider concept-type concept-id-revision-id-tuples]
  (doseq [[concept-id revision-id] concept-id-revision-id-tuples]
    (force-delete db concept-type provider concept-id revision-id)))

(defn get-concept-type-counts-by-collection
  [db concept-type provider]
  (->> @(:concepts db)
      (filter #(= (:provider-id provider) (:provider-id %)))
      (filter #(= concept-type (:concept-type %)))
      (group-by (comp :parent-collection-id :extra-fields))
      (map #(update-in % [1] count))
      (into {})))

(defn reset
  [db]
  (reset! (:concepts db) [])
  ;; XXX WAT; no calling into other implementations; split this out into a common ns
  (reset! (:next-id db) (dec cmr.metadata-db.data.oracle.concepts/INITIAL_CONCEPT_NUM))
  (reset! (:next-transaction-id db) 1))

(defn get-expired-concepts
  [db provider concept-type]
  (->> @(:concepts db)
       (filter #(= (:provider-id provider) (:provider-id %)))
       (filter #(= concept-type (:concept-type %)))
       latest-revisions
       (filter expired?)
       (remove :deleted)))

(defn get-tombstoned-concept-revisions
  [db provider concept-type tombstone-cut-off-date limit]
  (->> @(:concepts db)
       (filter #(= concept-type (:concept-type %)))
       (filter #(= (:provider-id provider) (:provider-id %)))
       (filter :deleted)
       (filter #(t/before? (p/parse-datetime (:revision-date %)) tombstone-cut-off-date))
       (map #(vector (:concept-id %) (:revision-id %)))
       (take limit)))

(defn get-old-concept-revisions
  [db provider concept-type max-versions limit]
  (letfn [(drop-highest
          [concepts]
          (->> concepts
               (sort-by :revision-id)
               (drop-last max-versions)))]
   (->> @(:concepts db)
        (filter #(= concept-type (:concept-type %)))
        (filter #(= (:provider-id provider) (:provider-id %)))
        (group-by :concept-id)
        vals
        (filter #(> (count %) max-versions))
        (mapcat drop-highest)
        (map concept->tuple))))

(def concept-store-behaviour
  {:generate-concept-id generate-concept-id
   :get-concept-id get-concept-id
   :get-granule-concept-ids get-granule-concept-ids
   :get-concept get-concept
   :get-concepts get-concepts
   :get-latest-concepts get-latest-concepts
   :get-transactions-for-concept get-transactions-for-concept
   :save-concept save-concept
   :force-delete force-delete
   :force-delete-concepts force-delete-concepts
   :get-concept-type-counts-by-collection get-concept-type-counts-by-collection
   :reset reset
   :get-expired-concepts get-expired-concepts
   :get-tombstoned-concept-revisions get-tombstoned-concept-revisions
   :get-old-concept-revisions get-old-concept-revisions})

(extend MemoryStore
        concepts/ConceptsStore
        concept-store-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB ProvidersStore Implementation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  [db {:keys [provider-id] :as provider}]
  (swap! (:providers db) assoc provider-id provider))

(defn get-providers
  [db]
  (vals @(:providers db)))

(defn get-provider
  [db provider-id]
  (@(:providers db) provider-id))

(defn update-provider
  [db {:keys [provider-id] :as provider}]
  (swap! (:providers db) assoc provider-id provider))

(defn delete-provider
  [db provider]
  ;; Cascade to delete the concepts
  (doseq [{:keys [concept-type concept-id revision-id]} (find-concepts db [provider] nil)]
   (force-delete db concept-type provider concept-id revision-id))

  ;; Cascade to delete the variable associations and service associations,
  ;; this is a hacky way of doing things
  (doseq [assoc-type [:variable-association :service-association]]
   (doseq [association (find-concepts db [{:provider-id "CMR"}]
                                         {:concept-type assoc-type})]
     (let [{:keys [concept-id revision-id extra-fields]} association
           {:keys [associated-concept-id variable-concept-id service-concept-id]} extra-fields
           referenced-providers (map (fn [cid]
                                       (some-> cid
                                               cc/parse-concept-id
                                               :provider-id))
                                     [associated-concept-id variable-concept-id service-concept-id])]
       ;; If the association references the deleted provider through
       ;; either collection or variable/service, delete the association
       (when (some #{(:provider-id provider)} referenced-providers)
         (force-delete db assoc-type {:provider-id "CMR"} concept-id revision-id)))))

  ;; to find items that reference the provider that should be deleted (e.g. ACLs)
  (doseq [{:keys [concept-type concept-id revision-id]} (find-concepts
                                                         db
                                                         [pv/cmr-provider]
                                                         {:target-provider-id (:provider-id provider)})]
   (force-delete db concept-type pv/cmr-provider concept-id revision-id))
  ;; finally delete the provider
  (swap! (:providers db) dissoc (:provider-id provider)))

(defn reset-providers
  [db]
  (reset! (:providers db) {}))

(def provider-store-behaviour
  {:save-provider save-provider
   :get-providers get-providers
   :get-provider get-provider
   :update-provider update-provider
   :delete-provider delete-provider
   :reset-providers reset-providers})

(extend MemoryStore
        providers/ProvidersStore
        provider-store-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MemoryStore Constructor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-db
  "Creates and returns an in-memory database."
  ([]
   (create-db []))
  ([concepts]
   (connection/create-db
    {:concepts concepts
     ;; XXX WAT; no calling into other implementations;
     ;      split this out into a common ns
     :next-id cmr.metadata-db.data.oracle.concepts/INITIAL_CONCEPT_NUM})))
