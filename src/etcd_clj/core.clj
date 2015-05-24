(ns etcd-clj.core
  "For detailed API see https://github.com/coreos/etcd/blob/master/Documentation/api.md"
  (:import [java.net URLEncoder])
  (:require [cheshire.core :as json]
            [etcd-clj.http :as http]
            [clojure.string :as str])
  (:refer-clojure :exclude [list get set]))


(def ^{:dynamic true :doc "request timeout"}  *timeout* 2000)
(def ^{:dynamic true} *api-version* "v2")


(defn set-default-timeout! [timeout]
  (alter-var-root (var *timeout*) (constantly timeout)))


(defn set-connection!
  "Defaults for :port 4001, :host \"127.0.0.1\", :protocol \"http\""
  [provider-class & config]
  (let [config (http/prepare-config config)
        provider (provider-class config)
        connection (http/open provider)]
    (if (thread-bound? #'http/*http-config*)
      (set! http/*http-config* config)
      (alter-var-root #'http/*http-config* (constantly config)))
    (if (thread-bound? #'http/*connection*)
      (set! http/*connection* connection)
      (alter-var-root #'http/*connection* (constantly connection)))))

(defmacro with-connection [config & body]
  `(do
     (let [config# (merge http/default-config ~config)]
       (binding [http/*http-config* config#]
         ~@body))))


(defn url-encode
  ;; copied from cemeric.url
  [string]
  (some-> string str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(defn ^:no-doc make-url
  "Constructs url used for all api calls"
  [& parts]
  (str (java.net.URL. (:protocol http/*http-config*)
                      (:host http/*http-config*)
                      (:port http/*http-config*)
                      (str/join "/" (concat [""] parts)))))

(defn ^:no-doc parse-body
  [body]
  (cheshire.core/decode body true))

(defn api-req
  "Makes an api call, wraps callback with parsing response body if it is provided"
  [method path & {:keys [conn timeout]
                  :as opts}]
  (http/request (or conn http/*connection* @http/injected-connection)
                (merge {:method method
                        :timeout (or timeout *timeout*)
                        :url (make-url *api-version* path)}
                       (dissoc opts :timeout))
                parse-body))

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
  [key value & {:keys [ttl dir order prev-value prev-index prev-exist]
                :as opts
                :or {dir false}}]
  (api-req (if order :post :put)
           (->> key url-encode (format "keys/%s"))
           :form-params (merge
                         (dissoc opts :ttl :dir :order :prev-value :prev-index :prev-exist)
                         (if dir
                           {:dir dir}
                           {:value value})
                         (remove #(-> % second nil?)
                                 {:prevIndex prev-index
                                  :prevValue prev-value
                                  :prevExist prev-exist})
                         (when (contains? opts :ttl)
                           {:ttl ttl}))))

(defn get
  "Get key from etcd.
  Options:
  :callback   - return promise immediately with a promise
  :wait       - wait a change, it's etcd feature
  :wait-index - wait for a concrete index
  :recursive  - get content recursively
  :sorted     - returns result in sorted order"
  [key & {:keys [recursive wait wait-index sorted]
          :as opts
          :or {recursive false wait false}}]
  (api-req :get (->> key url-encode (format "keys/%s"))
           :timeout (when wait Integer/MAX_VALUE)
           :query-params (merge (dissoc opts :recursive :wait :wait-index :sorted)
                                (filter second
                                       {:wait wait
                                        :waitIndex wait-index
                                        :recursive recursive
                                        :sorted sorted}))))

(defn del
  "Delete a key.
  Options:
  :recursive - delete key recursively
  :dir       - delete a dir
  :prev-value
  :prev-index - CAD conditions
  :callback
  "
  [key & {:keys [recursive dir prev-value prev-index]
                          :or {recursive false}
                          :as opts}]
  (api-req :delete (->> key url-encode (format "keys/%s"))
           :query-params (merge {}
                                (filter second
                                        {:recursive recursive
                                         :prevValue prev-value
                                         :prevIndex prev-index
                                         :dir dir})
                                (dissoc opts :recursive :dir :prev-value :prev-index))))

(defn wait
  "Immediately returns promise|future or block,
  behaviourdepends on provider

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
  [& {:keys [connection]
      :or {connection (or http/*connection* @http/injected-connection)}}]
  @(http/request connection
                 {:method :get
                  :url (make-url "version")}
                 identity))


;; Helpers, simplified api, throws exceptions if error occurs

(defn set!
  "Same as set, but throws exception on error"
  [& args]
  (let [resp @(apply set args)
        error-code (:errorCode resp)]
    (if error-code
      (throw (ex-info (:message resp) resp))
      resp)))

(defn get!
  "Returns only value for a key (for dirs returns nil).
  Throws an exception on errors.
  Use get for advanced usage."
  [& args]
  (let [resp @(apply get args)
        error-code (:errorCode resp)]
    (if (and error-code (not= error-code 100)) ;; 100 like 404 in http, just return nil
      (throw (ex-info (:message resp) resp))
      (get-in resp [:node :value]))))

(defn ls
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
  (let [resp @(get key :dir true :recursive recursive)
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
