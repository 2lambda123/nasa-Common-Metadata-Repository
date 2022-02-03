;; WARNING: This file was generated from umm-t-json-schema.json. Do not manually modify.
(ns cmr.umm-spec.models.umm-tool-models
   "Defines UMM-T clojure records."
 (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord UMM-T
  [
   ;; The programming language(s) and associated version supported by the downloadable tool.
   SupportedSoftwareLanguages

   ;; This field provides users with information on what changes were included in the most recent
   ;; version.
   VersionDescription

   ;; The Digital Object Identifier (DOI) of the web user interface or downloadable tool.
   DOI

   ;; The project element describes the list of input format names supported by the web user
   ;; interface or downloadable tool.
   SupportedInputFormats

   ;; This is the contact persons of the downloadable tool or web user interface.
   ContactPersons

   ;; The tool provider, or organization, or institution responsible for developing, archiving,
   ;; and/or distributing the web user interface, software, or tool.
   Organizations

   ;; Information about any constraints for accessing the downloadable tool or web user interface.
   AccessConstraints

   ;; Group(s) to contact at an organization to get information about the web user interface or
   ;; downloadable tool, including how the group may be contacted.
   ContactGroups

   ;; Allows for the specification of Earth Science keywords that are representative of the service,
   ;; software, or tool being described. The controlled vocabulary for Science Keywords is
   ;; maintained in the Keyword Management System (KMS).
   ToolKeywords

   ;; This element describes the latest date when the tool was most recently pushed to production
   ;; for support and maintenance.
   LastUpdatedDate

   ;; Information about the quality of the downloadable tool or web user interface. This would
   ;; include information about any quality assurance procedures followed in development. Note: This
   ;; field allows lightweight markup language with plain text formatting syntax. Line breaks within
   ;; the text are preserved.
   Quality

   ;; This element contains information about a smart handoff from one web user interface to
   ;; another.
   PotentialAction

   ;; Information on how the item (downloadable tool or web user interface) may or may not be used
   ;; after access is granted. This includes any special restrictions, legal prerequisites, terms
   ;; and conditions, and/or limitations on using the item. Providers may request acknowledgement of
   ;; the item from users and claim no responsibility for quality and completeness.
   UseConstraints

   ;; The operating system(s) and associated version supported by the downloadable tool.
   SupportedOperatingSystems

   ;; The name of the downloadable tool or web user interface.
   Name

   ;; A URL associated with the web user interface or downloadable tool, e.g., the home page for the
   ;; tool provider which is responsible for the tool.
   RelatedURLs

   ;; A brief description of the web user interface or downloadable tool. Note: This field allows
   ;; lightweight markup language with plain text formatting syntax. Line breaks within the text are
   ;; preserved.
   Description

   ;; The type of the downloadable tool or web user interface.
   Type

   ;; Words or phrases to further describe the downloadable tool or web user interface.
   AncillaryKeywords

   ;; The URL where you can directly access the web user interface or downloadable tool.
   URL

   ;; The browser(s) and associated version supported by the web user interface.
   SupportedBrowsers

   ;; The edition or version of the web user interface software, or tool. A value of 'NOT PROVIDED'
   ;; may be used if the version is not available or unknown.
   Version

   ;; The long name of the downloadable tool or web user interface.
   LongName

   ;; Requires the client, or user, to add in schema information into every tool record. It includes
   ;; the schema's name, version, and URL location. The information is controlled through
   ;; enumerations at the end of this schema.
   MetadataSpecification

   ;; The project element describes the list of output format names supported by the web user
   ;; interface or downloadable tool.
   SupportedOutputFormats
  ])
(record-pretty-printer/enable-record-pretty-printing UMM-T)

;; The HTTP API definition of this tool for use with smart-handoffs.
(defrecord PotentialActionType
  [
   ;; The intent of action this tool supports. Does this tool provide a search? Does it create a
   ;; resource? Does it consume a resource?
   Type

   ;; The HTTP endpoint definition.
   Target

   ;; A set of HTTP query parameter inputs. Each one indicates how a property should be filled in
   ;; before initiating the action
   QueryInput
  ])
(record-pretty-printer/enable-record-pretty-printing PotentialActionType)

;; The definition of a query parameter for an HTTP API
(defrecord PropertyValueSpecificationType
  [
   ;; Note: not currently part of schema.org specification. This element references a standard
   ;; describing the type and format of the query parameter value. For example,
   ;; `https://schema.org/box` will describe a geo bounding box with the format `min Longitude, min
   ;; Latitude, max Longitude, max Latitude`.
   ValueType

   ;; Provides information regarding the correct population of the url template parameter with
   ;; respect to type, format and whether the parameter is required.
   Description

   ;; Indicates the name of the parameter this PropertyValue specification maps to in the URL
   ;; template.
   ValueName

   ;; Whether the property must be filled in to complete the action. Default is false.
   ValueRequired
  ])
(record-pretty-printer/enable-record-pretty-printing PropertyValueSpecificationType)

;; This object requires any metadata record that is validated by this schema to provide information
;; about the schema.
(defrecord MetadataSpecificationType
  [
   ;; This element represents the URL where the schema lives. The schema can be downloaded.
   URL

   ;; This element represents the name of the schema.
   Name

   ;; This element represents the version of the schema.
   Version
  ])
(record-pretty-printer/enable-record-pretty-printing MetadataSpecificationType)

;; The organization or institution responsible for developing, archiving, and/or hosting the web
;; user interface or downloadable tool.
(defrecord OrganizationType
  [
   ;; This is the roles of the organization.
   Roles

   ;; This is the short name of the organization.
   ShortName

   ;; This is the long name of the organization.
   LongName

   ;; The URL of the organization.
   URLValue
  ])
(record-pretty-printer/enable-record-pretty-printing OrganizationType)

;; Defines the contact information of a downloadable tool or web user interface.
(defrecord ContactInformationType
  [
   ;; Time period when the contact answers questions or provides services.
   ServiceHours

   ;; Supplemental instructions on how or when to contact the responsible party.
   ContactInstruction

   ;; Mechanisms of contacting.
   ContactMechanisms

   ;; Contact addresses.
   Addresses
  ])
(record-pretty-printer/enable-record-pretty-printing ContactInformationType)

(defrecord ContactGroupType
  [
   ;; This is the roles of the downloadable tool or web user interface contact.
   Roles

   ;; This is the contact information of the downloadable tool or web user interface contact.
   ContactInformation

   ;; This is the contact group name.
   GroupName
  ])
(record-pretty-printer/enable-record-pretty-printing ContactGroupType)

;; Enables specification of Earth science tool keywords related to the tool. The Earth Science Tool
;; keywords are chosen from a controlled keyword hierarchy maintained in the Keyword Management
;; System (KMS).
(defrecord ToolKeywordType
  [
   ToolCategory

   ToolTopic

   ToolTerm

   ToolSpecificTerm
  ])
(record-pretty-printer/enable-record-pretty-printing ToolKeywordType)

;; This element describes the operating system(s) and associated version supported by the
;; downloadable tool.
(defrecord SupportedOperatingSystemType
  [
   ;; The short name of the operating system associated with the downloadable tool.
   OperatingSystemName

   ;; The version of the operating system associated with the downloadable tool.
   OperatingSystemVersion
  ])
(record-pretty-printer/enable-record-pretty-printing SupportedOperatingSystemType)

;; This element describes the programming language(s) and associated version supported by the web
;; user interface.
(defrecord SupportedSoftwareLanguageType
  [
   ;; The short name of the programming language associated with the downloadable tool.
   SoftwareLanguageName

   ;; The version of the programming language associated with the downloadable tool.
   SoftwareLanguageVersion
  ])
(record-pretty-printer/enable-record-pretty-printing SupportedSoftwareLanguageType)

;; Method for contacting the service or tool contact. A contact can be available via phone, email,
;; Facebook, or Twitter.
(defrecord ContactMechanismType
  [
   ;; This is the method type for contacting the responsible party - phone, email, Facebook, or
   ;; Twitter.
   Type

   ;; This is the contact phone number, email address, Facebook address, or Twitter handle
   ;; associated with the contact method.
   Value
  ])
(record-pretty-printer/enable-record-pretty-printing ContactMechanismType)

;; This object describes tool quality, composed of the quality flag, the quality flagging system,
;; traceability and lineage.
(defrecord ToolQualityType
  [
   ;; The quality flag for the tool or web user interface.
   QualityFlag

   ;; The quality traceability of the downloadable tool or web user interface.
   Traceability

   ;; The quality lineage of the downloadable tool or web user interface.
   Lineage
  ])
(record-pretty-printer/enable-record-pretty-printing ToolQualityType)

;; This element describes the browser(s) and associated version supported by the downloadable tool.
(defrecord SupportedBrowserType
  [
   ;; The short name of the browser associated with the downloadable tool.
   BrowserName

   ;; The version of the browser associated with the downloadable tool.
   BrowserVersion
  ])
(record-pretty-printer/enable-record-pretty-printing SupportedBrowserType)

;; The definition of the tool's HTTP API.
(defrecord ActionTargetType
  [
   ;; The type of target. For example, is it an entry point into the application this record
   ;; describes?
   Type

   ;; Human readable text that answers the question of what this API can do
   Description

   ;; The supported MIME type(s) of the response from the HTTP API.
   ResponseContentType

   ;; An url template (RFC6570) that will be used to construct the target of the execution of the
   ;; action.
   UrlTemplate

   ;; The accepted HTTP methods for this API.
   HttpMethod
  ])
(record-pretty-printer/enable-record-pretty-printing ActionTargetType)

(defrecord ContactPersonType
  [
   ;; This is the roles of the downloadable tool or web user interface contact.
   Roles

   ;; This is the contact information of the tool contact.
   ContactInformation

   ;; First name of the individual.
   FirstName

   ;; Middle name of the individual.
   MiddleName

   ;; Last name of the individual.
   LastName
  ])
(record-pretty-printer/enable-record-pretty-printing ContactPersonType)

;; Represents Internet sites that contain information related to the data, as well as related
;; Internet sites such as project home pages, related data archives/servers, metadata extensions,
;; online software packages, web mapping services, and calibration/validation data.
(defrecord RelatedURLType
  [
   ;; Description of the web page at this URL.
   Description

   ;; A keyword describing the distinct content type of the online resource to this resource. (e.g.,
   ;; 'VisualizationURL').
   URLContentType

   ;; A keyword describing the type of the online resource to this resource. This helps the GUI to
   ;; know what to do with this resource. (e.g., 'GET RELATED VISUALIZATION').
   Type

   ;; A keyword describing the subtype of the online resource to this resource. This further helps
   ;; the GUI to know what to do with this resource. (e.g., 'DATA RECIPE', 'SCIENCE DATA PRODUCT
   ;; VALIDATION', 'GIOVANNI').
   Subtype

   ;; The URL for the relevant web page (e.g., the URL of the responsible organization's home page,
   ;; the URL of the collection landing page, the URL of the download site for the collection).
   URL
  ])
(record-pretty-printer/enable-record-pretty-printing RelatedURLType)

;; This entity contains the physical address details for the contact.
(defrecord AddressType
  [
   ;; An address line for the street address, used for mailing or physical addresses of
   ;; organizations or individuals who serve as contacts for the service.
   StreetAddresses

   ;; The city portion of the physical address.
   City

   ;; The state or province portion of the physical address.
   StateProvince

   ;; The country of the physical address.
   Country

   ;; The zip or other postal code portion of the physical address.
   PostalCode
  ])
(record-pretty-printer/enable-record-pretty-printing AddressType)

;; Represents the Internet site where you can directly access the tool or web user interface.
(defrecord URLType
  [
   ;; Description of online resource at this URL.
   Description

   ;; A keyword describing the distinct content type of the online resource to this resource. (e.g.,
   ;; 'DistributionURL').
   URLContentType

   ;; A keyword describing the type of the online resource to this resource. This helps the GUI to
   ;; know what to do with this resource. (e.g., 'DOWNLOAD SOFTWARE').
   Type

   ;; A keyword describing the subtype of the online resource to this resource. This further helps
   ;; the GUI to know what to do with this resource. (e.g., 'MAP VIEWER', 'SIMPLE SUBSET WIZARD
   ;; (SSW)').
   Subtype

   ;; The URL for the relevant online resource where you can directly access the downloadable tool
   ;; or web user interface.
   URLValue
  ])
(record-pretty-printer/enable-record-pretty-printing URLType)

;; Information on how the downloadable tool or web user interface may or may not be used after
;; access is granted. This includes any special restrictions, legal prerequisites, terms and
;; conditions, and/or limitations on using the item. Providers may request acknowledgement of the
;; item from users and claim no responsibility for quality and completeness.
(defrecord UseConstraintsType
  [
   ;; The web address of the license associated with the tool.
   LicenseURL

   ;; The text of the license associated with the tool.
   LicenseText
  ])
(record-pretty-printer/enable-record-pretty-printing UseConstraintsType)