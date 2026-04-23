#!/bin/bash
# HexMod NeoForge GameTest runner.
#
# Drives gradle :Neoforge:runGameTest which boots a dedicated server with
# `-Dneoforge.gameTestServer=true`, runs every @GameTest in the hexcasting
# namespace, and exits 0 if all tests pass or non-zero on any failure.
# Cache + JDK-21 detection mirrors scripts/smoketest.sh.
set -u

BOOT_TIMEOUT="${BOOT_TIMEOUT:-600}"
log()  { printf '== %s ==\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Find JDK 21 — NeoForge 21.1.x can't run on 25 (see smoketest.sh).
JDK21=""
for candidate in \
    "${JAVA_HOME_21:-}" \
    "/c/Program Files/Eclipse Adoptium/jdk-21"*"-hotspot" \
    "/c/Program Files/Java/jdk-21"* ; do
    [ -d "$candidate" ] && [ -x "$candidate/bin/java.exe" ] || [ -x "$candidate/bin/java" ] && { JDK21="$candidate"; break; }
done

if [ -n "$JDK21" ]; then
    log "Using JDK 21 at $JDK21"
    export JAVA_HOME="$JDK21"
    export PATH="$JDK21/bin:$PATH"
fi

log "Running :Neoforge:runGameTest (max ${BOOT_TIMEOUT}s)"
# --stacktrace so any test failure surfaces the test name + assertion line.
timeout "$BOOT_TIMEOUT" sh ./gradlew :Neoforge:runGameTest --no-daemon --stacktrace
status=$?

if [ "$status" -eq 124 ]; then
    fail "gametest run exceeded ${BOOT_TIMEOUT}s — killed"
fi
if [ "$status" -ne 0 ]; then
    fail "one or more GameTests failed (gradle exit $status)"
fi

log "PASS: all GameTests green"
