#!/usr/bin/env bash
# Derives the strict mixin keep set from a jar, so no protection layer can
# rename anything a mixin needs.
#
#   mixin-keeplist.sh <jar> <outdir>
#
# Writes:
#   <outdir>/mixin-classes.txt  one mixin class (dotted) per line
#   <outdir>/mixin-keep.txt     one "--keep <pattern>" line per Umbra argument
#
# Helper classes are found by dependency analysis: every com/example/addon/...
# class referenced from the constant pool of a mixin class is kept by name too,
# because @Shadow/@Inject bodies call into them.
set -uo pipefail

JAR="$1"
OUTDIR="$2"
mkdir -p "$OUTDIR"

python3 - "$JAR" "$OUTDIR" <<'PY'
import json
import re
import sys
import zipfile

jar_path, outdir = sys.argv[1], sys.argv[2]
REF = re.compile(rb"com/example/addon/[A-Za-z0-9_$/]+")

mixin_classes = []
with zipfile.ZipFile(jar_path) as z:
    names = z.namelist()
    try:
        meta = json.loads(z.read("fabric.mod.json").decode("utf-8"))
    except KeyError:
        meta = {}

    for cfg_name in meta.get("mixins", []):
        try:
            cfg = json.loads(z.read(cfg_name).decode("utf-8"))
        except KeyError:
            continue
        pkg = cfg.get("package", "").replace(".", "/")
        for key in ("mixins", "client", "server"):
            for entry in cfg.get(key, []):
                internal = (pkg + "/" if pkg else "") + entry.replace(".", "/")
                mixin_classes.append(internal)

    helpers = set()
    for internal in mixin_classes:
        entry = internal + ".class"
        if entry not in names:
            continue
        data = z.read(entry)
        for match in REF.findall(data):
            ref = match.decode("ascii")
            if ref != internal and not ref.startswith(internal + "$"):
                helpers.add(ref)

with open(outdir + "/mixin-classes.txt", "w") as f:
    for internal in sorted(set(mixin_classes)):
        f.write(internal.replace("/", ".") + "\n")

patterns = []
for internal in sorted(set(mixin_classes)):
    dotted = internal.replace("/", ".")
    patterns.append(dotted)
    if "$" not in internal.rsplit("/", 1)[-1]:
        patterns.append(dotted + "$*")
for helper in sorted(helpers):
    patterns.append(helper.replace("/", "."))

with open(outdir + "/mixin-keep.txt", "w") as f:
    for pattern in patterns:
        f.write("--keep\n" + pattern + "\n")

print("mixin classes: %d, keep patterns: %d, helper classes: %d"
      % (len(set(mixin_classes)), len(patterns), len(helpers)))
PY
