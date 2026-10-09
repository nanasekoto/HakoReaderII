# Hako Pocket maintenance instructions

Read README.md, docs/OPERATING_RULES.md and docs/ARCHITECTURE_INVARIANTS.md before changing behavior. Current main consolidates the accepted 0.7.15 Gecko implementation.

- Preserve vn.nanase.hako, the existing signing identity, SQLite schema v3 and chapter cache IDs/paths. Do not remove user data as a fix.
- Keep Gecko isolated; native offline reading must not initialize a browser engine.
- Shelf import is metadata synchronization, not permission to download every followed book.
- Apply download scope before priority; include the read-in-app condition for followed/favorite to-end downloads.
- Reuse valid text even when images are missing. Do not replace validation with file-size or ready-only checks.
- Preserve old catalog entries and reading positions when server chapters disappear.
- UI adapters use prepared worker snapshots. Reserve status space; do not change list geometry when progress appears.
- Resume queued work after the busy owner releases it. Do not silently drop update requests.
- Verify remote read-all conservatively; distinguish local completion from user-marked completed novels.
- Use version.properties for application versions and gecko-version.txt for the pinned engine.
- Run bash test.sh and the relevant Android build. Use manual emulator tests when Android/UI/IPC changes require them. Never claim JVM or emulator results prove physical S4 behavior.
- Work on a feature/fix branch for future changes unless the user explicitly authorizes main.
