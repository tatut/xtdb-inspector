# xtdb-inspector

![test workflow](https://github.com/tatut/xtdb-inspector/actions/workflows/test.yml/badge.svg)

Web UI for inspecting XTDB database

Demo:

Browsing documents and showing history:

![Demo screen recording of document browsing](demo/demo-doc.gif)

Query viewer and saved queries:

![Demo screen recording of queries](demo/demo-query.gif)

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

## Changes

### 2022-10-18
- Support exporting query to EDN file

### 2022-10-17
- Add `:allow-editing?` configuration option (default: `true`) for disabling doc edit functionality
- Support `:wrap-page-fn` that wraps any page rendering with a custom fn

### 2022-08-10
- Show latest transaction first by default

### 2022-07
- Dashboard page
- Graphs for attribute values

### 2022-06-29
- Pretty print EDN values (better coloring and layout)

### 2022-06-27
- Add new page for transaction log
- Document view can expand linked docs inline

### 2022-06-21
- FIXED Attribute name in URL is encoded, so attributes with URL characters (like ?) work
- Add convenience button to document page to copy the doc id to clipboard

### 2022-06-17
- Tables now have filtering and ordering
- Attribute values page limits to 100 items (with button to fetch more)
- Remember last run query in editor page

### 2022-04-13
- Add `java.time.Instant` display and editing
- Allow adding new attributes and creating documents

### 2022-03-26
- Better display end edit non-EDN values (`java.time` types)

### 2021-10-28
- Add top bar navigation with search. Can query lucene index to find docs.
- Directly navigate to saved query by name (`/query/<query name>`) to load it
- New attribute page that shows attributes and their values
- Document page can now do simple edits to the values
