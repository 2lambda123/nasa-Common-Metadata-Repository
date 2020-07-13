(ns cmr.system-int-test.search.autocomplete-collection-facet-search-test
  "Integration tests for autocomplete collection search facets"
  (:require
    [clojure.test :refer :all]
    [cheshire.core :as json]
    [clojurewerkz.elastisch.rest :as esr]
    [clojurewerkz.elastisch.rest.document :as esd]
    [cmr.common.log :as log :refer [debug]]
    [cmr.common.util :refer [are3]]
    [cmr.system-int-test.utils.url-helper :as url]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.search-util :as search]))

(def ^:private test-values [{:value "foo" :type "instrument"}
                            {:value "bar" :type "platform"}
                            {:value "baaz" :type "instrument"}
                            {:value "BAAZ" :type "platform"}
                            {:value "ATMOSPHERE:EARTH SCIENCE:LIQUID PRECIPITATION:RAIN:FREEZING RAIN"
                             :type "science_keyword"
                             :fields "variable-level-3"}
                            {:value "africa" :type "spatial_keyword"}
                            {:value "antarctica" :type "spatial_keyword"}
                            {:value "arctic" :type "spatial_keyword"}
                            {:value "asia" :type "spatial_keyword"}
                            {:value "australia" :type "spatial_keyword"}
                            {:value "europe" :type "spatial_keyword"}
                            {:value "north america" :type "spatial_keyword"}
                            {:value "south america" :type "spatial_keyword"}
                            {:value "arctic ocean" :type "spatial_keyword"}
                            {:value "atlantic ocean" :type "spatial_keyword"}
                            {:value "pacific ocean" :type "spatial_keyword"}
                            {:value "indian ocean" :type "spatial_keyword"}])

(defn autocomplete-fixture
  [f]
  (let [conn (esr/connect (url/elastic-root))
        documents (map #(esd/create conn "1_autocomplete" "suggestion" %) test-values)]
    (doseq [doc documents] (debug "ingested " doc))
    (index/wait-until-indexed)
    (f)
    (index/wait-until-indexed)))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture)
                      autocomplete-fixture]))

(defn- query->json-response-body
  ([query]
   (query->json-response-body query nil))
  ([query opts]
  (let [response (search/get-autocomplete-suggestions query opts)
        body (:body response)
        data (json/parse-string body true)]
    data)))

(defn- response-body->entries
  [data]
  (:entry (:feed data)))

(deftest autocomplete-response-test
  (testing "response form test"
   (let [response (search/get-autocomplete-suggestions "q=foo")
         body (:body response)
         data (json/parse-string body true)]
     (is (contains? data :feed))
     (is (contains? (:feed data) :entry))))

  (testing "returns CMR-Hits header"
   (let [response (search/get-autocomplete-suggestions "q=foo")
         headers (:headers response)]
     (is (:CMR-Hits headers))
     (is (:CMR-Took headers))))

  (testing "entries return in descending score order"
   (let [response (query->json-response-body "q=bar")
         entries (response-body->entries response)
         a (first entries)
         b (second entries)]
     (is (> (:score a) (:score b))))))

(deftest autocomplete-functionality-test
  (testing "value search"
   (are3
    [query expected]
    (is (= expected (count (response-body->entries (query->json-response-body query)))))

    "partial value"
    "q=f" 1

    "full value match"
    "q=foo" 1

    "full value with extra value match"
    "q=foos" 1

    "case sensitivity test"
    "q=FOO" 1

    "no result test"
    "q=zee" 0

    "search with type filter"
    "q=foo&type[]=instrument" 1

    "search with multiple types filter"
    "q=b&type[]=instrument&type[]=platform&type[]=project&type[]=provider" 3

    "search with type filter with no results"
    "q=foo&type[]=platform" 0)))

(deftest autocomplete-usage-test
  (testing "invalid or missing query param tests: "
   (are3
    [query]
    (is (= 400 (:status (search/get-autocomplete-suggestions query {:throw-exceptions false}))))

    "no query provided"
    nil

    "blank query provided"
    "q=  "

    "page_size too large"
    "q=ice&page_size=999999999"

    "page_size non-positive"
    "q=ice&page_size=-1"))

  (testing "page_size test"
   (are3
    [query expected cmr-hits]
    (let [response (search/get-autocomplete-suggestions query)
          headers (:headers response)
          body (json/parse-string (:body response) true)
          entry (:entry (:feed body))]
      (is (= (str cmr-hits) (:CMR-Hits headers)))
      (is (= expected (count entry))))

    "page size 0"
    "q=an&page_size=0" 0 10

    "page size 1"
    "q=an&page_size=1" 1 10

    "page size 2"
    "q=an&page_size=2" 2 10

    "page size 100"
    "q=an*&page_size=100" 10 10))

  (testing "page_num should default to 1"
   (let [a (as-> (query->json-response-body "q=b") response
                 (first (response-body->entries response)))

         b (as-> (query->json-response-body "q=b&page_num=1") response
                 (first (response-body->entries response)))]
     (is (= a b))))

  (testing "page_num should yield different 'first' results test"
   (let [page-one (as-> (query->json-response-body "q=b&page_size=1&page_num=1") response
                        (response-body->entries response))

         page-two(as-> (query->json-response-body "q=b&page_size=1&page_num=2") response
                       (response-body->entries response))]
     (is (not= page-one page-two)))))
