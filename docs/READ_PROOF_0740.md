# 0.7.40 — Read-all proof by chapter IDs

Baseline: dc30111acfe5668886fe91b69d3e14d9ada7e6b2 (.39/code55). Version .40/code56. Input: TONG_HOP_LOI_0.7.39_GUI_GPT.md. No UI layout or download-scope change.

## Current send path

Existing event admission/Repository.busy/Job42 handoff retained. Reconcile queue -> skip unchanged held request -> fresh per-title catalog -> verifyServerSubmission(captured request) -> recheck generation/pause/stop -> verified web action -> generation-guarded retirement. No button retains same-tip suppression. Same-signature no-button/real network evidence remains required; no invented SENT.

Server verification batches known chapters, effective read IDs (watermark plus explicitly read IDs) and queued snapshot IDs. Fresh catalog must be successful, server text nonempty with unique known IDs, request still current, queued tip equal completed tip and present in read/snapshot. Every server text ID must be both read and in the queued snapshot. It does NOT require queuedTip to be the last HTML/catalog element. Thus a completed main-volume chapter can precede an already-read side story. New text missing from either read evidence or completion snapshot is never silently accepted.

completeThrough retains monotone ordinal watermark semantics; explicitly read inserted/earlier chapters remain in read_chapters. queueIfAllRead now offers queueCaughtUp at completion even if tip unchanged, allowing snapshot payload to capture explicitly completed inserted chapters. queueCaughtUp already deduplicates identical payload/tip and preserves generation for no-op. Existing .39 pending requests are verified under the new rule without deleting user data.

## Failures and event-only holds

verifyServerSubmission returns precise reason: OK, REQUEST_CHANGED, CATALOG_FAILED, EMPTY_SERVER_TEXT, INVALID_SERVER_IDS, UNKNOWN_SERVER_CHAPTER, INVALID_SNAPSHOT, INVALID_CATALOG_OR_SNAPSHOT, TIP_MISMATCH, TIP_NOT_READ, TIP_NOT_IN_SNAPSHOT, NEW_UNREAD, SNAPSHOT_MISSING_IDS.

Unread text retires obsolete completion as NEW_UNREAD. Actual I/O errors remain WAIT_RETRY with the exception class, and failed fresh catalog remains retryable. Deterministic missing/mismatched proof becomes WAIT_DATA, with exact reason. No more generic server_tip_unverified/retry_io classification.

read_sync_holds has one SHA-256 metadata signature per held pending book: generation/tip/payload/completed watermark, server IDs, catalog failure flag, effective read IDs and reading classification. No HTML/filesystem scans. Unchanged automatic events skip held requests before network or attempt increment; no runnable requests means Job42 is removed/finishes without retry. Data changes invalidate signatures; manual_caught_up and auth_return allow an explicit fresh attempt. clearQueue removes its hold. No polling/timer or hot-page logging introduced. Hashes are invalidation hints only, never evidence permitting submission; a released hold still requires a fresh server catalog and full ID proof.

lastProcessOutcome is retry_pending for retryable pending work, wait_data for held proof, sent_all only when queue empty. Small bounded read_sync_status business telemetry retained with attempts and precise reason. No claim that every network event was an actual remote attempt.

## Critical/background paths and invariants

UI completes local checkpoint promptly. Verification, hashing and web work stay on workers. Package vn.nanase.hako, signing key, SQLite schema3, chapter files, watermark, offline reader, 15-text-chapter scope, pause, no-button suppression, private/manual email config unchanged. No fallback based solely on unread==0. No weakening generation guard, fresh catalog or verified action.

## Tests and rollback

8 new pure ReadPolicy proof cases plus integration additions in ReadQueueWorkflowTest cover main-tip before already-read side story through actual send, inserted text absent from old snapshot despite unread0, unchanged-event no-retry/attempt/no-Job42, manual one-shot recheck, explicit inserted read refreshing snapshot, tip mismatch and precise log. All prior suites remain mandatory. CI: JVM/JS/static -> targeted Android -> remaining Android -> ARM32 release. Actual results in delivery report.

Rollback requires a forward-version signed APK built from .39 (Android downgrade is not assumed). Hold preferences are additive; do not uninstall/erase DB. Reverting restores the .39 verification defect, so prefer forward fix. Device Hako responses/battery remain real-device validation, not inferred from fixtures.
