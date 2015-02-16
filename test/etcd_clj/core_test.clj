(ns etcd-clj.core-test
  (:require [clojure.test :refer :all]
            [etcd-clj.core :as etcd]))

(defn kns [f]
  (etcd/mkdir "etcd-clj")
  (f)
  (etcd/del "etcd-clj" :recursive true))

(use-fixtures :once kns)

(deftest set-works
  (is (= "value"
        (-> (etcd/set "etcd-clj/key" "value") :node :value)))
  (testing "ttl works"
    (etcd/set "etcd-clj/key" "value" :ttl 1)
    (is (= "value"
           (-> (etcd/get "etcd-clj/key") :node :value)))
    (Thread/sleep 1500)
    (is (nil? (etcd/sget "etcd-clj/key")))))

(deftest get-works
  (etcd/set "etcd-clj/key" "value")
  (is (= "value" (etcd/sget "etcd-clj/key")))
  (testing "directories work"
    (etcd/del "etcd-clj/test" :recursive true)
    (etcd/mkdir "etcd-clj/test")
    (etcd/set! "etcd-clj/test/bar" 1)
    (etcd/set! "etcd-clj/test/baz" 2)
    (is (= #{"1" "2"} (->> (etcd/get "etcd-clj/test")
                           :node
                           :nodes
                           (map :value)
                           set))))

  (testing "recursive directories work"
    (etcd/mkdir "etcd-clj/recursive")
    (etcd/mkdir "etcd-clj/recursive/test")
    (etcd/set "etcd-clj/recursive/test/bar" "value")
    (= "value" (-> (etcd/get "etcd-clj/recursive" :recursive true)
                   :node
                   :nodes
                   first
                   :nodes
                   first
                   :value))))

(deftest del-works
  (etcd/set "etcd-clj/new-key" "value")
  (is (-> (etcd/get "etcd-clj/new-key")  :node :value))
  (etcd/del "etcd-clj/new-key")
  (is (nil? (etcd/sget "etcd-clj/new-key")))

  (testing "deleting directories works"
    (etcd/mkdir "etcd-clj/a")
    (etcd/mkdir "etcd-clj/a/b")
    (etcd/mkdir "etcd-clj/a/b/c")
    (etcd/mkdir "etcd-clj/a/b/c")
    (etcd/set! "etcd-clj/a/b/c/d" "value")
    (is (etcd/sget "etcd-clj/a/b/c/d"))
    (etcd/del "etcd-clj/a" :recursive true)
    (is (nil? (etcd/sget "etcd-clj/a/b/c/d")))))

(deftest CAS
  (let [mykey "etcd-clj/unique-key"]
    (etcd/del mykey)
    (is (-> (etcd/set mykey "value" :prev-exist false)
            :node
            :value))
    (is (-> (etcd/set mykey "new value" :prev-value "value")
            :node
            :value))
    (is (-> (etcd/set mykey "new value" :prev-value "value")
            :errorCode))
    (is (-> (etcd/set mykey "new value" :prev-exist false)
            :errorCode))))

(deftest CAD
  (let [mykey "etcd-clj/cad-key"
        index (-> (etcd/set mykey "cad value") :node :modifiedIndex)]
    (is (-> (etcd/del mykey :prev-value "value")
            :errorCode
            (= 101)))
    (is (-> (etcd/del mykey :prev-index (inc index))
            :errorCode
            (= 101)))

    (is (= "cad value" (-> mykey etcd/get :node :value)))
    (is (-> (etcd/del mykey :prev-value "cad value")
            :errorCode
            nil?))
    (is (-> mykey etcd/get :errorCode (= 100)))))

(deftest watch-key-works
  (etcd/del "etcd-clj/new-key")
  (let [wait-future (etcd/wait "etcd-clj/new-key")]
    (is (nil? (-> (etcd/get "etcd-clj/new-key")
                  :node
                  :value)))

    (future
      (Thread/sleep 500)
      (etcd/set "etcd-clj/new-key" "value"))
    (is (= "value"
           (-> wait-future
               (deref 2000 nil)
               :node
               :value))))

  (testing "callbacks work"
    (let [result-atom (atom nil)
          watch-promise (etcd/wait "etcd-clj/new-key" :callback (fn [result]
                                                              (reset! result-atom result)))]
      (etcd/set "etcd-clj/new-key" "new value")
      @watch-promise
      (is (= "new value" (-> @result-atom :node :value))))))

(deftest exceptional-errors-throw-exceptions
  (is (thrown? java.util.concurrent.ExecutionException ;java.net.ConnectException
        (etcd/with-connection {:port 4002}
          (etcd/get "etcd-clj/key")))))

(deftest keys-are-url-encoded
  (is (= "my value"
         (-> (etcd/set "etcd-clj/my key" "my value")
             :node
             :value)))
  (is (= "my value" (-> (etcd/get "etcd-clj/my key") :node :value)))
  (etcd/del "etcd-clj/my key")
  (is (nil? (-> (etcd/get "etcd-clj/my key") :node :value))))
