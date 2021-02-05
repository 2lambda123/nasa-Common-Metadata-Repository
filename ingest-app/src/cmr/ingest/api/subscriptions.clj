(ns cmr.ingest.api.subscriptions
  "Subscription ingest functions in support of the ingest API."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.metadata-db :as mdb])
  (:import
   [java.util UUID]))

(defn- validate-and-prepare-subscription-concept
  "Validate subscription concept, set the concept format and returns the concept;
  throws error if the metadata is not a valid against the UMM subscription JSON schema."
  [concept]
  (let [concept (update-in concept [:format] (partial ingest/fix-ingest-concept-format :subscription))]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)
    concept))

(defn- subscriber-collection-permission-error
  [subscriber-id concept-id]
  (errors/throw-service-error
   :unauthorized
   (format "Collection with concept id [%s] does not exist or subscriber-id [%s] does not have permission to view the collection."
           concept-id
           subscriber-id)))

(defn- check-subscriber-collection-permission
  "Checks that the subscriber-id can read the collection supplied in the subscription metadata"
  [request-context concept]
  (let [metadata (-> (:metadata concept)
                     (json/decode true))
        concept-id (:CollectionConceptId metadata)
        subscriber-id (:SubscriberId metadata)]
      (try
        (let [permissions (-> (access-control/get-permissions request-context
                                                              {:concept_id concept-id
                                                               :user_id subscriber-id})
                              json/decode
                              (get concept-id))]
          (when-not (some #{"read"} permissions)
            (subscriber-collection-permission-error
             subscriber-id
             concept-id)))
        (catch Exception e
          (subscriber-collection-permission-error
           subscriber-id
           concept-id)))))

(defn- perform-subscription-ingest
  "This function assumes all checks have already taken place and that a
  subscription is ready to be saved"
  [request-context concept headers]
  (let [validated-concept (validate-and-prepare-subscription-concept concept)
        _ (check-subscriber-collection-permission request-context concept)
        concept-with-user-id (api-core/set-user-id validated-concept
                                                   request-context
                                                   headers)
        ;; Log the ingest attempt before the save
        _ (info (format "Ingesting subscription %s from client %s"
                        (api-core/concept->loggable-string concept-with-user-id)
                        (:client-id request-context)))
        save-subscription-result (ingest/save-subscription request-context
                                                           concept-with-user-id)]
    ;; Log the successful ingest, with the metadata size in bytes.
    (api-core/log-concept-with-metadata-size concept-with-user-id request-context)
    (api-core/generate-ingest-response headers save-subscription-result)))

(defn- common-ingest-checks
  "Common checks needed before starting to process an ingest operation"
  [request-context provider-id]
  (common-enabled/validate-write-enabled request-context "ingest")
  (lt-validation/validate-launchpad-token request-context)
  (api-core/verify-provider-exists request-context provider-id))

(defn- check-subscription-management-acls
  "Check the user-id of the user submitting the request matches the
   subscriber-id of the subscription being modified."
  [context subscriber-id provider-id]
  (let [token-user (api-core/get-user-id-from-token context)]
    (if (and token-user
             (= token-user subscriber-id))
      (warn (format (str "ACLs were bypassed because the token account '%s' "
                         "matched the subscription user '%s' in the metadata.")
                    token-user
                    subscriber-id))
      (do
        (info (format (str "ACLs were checked because the token user %s is "
                           "not the same as the subscription user %s in the "
                           "metadata.")
                      token-user
                      subscriber-id))
        (acl/verify-ingest-management-permission
         context :update :provider-object provider-id)
        (acl/verify-subscription-management-permission
         context :update :provider-object provider-id)))))

(defn generate-native-id
  "Generate a native-id for a subscription based on the name."
  [subscription]
  (-> subscription
      :metadata
      (json/parse-string true)
      :Name
      csk/->snake_case
      (str "_" (UUID/randomUUID))))

(defn native-id-collision?
"Queries metadata db for a matching provider-id and native-id pair."
[context provider-id native-id]
(let [query {:provider-id provider-id
             :native-id native-id
             :exclude-metadata true
             :latest true}]
(-> context
    (mdb/find-concepts query :subscription)
    seq)))

(defn get-unique-native-id
"Get a native-id that is unique by testing against the database."
[context subscription]
(let [native-id (generate-native-id subscription)
      provider-id (:provider-id subscription)]
(if (native-id-collision? context provider-id native-id)
  (do
    (warn (format "Collision detected while generating native-id [%s] for provider [%s], retrying."
                  native-id provider-id))
    (get-unique-native-id context subscription))
  native-id)))

(defn create-subscription
  "Processes a request to create a subscription. A native id will be generated."
  [provider-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-ingest-checks request-context provider-id)
    (let [tmp-subscription (api-core/body->concept!
                            :subscription
                            provider-id
                            (str (UUID/randomUUID))
                            body
                            content-type
                            headers)
          native-id (get-unique-native-id request-context tmp-subscription)
          new-subscription (assoc tmp-subscription :native-id native-id)
          subscriber-id (:SubscriberId (json/decode (:metadata new-subscription) true))]
      (check-subscription-management-acls request-context subscriber-id provider-id)
      (perform-subscription-ingest request-context new-subscription headers))))

