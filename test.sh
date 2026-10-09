#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
classes=build/test-classes
mkdir -p "$classes"
sources=app/src/main/java/vn/nanase/hako
javac -encoding UTF-8 -cp libs/jsoup.jar -d "$classes" \
  "$sources"/{HakoParser,FetchPolicy,PageBreaks,ShelfPolicy,ShelfPagePolicy,CachePolicy,DownloadLedger,DownloadScope,DownloadPriority,ShelfAnchor,HoldKey,CatalogPolicy,ReadPolicy}.java tests/*.java
classpath="libs/jsoup.jar:$classes"
for test in ParserTest FixTest ShelfFeatureTest; do
  java -cp "$classpath" "$test"
done
for test in CachePolicyTest DownloadLedgerTest DownloadScopeTest DownloadPriorityTest ShelfAnchorTest ReadingRangeTest ShelfPagePolicyTest HoldKeyTest PolicyIntegrityTest; do
  java -cp "$classpath" "vn.nanase.hako.$test"
done
node --check app/src/main/assets/extract.js
node --check app/src/main/assets/gecko-bridge/bridge.js
