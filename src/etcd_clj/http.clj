(ns etcd-clj.http
  (:require [clojure.string :as str])
  (:import [com.ning.http.client AsyncHttpClient AsyncCompletionHandler
            Response RequestBuilder Request AsyncHttpClientConfig
            AsyncHttpClientConfig$Builder]
           ))


(defprotocol IResponse
  (body [_]))

(extend-protocol IResponse
  Response
  (body [this]
    (.getResponseBody this)))

(set! *warn-on-reflection* true)

(defn ^AsyncHttpClientConfig build-config
  [{:keys [connect-timeout read-timeout config]
    :as opts}]
  (let [^AsyncHttpClientConfig$Builder cfg
        (if config
          (AsyncHttpClientConfig$Builder. config)
          (AsyncHttpClientConfig$Builder. ))]
    (when connect-timeout
      (.setConnectTimeout cfg connect-timeout))
    (when read-timeout
      (.setReadTimeout cfg read-timeout))
    (.build cfg)))

(defn ^AsyncHttpClient create
  [^AsyncHttpClientConfig cfg]
  (AsyncHttpClient. cfg))

(defonce ^:dynamic *default-client* (delay (AsyncHttpClient. )))

(defn- ^Request prepare-request
  [{:keys [method url query-params form-params ^String body ^int timeout]
    :as opts}]
  (let [method (or method "GET")
        ^RequestBuilder rb (RequestBuilder. (-> method name str/upper-case))]
    (.setUrl rb ^String url)
    (doseq [[k v] query-params]
      (.addQueryParam rb (name k) (str v)))
    (doseq [[k v] form-params]
      (.addFormParam rb (name k) (str v)))
    (when body
      (.setBody rb body))
    (when timeout
      (.setRequestTimeout rb timeout))
    (when form-params
      (.setHeader rb "Content-Type" "application/x-www-form-urlencoded"))
  (.build rb)))


(defn ^java.util.concurrent.Future request
  [{:keys [client callback on-completed on-error ]
    :as opts
    :or {client @*default-client*
         }}]
  (let [request (prepare-request opts)
        f (-> ^AsyncHttpClient client
              (.prepareRequest request)
              (.execute
               (proxy [AsyncCompletionHandler Response] []
                 (^Response onCompleted [^Response r]
                   (when on-completed
                     (on-completed r))
                   r)
                 
                 (^void onThrowable [^Throwable t]
                   (when on-error
                     (on-error))))
                 ))]
    f))

