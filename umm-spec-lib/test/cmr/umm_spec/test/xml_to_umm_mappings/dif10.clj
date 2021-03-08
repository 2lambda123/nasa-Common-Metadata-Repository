(ns cmr.umm-spec.test.xml-to-umm-mappings.dif10
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.common.util :as common-util :refer [are3]]
            [cmr.umm-spec.xml-to-umm-mappings.dif10 :as parse]))

(deftest dif10-metadata-dates-test

  (testing "date elements with non-date values are skipped"
    (is (= [{:Type "UPDATE" :Date (t/date-time 2015 1 1)}]
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>obsequious lettuces</Data_Creation>
                                        <Data_Last_Revision>2015-01-01</Data_Last_Revision>
                                      </Metadata_Dates>
                                    </DIF>"))))

  (testing "valid dates return DataDates records"
    (is (= [{:Type "CREATE",
             :Date (t/date-time 2014 5 1 2 30 24)}]
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>2014-05-01T02:30:24</Data_Creation>
                                      </Metadata_Dates>
                                    </DIF>"))))

  (testing "default date is skipped"
    (is (= []
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>1970-01-01T00:00:00</Data_Creation>
                                      </Metadata_Dates>
                                    </DIF>")))))

(deftest dif10-temporal-end-dates
  (is (= (t/date-time 2015 1 1 23 59 59 999)
         (-> (parse/parse-dif10-xml "<DIF>
                                       <Temporal_Coverage>
                                         <Range_DateTime>
                                           <Beginning_Date_Time>2014-01-01</Beginning_Date_Time>
                                           <Ending_Date_Time>2015-01-01</Ending_Date_Time>
                                         </Range_DateTime>
                                       </Temporal_Coverage>
                                     </DIF>"
                                     true)
             :TemporalExtents
             first
             :RangeDateTimes
             first
             :EndingDateTime)))
  (is (= (t/date-time 2015 1 1 4 30 12)
         (-> (parse/parse-dif10-xml "<DIF>
                                       <Temporal_Coverage>
                                         <Range_DateTime>
                                           <Beginning_Date_Time>2014-01-01T04:30:12</Beginning_Date_Time>
                                           <Ending_Date_Time>2015-01-01T04:30:12</Ending_Date_Time>
                                         </Range_DateTime>
                                       </Temporal_Coverage>
                                     </DIF>"
                                     true)
             :TemporalExtents
             first
             :RangeDateTimes
             first
             :EndingDateTime))))

(deftest dif10-direct-distribution-information-test
  "Testing the direct distribution information translation from dif10 to UMM-C."
  (is (= {:Region "us-west-1"
          :S3BucketAndObjectPrefixNames ["bucket1" "bucket2"]
          :S3CredentialsAPIEndpoint "https://www.credAPIURL.org"
          :S3CredentialsAPIDocumentationURL "https://www.credAPIDocURL.org"}
         (:DirectDistributionInformation
           (parse/parse-dif10-xml "<DIF>
                                     <DirectDistributionInformation>
                                       <Region>us-west-1</Region>
                                       <S3BucketAndObjectPrefixName>bucket1</S3BucketAndObjectPrefixName>
                                       <S3BucketAndObjectPrefixName>bucket2</S3BucketAndObjectPrefixName>
                                       <S3CredentialsAPIEndpoint>https://www.credAPIURL.org</S3CredentialsAPIEndpoint>
                                       <S3CredentialsAPIDocumentationURL>https://www.credAPIDocURL.org</S3CredentialsAPIDocumentationURL>
                                     </DirectDistributionInformation>
                                   </DIF>"
                                   true)))))

(deftest dif10-direct-distribution-information-nil-test
  "Testing the direct distribution information translation from dif10 to UMM-C when its nil."
  (is (= nil
         (:DirectDistributionInformation
           (parse/parse-dif10-xml "<DIF>
                                   </DIF>"
                                   true)))))

(deftest dif10-doi-translation-test
  "This tests the DIF 10 DOI translation from dif10 to UMM-C."

  (are3 [expected-doi-result expected-associated-doi-result test-string]
    (let [result (parse/parse-dif10-xml test-string true)]
      (is (= expected-doi-result
             (:DOI result))
          (= expected-associated-doi-result
             (:AssociatedDOIs result))))

    "Test the nominal success case."
    {:DOI "10.5067/IAGYM8Q26QRE"}
    [{:DOI "10.5678/assoc-doi-1"
      :Title "Title1"
      :Authority "doi.org"}
     {:DOI "10.5678/assoc-doi-2"
      :Title "Title2"
      :Authority "doi.org"}]
    "<DIF>
        <Dataset_Citation>
            <Dataset_Creator>JAXA</Dataset_Creator>
            <!--Dataset_Title was trimmed-->
            <Dataset_Title>Level 3 Soil moisture (AMSR2) product</Dataset_Title>
            <Persistent_Identifier>
              <Type>DOI</Type>
              <Identifier>10.5067/IAGYM8Q26QRE</Identifier>
            </Persistent_Identifier>
        </Dataset_Citation>
        <AssociatedDOIs>
          <AssociatedDOI>
            <DOI>10.5678/assoc-doi-1</DOI>
            <Title>Title1</Title>
            <Authority>doi.org</Authority>
          </AssociatedDOI>
          <AssociatedDOI>
            <DOI>10.5678/assoc-doi-2</DOI>
            <Title>Title2</Title>
            <Authority>doi.org</Authority>
          </AssociatedDOI>
        </AssociatedDOIs>
     </DIF>"

    "Test missing Collection DOI."
    {:MissingReason "Unknown",
     :Explanation "It is unknown if this record has a DOI."}
    [{:DOI "10.5678/assoc-doi-1", :Title "Title1", :Authority "doi.org"}
     {:DOI "10.5678/assoc-doi-2", :Title "Title2", :Authority "doi.org"}]
    "<DIF>
        <Dataset_Citation>
            <Dataset_Creator>JAXA</Dataset_Creator>
            <!--Dataset_Title was trimmed-->
            <Dataset_Title>Level 3 Soil moisture (AMSR2) product</Dataset_Title>
        </Dataset_Citation>
        <AssociatedDOIs>
          <AssociatedDOI>
            <DOI>10.5678/assoc-doi-1</DOI>
            <Title>Title1</Title>
            <Authority>doi.org</Authority>
          </AssociatedDOI>
          <AssociatedDOI>
            <DOI>10.5678/assoc-doi-2</DOI>
            <Title>Title2</Title>
            <Authority>doi.org</Authority>
          </AssociatedDOI>
        </AssociatedDOIs>
     </DIF>"

    "Test missing AssociatedDOIs."
    {:DOI "10.5067/IAGYM8Q26QRE"}
    nil
    "<DIF>
        <Dataset_Citation>
            <Dataset_Creator>JAXA</Dataset_Creator>
            <!--Dataset_Title was trimmed-->
            <Dataset_Title>Level 3 Soil moisture (AMSR2) product</Dataset_Title>
            <Persistent_Identifier>
              <Type>DOI</Type>
              <Identifier>10.5067/IAGYM8Q26QRE</Identifier>
            </Persistent_Identifier>
        </Dataset_Citation>
     </DIF>"

    "Test missing both Collection DOI and AssociatedDOIs."
    {:MissingReason "Unknown",
     :Explanation "It is unknown if this record has a DOI."}
    nil
    "<DIF>
     </DIF>"))
