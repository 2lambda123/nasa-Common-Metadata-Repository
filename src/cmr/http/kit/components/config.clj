(ns cmr.http.kit.components.config
  (:require
   [cmr.exchange.common.components.config :as config]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-cfg config/get-cfg)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn http-entry-point-fn
  [system]
  (get-in (get-cfg system) [:httpd :entry-point-fn]))

(defn http-assets
  [system]
  (get-in (get-cfg system) [:httpd :assets]))

(defn http-docs
  [system]
  (get-in (get-cfg system) [:httpd :docs]))

(defn http-port
  [system]
  (get-in (get-cfg system) [:httpd :port]))

(defn http-index-dirs
  [system]
  (get-in (get-cfg system) [:httpd :index-dirs]))

(defn http-replace-base-url
  [system]
  (get-in (get-cfg system) [:httpd :replace-base-url]))

(defn http-rest-docs-base-url-template
  [system]
  (get-in (get-cfg system) [:httpd :rest-docs :base-url-template]))

(defn http-rest-docs-outdir
  [system]
  (get-in (get-cfg system) [:httpd :rest-docs :outdir]))

(defn http-rest-docs-source
  [system]
  (get-in (get-cfg system) [:httpd :rest-docs :source]))

(defn http-skip-static
  [system]
  (get-in (get-cfg system) [:httpd :skip-static]))

(def log-color? config/log-color?)
(def log-level config/log-level)
(def log-nss config/log-nss)

(defn streaming-heartbeat
  [system]
  (get-in (get-cfg system) [:streaming :heartbeat]))

(defn streaming-timeout
  [system]
  (get-in (get-cfg system) [:streaming :timeout]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config
