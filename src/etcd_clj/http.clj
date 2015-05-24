(ns etcd-clj.http)


(def default-config {:protocol "http" :host "127.0.0.1" :port 4001
                     :connect-timeout 1000 :read-timeout Integer/MAX_VALUE})

(defprotocol Connection
  (open [this])
  (request [this params parse-body]))


(def ^{:dynamic true :doc "http config"}  *http-config* default-config)
(def ^{:dynamic true :no-doc true}  *connection* nil)


(defn prepare-config
  [options]
  (->> options
       (filter second)
       (into {})
       (merge default-config)))

(defonce injected-connection
  (delay (when-let [provider-cnt (ns-resolve 'etcd-clj.http 'injected-provider)]
    (open (provider-cnt *http-config*)))))

