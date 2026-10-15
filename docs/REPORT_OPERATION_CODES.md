# Report provenance — 0.7.29

Report root `version` and `commit` identify the exact source. `operations.sourceRoot` is the repository prefix; `definitions` maps each recorded code to its responsible class/method. Source: OperationTrace, taskOp, Store.traceQuery, Store.readable. No SQL text, title, URL or novel content is emitted.

| Code | Meaning / responsible entry |
|---|---|
| read.open / read.prepare / read.layout | Selected chapter request / worker HTML preparation / UI StaticLayout |
| read.adjacent / read.next_web / read.page | Local chapter lookup / new catalog lookup at boundary / page turn |
| read.persist / read.complete / read.sync | Saved position / completion watermark / conservative remote synchronization |
| toc.open / toc.volume / toc.sort / toc.refresh | TOC, volume, ordering and changed-book snapshot |
| book.info_cache / book.info | Cached novel details / fresh web details |
| web.list / book.add / web.catalog | Discovery/search / explicit URL import / catalog refresh |
| net.request / net.bind | Gecko request / service binding |
| download.enqueue / download.prefetch / download.run | Explicit request / reader prefetch / download coordinator |
| library.refresh | Incremental display metadata publication |
| book.clear | Delete downloaded content, retain personal metadata |
| settings.size / line / paragraph / indent / margin / apply | Selected hardware-key control and applying settings |
| data.export / data.import | Explicit portable archive |
| report.save / report.send | Manual outbox save or delivery |
| db.outside / ui.other | Query outside a traced scope / legacy task with no specific classification; do not infer a missing measurement |

All times are milliseconds. Each finished sample contains `id`, `parent` when nested, `op`, `startMs` (monotonic, relative to device boot), `wait` (executor queue), `run` (worker/body), `ui` (completion callback), `thread`, `q`, `cache` and `result`. `capturedElapsedMs` provides chronology within this report. Nested run times overlap their parents: **do not sum parent and child as total latency**. UI callback time is separate from the worker phase. Asynchronous child work can finish after its parent.

`q` columns: books / chapters / other raw queries (not returned rows or writes). A query is charged only to the innermost current scope. `cache` columns: checks / parses / totalMs / maxMs for physical readable checks in that scope. UI steps that perform no queries have zero counts. Existing `counts` and `timings` remain compatible with older report readers.

`statsColumns` describes the ordered aggregate array in `totals` (count, wait/run totals and maxima, categorized queries, UI total/max, cache checks/parses/time/max). Keep totals for up to 48 codes, the latest 24 finished samples, at most 16 in-flight samples. In-flight entries describe work still running at capture, useful for a delayed reader or stall. Report size limit and outbox retention are unchanged. No per-frame/per-query JSON event stream; telemetry does no disk write per action. Save/send writes only when the user requests a report or the existing local session persistence occurs.

`result=sent` requires the configured receiver's matching positive acknowledgement; it does not independently prove arrival in an inbox. Manual-only reporting remains enforced. Stall snapshots include captured version/commit/session, and are cleared after acknowledged delivery only if the saved snapshot matches. A previous-session stall is not a current-version recurrence.

To analyze: locate the largest `wait`, then its code and parent; distinguish web request time from cache/prepare/layout time. Compare query/cache aggregate counts for repeated actions. Resolve the mapped method against the root commit. Report actual S4 measurements separately from emulator regression results.