(defn create-subscription-with-native-id
  "Processes a request to create a subscription using the native-id provided."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-ingest-checks request-context provider-id)
    (when (native-id-collision? request-context provider-id native-id)
      (errors/throw-service-error
       :collision (format "Subscription with with provider-id [%s] and native-id [%s] already exists."
                          provider-id
                          native-id)))
    (let [new-subscription (api-core/body->concept!
                            :subscription
                            provider-id
                            native-id
                            body
                            content-type
                            headers)
          subscriber-id (:SubscriberId (json/decode (:metadata new-subscription) true))]
      (check-subscription-management-acls request-context subscriber-id provider-id)
      (perform-subscription-ingest request-context new-subscription headers))))

(defn create-or-update-subscription-with-native-id
  "Processes a request to create or update a subscription. This function
  does NOT fail on collisions. This is mapped to PUT methods to preserve
  existing functionality."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        new-subscription (api-core/body->concept! :subscription
                                                  provider-id
                                                  native-id
                                                  body
                                                  content-type
                                                  headers)
        original-subscriber (when-let [original-subscription
                                       (first (mdb/find-concepts
                                               request-context
                                               {:provider-id provider-id
                                                :native-id native-id
                                                :exclude-metadata false
                                                :latest true}
                                               :subscription))]
                              (get-in original-subscription [:extra-fields :subscriber-id]))
        subscriber-id (or original-subscriber
                          (:SubscriberId (json/decode (:metadata new-subscription) true)))]
    (common-ingest-checks request-context provider-id)
    (check-subscription-management-acls request-context subscriber-id provider-id)
    (perform-subscription-ingest request-context new-subscription headers)))

(defn delete-subscription
  "Deletes the subscription with the given provider id and native id."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        subscriber-id (when-let [subscription (first (mdb/find-concepts
                                                      request-context
                                                      {:provider-id provider-id
                                                       :native-id native-id
                                                       :exclude-metadata false
                                                       :latest true}
                                                      :subscription))]
                        (get-in subscription [:extra-fields :subscriber-id]))
        concept-attribs (-> {:provider-id provider-id
                             :native-id native-id
                             :concept-type :subscription}
                            (api-core/set-revision-id headers)
                            (api-core/set-user-id request-context headers))]

    (common-ingest-checks request-context provider-id)
    (check-subscription-management-acls request-context subscriber-id provider-id)

    (info (format "Deleting subscription %s from client %s"
                  (pr-str concept-attribs) (:client-id request-context)))
    (api-core/generate-ingest-response headers
                                       (api-core/format-and-contextualize-warnings
                                        (ingest/delete-concept
                                         request-context
                                         concept-attribs)))))
