(ns cmr.metadata.proxy.concepts.variable
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX We can pull this from configuration once async-get-metadata function
;;     signatures get updated to accept the system data structure as an arg.
(def variables-api-path "/variables")
(def pinned-variable-schema-version "1.4")
(def results-content-type "application/vnd.nasa.cmr.umm_results+json")
(def charset "charset=utf-8")
(def accept-format "%s; version=%s; %s")
(def accept-header (format accept-format
                           results-content-type
                           pinned-variable-schema-version
                           charset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-query
  "Build variable search query for variable-ids and variable-aliases separately.
  At least one of the parameters is nil when this is called."
  [variable-ids variable-aliases]
  (if (seq variable-ids)
    (string/join
      "&"
      (conj
        (map #(str (codec/url-encode "concept_id[]") "=" %) variable-ids)
        (str "page_size=" (count variable-ids))))
    (string/join
      "&"
      (conj
        (map #(str (codec/url-encode "alias[]") "=" %) variable-aliases)
        (str "page_size=" (count variable-aliases))))))

(defn async-get-metadata
  "Given a 'params' data structure with a ':variables' key (which may or may
  not have values) and a list of all collection variable-ids, return the
  metadata for the passed variables, if defined, and for all associated
  variables, if params does not contain any."
  [search-endpoint user-token variable-ids variable-aliases]
  (if (or (seq variable-ids) (seq variable-aliases))
    (let [url (str search-endpoint variables-api-path)
          payload (build-query variable-ids variable-aliases)]
      (log/debug "Variables query CMR URL:" url)
      (log/debug "Variables query CMR payload:" payload)
      (request/async-post
       url
       (-> {}
           (request/add-token-header user-token)
           (request/add-accept accept-header)
           (request/add-form-ct)
           (request/add-payload payload)
           ((fn [x] (log/trace "Full request:" x) x)))
       {}
       response/json-handler))
    (deliver (promise) [])))

(defn async-get-metadata-by-ids
  "Get variables by ids"
  [search-endpoint user-token {variable-ids :variables}]
  (async-get-metadata search-endpoint user-token variable-ids nil))

(defn async-get-metadata-by-aliases
  "Get variables by aliases"
  [search-endpoint user-token {variable-aliases :variable-aliases}]
  (async-get-metadata search-endpoint user-token nil variable-aliases))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error metadata-errors/variable-metadata)
        (log/error rslts)
        rslts)
      (do
        (log/trace "Got results from CMR variable search:"
                   (results/elided rslts))
        (log/trace "Remaining results:" (results/remaining-items rslts))
        (:items rslts)))))

(defn get-metadata
  [search-endpoint user-token params]
  (let [promise-by-ids (async-get-metadata-by-ids search-endpoint user-token params)
        promise-by-aliases (async-get-metadata-by-aliases search-endpoint user-token params)
        variables-by-ids (extract-metadata promise-by-ids)
        variables-by-aliases (extract-metadata promise-by-aliases)]
    (distinct (concat variables-by-ids variables-by-aliases))))
