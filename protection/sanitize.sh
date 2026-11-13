#!/usr/bin/env bash
# ASM post-processing pass: renames the class names the obfuscators kept and
# removes SourceFile / SourceDebugExtension / local-variable / line-number /
# method-parameter metadata.
#
#   sanitize.sh <in.jar> <out.jar>
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools/protection"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

ASM_CP="$TOOLS/asm-9.8.jar:$TOOLS/asm-commons-9.8.jar:$TOOLS/asm-tree-9.8.jar"
CLASSES="$ROOT/build/sanitizer"

for jar in "$TOOLS/asm-9.8.jar" "$TOOLS/asm-commons-9.8.jar"; do
    [ -f "$jar" ] || { echo "missing ASM: $jar"; exit 1; }
done

mkdir -p "$CLASSES"
"$JAVAC_BIN" -cp "$ASM_CP" -d "$CLASSES" "$ROOT/protection/Sanitizer.java" || exit 1
"$JAVA_BIN" -cp "$CLASSES:$ASM_CP" Sanitizer "$1" "$2"
