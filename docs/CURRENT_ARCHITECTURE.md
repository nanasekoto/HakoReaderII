# Current architecture — 0.7.23 workflow/diagnostics batch

Baseline: 0.7.22 commit 2abfca4a8ab22ef89d66338769039b3786cb9a32. Current operating addendum is this file, SNAPSHOT_STARTUP_0722.md and sequential-progress rules; older README/0.7.15 diagrams are historical.

## Ownership and workflows

| Path | Owner/current flow |
|---|---|
| Startup | MainActivity restores atomic base/delta display metadata; first-layout timing is measured. Index preparation is background work, without chapter/DB scanning on warm startup. |
| Lists | UiLibrarySnapshot caches ordered shelf/recent/favorite/completed/filter indices. LibraryState updates changed IDs, uses binary insertion for small updates and rebuild for large batches; metadata-only timestamp changes reuse indices. UI reuses stable adapter contents/order and avoids no-op notify/clear/addAll. |
| Read/open | One worker request gets Store.Selection, validates selected cache, downloads only if missing, reads HTML and builds NativeReader.Prepared. Native UI only adopts spans and performs final layout. A single prepared chapter <=256k chars is retained, keyed by content+image fingerprint and formatting/viewport. |
| Navigation | Adjacent lookup remains canonical, with no wrap to chapter one. Automatic download scope and retained-full rules are unchanged. Opening uncached selected/resume content now opens it after the requested download rather than requiring another tap on TOC. |
| Reading | Repeated same-position/layout callbacks are deduplicated within the view; final completion is handled once per view. Paragraph anchor lookup uses binary search. Font face is reused while font identity/file stamp is unchanged. Final StaticLayout remains on UI and is explicitly measured. |
| Shelf | Query followed metadata through followed/rank index, use hash membership and update only changed title/info/rank. Full membership reconciliation preserves personal book records, progress and content. Existing safe latest-key stopping and explicit audit remain. |
| Login | Existing Gecko profile/IPC handoff remains; returned history import runs on worker instead of UI. |
| Read-sync | Retain fresh catalog validation before/after mark-read. A queued completion already blocked by known unread text does not fetch the shelf merely because the queue exists. No time-based shortcut weakens server proof. |
| Reports | Small header button opens manual send/status/export/settings. Diagnostics stores bounded phase aggregates, up to 64 timing samples/phase, aggregate counts, Java error frames, previous-session/crash and Android exit reason. Native ReportJob uses Wi-Fi HTTPS without Gecko or chapter/DB scans. |

## Persisted state and limits

- SQLite v3, package/signing identity and chapter cache paths unchanged. Optional shelf index is created lazily in the shelf worker.
- Display base/delta AtomicFiles remain rebuildable derived state. Dirty markers follow original Store writes; catalog/read/current/ready/membership changes invalidate affected summaries. Warm navigation does not query chapters.
- Prepared content is one bounded item per MainActivity; file/image fingerprint, paragraph/indent style, density and viewport invalidate it. It is discarded with activity lifetime. Reformat parsing is cancellable background work; final layout is not yet asynchronous.
- Diagnostics-last/crash are bounded atomic snapshots; outbox has at most eight reports. Counter/phase names are code-defined, not arbitrary user values. No title, URL, cookie, token, password or novel content is collected. PSS/CPU metrics cover the main process, not the isolated Gecko process.
- Report endpoint/token live in private settings. Script recipient/token live in private Script Properties. Automatic queueing/sending is limited to daily/background/Wi-Fi; manual send is explicit. Endpoint absent means local collection/export only, not successful email. Deletion requires matching service report-ID acknowledgement; mailbox delivery needs actual end-to-end confirmation.
- No heartbeat watchdog. ANR/process death metadata is collected on next launch when Android provides it. The report upload and existing jobs keep separate IDs and queues.

## Critical versus background

Critical: show cached metadata, acknowledge input, choose requested chapter, read/prepare its content, adopt text and final layout/first draw, turn pages. A foreground request waits for its own worker result; moving it to a worker does not make that wait disappear. Background: indices, changed-book summaries, journal compaction, progress persistence, eligible downloads, read-sync and reports. They must not hold main-thread Store locks.

## Gates, rollback and remaining questions

Run backend mock/unit checks + existing JVM/JS policies first. Build debug only for targeted Android regression/profiling; release ARM32 is gated on successful Android tests. One coherent batch, not one APK per edit. After a failure, identify cause and allow one evidenced repair pass; a still uncertain failure is reported, not repeatedly guessed.

Rollback by reverting this feature branch/caches; never delete SQLite, chapters or queued completion. Disable reporting/clear endpoint and remove Apps Script deployment independently. NativeReader.content compatibility remains for callers; normal app opening uses Prepared.

Remaining: actual S4 CPU/RAM/p50/p95 must come from installed-device reports. Main StaticLayout and visible bitmap decode are still measured UI work; immutable display maps still incur pool copies on publication. Coordinator-wide web freshness coalescing is intentionally deferred until logs identify redundant sessions; proof-sensitive mark-read requests remain. Receiver activation needs the user's Google deployment/authorization and an actual email receipt; mocked email tests do not establish deliverability.
