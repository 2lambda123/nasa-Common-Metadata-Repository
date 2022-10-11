(ns cmr.common.generics
  "Defines utilities for new generic document pipeline. Most functions will deal
   with either returning generic config files, or lists of approved generics."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :as cfg]
   [cmr.common.log :as log :refer (error)]
   [cmr.schema-validation.json-schema :as js-validater]
   [inflections.core :as inf]))

(defn approved-generic?
  "Check to see if a requested generic is on the approved list.
   Parameters:
   * schema: schema keyword like :grid
   * version: string like 0.0.1
   Returns: true if schema and version are supported, nil otherwise"
  [schema version]
  (when (and schema version)
    (some #(= version %) (schema (cfg/approved-pipeline-documents)))))

(defn latest-approved-documents
  "Return a map of all the configured approved generics and the latest version
   string for each one.
   Return {:doc-type \"1.2.3\"}"
  []
  (reduce (fn [data item]
            (assoc data (first item) (last (second item))))
          {}
          (cfg/approved-pipeline-documents)))

(defn latest-approved-document-types
  "Return a list of configured approved generic keywords
   Returns: (:grid :dataqualitysummary ...)"
  []
  (keys (latest-approved-documents)))

(defn read-schema-file
  "Return the specific schema given the schema keyword name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * file-name: [metadata | index | schema]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version]
  (try
    (-> "schemas/%s/v%s/%s.json"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp))
    (catch Exception e
      (error
       (format (str "The %s.json file for schema [%s] version [%s] cannot be found. "
                    " - [%s] - "
                    "Please make sure that it exists. %s")
               (name file-name)
               (name generic-keyword)
               generic-version
               (format "schemas/%s/v%s/%s.json" (name generic-keyword) generic-version (name file-name))
               (.getMessage e)))
      (println "read-schema-file failed"))))

(defn read-schema-index
  "Return the schema index configuration file given the schema name and version
   number. Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "index" generic-keyword generic-version))

(defn read-schema-specification
  "Return the schema specification file given the schema name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "schema" generic-keyword generic-version))

(defn read-schema-example
  "Return the schema example metadata file given the schema name and version
   number. Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "metadata" generic-keyword generic-version))

(defn validate-index-against-schema
  "Validate a document, returns an array of errors if there are problems
   Parameters:
   * raw-json, json as a string to validate
   Returns: list of errors or nil"
  [raw-json]
  (let [schema-file (read-schema-file :schema :index "0.0.1")
        schema-obj (js-validater/json-string->json-schema schema-file)]
    (js-validater/validate-json schema-obj raw-json)))

(defn approved-generic-concept-prefixes
  "Return the active list of approved generic content types with the defined
   prefix in the :SubConceptType field found in the index.json file. If field is
   not defined, then X is used.
   Parameters: none, based off approved-documents?
   Return: {doc-type \"concept-prefix\"}"
  []
  (reduce (fn [data item]
            (let [generic-keyword (first item)
                  index-raw (read-schema-index generic-keyword (second item))
                  parse-errors (validate-index-against-schema index-raw)]
              (when-not (some? parse-errors)
                (assoc data
                       generic-keyword
                       (get (json/parse-string index-raw true) :SubConceptType "X")))))
          {}
          (latest-approved-documents)))

;(def ingest-table-of-contents-template "</ul></li></ul> <li>%uc-plural-generic%\n<ul>\n<li>/providers/&lt;provider-id&gt;/%plural-generic%/&lt;native-id&gt;\n<ul>\n<li><a href=\"#create-update-%generic%\">PUT - Create or update a %generic%.</a></li>\n<li><a href=\"#delete-%generic%\">DELETE - Delete a %uc-generic%.</a></li>")

;(def search-table-of-contents-template "</ul></li><li><a href=\"#%generic%\">%uc-generic%</a>\n<ul>\n<li><a href=\"#searching-for-%plural-generic%\">Searching for %uc-plural-generic%</a>\n<ul>\n<li><a href=\"#%generic%-search-params\">%uc-generic% Search Parameters</a></li>\n<li><a href=\"#%generic%-search-response\">%uc-generic% Search Response</a></li>\n</ul>\n</li>\n<li><a href=\"#retrieving-all-revisions-of-a-%generic%\">Retrieving All Revisions of a %uc-generic%</a></li>")


(def ingest-table-of-contents-template (slurp (io/resource "ingest-table-of-contents-template.txt")))
(def search-table-of-contents-template (slurp (io/resource "search-table-of-contents-template.txt")))


;; (def search-table-of-contents-template (slurp (io/resource "search-table-of-contents-template.txt")))

(defn read-generic-doc-file
  "Return the specific schema's documentation files given the schema keyword name and version number.
   if the file cannot be read, return an empty string which will have no impact on the API document.
   Parameters:
   * file-name: [ingest | search]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version]
  (try
    (-> "schemas/%s/v%s/%s.md"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp))
    (catch Exception e (str ""))))

(defn all-generic-docs
  "Parse over all of the generic documents and return their combined markdown as a string
  Parameters:
  * file-name: [ingest | search]
  Returns: string"
  [file-name]
  (string/join (seq (for [[k,v] (latest-approved-documents)] (read-generic-doc-file file-name k (str v))))))

;; (defn create-generic-table-of-contents
;;   "From the markdown file string of either the ingest or search, dyanamically create the table of contents"
;;   [ generic-markdown]
;;   (re-seq #"\#\s+<a.*\n" generic-markdown))

;HTML templates for generic table of content items

(defn get-generics-with-documenation
  "Retrieve the names of the generics that have documentation files. Re-seq returns both the full match and the group, so parse out all of the odd numbered indicies
   so that only the group matches remain
   Parameters:
   * generic-markdown: a slurped markdown file
   Returns: string"
  [generic-markdown]
  (take-nth 2 (rest (flatten (re-seq #"### <a name=\"create-update-([a-z]+)" generic-markdown)))))

(defn table-of-contents-html
  "Return the html for the table of contents given a generic concept
   Parameters:
   * table-of-contents-template: [ingest-table-of-contents-template | search-table-of-contents-template]
   Returns: string"
  [table-of-contents-template generic-type]
  (-> table-of-contents-template
      (string/replace #"%generic%" generic-type)
      (string/replace  #"%uc-generic%" (string/capitalize generic-type))
      (string/replace  #"%plural-generic%" (inf/plural generic-type))
      (string/replace  #"%uc-plural-generic%" (inf/plural (string/capitalize generic-type)))))

(defn all-generic-table-of-contents
  "Parse over all of the generic documents and return their combined html for the table of contents as a string
   we use ingest to retrieve the list of generics with documenation for both search and ingest as it would be expected both documents would be in the schema dir
   Parameters:
   * table-of-contents-template: [ingest-table-of-contents-template | search-table-of-contents-template]
   Returns: string"
  [table-of-contents-template]
  (string/join (mapv #(table-of-contents-html table-of-contents-template %) (get-generics-with-documenation (all-generic-docs "ingest")))))
