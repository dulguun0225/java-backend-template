#!/usr/bin/env bash
# The committed jOOQ tree equals what the committed migrations generate, and generation is byte-reproducible:
# regenerate twice under a different timezone and locale, assert both runs identical to each other and to the
# committed tree. Needs Docker (Testcontainers PostgreSQL).
set -euo pipefail
cd "$(dirname "$0")/.."
gen=starter-platform/src/main/java/com/example/starter/db
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
cp -r "$gen" "$work/committed"
mvn -B -ntp -q -Pcodegen -pl starter-platform process-test-classes -Dspotless.check.skip=true
cp -r "$gen" "$work/run1"
TZ=Pacific/Kiritimati LANG=tr_TR.UTF-8 LC_ALL=tr_TR.UTF-8 mvn -B -ntp -q -Pcodegen -pl starter-platform process-test-classes -Dspotless.check.skip=true
cp -r "$gen" "$work/run2"
diff -r "$work/run1" "$work/run2" || { echo "codegen is not byte-reproducible across timezone/locale" >&2; exit 1; }
diff -r "$work/committed" "$work/run1" || { echo "committed jOOQ tree drifted from the migrations; run codegen and commit" >&2; exit 1; }
echo "jOOQ tree matches the migrations and regenerates byte-identically"
