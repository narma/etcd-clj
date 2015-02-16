(ns etcd-clj.core
  "For detailed API see https://github.com/coreos/etcd/blob/master/Documentation/api.md"
  (:import [java.net URLEncoder])
  (:require [cheshire.core :as json]
            [etcd-clj.http :as http]
            [clojure.string :as str])
  (:refer-clojure :exclude [list get set]))

(set! *warn-on-reflection* true)

(def default-config {:protocol "http" :host "127.0.0.1" :port 4001
                     :connect-timeout 1000 :read-timeout Integer/MAX_VALUE})

(def ^{:dynamic true :doc "Connection config" }  *etcd-config* default-config)

(def ^{:dynamic true :doc "request timeout"}  *timeout* 2000)

(def ^:dynamic *api-version* "v2")

(let [cfg (http/build-config default-config)]
  (defonce ^{:dynamic true :no-doc true}  *connection* (http/create cfg)))

(defn url-encode
  ;; copied from cemeric.url
  [string]
  (some-> string str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(defn set-connection!
  "Defaults for :port 4001, :host \"127.0.0.1\", :protocol \"http\","
  ;; Blindly copied the approach from congomongo, but without most of the protections
  [{:keys [protocol host port connect-timeout read-timeout]
    :as opts}]
  (let [config (->> opts
                    (filter second)
                    (into {})
                    (merge default-config))]
    (if (thread-bound? #'*etcd-config*)
      (set! *etcd-config* config)
      (alter-var-root #'*etcd-config* (constantly config)))
    (let [new-conn (-> config http/build-config http/create)]
      (if (thread-bound? (var *connection*))
        (set! *connection* new-conn)
        (alter-var-root (var *connection*) (constantly new-conn))))))

(defmacro with-connection [config & body]
  `(do
     (let [config# (merge default-config ~config)]
       (binding [*etcd-config* config#]
         ~@body))))

(defn ^:no-doc make-url
  "Constructs url used for all api calls"
  [& parts]
  (str (java.net.URL. (:protocol *etcd-config*)
                      (:host *etcd-config*)
                      (:port *etcd-config*)
                      (str/join "/" (concat [""] parts)))))

(defn ^:no-doc parse-response
  [resp]
  (-> (http/body resp)
      (cheshire.core/decode true)))

(defn  ^:no-doc wrap-callback
  [callback]
  (fn [resp]
    (-> resp
        parse-response
        callback)))

(defn api-req
  "Makes an api call, wraps callback with parsing response body if it is provided"
  [method path & {:keys [callback timeout as] :as opts}]
  (let [resp (http/request (merge {:method method
                                   :client *connection*
                                   :timeout *timeout*
                                   :url (make-url *api-version* path)}
                                  (filter second
                                          {:timeout timeout
                                           :on-completed (when callback
                                                           (wrap-callback callback))
                                           })
                                  (dissoc opts :callback :timeout)
                                  ))
        ret (reify
              clojure.lang.IPending
              (isRealized [_] (.isDone resp))
              
              java.util.concurrent.Future
              (cancel [_ interrupt?]
                (.cancel resp interrupt?))
              (get [_] (->
                        (.get resp)
                        parse-response))
              (get [_ timeout unit]
                (-> (.get resp timeout unit)
                    parse-response))
              (isCancelled [_] (.isCancelled resp))
              (isDone [_] (.isDone resp)))]

    (if (or callback (= as :future))
      ret
      @ret)))

(defn set
  "Sets key to value.
  Options:
  :dir        - create a dir
  :ttl        - set ttl with a seconds, use `nil` for remove tll
  :prev-value
  :prev-index
  :prev-exist - conditional arguments, see etcd API for usage
  :order      - Creating an in-order key in that dir
  "
  [key value & {:keys [ttl dir callback order prev-value prev-index prev-exist as]
                :as opts
                :or {dir false}}]
  (api-req (if order :post :put)
           (->> key url-encode (format "keys/%s"))
           :form-params (merge (if dir
                                 {:dir dir}
                                 {:value value})
                               (remove #(-> % second nil?)
                                       {:as as
                                        :prevIndex prev-index
                                        :prevValue prev-value
                                        :prevExist prev-exist})
                               (when (contains? opts :ttl)
                                 {:ttl ttl}))
           :callback callback))

(defn get
  "Get key from etcd.
  Options:
  :callback   - return promise immediately with a promise
  :wait       - wait a change, it's etcd feature
  :wait-index - wait for a concrete index
  :recursive  - get content recursively
  :sorted     - returns result in sorted order
"
  [key & {:keys [recursive wait wait-index callback sorted as]
                      :or {recursive false wait false}}]
  (api-req :get (->> key url-encode (format "keys/%s"))           
           :timeout (when wait Integer/MAX_VALUE)
           :as (if wait :future as)
           :query-params (merge {}
                                (filter second
                                       {:wait wait
                                        :waitIndex wait-index
                                        :recursive recursive
                                        :sorted sorted}))
           :callback callback))

(defn del
  "Delete a key.
  Options:
  :recursive - delete key recursively
  :dir       - delete a dir
  :prev-value
  :prev-index - CAD conditions
  :callback 
  "
  [key & {:keys [recursive callback dir prev-value prev-index as]
                          :or {recursive false}
                          :as opts}]
  (api-req :delete (->> key url-encode (format "keys/%s"))
           :query-params (merge {}
                                (filter second
                                        {:as as
                                         :recursive recursive
                                         :prevValue prev-value
                                         :prevIndex prev-index
                                         :dir dir}))
           :callback callback))

(defn wait
  "Immediately returns promise if :callback was provided
  Otherwise it is blocked until response is ready. 
  In this case you may want to use (future).

  get with implicit :wait true"
  [key & {:as opts}]
  (let [args (merge opts {:wait true})]
    (apply get (flatten (into [key] args)))))

(defn mkdir
  "`set` with implicit :dir true and without value"
  [key & {:as opts}]
  (let [args (merge opts {:dir true})]
    (apply set (flatten (into [key nil] args)))))

(defn stats-leader
  "leader stats"
  []
  (api-req :get "stats/leader"))

(defn stats-self
  "self stat"
  []
  (api-req :get "stats/self"))

(defn stats-store
  "store stat"
  []
  (api-req :get "stats/store"))

(defn version
  "returns a hash-map version even for old etcd.
  keys: :internalVersion, :releaseVersion"
  []
  (let [resp @(http/request {:method :get
                            :url (make-url "version")})
        body (-> resp http/body)]
    (if (re-matches #"etcd.*" body) ;; 0.x version
      {:releaseVersion (last (re-matches #"etcd\s(.*)" body))
       :internalVersion "0"} ;; be consistent
      (parse-response resp))));; 2.x version


;; Helpers, simplified api, throws exceptions if error occurs

(defn set!
  "Same as set, but throws exception on error"
  [& args]
  (let [resp (apply set args)
        error-code (:errorCode resp)]
    (if error-code
      (throw (ex-info (:message resp) resp))
      resp)))

(defn sget
  "Returns only value for a key (for dirs returns nil).
  Throws an exception on errors.
  Use get for advanced usage."
  [& args]
  (let [resp (apply get args)
        error-code (:errorCode resp)]
    (if (and error-code (not= error-code 100)) ;; 100 like 404 in http, just return nil
      (throw (ex-info (:message resp) resp))
      (get-in resp [:node :value]))))

(defn slist
  "Lists content of directory. Returns a hash-map.
  Options:
   :recursive     - lists subdirectories recursively, default is false.
   :with-dirs     - makes sence only when option :recursive is false
                    includes dirs in result with nil values.
                    Default is true.
  "
  [key & {:keys [recursive with-dirs]
          :or {recursive false
               with-dirs true}}]
  (let [resp (get key :dir true :recursive recursive)
        nodes (get-in resp [:node :nodes])]
    (when (and (:errorCode resp)
               (not= (:errorCode resp) 100)) ;; except key not found
      (throw (ex-info (:message resp) resp)))
    (loop [dir nodes
           data-rest []
           ks []
           acc {}]
      (let [grouped (group-by :dir dir)
            items (clojure.core/get grouped nil)
            dirs (clojure.core/get grouped true)
            acc (reduce #(assoc-in %1
                                   (conj ks (:key %2))
                                   (:value %2))
                        acc
                        (if with-dirs dir items))]
        (cond
          (and (nil? dirs) (empty? data-rest))
          acc
          
          (nil? dirs)
          (recur (first data-rest)
                 (rest data-rest)
                 (pop ks)
                 acc)
          
          :else
          (recur (-> (first dirs) :nodes)
                 (cons (rest dirs) data-rest)
                 (conj ks (-> (first dirs) :key))
                 acc))))))
