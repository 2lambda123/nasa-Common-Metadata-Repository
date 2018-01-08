(defproject nasa-cmr/cmr-system-int-test "0.1.0-SNAPSHOT"
  :description "This project provides end to end integration testing for CMR components."
  :url "https://github.com/nasa/Common-Metadata-Repository/tree/master/system-int-test"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :exclusions [
    [cheshire]
    [clj-time]
    [com.google.code.findbugs/jsr305]
    [commons-codec/commons-codec]
    [commons-io]
    [org.apache.httpcomponents/httpclient]
    [org.apache.httpcomponents/httpcore]
    [org.clojure/tools.logging]
    [org.clojure/tools.reader]
    [potemkin]
    [ring/ring-codec]]
  :dependencies [
    [cheshire "5.8.0"]
    [clj-http "2.0.0"]
    [clj-time "0.14.2"]
    [clj-xml-validation "1.0.2"]
    [com.google.code.findbugs/jsr305 "3.0.1"]
    [commons-codec/commons-codec "1.11"]
    [commons-io "2.6"]
    [nasa-cmr/cmr-access-control-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-bootstrap-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-elastic-utils-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-indexer-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-ingest-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-mock-echo-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-search-app "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-transmit-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-umm-spec-lib "0.1.0-SNAPSHOT"]
    [nasa-cmr/cmr-virtual-product-app "0.1.0-SNAPSHOT"]
    [org.apache.httpcomponents/httpclient "4.5.4"]
    [org.apache.httpcomponents/httpcore "4.4.7"]
    [org.clojure/clojure "1.8.0"]
    [org.clojure/tools.logging "0.3.1"]
    [org.clojure/tools.reader "1.1.1"]
    [potemkin "0.4.4"]
    [prismatic/schema "1.1.3"]
    [ring/ring-codec "1.0.1"]
    [ring/ring-core "1.5.1"]]
  :plugins [[lein-shell "0.4.0"]
            [test2junit "1.2.1"]]
  :jvm-opts ^:replace ["-server"
                       "-XX:-OmitStackTraceInFastThrow"
                       "-Dclojure.compiler.direct-linking=true"]

  :profiles {
    :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                         [org.clojars.gjahad/debug-repl "0.3.3"]
                         [nasa-cmr/cmr-vdd-spatial-viz "0.1.0-SNAPSHOT"]
                         [pjstadig/humane-test-output "0.8.1"]]
          :injections [(require 'pjstadig.humane-test-output)
                       (pjstadig.humane-test-output/activate!)]
          :jvm-opts ^:replace ["-server"
                               "-XX:-OmitStackTraceInFastThrow"]
          :source-paths ["src" "dev"]}
    :static {}
    ;; This profile is used for linting and static analysis. To run for this
    ;; project, use `lein lint` from inside the project directory. To run for
    ;; all projects at the same time, use the same command but from the top-
    ;; level directory.
    :lint {
      :source-paths ^:replace ["src"]
      :test-paths ^:replace []
      :plugins [[jonase/eastwood "0.2.3"]
                [lein-ancient "0.6.10"]
                [lein-bikeshed "0.4.1"]
                [lein-kibit "0.1.2"]
                [venantius/yagni "0.1.4"]]}}
  :aliases {;; Alias to test2junit for consistency with lein-test-out
            "test-out" ["test2junit"]
            ;; Linting aliases
            "kibit" ["do" ["with-profile" "lint" "shell" "echo" "== Kibit =="]
                          ["with-profile" "lint" "kibit"]]
            "eastwood" ["with-profile" "lint" "eastwood" "{:namespaces [:source-paths]}"]
            "bikeshed" ["with-profile" "lint" "bikeshed" "--max-line-length=100"]
            "yagni" ["with-profile" "lint" "yagni"]
            "check-deps" ["with-profile" "lint" "ancient" "all"]
            "lint" ["do" ["check"] ["kibit"] ["eastwood"]]
            ;; Placeholder for future docs and enabler of top-level alias
            "generate-static" ["with-profile" "static" "shell" "echo"]})
