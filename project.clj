(defproject etcd-clj "0.2.4"
  :description "Clojure etcd client"
  :plugins [[lein-modules "0.3.11"]]
  :packaging "pom"

  :dependencies [[cheshire "5.2.0"]]
  :profiles {:travis {:modules {:subprocess "lein2"}}
             :dev {:plugins [[codox "0.8.0"]]
                   :dependencies [[org.clojure/clojure "1.7.0-beta3"]
                                  [com.ning/async-http-client "1.9.10"]]
                   :source-paths ["src" "providers/asynchttp/src"]
                   :codox {:defaults {:doc/format :markdown}
                           :src-dir-uri "https://github.com/narma/etcd-clj/blob/master/"
                           :src-linenum-anchor-prefix "L"}
                   }}

  :modules {
   :dirs ["." "providers/asynchttp"]
   :inherited {:url "http://github.com/narma/etcd-clj"
               :scm {:dir "."}
               :license {:name "Eclipse Public License"
                         :url "http://www.eclipse.org/legal/epl-v10.html"}
               :profiles {:provided {:dependencies [[org.clojure/clojure _]]}}
               :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]
               }
            }
    :versions {org.clojure/clojure "1.7.0-beta3"
               etcd-clj :version
    }
  )











