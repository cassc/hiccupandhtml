(defproject hiccupandhtml "0.1.0-SNAPSHOT"
  :description ""
  :url "https://github.com/cassc/hiccupandhtml"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/core.async "0.2.395"]
                 [cljsjs/codemirror "5.11.0-1"]
                 [com.rpl/specter "1.1.2"]
                 [hickory "0.7.1"]
                 [reagent "0.5.1"]]

  :plugins [[lein-cljsbuild "1.1.4"
             :exclusions [org.clojure/clojure]]
            [lein-figwheel "0.5.4-7"]]

  :clean-targets ^{:protect false} ["resources/public/js/out"
                                    "resources/public/cljs"
                                    :target-path]

  :source-paths ["src"]

  :profiles {:prod {:hooks [leiningen.cljsbuild]
                    :cljsbuild {:builds {:app
                                         {:figwheel false
                                          :compiler ^{:replace true}
                                          {:output-to "resources/public/cljs/hiccupandhtml.js"
                                           :output-dir "resources/public/cljs/prod"
                                           :optimizations :advanced ;; :whitespace :advanced
                                           :source-map "resources/public/cljs/hiccupandhtml.map"
                                           :infer-externs true
                                           :externs ["externs/common.js"]
                                           :pretty-print false}}}}}
             :dev {:env {:dev true}
                   :dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.2"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
  :cljsbuild {:builds {:app
                       {:source-paths ["src"]
                        :figwheel true
                        :compiler {:main hiccupandhtml.core
                                   :asset-path "cljs/out"
                                   :output-to "resources/public/cljs/hiccupandhtml.js"
                                   :output-dir "resources/public/cljs/out"
                                   :source-map-timestamp true}}}}

  :figwheel {:css-dirs ["resources/public/css"]
             :open-file-command "emacsclient"})
