(ns cmr.umm-spec.date-util
  "Useful UMM date values and functions.")

(def default-date-value "1970-01-01T00:00:00")

(defn or-default
  "Returns x if not nil, or else the default date placeholder value."
  [x]
  (or x default-date-value))

(defn not-default
  "Returns x if it is not the default date value string."
  [x]
  (when (not= x default-date-value)
    x))

(defn latest-date-of-type
  "Returns :Date value of the most recent UMM DateType map in date-coll with the given type."
  [date-coll date-type]
  (->> date-coll
       (filter #(= date-type (:Type %)))
       (map :Date)
       sort
       last))

(defn- data-date-getter
  [date-type]
  (fn [c]
    (-> c :DataDates (latest-date-of-type date-type))))

(def data-create-date (data-date-getter "CREATE"))

(def data-update-date (data-date-getter "UPDATE"))

(def data-review-date (data-date-getter "REVIEW"))

(def data-delete-date (data-date-getter "DELETE"))
