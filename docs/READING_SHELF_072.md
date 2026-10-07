# Gecko 0.7.2: reading and shelf changes

Base: main / 716a2ec (Gemini Gecko build confirmed by the owner on Xteink S4).

- TOC exposes a labelled resume/start button; existing exact-position resume is retained.
- Recent books expose a manual Completed checkbox next to download. Checking it enables full retention and requests all missing chapters. The app never infers completion; unchecking does not delete downloads or disable full retention.
- Shelf parsing records latest chapter ID/title and, when available, an absolute update time. A catalog snapshot is written only after successful catalog extraction. Identical nonempty shelf keys skip catalog fetches during shelf sync; missing files still download.
- Pagination can stop at an old page only with explicitly selected descending update order, valid descending timestamps, and an earlier full-sync watermark. Partial imports merge into existing shelf membership; they do not delete unseen books. Missing metadata falls back to all pages/catalogs. A small update count alone does not justify skipping pages.
- Favorites distinguish local unread chapters from chapters added at the last actual catalog refresh. Initial catalog import does not count all existing chapters as newly published.
- Reaching the last page marks that chapter read. Once every local chapter is read, the durable read-all queue is created. Explicit read-all requests also reject remaining local unread chapters. The existing fresh catalog validation runs before submitting to Hako; arrivals block submission and offline/network failures retain the queue. Verification and downloads now share the busy lock. Cleanup/profile code is unchanged.
- Hako's mark-all endpoint is not atomic with catalog checking: a publication during the network gap cannot be absolutely prevented. The existing post-submit refresh keeps such chapters locally unread.

Scope: no browser engine, cookie/profile, extraction bridge, or reader rendering changes.
Validation: 22 focused Java shelf/parser cases; one release APK build. No emulator or broad suite.
Review: compare feature/reading-shelf-update against main; focus on Repository, Store, ReadSync, shelf parsing and the two UI controls.
Version: vn.nanase.hako, code 18, 0.7.2-Gecko; existing signing configuration retained.
