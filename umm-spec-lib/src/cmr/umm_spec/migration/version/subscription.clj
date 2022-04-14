(ns cmr.umm-spec.migration.version.subscription
  "Contains functions for migrating between versions of the UMM subscription schema."
  (:require
   [clojure.string :as string]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.version.interface :as interface]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;;; subscription Migration Implementations


(defmethod interface/migrate-umm-version [:subscription "1.0" "1.1"]
  [context subscription & _]
  (as-> subscription s
      (if (> (count (:CollectionConceptId s)) 0)
        (assoc s :Type "granule")
        (assoc s :Type "collection"))
      (m-spec/update-version s :subscription "1.1")))