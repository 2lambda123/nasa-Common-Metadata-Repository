(ns cmr.system-int-test.search.variable.variable-search-test
  "This tests searching variables."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variables]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"})
                variables/grant-all-variable-fixture]))

(deftest search-for-variables-validation-test
  (testing "Unrecognized parameters"
    (is (= {:status 400, :errors ["Parameter [foo] was not recognized."]}
           (variables/search {:foo "bar"}))))
  (testing "Unsupported parameters"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting variables."]}
           (variables/search {:sort-key "concept_id"})))
    (is (= {:status 400, :errors ["Parameter [entry_title] was not recognized."]}
           (variables/search {:entry_title "foo"}))))

  (testing "Unsupported options"
    (are [field option]
      (= {:status 400
          :errors [(format "Option [%s] is not supported for param [%s]" option (name field))]}
         (variables/search {field "foo" (format "options[%s][%s]" (name field) option) true}))
      :variable_name "and"
      :measurement "and"))

  (testing "Search with wildcards in concept_id param not supported."
    (is (= {:errors
            ["Concept-id [V*] is not valid."
             "Option [pattern] is not supported for param [concept_id]"],
            :status 400}
           (variables/search {:concept-id "V*" "options[concept-id][pattern]" true}))))

  (testing "Search with ignore_case in concept_id param not supported."
    (is (= {:errors
            ["Concept-id [V*] is not valid."
             "Option [ignore_case] is not supported for param [concept_id]"],
            :status 400}
           (variables/search {:concept-id "V*" "options[concept-id][ignore-case]" true}))))

  (testing "Default variable search result format is JSON"
    (let [{:keys [status headers]} (search/find-concepts-in-format nil :variable {})]
      (is (= 200 status))
      (is (= "application/json; charset=utf-8" (get headers "Content-Type")))))

  (testing "Unsuported result format"
    (is (= {:errors ["The mime type [application/xml] is not supported for variables."]
            :status 400}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format :xml :variable {})))))

  (testing "Unsuported result format"
    (is (= {:errors ["The mime type [application/atom+xml] is not supported for variables."]
            :status 400}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format
             nil :variable {} {:url-extension "atom"}))))))

