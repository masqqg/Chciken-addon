#!/usr/bin/env bash
# Decompiles the input jar and the protected jar with every free decompiler in
# the toolchain and compares how much of the source survives.
#
#   decompile-compare.sh <input.jar> <protected.jar> <outdir>
#
# Writes <outdir>/COMPARISON.md.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools/protection"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

IN="$1"; PROT="$2"; OUT="$3"
mkdir -p "$OUT"

MARKERS=(ChickenStorageESP ClusterBypass ClearRender storage-esp-bypass amethyst geode kelp tracer)
# long user-facing sentences from the modules; prose that must not survive
LITERALS=("packet level leak detection" "sealed air pockets" "amethyst geode" "tall red line")

# decompile <tool> <jar> <outdir>
decompile() {
    local tool="$1" jar="$2" dir="$3"
    mkdir -p "$dir"
    case "$tool" in
        cfr)
            "$JAVA_BIN" -jar "$TOOLS/cfr-0.152.jar" "$jar" --outputdir "$dir" --silent true \
                > "$dir/.log" 2>&1 ;;
        vineflower)
            "$JAVA_BIN" -jar "$TOOLS/vineflower-1.12.0.jar" -dgs=1 "$jar" "$dir" \
                > "$dir/.log" 2>&1 ;;
        procyon)
            "$JAVA_BIN" -jar "$TOOLS/procyon-decompiler-0.6.0.jar" -o "$dir" "$jar" \
                > "$dir/.log" 2>&1 ;;
        jadx)
            "$TOOLS/jadx/bin/jadx" --no-res --no-debug-info -d "$dir" "$jar" \
                > "$dir/.log" 2>&1 ;;
    esac
}

# stats <dir> -> "files lines distinct_markers distinct_literals"
stats() {
    local dir="$1"
    local files lines markers literals m l

    files=$(find "$dir" -name '*.java' 2>/dev/null | wc -l | tr -d ' ')
    lines=$(find "$dir" -name '*.java' -exec cat {} + 2>/dev/null | wc -l | tr -d ' ')

    markers=0
    for m in "${MARKERS[@]}"; do
        grep -rqi -- "$m" "$dir" --include='*.java' 2>/dev/null && markers=$((markers + 1))
    done

    literals=0
    for l in "${LITERALS[@]}"; do
        grep -rqi -- "$l" "$dir" --include='*.java' 2>/dev/null && literals=$((literals + 1))
    done

    echo "$files $lines $markers $literals"
}

ROWS=()
for tool in cfr vineflower procyon jadx; do
    case "$tool" in
        cfr)          [ -f "$TOOLS/cfr-0.152.jar" ] || continue ;;
        vineflower)   [ -f "$TOOLS/vineflower-1.12.0.jar" ] || continue ;;
        procyon)      [ -f "$TOOLS/procyon-decompiler-0.6.0.jar" ] || continue ;;
        jadx)         [ -x "$TOOLS/jadx/bin/jadx" ] || continue ;;
    esac

    echo "== $tool"
    rm -rf "$OUT/$tool-before" "$OUT/$tool-after"
    decompile "$tool" "$IN"   "$OUT/$tool-before"
    decompile "$tool" "$PROT" "$OUT/$tool-after"

    before="$(stats "$OUT/$tool-before")"
    after="$(stats "$OUT/$tool-after")"
    ROWS+=("$tool|$before|$after")
    echo "   before: $before / after: $after"
done

{
    echo "# Decompiler comparison"
    echo
    echo "Input: \`$(basename "$IN")\` - protected: \`$(basename "$PROT")\`"
    echo
    echo "| decompiler | .java before | lines before | markers before | literals before | .java after | lines after | markers after | literals after |"
    echo "| --- | --- | --- | --- | --- | --- | --- | --- | --- |"
    for row in "${ROWS[@]:-}"; do
        IFS='|' read -r tool b a <<< "$row"
        read -r bf bl bm bl2 <<< "$b"
        read -r af al am al2 <<< "$a"
        echo "| $tool | $bf | $bl | $bm/${#MARKERS[@]} | $bl2/${#LITERALS[@]} | $af | $al | $am/${#MARKERS[@]} | $al2/${#LITERALS[@]} |"
    done
    echo
    echo "markers: ${MARKERS[*]}"
    echo
    echo "literals: ${LITERALS[*]}"
    echo
    echo "Markers/literals count *distinct* identifiers and sentences still spelled out"
    echo "in the decompiled source. A drop to 0 means the module names and the"
    echo "user-facing strings no longer survive static decompilation."
} > "$OUT/COMPARISON.md"

echo "wrote $OUT/COMPARISON.md"
