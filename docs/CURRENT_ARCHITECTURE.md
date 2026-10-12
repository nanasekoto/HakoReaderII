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


## 0.7.24 manual reports and responsiveness
Home reads only in-memory snapshot; cold disk restore runs on library worker. Manual report requests are the only jobs accepted. Background flush persists diagnostics but never queues/uploads mail. Optional private asset is injected locally after public CI, then aligned and signed using the existing key; it is never committed. Foreground-only 3s probe records at most three stacks when UI acknowledgment is delayed 6s; no upload is triggered. Snapshot restore, monitor and local asset installation leave database/cache/reading policy unchanged. Rollback to prior APK source removes this probe and restores synchronous startup snapshot; user data remains. Real S4 stall cause requires captured stack; monitored delays are not themselves proof of OS ANR.


## 0.7.25 manual report path
Dedicated report executor persists to internal outbox without ACTION_CREATE_DOCUMENT, independent of chapter IO. Manual send performs native HTTPS immediately on Wi-Fi, using JobScheduler only when offline; direct send and retry delivery are serialized. Configuration install also runs on report executor before save. Backend rejection reasons are translated visibly; failed send retains the saved file. PackageManager metrics are outside telemetry lock to avoid blocking UI timing calls. No startup/pause auto send. Rollback: revert report path; DB and chapter cache unaffected. Live relay authentication verified with intentionally invalid report, no email emitted; S4 delivery still needs user test.


## 0.7.26 cache/report review
Negative validation fingerprints are now reused only while HTML and referenced image fingerprints match; repairing/removing/changing an image invalidates both positive and negative results. Progress filters download scope before validating files, preserving classification within that scope. Optional file picker launches fail with a visible message rather than crashing on S4. Reports add persisted bounded aggregate workflow metrics and query categories, no novel titles/content. Backend HTML summary retains Script Properties, strict token/schema/report ID, lock/receipt/quota and matching ok acknowledgement; deployment must be updated separately. No Sheets or automatic email. Rollback reverts this batch without schema/cache-path changes.


## 0.7.27 reading / collecting / choosing
Current source of truth remains SQLite v3 + chapter content files. Store now keeps a metadata-only LRU of four catalogs (16k rows across multiple books; one oversized active book retained), with an ID lookup index. `chapters` never stats HTML/images; `ready` in lists is display metadata, not proof of readable content. Actual open/download still calls `readable`. Catalog/state/rebase/clean mutations update or evict the affected metadata. No-op catalogs do not rewrite rows or invalidate display state. `canSubmitServer` checks a single ID map, and `completedTip` scans one read-set/catalog instead of per-ID queries; fresh server catalog proof is unchanged.

TocState owns per-book AtomicFile metadata at toc-state/ID.json + a generation-bound small .progress overlay. The base is rewritten only when chapter metadata objects change; position/read changes write the overlay. Snapshot holds Book/chapters/read IDs, never HTML/images. UI keeps prepared groups for the selected snapshot: warm TOC/volume navigation renders without Store locks/queries. Cold restore reads only that book snapshot on worker, reconciles SQLite afterward, and keeps UI input free. Dirty catalog/classification/state/read/position changes rebuild only that book on coalesced worker. Snapshot is derived and safely rebuildable; failed/corrupt snapshot falls back to worker metadata. Memory entries bounded to four books/16k rows across books; disk snapshot read capped at 8MB. Chapter header Resume resolves authoritative saved position on worker, not stale displayed chapter ID.

Validation index at validation-index.json persists up to 4096 fingerprints/referenced local image names/chapter title hints. Load is lazy on validation worker, no filesystem scan; each reuse still compares actual HTML/image size/time stamps. Changed title/HTML/image invalidates proof; corrupt index causes actual parsing, never trusting ready alone. Image/HTML decode never moves into TOC display. Known ignored old illustration policy is checked before scheduling expensive validation in download queues. Index writes coalesced 2s; TOC writes coalesced after mutations. Startup read-sync scheduling moved off main thread.

Hot path: cached lists → cached TOC groups → selected chapter validation/prepared text → UI layout. Background: affected-book snapshot persistence, validation-index persistence, network catalog/download/read-sync and explicit reports. Invariants: no wrapping at latest chapter, illustrations excluded from reading watermark, server read-all requires current proof, retention/download scope/Pause/charging+Wi-Fi unchanged, package/signing/database paths unchanged, report send manual-only.

Files: Store, TocState, MainActivity, Repository, existing LibraryState/UiLibrarySnapshot. Targeted TocWorkflowTest covers metadata lookup query bounds, unchanged catalog writes, readiness versus physical validation, snapshot/watermark persistence, progress overlay, batched remote-read eligibility, warm TOC and volume while Store monitor is locked, cold validation-index reuse and replacement invalidation. Existing Android suites retained. Rollback: revert this batch; remove only rebuildable toc/validation index files if needed, never database/content/read queue. Remaining: StaticLayout/visible image decode on UI, all rows in one active large catalog, physical S4 improvement measurement and repeated network workflow evidence.


## 0.7.28 main-thread service queue handoff
S4 report defb7283 (0.7.26) records previous_exit_reason=6 and a 12.129s main-thread stall at DownloadService.onStartCommand → Repository.queueBook. Static synchronized queueBook/takeBook/queuedBookMode shared Repository.class with synchronized download network IO; this persisted into 0.7.27. Queue handoff now uses a dedicated tiny monitor only for immutable request pair assignment/take/peek/clear. Network serialization remains unchanged. Pause clears request pair under the same queue lock; resume builds one consistent request snapshot. QueueResponsivenessTest holds Repository.class while invoking queue/take on main and requires immediate completion and preservation of full request priority. This fixes the identified contention path without changing download scopes, retention, cache, snapshots or manual diagnostics. Rollback reverts the lock split; no database migration.

