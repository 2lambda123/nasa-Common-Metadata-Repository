(ns cmr.elasticsearch.plugins.spatial.engine.core
  #_{:clj-kondo/ignore [:unused-import]}
  (:import
   (cmr.elasticsearch.plugins SpatialScriptFactory) 
   (org.elasticsearch.script FilterScript ScriptContext ScriptEngine)
   (java.util Map))
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScriptEngine
   :implements [org.elasticsearch.script.ScriptEngine]))

(import 'cmr.elasticsearch.plugins.SpatialScriptEngine)

(defn -getType
  "Get script lang."
  [^SpatialScriptEngine _this]
  "cmr_spatial")

#_{:clj-kondo/ignore [:unused-binding]}
(defn -compile
  "Compile script."
  [^SpatialScriptEngine this
   ^String script-name
   ^String script-source
   ^ScriptContext context
   ^Map params]
  (cond
    (not (.equals context FilterScript/CONTEXT))
    (throw (new IllegalArgumentException
                (format "%s scripts cannot be used for context [%s]"
                        (.getType this) (.name context))))

    (not (.equals "spatial" script-source))
    (throw (new IllegalArgumentException
                (format "Unknown script name %s" script-source)))

    :else
    (-> context .factoryClazz (.cast (new SpatialScriptFactory)))))

#_{:clj-kondo/ignore [:redundant-do]}
(defn -close
  "Do nothing."
  [_this]
  (do))
