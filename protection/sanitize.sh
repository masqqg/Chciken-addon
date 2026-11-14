#!/usr/bin/env bash
# ASM custom protection layer: renames the class names the obfuscators kept,
# removes SourceFile / SourceDebugExtension / local-variable / line-number /
# method-parameter metadata, verifies every rewritten class with ASM, analyses
# reflective class-name strings and writes a per-class integrity manifest.
#
#   sanitize.sh <in.jar> <out.jar> [report.txt]
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools/protection"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

ASM_CP="$TOOLS/asm-9.8.jar:$TOOLS/asm-commons-9.8.jar:$TOOLS/asm-tree-9.8.jar:$TOOLS/asm-util-9.8.jar:$TOOLS/asm-analysis-9.8.jar"
CLASSES="$ROOT/build/sanitizer"

for jar in "$TOOLS/asm-9.8.jar" "$TOOLS/asm-commons-9.8.jar" "$TOOLS/asm-util-9.8.jar" "$TOOLS/asm-analysis-9.8.jar"; do
    [ -f "$jar" ] || { echo "missing ASM: $jar"; exit 1; }
done

mkdir -p "$CLASSES"
"$JAVAC_BIN" -cp "$ASM_CP" -d "$CLASSES" "$ROOT/protection/Sanitizer.java" || exit 1
"$JAVA_BIN" -cp "$CLASSES:$ASM_CP" Sanitizer "$1" "$2" "${3:-}"
