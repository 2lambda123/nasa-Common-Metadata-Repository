(ns cmr.ingest.api.provider
  "Defines the HTTP URL routes for the provider endpoint in the ingest application.

   Example usage:
   server=\"http://localhost:3002\"
   token=\"mock-echo-system-token\"
   file_path=\"schemas/resources/schemas/provider/v1.0.0/metadata.json\"
   curl -s \\
       -X PUT \\
       -H \"Cmr-Pretty: true\" \\
       -H \"Content-type: application/json\" \\
       -H \"Authorization: $token\" \\
       $server/providers/PROVX \\
       -d \"@$full_path\"
   "
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.generics :as gcom]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as srvc-errors]
   [cmr.ingest.services.provider-service :as provider-service]
   [compojure.core :refer :all]))

(defn- drop-metadata
  "Look for and remove metadata field from the body returned by the metadata db
   service, as it is not needed in the legacy responses."
  [response]
  (let [body (-> response
                 :body
                 json/parse-string
                 (as-> metadata (map #(dissoc % "metadata") metadata))
                 json/generate-string)]
    (assoc response :body body)))

(defn- result->response-map
  "Returns the response map of the given result, in the old style, no metadata"
  [result]
  (let [{:keys [status body]} result]
    {:status status
     :headers {"Content-Type" (mt/with-utf-8 mt/json)}
     :body body}))

(defn- one-result->response-map
  "Returns the response map of the given result, but this expects there to be
   just one value and it only returns the metadata, see result->response-map for
   the older return type. However, if there is an error in the response, then the
   body is returned as is"
  [result]
  (let [{:keys [status body]} result
        metadata (-> body
                     (json/parse-string)
                     (get "metadata")
                     (json/generate-string))]
    {:status status
     :headers {"Content-Type" (mt/with-utf-8 mt/json)}
     :body (if (= status 200) metadata body)}))

(defn read-body
  [headers body-input]
  (if (= mt/json (mt/content-type-mime-type headers))
    (json/decode (slurp body-input) true)
    (srvc-errors/throw-service-error
      :invalid-content-type "Creating or updating a provider requires a JSON content type.")))

(defn- validate-provider-metadata
  "Validate the provider metadata against a schema, throwing an exception if
   something does not match"
  [concept]
  (let [results (-> concept
                    (json/generate-string)
                    (gcom/validate-metadata-against-schema :provider "1.0.0" false))]
    (when (some? results) (srvc-errors/throw-service-errors :invalid-data results))))

(defn- validate-boolean
  "Throw an error if one of the boolean inputs to provider does not look boolean"
  [field property-name]
  (when-not (or (nil? field) (true? field) (false? field))
    (srvc-errors/throw-service-error
     :invalid-data
     (format "%s must be either true or false but was [\"%s\"]" property-name field))))

(defn- validate-and-prepare-provider-concept
  "Validate a provider concept and construct a map that is usable to the metadata_db
   service. This function will accept two types of providers, Legacy and Modern.
   A legacy provider is just 4 fields, provider-id, short-name, cmr-only, and small.
   These fields were sent in by the legacy-services server and will create a
   provider with no metadata. A Modern provider is defined with a metadata schema
   and will include lots of fields. Both formats will take short-name, cmr-only,
   and small but the modern format will strip these out. provider-id is encoded
   differently in both these formats, legacy will use the cmr style case where as
   modern will camel case the value. When you strip out all the legacy service
   fields you should be left with a metadata record. This fact is also used to
   differentiate between the two styles of posting, an empty record is the legacy
   style, where as modern will continue to have fields.

   throws error if the metadata is not a valid against the UMM service JSON schema."
  [concept]
  ;; find fields and construct the MetadataDB format
  (let [provider-id (get concept :ProviderId (get concept :provider-id))
        short-name provider-id ;; every production provider does this anyways, so make it official
        small (:small concept)
        cmr-only (:cmr-only concept)
        consortiums (string/join " " (get concept :Consortiums ""))
        metadata (-> concept (dissoc :provider-id :short-name :cmr-only :small))]
    (when-not (empty? metadata) (validate-provider-metadata metadata))
    (validate-boolean small "Small")
    (validate-boolean cmr-only "Cmr Only")
    ;; structure the result in a database friendly map, each key mapping to a
    ;; table column similar to that of a Concept table. Metadata may be empty
    ;; for the legacy-services use case
    {:provider-id provider-id
     :short-name short-name
     :cmr-only (if (some? cmr-only) cmr-only false)
     :small (if (some? small) small false)
     :consortiums consortiums
     :metadata metadata}))

(def provider-api-routes
  (context "/providers" []

    ;; create a new provider
    (POST "/" {:keys [request-context body params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (common-enabled/validate-write-enabled request-context "ingest")
      (one-result->response-map
        (provider-service/create-provider request-context
                                          (validate-and-prepare-provider-concept
                                           (read-body headers body)))))

    ;; read a provider
    (GET "/:provider-id" {{:keys [provider-id] :as params} :params
                          request-context :request-context
                          headers :headers}
      (one-result->response-map (provider-service/read-provider request-context provider-id)))

    ;; update an existing provider
    (PUT "/:provider-id" {{:keys [provider-id] :as params} :params
                          request-context :request-context
                          body :body
                          headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (common-enabled/validate-write-enabled request-context "ingest")
      (one-result->response-map
       (provider-service/update-provider request-context
                                         (validate-and-prepare-provider-concept
                                          (read-body headers body)))))

    ;; delete a provider
    (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params
                             request-context :request-context
                             headers :headers}
      (acl/verify-ingest-management-permission request-context :update)
      (common-enabled/validate-write-enabled request-context "ingest")
      (provider-service/verify-empty-provider request-context provider-id headers)
      (one-result->response-map
       (provider-service/delete-provider request-context provider-id)))

    ;; get a list of providers, but return in the old format where metadata is in a field
    (GET "/" {:keys [request-context]}
      (result->response-map
       (drop-metadata
        (provider-service/get-providers-raw request-context))))))

(comment

  ;; Create a sample provider map and then try to validate it. The provider ingest
  ;; may not be approved for public documentation, so this may be one of the few
  ;; examples of what a document is to look like.
  (let [concept {:ProviderId "REAL_ID"
                 :cmr-only false
                 :small "yes"
                 ;:wrong "test" ;; uncomment this line out to see a schema error
                 :DescriptionOfHolding "sample provider, no data"
                 :Organizations [{:Roles ["ORIGINATOR"]
                                  :ShortName "REAL_ID"
                                  :URLValue "https://example.gov"}]
                 :MetadataSpecification {:Name "Provider"
                                         :URL "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0"
                                         "Version" "1.0.0"}
                 :Consortiums ["one" "two" "*-lord!"]}
        metadata (json/generate-string concept)]
    (validate-and-prepare-provider-concept concept))


  )
