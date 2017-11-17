(ns cmr.indexer.data.concepts.tag
  "Contains functions to parse and convert tag concepts"
  (:require [clojure.string :as str]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]
            [cmr.indexer.data.elasticsearch :as es]))

(defmethod es/parsed-concept->elastic-doc :tag
  [context concept parsed-concept]
  (let [{:keys [concept-id deleted]} concept
        {:keys [tag-key description originator-id]} parsed-concept]
    (if deleted
      ;; This is only called by re-indexing (bulk indexing)
      ;; Regular deleted tags would have gone through the index-service/delete-concept path.
      {:concept-id concept-id
       :deleted deleted}
      {:concept-id concept-id
       :tag-key.lowercase (util/safe-lowercase tag-key)
       :description description
       :originator-id.lowercase  (util/safe-lowercase originator-id)})))

(defn tag-association->elastic-doc
  "Converts the tag association into the portion going in the collection elastic document."
  [tag-association]
  (let [{:keys [tag-key originator-id data]} tag-association]
    {:tag-key.lowercase (util/safe-lowercase tag-key)
     :originator-id.lowercase  (util/safe-lowercase originator-id)
     :tag-value.lowercase (util/safe-lowercase data)}))
