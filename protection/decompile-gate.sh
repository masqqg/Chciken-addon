#!/usr/bin/env bash
# Decompile gate: fails the build when the protected jar became MORE readable
# than the recorded baseline (i.e. a protection change made analysis easier).
#
#   decompile-gate.sh <decompilers-dir> <baseline.json>
#
# It reads the "after" output that decompile-compare.sh already produced:
#   <dir>/cfr-after, <dir>/vineflower-after
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$1"
BASE="${2:-$ROOT/protection/config/decompile-baseline.json}"

[ -d "$DIR" ] || { echo "no decompiler output: $DIR"; exit 2; }

cfr_failures=$(grep -rc "failed to decompile" "$DIR/cfr-after" 2>/dev/null | awk -F: '{s+=$2} END {print s+0}')
vf_failures=$(grep -rc "Couldn't be decompiled" "$DIR/vineflower-after" 2>/dev/null | awk -F: '{s+=$2} END {print s+0}')

leaked=0
for marker in ChickenStorageESP ClusterBypass ClearRender storage-esp-bypass; do
    hits=$(grep -rl "$marker" "$DIR"/*-after --include='*.java' 2>/dev/null | wc -l | tr -d ' ')
    [ "${hits:-0}" -gt 0 ] && leaked=$((leaked + 1))
done

python3 - "$BASE" "$cfr_failures" "$vf_failures" "$leaked" <<'PY'
import json
import sys

baseline_path, cfr, vf, leaked = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])

with open(baseline_path) as f:
    baseline = json.load(f)

problems = []
if cfr < baseline["cfr_failures_min"]:
    problems.append("CFR decompiled more than the baseline: %d failures < %d required"
                    % (cfr, baseline["cfr_failures_min"]))
if vf < baseline["vineflower_failures_min"]:
    problems.append("Vineflower decompiled more than the baseline: %d failures < %d required"
                    % (vf, baseline["vineflower_failures_min"]))
if leaked > baseline["leaked_identifiers_max"]:
    problems.append("readable module identifiers: %d > %d allowed"
                    % (leaked, baseline["leaked_identifiers_max"]))

print("cfr_failures=%d vineflower_failures=%d leaked_identifiers=%d" % (cfr, vf, leaked))
for problem in problems:
    print("FAIL " + problem)

print("decompile gate passed" if not problems else "decompile gate failed")
sys.exit(0 if not problems else 1)
PY
