#!/usr/bin/env bash
# Mixin guard: proves that no protection layer changed the mixin setup.
#
#   verify-mixins.sh <input.jar> <output.jar>
#
# Checks, comparing the pre-protection jar with the protected one:
#   - the mixins list in fabric.mod.json is unchanged
#   - every mixin config file still exists
#   - package + client/server/mixins entries in each config are unchanged
#   - every listed mixin class is still present under its original name
#   - a refmap, when declared, still exists
#   - every annotation value (@Mixin targets, @Inject/@Redirect/@ModifyArg/
#     @ModifyVariable/@Overwrite/@Shadow), method and field descriptor of the
#     mixin classes is identical (ASM snapshot diff)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools/protection"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

IN="$1"
OUT="$2"

python3 - "$IN" "$OUT" <<'PY'
import json
import sys
import zipfile

src_jar, out_jar = sys.argv[1], sys.argv[2]
problems = []


def mixin_configs(z):
    try:
        meta = json.loads(z.read("fabric.mod.json").decode("utf-8"))
    except KeyError:
        return []
    return meta.get("mixins", [])


def read_config(z, name):
    try:
        return json.loads(z.read(name).decode("utf-8"))
    except KeyError:
        return None


def entries(cfg):
    pkg = cfg.get("package", "").replace(".", "/")
    names = []
    for key in ("mixins", "client", "server"):
        names += cfg.get(key, [])
    return pkg, names


with zipfile.ZipFile(src_jar) as before, zipfile.ZipFile(out_jar) as after:
    src_list = mixin_configs(before)
    out_list = mixin_configs(after)

    if src_list != out_list:
        problems.append("fabric.mod.json mixin list changed: %s -> %s" % (src_list, out_list))

    for cfg_name in out_list:
        cfg_after = read_config(after, cfg_name)
        if cfg_after is None:
            problems.append("mixin config missing from the protected jar: " + cfg_name)
            continue

        cfg_before = read_config(before, cfg_name)
        pkg_after, names_after = entries(cfg_after)

        if cfg_before is not None:
            pkg_before, names_before = entries(cfg_before)
            if pkg_before != pkg_after or names_before != names_after:
                problems.append("mixin entries changed in %s" % cfg_name)

        for name in names_after:
            path = (pkg_after + "/" if pkg_after else "") + name.replace(".", "/") + ".class"
            if path not in after.namelist():
                problems.append("mixin class missing or renamed: " + path)

        refmap = cfg_after.get("refmap")
        if refmap and refmap not in after.namelist():
            problems.append("refmap missing: " + refmap)

        print("ok %s: package %s, %d mixin entr%s"
              % (cfg_name, pkg_after or "-", len(names_after),
                 "y" if len(names_after) == 1 else "ies"))

for problem in problems:
    print("FAIL " + problem)

print("mixins intact" if not problems else "mixin verification failed")
sys.exit(0 if not problems else 1)
PY

[ $? -eq 0 ] || exit 1

# Annotation-level check: every @Mixin/@Inject/@Shadow/@Redirect value, method
# and field descriptor of the mixin classes has to be identical.
MIXIN_CLASSES="$ROOT/build/mixin-classes.txt"
if [ -s "$MIXIN_CLASSES" ]; then
    ASM_CP="$TOOLS/asm-9.8.jar:$TOOLS/asm-commons-9.8.jar:$TOOLS/asm-tree-9.8.jar"
    CLASSES="$ROOT/build/sanitizer"

    "$JAVAC_BIN" -cp "$ASM_CP" -d "$CLASSES" "$ROOT/protection/MixinFingerprint.java" || exit 1

    NAMES=()
    while IFS= read -r line; do
        [ -n "$line" ] && NAMES+=("$line")
    done < "$MIXIN_CLASSES"

    TMP="$(mktemp -d)"
    "$JAVA_BIN" -cp "$CLASSES:$ASM_CP" MixinFingerprint "$IN" "${NAMES[@]}" | sort > "$TMP/before.txt"
    "$JAVA_BIN" -cp "$CLASSES:$ASM_CP" MixinFingerprint "$OUT" "${NAMES[@]}" | sort > "$TMP/after.txt"

    if diff -u "$TMP/before.txt" "$TMP/after.txt" > "$TMP/diff.txt"; then
        echo "ok annotation snapshot identical for ${#NAMES[@]} mixin class(es)"
    else
        echo "FAIL mixin annotations or members changed:"
        head -40 "$TMP/diff.txt"
        rm -rf "$TMP"
        exit 1
    fi
    rm -rf "$TMP"
fi

exit 0
