(ns cmr.ingest.api.routes
  "Defines the HTTP URL routes for the ingest API."
  (:require
   [clojure.stacktrace :refer [print-stack-trace]]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.request-context-user-augmenter :as augmenter]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.ingest.api.bulk :as bulk]
   [cmr.ingest.api.collections :as collections]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.granules :as granules]
   [cmr.ingest.api.multipart :as mp]
   [cmr.ingest.api.provider :as provider-api]
   [cmr.ingest.api.services :as services]
   [cmr.ingest.api.translation :as translation-api]
   [cmr.ingest.api.variables :as variables]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.jobs :as jobs]
   [compojure.core :refer [DELETE GET POST PUT context routes]]
   [drift.execute :as drift]))

(def URS_TOKEN_MAX_LENGTH 100)
(def CMR_INGEST_SEPARATOR "CMR_INGEST:")

(def db-migration-routes
  (POST "/db-migrate"
      {ctx :request-context params :params}
      (acl/verify-ingest-management-permission ctx :update)
      (let [migrate-args (if-let [version (:version params)]
                           ["migrate" "-version" version]
                           ["migrate"])]
        (info "Running db migration:" migrate-args)
        (drift/run
         (conj
          migrate-args
          "-c"
          "config.migrate-config/app-migrate-config")))
      {:status 204}))

(def job-management-routes
  (common-routes/job-api-routes
   (routes
     (POST "/reindex-collection-permitted-groups"
           {ctx :request-context}
           (acl/verify-ingest-management-permission ctx :update)
           (jobs/reindex-collection-permitted-groups ctx)
           {:status 200})
     (POST "/reindex-all-collections"
           {ctx :request-context params :params}
           (acl/verify-ingest-management-permission ctx :update)
           (jobs/reindex-all-collections
            ctx (= "true" (:force_version params)))
           {:status 200})
     (POST "/cleanup-expired-collections"
           {ctx :request-context}
           (acl/verify-ingest-management-permission ctx :update)
           (jobs/cleanup-expired-collections ctx)
           {:status 200})
     (POST "/trigger-full-collection-granule-aggregate-cache-refresh"
           {ctx :request-context}
           (acl/verify-ingest-management-permission ctx :update)
           (jobs/trigger-full-refresh-collection-granule-aggregation-cache
            ctx)
           {:status 200})
     (POST "/trigger-partial-collection-granule-aggregate-cache-refresh"
           {ctx :request-context}
           (acl/verify-ingest-management-permission ctx :update)
           (jobs/trigger-partial-refresh-collection-granule-aggregation-cache
            ctx)
           {:status 200}))))

(defn- add-cmr-ingest-to-context
  "Add CMR Ingest prefix to token in the request context of the given request.
   This function should be called on routes that will ingest into CMR.
   The CMR Ingest prefix is used to indicates to legacy services that when
   validating the Launchpad token, the NAMS CMR Ingest group should also be checked.
   Ingest will only be allowed if the user is in the NAMS CMR Ingest group and
   also has the right ACLs which is based on Earthdata Login uid."
  [request]
  (let [token (-> request :request-context :token)]
    (if (> (count token) URS_TOKEN_MAX_LENGTH)
      ;; for Launchpad token add CMR_INGEST: prefix so that legacy service
      ;; can do extra vaidation to check if the user has signed up for
      ;; CMR Ingest workflow.
      (-> request
          (update-in [:request-context :token] #(str CMR_INGEST_SEPARATOR %))
          (update-in [:request-context] augmenter/add-user-id-and-sids-to-context))
      request)))

(def ingest-routes
  (routes
    ;; Provider ingest routes
    (api-core/set-default-error-format
     :xml
     (context "/providers/:provider-id" [provider-id]
       ;; Collections
       (context ["/validate/collection/:native-id" :native-id #".*$"] [native-id]
         (POST "/"
           request
           (collections/validate-collection provider-id native-id request)))
       (context ["/collections/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (collections/ingest-collection provider-id native-id (add-cmr-ingest-to-context request)))
         (DELETE "/"
           request
           (collections/delete-collection provider-id native-id (add-cmr-ingest-to-context request))))
       ;; Granules
       (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
         (POST "/"
           request
           (granules/validate-granule provider-id native-id request)))
       (context ["/granules/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (granules/ingest-granule provider-id native-id (add-cmr-ingest-to-context request)))
         (DELETE "/"
           request
           (granules/delete-granule provider-id native-id (add-cmr-ingest-to-context request))))
       ;; Variables
       (context ["/variables/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (variables/ingest-variable provider-id native-id (add-cmr-ingest-to-context request)))
         (DELETE "/"
           request
           (variables/delete-variable provider-id native-id (add-cmr-ingest-to-context request))))
       ;; Services
       (context ["/services/:native-id" :native-id #".*$"] [native-id]
         (PUT "/"
           request
           (services/ingest-service provider-id native-id (add-cmr-ingest-to-context request)))
         (DELETE "/"
           request
           (services/delete-service provider-id native-id (add-cmr-ingest-to-context request))))
       ;; Bulk updates
       (context "/bulk-update/collections" []
         (POST "/"
           request
           (bulk/bulk-update-collections provider-id (add-cmr-ingest-to-context request)))
         (GET "/status" ; Gets all tasks for provider
           request
           (bulk/get-provider-tasks provider-id request))
         (GET "/status/:task-id"
           [task-id :as request]
           (bulk/get-provider-task-status provider-id task-id request)))))))

(defn build-routes [system]
  (routes
    (context (get-in system [:public-conf :relative-root-url]) []
      provider-api/provider-api-routes

      ;; Add routes for translating metadata formats
      translation-api/translation-routes

      ;; Add routes to create, update, delete, & validate concepts
      ingest-routes

      ;; db migration route
      db-migration-routes

      ;; add routes for managing jobs
      job-management-routes

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; add routes for checking health of the application
      (common-health/health-api-routes ingest/health)

      ;; add routes for enabling/disabling writes
      (common-enabled/write-enabled-api-routes
       #(acl/verify-ingest-management-permission % :update)))))
