# xtdb-inspector
Web UI for inspecting XTDB database

Demo:
![Demo screen recording](demo.gif)

## Running

To simply try it out, start repl with `:dev` alias:
- eval `(start)`
- eval `(some-docs)` to generate test docs
- open http://localhost:3000/doc/%3Ahello in browser

Other way is to embed in an existing web application that
uses XTDB. The `xtdb-inspector.core/inspector-handler` returns
a ring handler. See monitoring below for config that needs to
be added to the XTDB node.

## Monitoring

To enable the UI access to the XTDB monitoring, configure the
monitoring reporter in the XTDB node config:

```
:xtdb-inspector.metrics/reporter {}
```