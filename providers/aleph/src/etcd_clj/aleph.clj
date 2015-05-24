(ns etcd-clj.asynchttp
  (:require [clojure.string :as str]
            [etcd-clj.http :as etcd-http]
            [aleph.http :as http]
            [aleph.http.client-middleware :as middleware]
            [byte-streams :as bs]
            [manifold.deferred :as d]))

(intern 'aleph.http.client-middleware 'default-middleware
        (remove #{middleware/wrap-exceptions} middleware/default-middleware))


(defrecord HttpConnection [config]
  etcd-http/Connection
  (etcd-http/open [this]
                  (let [pool (http/connection-pool {
                              :middleware middleware/wrap-request
                              :connection-timeout (:timeout config)
                              :request-timeout (:timeout config)})]
                  (assoc this :pool pool)))


  (etcd-http/request [this opts parse-body]
                (let [opts (-> opts
                               (assoc :request-method (:method opts))
                               (assoc :pool (:pool this)))
                      request (http/request opts)]
                  (d/chain request :body bs/to-string parse-body))))

(intern 'etcd-clj.http 'injected-provider ->HttpConnection)
