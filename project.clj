(defproject etcd-clj "0.2.3"
  :description "Clojure etcd client"
  :url "http://github.com/narma/etcd-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0" :scope "provided"]
                 [http-kit "2.1.19"]
                 [cheshire "5.2.0"]
                 [com.cemerick/url "0.1.0"]]
  :profiles {:dev {:plugins [[codox "0.8.0"]]
                   :dependencies []
                   :codox {:defaults {:doc/format :markdown}
                           :src-dir-uri "https://github.com/narma/etcd-clj/blob/master"
                           :src-linenum-anchor-prefix "L"}
                   }}
  )
                   










