#!/bin/bash
# HexMod dedicated-server boot smoke test.
#
# Installs a NeoForge 21.1.x server into build/smoketest/, drops the
# hex jar into its mods/ folder, boots with a flat-world config, watches
# the log for "Done (" (= mod loading + world gen complete), then issues
# /stop via stdin. Exits 0 if the server reached Done and no exceptions
# appeared in the log; exits 1 otherwise with the relevant log tail.
#
# Usage:
#   scripts/smoketest.sh                # boot + check
#   SMOKE_VERBOSE=1 scripts/smoketest.sh # also echo full log on pass
#
# Env:
#   NEOFORGE_VERSION  default 21.1.81 (matches the dev dep)
#   SMOKE_DIR         default build/smoketest
#   BOOT_TIMEOUT      default 300 seconds (world-gen can be slow on first boot)

set -u

NEOFORGE_VERSION="${NEOFORGE_VERSION:-21.1.81}"
SMOKE_DIR="${SMOKE_DIR:-build/smoketest}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-300}"
MODS_DIR="$SMOKE_DIR/mods"
BOOT_LOG="$SMOKE_DIR/boot.out"

log()  { printf '== %s ==\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; [ -f "$BOOT_LOG" ] && { printf -- '--- last 60 lines of boot.out ---\n' >&2; tail -60 "$BOOT_LOG" >&2; }; exit 1; }

# --- locate the NeoForge installer ------------------------------------------
INSTALLER=$(find "$HOME/.gradle/caches/modules-2" -name "neoforge-${NEOFORGE_VERSION}-installer.jar" 2>/dev/null | head -1)
[ -n "$INSTALLER" ] || INSTALLER=$(find /c/Users/zarax/.gradle/caches/modules-2 -name "neoforge-${NEOFORGE_VERSION}-installer.jar" 2>/dev/null | head -1)
[ -n "$INSTALLER" ] || fail "neoforge-${NEOFORGE_VERSION}-installer.jar not in gradle caches; run 'make build' once so gradle fetches it"

# --- locate the hex jar -----------------------------------------------------
JAR=$(ls -1 Neoforge/build/libs/hexcasting-neoforge-*.jar 2>/dev/null \
      | grep -Ev -- '-(dev|sources|dev-shadow|shadow|all|javadoc)\.jar$' \
      | head -1)
[ -n "$JAR" ] || fail "hex jar missing — run 'make build' first"

# --- install the server once ------------------------------------------------
mkdir -p "$SMOKE_DIR" "$MODS_DIR"
if [ ! -f "$SMOKE_DIR/run.sh" ] && [ ! -f "$SMOKE_DIR/run.bat" ]; then
    log "Installing NeoForge ${NEOFORGE_VERSION} server into $SMOKE_DIR"
    ( cd "$SMOKE_DIR" && java -jar "$INSTALLER" --installServer ) || fail "NeoForge installer failed"
fi

# --- minimal config ---------------------------------------------------------
echo "eula=true" > "$SMOKE_DIR/eula.txt"
cat > "$SMOKE_DIR/server.properties" <<'EOF'
level-name=smoketest-world
online-mode=false
view-distance=2
simulation-distance=2
spawn-protection=0
motd=HexMod smoke test
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
level-type=minecraft\:flat
EOF

# --- swap in the fresh jar --------------------------------------------------
rm -f "$MODS_DIR"/hexcasting-*.jar
cp "$JAR" "$MODS_DIR/"
log "Using jar $(basename "$JAR")"

# Kotlin for Forge: hex declares kotlinforforge as its ModLoader, so boot fails
# without this jar next to hex. The gradle cache ships a compile-time variant
# that *does not* contain the JiJ'd kfflang/kffmod, so we fetch the real mod
# jar from Modrinth once and cache it under build/smoketest-cache/.
CACHE_DIR="build/smoketest-cache"
mkdir -p "$CACHE_DIR"
KFF_JAR="$CACHE_DIR/kotlinforforge-5.6.0-neoforge.jar"
KFF_URL="https://cdn.modrinth.com/data/ordsPcFz/versions/5Vlx7W4o/kotlinforforge-5.6.0-all.jar"
if [ ! -f "$KFF_JAR" ]; then
    log "Fetching Kotlin-for-Forge 5.6.0 (neoforge) from Modrinth"
    curl -sSL --fail -o "$KFF_JAR.tmp" "$KFF_URL" && mv "$KFF_JAR.tmp" "$KFF_JAR" \
        || fail "download failed: $KFF_URL"
fi
rm -f "$MODS_DIR"/kotlinforforge-*.jar
cp "$KFF_JAR" "$MODS_DIR/$(basename "$KFF_JAR")"
log "Bundled dep $(basename "$KFF_JAR")"

# Optional dependencies hex advertises in mods.toml. Pulling these into the
# smoketest clears the remaining 3 warnings about missing optional-mod content
# (patchouli guide_book item, patchouli:shapeless_book_recipe serializer,
# farmersdelight:skillet in pride_colorizer_pansexual).
fetch_mod() {
    local dest="$1" url="$2"
    if [ ! -f "$dest" ]; then
        log "Fetching $(basename "$dest") from Modrinth"
        curl -sSL --fail -o "$dest.tmp" "$url" && mv "$dest.tmp" "$dest" \
            || fail "download failed: $url"
    fi
}
PATCHOULI_JAR="$CACHE_DIR/Patchouli-1.21.1-93-NEOFORGE.jar"
fetch_mod "$PATCHOULI_JAR" "https://cdn.modrinth.com/data/nU0bVIaL/versions/BIogJv2D/Patchouli-1.21.1-93-NEOFORGE.jar"
rm -f "$MODS_DIR"/Patchouli-*.jar "$MODS_DIR"/FarmersDelight-*.jar
cp "$PATCHOULI_JAR" "$MODS_DIR/$(basename "$PATCHOULI_JAR")"
log "Bundled optional dep: patchouli"

# --- pick a run script ------------------------------------------------------
# NeoForge installs run.sh (Unix) and run.bat (Windows) side by side. When the
# JDK is the Windows build (Temurin etc. under mingw/MSYS), the Unix classpath
# in run.sh won't parse — always prefer run.bat on Windows.
IS_WINDOWS=0
case "${OS:-}${WINDIR:-}" in *indows*|*\\*) IS_WINDOWS=1 ;; esac
case "$(uname -s 2>/dev/null)" in MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;; esac

