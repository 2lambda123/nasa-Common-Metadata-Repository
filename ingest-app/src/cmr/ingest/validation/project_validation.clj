(ns cmr.ingest.validation.project-validation
  "Provides functions to validate the projects during collection update"
  (:require
				[clojure.set :as s]
				[clojure.string :as string]
				[cmr.common.util :as util]
				[cmr.common-app.data.humanizer-alias-cache :as humanizer-alias-cache]))

;;TODO Jyna test this path
(defn deleted-project-searches
  "Returns granule searches for deleted projects. We should not delete projects in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [context concept-id concept prev-concept]
  (let [project-alias-map (humanizer-alias-cache/get-non-humanized-source-to-aliases-map context "project")
        current-projects (map :ShortName (:Projects concept))
        previous-projects (map :ShortName (:Projects prev-concept))
        project-aliases (mapcat #(get project-alias-map %) (map string/upper-case current-projects))
        deleted-project-names (s/difference
                                (set (map util/safe-lowercase previous-projects))
                                (set (map util/safe-lowercase (concat current-projects project-aliases))))]
    (for [name deleted-project-names]
      {:params {"project[]" name
                :collection-concept-id concept-id}
       :error-msg (format (str "Collection Project [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))
