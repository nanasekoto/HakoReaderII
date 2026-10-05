#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
javac -encoding UTF-8 -cp libs/jsoup.jar -d build/test-classes app/src/main/java/vn/nanase/hako/{HakoParser,FetchPolicy,PageBreaks}.java tests/{ParserTest,FixTest}.java
java -cp libs/jsoup.jar:build/test-classes ParserTest
java -cp libs/jsoup.jar:build/test-classes FixTest
javac -encoding UTF-8 -d build/test-classes app/src/main/java/vn/nanase/hako/VolumeChord.java tests/VolumeChordTest.java
java -cp build/test-classes vn.nanase.hako.VolumeChordTest
node --check app/src/main/assets/extract.js