# NeoForge 21.1.x requires JDK 21 (ASM 9.7 bundled by modlauncher can't read the
# Java 25 class files in a JDK 25 runtime). Find a JDK 21 and force it onto PATH
# for the subshell that runs the server — the system default might be JDK 25.
JDK21=""
for candidate in \
    "${JAVA_HOME_21:-}" \
    "/c/Program Files/Eclipse Adoptium/jdk-21"*"-hotspot" \
    "/c/Program Files/Java/jdk-21"* ; do
    [ -d "$candidate" ] && [ -x "$candidate/bin/java.exe" ] || [ -x "$candidate/bin/java" ] && { JDK21="$candidate"; break; }
done
if [ -n "$JDK21" ]; then
    log "Using JDK 21 at $JDK21"
else
    log "WARN: no JDK 21 located — hoping PATH java is version 21"
fi

if [ "$IS_WINDOWS" = "1" ] && [ -f "$SMOKE_DIR/run.bat" ]; then
    # cmd resolves relative names against CWD but not via PATH; the .\ prefix
    # forces "current dir, run.bat" so we bypass PATHEXT quirks under mingw.
    RUN=( cmd //c '.\\run.bat nogui' )
elif [ -f "$SMOKE_DIR/run.sh" ]; then
    RUN=( sh ./run.sh nogui )
elif [ -f "$SMOKE_DIR/run.bat" ]; then
    RUN=( cmd //c '.\\run.bat nogui' )
else
    fail "neither run.sh nor run.bat present in $SMOKE_DIR"
fi

# --- boot -------------------------------------------------------------------
# Note: piping "stop\n" through a FIFO or bash pipe works on Linux but hits
# "Invalid handle" on Windows because run.bat → cmd.exe reassigns stdin
# mid-process. Instead, launch with /dev/null stdin and SIGKILL the JVM once
# "Done (" appears — we only care whether mod loading succeeded, not a
# clean shutdown.
log "Booting (max ${BOOT_TIMEOUT}s)"
: > "$BOOT_LOG"
(
    cd "$SMOKE_DIR"
    if [ -n "$JDK21" ]; then
        export JAVA_HOME="$JDK21"
        export PATH="$JDK21/bin:$PATH"
    fi
    "${RUN[@]}" < /dev/null
) > "$BOOT_LOG" 2>&1 &
SERVER_PID=$!

DEADLINE=$(( $(date +%s) + BOOT_TIMEOUT ))
STATUS=timeout
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        STATUS=early_exit
        break
    fi
    if grep -q 'Done (' "$BOOT_LOG" 2>/dev/null; then
        log "Server reached Done; killing (graceful /stop is flaky under cmd.exe stdin)"
        STATUS=done_reached
        break
    fi
    sleep 2
done

# Kill the whole tree: subshell PID, cmd.exe, the server JVM (BootstrapLauncher).
if [ "$IS_WINDOWS" = "1" ]; then
    for pid in $(jps 2>/dev/null | awk '/BootstrapLauncher/{print $1}'); do
        taskkill //F //PID "$pid" >/dev/null 2>&1 || true
    done
fi
kill -9 "$SERVER_PID" 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true

# --- verdict ----------------------------------------------------------------
case "$STATUS" in
    done_reached) log "Server booted cleanly" ;;
    early_exit)   fail "server exited before reaching Done" ;;
    timeout)      fail "server did not reach Done within ${BOOT_TIMEOUT}s" ;;
esac

# Stderr/exception scan. Vanilla & deps emit the word "error" in several
# harmless contexts, so match only the patterns that genuinely indicate a
# mod-loading or registry failure.
BAD_PATTERN='NoClassDefFoundError|ClassNotFoundException|NullPointerException|MOD LOADING ERROR|Failed to load registries|Mixin apply failed|Unable to load registries|Encountered an exception while loading|Parsing error loading recipe hexcasting:|Couldn'"'"'t parse element.*:hexcasting:'
if grep -qE "$BAD_PATTERN" "$BOOT_LOG"; then
    log "Exceptions detected in boot log"
    grep -nE "$BAD_PATTERN" "$BOOT_LOG" | head -30
    echo "--- surrounding context (first 3 hits) ---"
    grep -nE "$BAD_PATTERN" "$BOOT_LOG" | head -3 | awk -F: '{print $1}' | while read -r line; do
        sed -n "$((line-2)),$((line+8))p" "$BOOT_LOG"
        echo "---"
    done
    exit 1
fi

[ "${SMOKE_VERBOSE:-0}" = "1" ] && cat "$BOOT_LOG"
log "PASS: server reached Done and no boot exceptions"
exit 0
