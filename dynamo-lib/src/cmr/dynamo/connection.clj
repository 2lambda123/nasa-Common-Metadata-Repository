(ns cmr.dynamo.connection
  "Contains functions for interacting with the DynamoDB storage instance"
  (:require [clojure.string :as str]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer [debug error info trace warn]]
            [taoensso.faraday :as far]
            [cmr.dynamo.config :as dynamo-config]))

(defn concept-revision-tuple->key
  "Extracts data from a tuple structured as (concept-id, revision-id) into an associative array that can be used to query DynamoDB"
  [tuple]
  {:concept-id (first tuple) :revision-id (second tuple)})

(def connection-options 
  {:endpoint (dynamo-config/dynamo-url)})

(defn save-concept
  "Uses Farady to send an entire concept (minus the :metadata :created-at and :revision-date fields) to save into DynamoDB"
  [concept]
  (info "Concept passed to dynamo/save-concept: " concept)
  (far/put-item connection-options (dynamo-config/dynamo-table) (dissoc concept :metadata :created-at :revision-date)))

(defn get-concept
  "Gets a concept from DynamoDB. If revision-id is not specified, queries DynamoDB by concept-id, returning the most recent revision"
  ([concept-id]
   (first (far/query connection-options (dynamo-config/dynamo-table) {:concept-id [:eq concept-id]} {:order :desc :limit 1})))
  ([concept-id revision-id]
   (far/get-item connection-options (dynamo-config/dynamo-table) {:concept-id concept-id :revision-id revision-id})))

(defn get-concepts-provided
  "Gets a group of concepts from DynamoDB. DynamoDB imposes a 100 batch limit on batch-get-item so that is applied here"
  [concept-id-revision-id-tuples]
  (remove nil? (doall (map (fn [batch] (far/batch-get-item connection-options {(dynamo-config/dynamo-table) {:prim-kvs (vec batch)}})) (partition-all 100 (map concept-revision-tuple->key concept-id-revision-id-tuples))))))

(defn get-concepts
  "Gets a group of concepts from DynamoDB based on search parameters"
  [params]
  (info "Params for searching DynamoDB: " params))

(defn get-concepts-small-table
  "Gets a group of concepts from DynamoDB using provider-id, concept-id, revision-id tuples"
  [params]
  (info "Params for searching DynamoDB small-table: " params))

(defn delete-concept
  "Deletes a concept from DynamoDB"
  [concept-id revision-id]
  (far/delete-item connection-options (dynamo-config/dynamo-table) {:concept-id concept-id :revision-id revision-id}))

(defn delete-concepts-provided
  "Deletes multiple concepts from DynamoDB using batch-write-item, which has a 25 item batch limit imposed by DynamoDB"
  [concept-id-revision-id-tuples]
  (doall (map (fn [batch] (far/batch-write-item connection-options 
                                                {(dynamo-config/dynamo-table) {:delete (vec batch)}})) 
              (partition-all 25 (map concept-revision-tuple->key concept-id-revision-id-tuples)))))

(defn delete-concepts
  "Deletes multiple concepts from DynamoDB by search parameters"
  [params]
  (info "Params for deleting from DynamoDB: " params))
