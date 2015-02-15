# etcd-clj

A Clojure client for [etcd].
Uses [http-kit] to talk to `etcd`, so we get callbacks for free.

## Installation

`[etcd-clj "0.2.3"]`

## Changelog

see [HISTORY.md](https://github.com/narma/etcd/blob/master/HISTORY.md)

It's a fork of [cetcd](https://github.com/dwwoelfel/cetcd) project.
The main goal of this lib is to fully cover the last API of etcd and to stay simple.


## Usage
`(require '[etcd-clj.core :as etcd])`

### Connection

`etcd-clj` doesn't require set connection if you use default configuration
Otherwrise call `set-connection!`

```clojure
(etcd/set-connection! :host myhost)
;; also valid options are :port and :protocol
```

### Set the value to a key

```clojure
user> (etcd/set :a 1)
{:action "set",
 :node
 {:key "/:a",
  :modifiedIndex 10,
  :createdIndex 10,
  :value "1",
  :prevValue "1"}}
```

Also `etcd/set!` do some, but throws exception if error occurs.


### Get the value of a key

```clojure
user> (etcd/get :a)
{:action "get",
 :node {:key "/:a", :modifiedIndex 10, :createdIndex 10, :value "1"}}
```

Get actual value using the simplified API
```clojure
user> (etcd/sget :a)
"1"
```

### List contents from a directory

In etcd `get` command works for regular keys and dirs.
See etcd documentation for how it works.

`etcd/list` fn is a helper which returns hash-map for a directory.
It has two options:

- `:recursive`     - lists subdirectories recursively, default is false.
- `:with-dirs`     - makes sence only when option :recursive is false
                     includes dirs in result with nil values.
                     Default is true.




Notice that `etcd` didn't preserve the type of the key's value. This job is left to the caller:

```clojure
user> (etcd/set :clojure-key (pr-str 1))
{:action "set", :node {:key "/:clojure-key", :value "1", :modifiedIndex 14, :createdIndex 14}
user> (-> (etcd/get-key :clojure-key)
          :node
          :value
          (clojure.edn/read-string))
1

```

### Delete a key

```clojure
user> (etcd/del :a)
{:action "delete",
 :node
 {:key "/:a", :modifiedIndex 11, :createdIndex 10, :prevValue "1"}}
 ```


Everything that `etcd` does can be accomplished with the `set`, `get`, and `del` functions above, by passing in the proper keyword args. There are also a few helper functions to keep things a bit cleaner.


### Create a dir

```clojure
(etcd/mkdir "mydir")
```
It's alias for `(set "mydir" nil :dir true)`


### Atomic Compare-and-Swap, CAS

Use set with conditional options:
* `:prev-value` - checks the previous value of the key.
* `:prev-index` - checks the previous modifiedIndex of the key.
* `:prev-exist` - checks existence of the key.

```clojure
user> (etcd/set :a 2 :prev-value 1)
{:action "compareAndSwap", :node {:key "/:a", :prevValue "1", :value "2", :modifiedIndex 15, :createdIndex 13}}
```

You have to check manually if the condition failed:

```clojure
user> (etcd/set :a 2 :prev-alue 1)
{:errorCode 101, :message "Test Failed", :cause "[1 != 2] [0 != 22]", :index 22}
```

### Compare and delete
Same as CAS but without `:prev-exist`

```clojure
user> (etcd/del :key :prev-value 2)
{:action "compareAndDelete", :node {:key "/:cad-key", :modifiedIndex 48, :createdIndex 47}, :prevNode {:key "/:cad-key", :value "2", :modifiedIndex 47, :createdIndex 47}}
```

You have to check manually if the condition failed:

```clojure
user> (etcd/set :key 2)
{:action "set", :node {:key "/:cad-key", :value "2", :modifiedIndex 49, :createdIndex 49}}
user> (etcd/del :key :prev-value 1)
{:errorCode 101, :message "Compare failed", :cause "[1 != 2] [0 != 49]", :index 49}
```

### Wait for a value

```clojure
user> (future (println "new value is:" (-> (etcd/wait :a) :node :value)))
#<core$future_call$reify__6267@ddd23bc: :pending>
user> (etcd/set :a 3)
new value is: 3
{:action "set", :node {:key "/:a", :prevValue "2", :value "3", :modifiedIndex 16, :createdIndex 16}}
```

If you provide a callback function, then it will immediately return with a promise:

```clojure
user> (def watchvalue (atom nil)) ;; give us a place store the resp in the callback
#'user/watchvalue
user> (etcd/wait :a :callback (fn [resp]
                                       (reset! watchvalue resp)))
#<core$promise$reify__6310@144d3f4b: :pending>
user> (etcd/set :a 4)
{:action "set", :node {:key "/:a", :prevValue "3", :value "4", :modifiedIndex 20, :createdIndex 20}}
user> watchvalue
#<Atom@69bcc736: {:action "set", :node {:key "/:a", :prevValue "3", :value "4", :modifiedIndex 20, :createdIndex 20}}>
```


## License

Copyright © 2013

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[etcd]: https://github.com/coreos/etcd
[http-kit]: http://http-kit.org/

