#!/usr/bin/env bash
# Offline link check for a mod jar: loads every class against the mod's
# library classpath, without launching Minecraft.
#
#   verify-jar.sh <jar> [libs-file]
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

JAR="$1"
LIBS_FILE="${2:-$ROOT/build/protection-libs.txt}"
CLASSES="$ROOT/build/verify-classes"

[ -f "$JAR" ] || { echo "no jar: $JAR"; exit 1; }
[ -f "$LIBS_FILE" ] || { echo "no libs file: $LIBS_FILE"; exit 1; }

mkdir -p "$CLASSES"
"$JAVAC_BIN" -d "$CLASSES" "$ROOT/protection/VerifyJar.java" || exit 1

# bash 3.2 (macOS) has no mapfile, so read the classpath line by line.
LIBS=()
while IFS= read -r lib; do
    [ -n "$lib" ] && LIBS+=("$lib")
done < "$LIBS_FILE"

"$JAVA_BIN" -cp "$CLASSES" VerifyJar "$JAR" "${LIBS[@]}"
