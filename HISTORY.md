# WIP

- Migrate from `http-kit` to `asynchttpclient` java library
- [BREAKING] wait now returns future object.
- [BREAKING] core API methods now returns `future` object instead `promise`
  When user provide `:callback` or `:as` with value `:future` arguments
- Remove `machines` which using deprecated key `_etcd`


# 0.2.3

- Forked from [cetcd](https://github.com/dwwoelfel/cetcd) project.
- Release as etcd-clj, use own api: set, get, del instead set-key!, get-key, delete-key!
- Allow use of condition keyword args in set and del
- Add :order param for `set` for ordering
- Add :sorted param for `get`
- Do not overwrite default keys in set-connection! within nils if only part of settings is provided.
- Fix :ttl nil usage for removing ttl
- Add *timeout* settings 
- Add `version` api call

# 0.2.2

- Adds support for prevValue and prevIndex to delete, implements compare-and-delete! (fixes #4). Thanks pjlegato!

# 0.2.1

- Update http-kit to make 307 redirects work (fixes #5). Thanks pjlegato!

# 0.2.0

- URL encode all keys

