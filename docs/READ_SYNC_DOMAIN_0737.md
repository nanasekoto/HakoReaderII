# Hako Pocket 0.7.37 — read-all recovery and origin verification

Baseline: main ee5031807cf9d7034badb8bd6c2f9d29af58e94d, tested .36 program 057b4f6. Keep docln.sbs as default; no assumed domain outage and no speculative origin migration.

## Workflow

Native final-page completion persists the watermark, publishes a book-scoped business state even if no remote request exists, then schedules the existing network job. Shelf requests capture request tokens before loading HTML. An authenticated target row with no button may retire only that token. Seeing the button return removes suppression and automatically restores an eligible completion, without manual re-queuing.

Each request has payload, tip and UUID generation. Reset/recreate at the same tip gets a new generation; stale shelf/action replies cannot consume it. Fresh catalog verification remains mandatory before clicking. New unread chapters retire an obsolete generation instead of pretending to send it. Only known action acknowledgements retire a request as sent. Unknown responses/error/auth keep eligible work for existing job backoff; no global cooldown or new polling loop. Missing target row is an error, not proof of missing button.

ReadSync publishes states WAIT_NETWORK, WAIT_VERIFY, NO_BUTTON, NEW_UNREAD, SENDING, SENT and WAIT_RETRY with a book ID. Reports include readQueue count, at most24 pending identifiers and24 last business statuses/reasons/timestamps. No title/content/cookie/token or network exception message is recorded. State persistence is change-based. Operation scope outcome differs from successful remote marking. The read-sync owner finalizes its workflow before releasing busy.

Domain settings accept a full HTTPS URL and extract its origin. Check-and-save tests a request-specific candidate; Save-and-open-verification explicitly applies the user's origin and opens its login without needing the old site online or a challenge bypass. Sender origin and canonical document identity are checked independently, including HTTPS/default port/no credentials; trailing root slash is harmless. Known aliases normalize to the chosen active origin. An actual browser return to an approved HAKO mirror adopts that location on a worker rather than continuing requests to an unlogged old mirror.

Store.rebase runs on a worker under a SQLite transaction and preserves IDs/content/read watermark. It persists the selected origin, clears metadata catalogs, invalidates derived TOC/StoryInfo disk data and publishes after commit. A pending_origin journal enables the next worker initialization to retry an interrupted rebase. Domain switching refuses an existing download owner instead of unbinding another active request. Gecko profile/cookies are never copied between domains or engines; the user may still need to sign in on the selected site.

## CPU/state correctness

Book-only mutations update LibraryState affected IDs and the cached TocState book overlay; they no longer schedule chapter/read reconstruction. No-op title/url/pin/drop/follow/rank/info changes skip mutation. TOC disk load reconciles current book metadata from SQLite on its worker; structural/read/cache mutations retain ordinary invalidation. Reader housekeeping has its own trace. Detail sorting uses the shared vector; volume Back restores its previous TOC context. Remove overwritten badge/string computations without removing the current unread badge.

## Invariants and remaining scope

Keep package/signature/schema3, chapter cache, .36 checkpoint5s/8 changes and barriers, .states generation, profiling toggle,15/14 batch, scope/priority, full-reset and deletion semantics, manual-only configured email. No claim that offline Android fixtures prove website DOM or physical S4 battery savings.

This batch focuses on read-all/origin, metadata invalidation and related UI/diagnostics. Global Pause/manual-full semantics and legacy smoke UI assumptions are not declared fully redesigned by this change; tests and follow-up review must not claim all Gemini findings were implemented. No new retry timer or force-copy cookies is added.

Targeted origin policy tests, actual action-script fixtures and SyncDomainTest cover aliases, sender identity, automatic button-return recovery, stale generation after reset, report state, transactional URL rebase, derived TOC eviction and content/read preservation. Existing Android gate runs before one final ARM32 build. Rollback: revert source batch with a higher versionCode, retaining schema/content and cookies; derived snapshots are rebuildable and extra preference keys may be ignored by old code.
