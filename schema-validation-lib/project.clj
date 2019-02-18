(defproject nasa-cmr/cmr-schema-validation-lib "0.1.0-SNAPSHOT"
  :description "Provides json schema validation code"
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/schema-validation-lib"
  :plugins [[lein-exec "0.3.7"]
            [lein-shell "0.5.0"]
            [test2junit "1.3.3"]]
  :exclusions [[chesire]]
  :dependencies [[cheshire "5.8.1"]
                 [com.github.everit-org.json-schema/org.everit.json.schema "1.11.0"]
                 [org.clojure/clojure "1.10.0"]]
  :repositories [["jitpack.io" "https://jitpack.io"]]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-server"
                       "-Dclojure.compiler.direct-linking=true"]
  :profiles {:security {:plugins [[com.livingsocial/lein-dependency-check "1.1.1"]]
                        :dependency-check {:output-format [:all]
                                           :suppression-file "resources/security/suppression.xml"}}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojars.gjahad/debug-repl "0.3.3"]
                                  [criterium "0.4.4"]
                                  [proto-repl "0.3.1"]
                                  [clj-http "2.3.0"]]
                   :jvm-opts ^:replace ["-server"]
                   :source-paths ["src" "dev" "test"]}
             :static {}
             :uberjar {:aot [cmr.schema-validation.json-schema]}
             ;; This profile is used for linting and static analysis. To run for this
             ;; project, use `lein lint` from inside the project directory. To run for
             ;; all projects at the same time, use the same command but from the top-
             ;; level directory.
             :lint {:source-paths ^:replace ["src"]
                    :test-paths ^:replace []
                    :plugins [[jonase/eastwood "0.2.5"]
                              [lein-ancient "0.6.15"]
                              [lein-bikeshed "0.5.0"]
                              [lein-kibit "0.1.6"]
                              [venantius/yagni "0.1.4"]]}
             ;; The following profile is overriden on the build server or in the user's
             ;; ~/.lein/profiles.clj file.
             :internal-repos {}}
  :aliases {;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" ":all"]
            "check-sec" ["with-profile" "security" "dependency-check"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
