(ns cmr.ingest.services.granule-bulk-update.checksum.echo10-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.checksum.echo10 :as echo10]))

(def ^:private update-value-and-algorithm
  "ECHO10 granule for testing updating granule checksum value and algorithm."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private update-value-and-algorithm-result
  "Result ECHO10 granule after updating granule checksum value and algorithm.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>bar-32</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private update-value-only
  "ECHO10 granule for testing updating granule checksum value only."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private update-value-only-result
  "Result ECHO10 granule after updating granule checksum value only.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private add-checksum-element
  "ECHO10 granule for testing adding a fresh checksum element to the DataGranule element."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private add-checksum-element-result
  "Result ECHO10 granule after adding a fresh checksum element to the DataGranule element.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private add-checksum-element-middle
  "ECHO10 granule for testing adding a fresh checksum element to the DataGranule element
   between two existing values, to make sure schema order for DataGranule children is respected."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private add-checksum-element-middle-result
  "Result ECHO10 granule after adding a fresh checksum element to the DataGranule element
   between two existing values, to make sure schema order for DataGranule children is respected.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>fooValue</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private update-checksum-element-middle
  "ECHO10 granule for testing updating checksum, when it isn't the first element in the DataGranule."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>old-foo</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private update-checksum-element-middle-result
  "Result ECHO10 granule after updating checksum, when it isn't the first element in the DataGranule.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>new-foo</Value>
         <Algorithm>Adler-32</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(deftest update-checksum
  (testing "various cases of updating checksum"
    (are3 [checksum-value source result]
      (is (= result (#'echo10/update-checksum-metadata source checksum-value)))

      "Add both value and algorithm"
      "foo,bar-32"
      update-value-and-algorithm
      update-value-and-algorithm-result

      "Update value only, not algorithm"
      "foo"
      update-value-only
      update-value-only-result

      "Add values when there is no existing Checksum element, as new first data-granule element"
      "foo,SHA-256"
      add-checksum-element
      add-checksum-element-result

      "Add a checksum element between two existing data-granule elements"
      "fooValue,SHA-256"
      add-checksum-element-middle
      add-checksum-element-middle-result

      "Update a checksum element between two existing data-granule elements"
      "new-foo,Adler-32"
      update-checksum-element-middle
      update-checksum-element-middle-result)))
