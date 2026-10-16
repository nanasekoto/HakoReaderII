# Hako 0.7.39 — Event-driven Read-all queue

Baseline: source 964be490429f2d129479a18ea89e642b16003f02, PR #8, version .38/code54. Audit input: BAO_CAO_KIEM_TOAN_HANG_CHO_DOC_HET_0.7.38.md (Gemini, real S4 report e5015213). New version .39/code55.

## Workflow and ownership

Final text page/checkpoint -> durable Store request (ID snapshot, tip, generation) -> QUEUED UI -> ReadSync.kick on a worker. Events: startup, network available/validated, auth return after imported history/domain, manual completion, follow saved, pause/resume, Repository owner release. UI never waits for remote read-all.

A synchronized admission gate permits one ReadSync worker. Events during a pass coalesce into one next pass; an idle worker exits. Repository.busy separately serializes all web owners. Busy deferral publishes WAIT_BUSY; Repository.run (success or exception), refreshForReading and domain application hand off after releasing ownership. There is no timer, polling loop or unconditional self-kick after a failed request. One failed title does not abort the pass for other titles.

ReadSync reconciles local queue before early exits, including an entirely stale queue. New unread text retires a request. Missing/local-only book retires it. A proven newer local completion may extend the request with a new generation. Missing catalog proceeds to fresh per-book verification. A stale followed=false flag alone does NOT retire a legacy pending request: local membership is not server evidence. All retirement remains generation guarded.

Per-title send path: current request -> fresh server catalog -> same-generation/pause/stop guards -> canSubmitServer -> action on /ke-sach -> accept only verified OK/OK_BUTTON_GONE or NO_READ_BUTTON -> generation-guarded retirement -> refresh catalog. No button suppresses the same tip until a new tip or verified returned button. New server text blocks the action. An already-sent HTTP action cannot be undone by a later OS cancellation.

ReadSync never imports the full shelf before processing. Actual shelf synchronization uses try/finally to offer the queue its turn even if importShelf throws, while retaining the original shelf error. The release hook also covers personal refresh and reading refresh. This does not broaden shelf/personal download scope.

Job 42 is a persisted OS fallback, with network constraint, minimum initial latency 60s and linear 60s backoff. A new event starts the worker even if an older Job 42 exists/backed off. Empty queue/pause removes obsolete fallback. A running job has its own stop token; it cannot set Repository.cancel or cancel unrelated downloads. jobFinished is in finally, skipped after OS stop as required by the job lifecycle. No exact historical Android backoff interval is claimed from the 9 spans alone.

## State/telemetry

Existing read_sync_status preferences retained; schema 2 additive fields only. Seven groups: syncPaused, job42Pending snapshot, workerActive snapshot, lastScheduleAt/outcome, lastProcessStartAt/endAt, lastProcessOutcome, per-generation attempts in states. Also lastScheduleReason and lastJobStopReason. States bounded to 24; no credentials/title/network bodies. Attempts increment only at actual per-title verification and remain in terminal history; a new generation resets them. Event writes only, no per-page profiling added. Profiling off still preserves this small business state and manual reporting.

## Preserved invariants and rollback

Package vn.nanase.hako; same signing identity; SQLite schema v3; caches and read watermark unchanged. Illustrations excluded from unread; full download reset unchanged; automatic 15-text-chapter scope, pause, foreground priority, manual-only reporting unchanged. Native offline reader does not create GeckoRuntime. No UI layout batch bundled here.

Rollback source: revert this batch while retaining private report config. New preferences/labels are additive and safely ignored by .38. Android generally rejects installing lower versionCode; prepare a forward-version rollback signed with the existing key instead of uninstalling or erasing data.

## Validation

New ReadQueueWorkflowTest uses an internal package-private transport adapter with real Store/ReadSync orchestration: stale-only queue, pause/network/busy states, release handoff, old Job42 bypass, generation races, absent button, fresh new text, stopped token, fair per-title failures, concurrent event coalescing and bounded telemetry. Existing suites remain required. CI order: JVM/JS -> new queue + UX/TOC/feature targeted suites -> remaining suites -> final ARM32 release. Build results belong in the delivery report, not assumed here. Emulator fixtures do not claim real Hako submission or S4 battery measurements.
