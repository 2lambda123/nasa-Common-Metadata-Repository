(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi
  "Functions for parsing UMM DOI records out of ISO 19115 and ISO SMAP XML documents."
  (:require
   [cmr.common.util :as util]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.common.xml.parse :refer [value-of]]))

(def doi-namespace
  "DOI namespace."
  "gov.nasa.esdis.umm.doi")

(defn- is-doi-field?
  "Returns true if the given gmd-id is for a DOI field."
  [gmd-id]
  (and (= (value-of gmd-id "gmd:MD_Identifier/gmd:description/gco:CharacterString") "DOI")
       (= (value-of gmd-id "gmd:MD_Identifier/gmd:codeSpace/gco:CharacterString") doi-namespace)))

(defn parse-doi
  "There could be multiple CI_Citations. Each CI_Citation could contain multiple gmd:identifiers.
   Each gmd:identifier could contain at most ONE DOI. The doi-list below will contain something like:
   [[nil]
    [nil {:DOI \"doi1\" :Authority \"auth1\"} {:DOI \"doi2\" :Authority \"auth2\"}]
    [{:DOI \"doi3\" :Authority \"auth3\"]]
   We will pick the first DOI for now."
  [doc citation-base-xpath]
  (let [orgname-path (str "gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty/"
                          "gmd:CI_ResponsibleParty/gmd:organisationName/gco:CharacterString")
        indname-path (str "gmd:MD_Identifier/gmd:authority/gmd:CI_Citation/gmd:citedResponsibleParty/"
                          "gmd:CI_ResponsibleParty/gmd:individualName/gco:CharacterString")
        doi-list (for [ci-ct (select doc citation-base-xpath)
                       gmd-id (select ci-ct "gmd:identifier")
                       :when (is-doi-field? gmd-id)
                       :let [doi-value (value-of
                                        gmd-id "gmd:MD_Identifier/gmd:code/gco:CharacterString")]
                       :when doi-value]
                   (util/remove-nil-keys
                    {:DOI doi-value
                     :Authority (or (value-of gmd-id orgname-path)
                                    (value-of gmd-id orgname-path))}))]
    (first doi-list)))