(deftest search-for-variables-test
  (let [variable1 (variables/ingest-variable-with-attrs {:native-id "var1"
                                                         :Name "Variable1"
                                                         :LongName "Measurement1"})
        variable2 (variables/ingest-variable-with-attrs {:native-id "var2"
                                                         :Name "Variable2"
                                                         :LongName "Measurement2"})
        variable3 (variables/ingest-variable-with-attrs {:native-id "var3"
                                                         :Name "a subsitute for variable2"
                                                         :LongName "variable1"})
        variable4 (variables/ingest-variable-with-attrs {:native-id "var4"
                                                         :Name "v.other"
                                                         :LongName "m.other"})
        all-variables [variable1 variable2 variable3 variable4]]
    (index/wait-until-indexed)

    (are3 [expected-variables query]
      (variables/assert-variable-search expected-variables (variables/search query))

      "Find all"
      all-variables {}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match"
      [variable1]
      {:name "Variable1"}

      "By name case sensitive, default ignore-case true"
      [variable1]
      {:name "variable1"}

      "By name ignore case false"
      []
      {:name "variable1" "options[name][ignore-case]" false}

      "By name ignore case true"
      [variable1]
      {:name "variable1" "options[name][ignore-case]" true}

      "By name Pattern, default false"
      []
      {:name "*other"}

      "By name Pattern true"
      [variable4]
      {:name "*other" "options[name][pattern]" true}

      "By name Pattern false"
      []
      {:name "*other" "options[name][pattern]" false}

      "By multiple names"
      [variable1 variable2]
      {:name ["Variable1" "variable2"]}

      "By multiple names with options"
      [variable1 variable4]
      {:name ["Variable1" "*other"] "options[name][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; variable-name Param
      "By variable-name case sensitive - exact match"
      [variable1]
      {:variable-name "Variable1"}

      "By variable-name case sensitive, default ignore-case true"
      [variable1]
      {:variable-name "variable1"}

      "By variable-name ignore case false"
      []
      {:variable-name "variable1" "options[variable-name][ignore-case]" false}

      "By variable-name ignore case true"
      [variable1]
      {:variable-name "variable1" "options[variable-name][ignore-case]" true}

      "By variable-name Pattern, default false"
      []
      {:variable-name "*other"}

      "By variable-name Pattern true"
      [variable4]
      {:variable-name "*other" "options[variable-name][pattern]" true}

      "By variable-name Pattern false"
      []
      {:variable-name "*other" "options[variable-name][pattern]" false}

      "By multiple variable-names"
      [variable1 variable2]
      {:variable-name ["Variable1" "variable2"]}

      "By multiple variable-names with options"
      [variable1 variable4]
      {:variable-name ["Variable1" "*other"] "options[variable-name][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; measurement Param
      "By measurement case sensitive - exact match"
      [variable1]
      {:measurement "Measurement1"}

      "By measurement case sensitive, default ignore-case true"
      [variable1]
      {:measurement "measurement1"}

      "By measurement ignore case false"
      []
      {:measurement "measurement1" "options[measurement][ignore-case]" false}

      "By measurement ignore case true"
      [variable1]
      {:measurement "measurement1" "options[measurement][ignore-case]" true}

      "By measurement Pattern, default false"
      []
      {:measurement "*other"}

      "By measurement Pattern true"
      [variable4]
      {:measurement "*other" "options[measurement][pattern]" true}

      "By measurement Pattern false"
      []
      {:measurement "*other" "options[measurement][pattern]" false}

      "By multiple measurements"
      [variable1 variable2]
      {:measurement ["Measurement1" "measurement2"]}

      "By multiple measurements with options"
      [variable1 variable4]
      {:measurement ["measurement1" "*other"] "options[measurement][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; concept-id Param
      "By concept-id - single"
      [variable1]
      {:concept-id (:concept-id variable1)}

      "By concept-id - multiple"
      [variable1 variable2]
      {:concept-id [(:concept-id variable1) (:concept-id variable2)]}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; keyword Param
      "By keyword case insensitive - exact match"
      [variable1]
      {:keyword "Measurement1"}

      "By keyword case insensitive, mixed case"
      [variable1]
      {:keyword "measuREment1"}

      "By keyword match both variable_name and measurement"
      [variable1 variable3]
      {:keyword "variable1"}

      "By keyword match tokenized variable_name and measurement"
      [variable2 variable3]
      {:keyword "variable2"}

      "By keyword match explict string"
      [variable3]
      {:keyword "a subsitute for variable2"}

      "By keyword match partial string"
      [variable3]
      {:keyword "subsitute variable2"}

      "By keyword match wildcard *"
      [variable1 variable2]
      {:keyword "meas*"}

      "By keyword match wildcard *, also apply to tokenized string"
      [variable1 variable2 variable3]
      {:keyword "var*"}

      "By keyword match wildcard ?, no match"
      []
      {:keyword "meas?"}

      "By keyword match wildcard ?, match"
      [variable1 variable2]
      {:keyword "measurement?"}

      "By keyword multiple wildcards"
      [variable3]
      {:keyword "sub* variable?"})))

(deftest search-variable-by-science-keywords-keyword-test
  (let [sk1 (data-umm-cmn/science-keyword {:Category "Cat1"
                                           :Topic "Topic1"
                                           :Term "Term1"
                                           :VariableLevel1 "Level1-1"
                                           :VariableLevel2 "Level1-2"
                                           :VariableLevel3 "Level1-3"
                                           :DetailedVariable "SUPER DETAILED!"})
        sk2 (data-umm-cmn/science-keyword {:Category "Hurricane"
                                           :Topic "Laser spoonA"
                                           :Term "Extreme"
                                           :VariableLevel1 "Level2-1"
                                           :VariableLevel2 "Level2-2"
                                           :VariableLevel3 "Level2-3"})
        sk3 (data-umm-cmn/science-keyword {:Category "Cat2"
                                           :Topic "Topic1"
                                           :Term "Term1"
                                           :VariableLevel1 "Level3-1"
                                           :VariableLevel2 "Level3-2"
                                           :VariableLevel3 "Level3-3"
                                           :DetailedVariable "S@PER"})
        variable1 (variables/ingest-variable-with-attrs {:native-id "var1"
                                                         :Name "Variable1"
                                                         :LongName "Measurement1"
                                                         :ScienceKeywords [sk1 sk2]})
        variable2 (variables/ingest-variable-with-attrs {:native-id "var2"
                                                         :Name "Variable2"
                                                         :LongName "Measurement2"
                                                         :ScienceKeywords [sk3]})
        variable3 (variables/ingest-variable-with-attrs {:native-id "var3"
                                                         :Name "a subsitute for variable2"
                                                         :LongName "variable1"})]
    (index/wait-until-indexed)

    (are3 [expected-variables keyword]
      (variables/assert-variable-search expected-variables (variables/search {:keyword keyword}))

      ;; Science keywords
      "Category"
      [variable1]
      "Cat1"

      "Topic"
      [variable1 variable2]
      "Topic1"

      "Term"
      [variable1 variable2]
      "Term1"

      "Variable level 1"
      [variable1]
      "Level2-1"

      "Variable level 2"
      [variable1]
      "Level2-2"

      "Variable level 3"
      [variable1]
      "Level2-3"

      "Detailed variable"
      [variable1]
      "SUPER"

      "Combination of keywords"
      [variable1]
      "Hurricane Laser"

      "Combination of keywords - different order, case insensitive"
      [variable1]
      "laser hurricane"

      "Wildcards"
      [variable1 variable2]
      "s?per term*")))

(deftest deleted-variables-not-found-test
  (let [token (e/login (s/context) "user1")
        [coll1 coll2 coll3 coll4] (for [n (range 1 5)]
                        (d/ingest-umm-spec-collection
                         "PROV1"
                         (data-umm-c/collection n {})
                         {:token token}))
        var1-concept (variables/make-variable-concept {:native-id "var1"
                                                       :Name "Variable1"})
        variable1 (variables/ingest-variable var1-concept {:token token})
        var2-concept (variables/make-variable-concept {:native-id "var2"
                                                       :Name "Variable2"})
        variable2 (variables/ingest-variable var2-concept {:token token})
        all-variables [variable1 variable2]
        associated-collections [{:concept-id (:concept-id coll1)}
                                {:concept-id (:concept-id coll2)}
                                {:concept-id (:concept-id coll3)}
                                {:concept-id (:concept-id coll4)}]]
    (index/wait-until-indexed)

    ;; Now I should find the all variables when searching
    (variables/assert-variable-search all-variables (variables/search {}))

    ;; Delete variable1
    (ingest/delete-concept var1-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching variables does not find the deleted variable
    (variables/assert-variable-search [variable2] (variables/search {}))

    ;; Now verify that after we delete a variable that has variable association,
    ;; we can't find it through search
    ;; create variable associations on variable2
    (variables/associate-by-concept-ids token
                                        (:concept-id variable2)
                                        associated-collections)
    (index/wait-until-indexed)
    ;; Delete variable2
    (ingest/delete-concept var2-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching variables does not find the deleted variables
    (variables/assert-variable-search [] (variables/search {}))
    ;; verify searching collections by variable name also found no matches
    (d/refs-match? [] (search/find-refs :collection {:variable_name "Variable2"}))))

(deftest variable-search-in-umm-json-format-test
  (testing "variable search result in UMM JSON format has associated collections"
    (let [token (e/login (s/context) "user1")
          [coll1 coll2 coll3] (for [n (range 1 4)]
                                (d/ingest-umm-spec-collection
                                 "PROV1"
                                 (data-umm-c/collection n {})
                                 {:token token}))
          ;; create variables
          variable1-concept (variables/make-variable-concept {:native-id "var1"
                                                              :Name "Variable1"})
          variable1 (variables/ingest-variable variable1-concept)
          variable2-concept (variables/make-variable-concept {:native-id "var2"
                                                              :Name "Variable2"})
          variable2 (variables/ingest-variable variable2-concept)
          variable3-concept (variables/make-variable-concept {:native-id "var3"
                                                              :Name "Variable3"})
          variable3 (variables/ingest-variable variable3-concept)
          variable1-concept-id (:concept-id variable1)
          variable2-concept-id (:concept-id variable2)
          variable1-assoc-colls [{:concept-id (:concept-id coll1)}
                                 {:concept-id (:concept-id coll2)}]
          variable2-assoc-colls [{:concept-id (:concept-id coll3)
                                  :revision-id 1}]
          ;; Add the variable info to variable concepts for comparision with UMM JSON result
          expected-variable1 (merge variable1-concept
                                    variable1
                                    {:associated-collections variable1-assoc-colls})
          expected-variable2 (merge variable2-concept
                                    variable2
                                    {:associated-collections variable2-assoc-colls})
          expected-variable3 (merge variable3-concept variable3)]
      ;; index the collections so that they can be found during variable association
      (index/wait-until-indexed)
      ;; create variable associations
      (variables/associate-by-concept-ids token variable1-concept-id variable1-assoc-colls)
      (variables/associate-by-concept-ids token variable2-concept-id variable2-assoc-colls)
      (index/wait-until-indexed)

      ;; verify variable search UMM JSON response has associated collections
      (du/assert-variable-umm-jsons-match
       umm-version/current-version [expected-variable1 expected-variable2 expected-variable3]
       (search/find-concepts-umm-json :variable {}))

      (testing "update variable not affect the associated collections in search result"
        (let [updated-variable1-name "Variable1234"
              updated-variable1-concept (variables/make-variable-concept
                                         {:native-id "var1"
                                          :Name updated-variable1-name})
              updated-variable1 (variables/ingest-variable updated-variable1-concept)
              expected-variable1 (merge updated-variable1-concept
                                        updated-variable1
                                        {:associated-collections variable1-assoc-colls})]
          (index/wait-until-indexed)

          (du/assert-variable-umm-jsons-match
           umm-version/current-version [expected-variable1]
           (search/find-concepts-umm-json :variable {:variable_name updated-variable1-name}))

          (testing "delete collection affect the associated collections in search result"
            (let [coll1-concept (mdb/get-concept (:concept-id coll1))
                  _ (ingest/delete-concept coll1-concept)
                  ;; Now variable1 is only associated to coll2, as coll1 is deleted
                  expected-variable1 (assoc expected-variable1
                                            :associated-collections
                                            [{:concept-id (:concept-id coll2)}])]
              (index/wait-until-indexed)

              (du/assert-variable-umm-jsons-match
               umm-version/current-version [expected-variable1]
               (search/find-concepts-umm-json
                :variable {:variable_name updated-variable1-name})))))))))
