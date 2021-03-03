(ns cmr.metadata-db.data.oracle.concepts.subscription
  "Implements multi-method variations for subscriptions"
  (:require
   [cmr.metadata-db.data.oracle.concepts :as concepts]
   [cmr.oracle.connection :as oracle]))

(defn add-last-notified-at-if-present
  [subscription db-result db]
  (if-let [last-notified (:last_notified_at db-result)]
    (assoc-in subscription [:extra-fields :last-notified-at]
              (oracle/oracle-timestamp->str-time db last-notified))
    subscription))

(defmethod concepts/db-result->concept-map :subscription
  [concept-type db provider-id result]
  (some-> (concepts/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :subscription)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :subscription-name] (:subscription_name result))
          (assoc-in [:extra-fields :subscriber-id] (:subscriber_id result))
          (add-last-notified-at-if-present result db)
          (assoc-in [:extra-fields :collection-concept-id]
                    (:collection_concept_id result))))

(defn- subscription-concept->insert-args
  [concept]
  (let [{{:keys [subscription-name
                 subscriber-id
                 collection-concept-id]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (concepts/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "subscription_name"
                   "subscriber_id" "collection_concept_id"])
     (concat values [provider-id user-id subscription-name
                     subscriber-id collection-concept-id])]))

(defmethod concepts/concept->insert-args [:subscription false]
  [concept _]
  (subscription-concept->insert-args concept))

(defmethod concepts/concept->insert-args [:subscription true]
  [concept _]
  (subscription-concept->insert-args concept))
