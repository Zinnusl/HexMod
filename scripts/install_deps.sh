#!/bin/bash
# Ensure hex's two hard runtime dependencies (kotlin-for-forge, patchouli)
# are present in the target mods/ folder. Sources them from the smoketest
# download cache; pulls from Modrinth if the cache is empty.
#
# Usage: install_deps.sh <target_mods_dir>
set -u

MODS_DIR="${1:?usage: install_deps.sh <target_mods_dir>}"
CACHE_DIR="build/smoketest-cache"
mkdir -p "$CACHE_DIR"

log() { printf '== %s ==\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

fetch_mod() {
    local dest="$1" url="$2"
    if [ ! -f "$dest" ]; then
        log "Fetching $(basename "$dest") from Modrinth"
        curl -sSL --fail -o "$dest.tmp" "$url" && mv "$dest.tmp" "$dest" \
            || fail "download failed: $url"
    fi
}

# Two required-at-runtime deps.
KFF_JAR="$CACHE_DIR/kotlinforforge-5.6.0-neoforge.jar"
PATCHOULI_JAR="$CACHE_DIR/Patchouli-1.21.1-93-NEOFORGE.jar"
fetch_mod "$KFF_JAR"       "https://cdn.modrinth.com/data/ordsPcFz/versions/5Vlx7W4o/kotlinforforge-5.6.0-all.jar"
fetch_mod "$PATCHOULI_JAR" "https://cdn.modrinth.com/data/nU0bVIaL/versions/BIogJv2D/Patchouli-1.21.1-93-NEOFORGE.jar"

# Replace any existing copy so version bumps in the script actually land.
rm -f "$MODS_DIR"/kotlinforforge-*.jar "$MODS_DIR"/Patchouli-*.jar
cp "$KFF_JAR"       "$MODS_DIR/kotlinforforge-5.6.0-all.jar"
cp "$PATCHOULI_JAR" "$MODS_DIR/$(basename "$PATCHOULI_JAR")"
log "Installed deps into $MODS_DIR: kotlin-for-forge 5.6.0, Patchouli 1.21.1-93"
