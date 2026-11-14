#!/usr/bin/env bash
# Verifies a protected jar against the per-class SHA-256 manifest produced by
# the ASM layer. Any modified, added or missing class fails the check.
#
#   verify-integrity.sh <jar> <manifest>
set -uo pipefail

JAR="$1"
MANIFEST="$2"
[ -f "$JAR" ] || { echo "missing jar: $JAR"; exit 2; }
[ -f "$MANIFEST" ] || { echo "missing manifest: $MANIFEST"; exit 2; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

python3 - "$JAR" "$TMP" <<'PY'
import sys, zipfile, hashlib, os
jar, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if not name.endswith(".class"):
            continue
        data = z.read(name)
        open(os.path.join(out, hashlib.sha256(name.encode()).hexdigest()), "w").write(
            hashlib.sha256(data).hexdigest() + "  " + name + "\n")
PY

cat "$TMP"/* > "$TMP/actual.txt" 2>/dev/null
sort -k2 "$TMP/actual.txt" > "$TMP/actual.sorted"
grep -v '^#' "$MANIFEST" | grep -v '^[[:space:]]*$' | sort -k2 > "$TMP/expected.sorted"

if ! diff -q "$TMP/expected.sorted" "$TMP/actual.sorted" > /dev/null; then
    echo "FAIL integrity mismatch:"
    diff "$TMP/expected.sorted" "$TMP/actual.sorted" | head -10
    exit 1
fi

echo "integrity verified: $(grep -c . "$TMP/expected.sorted") class(es) match the manifest"
