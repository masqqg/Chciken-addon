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
set -uo pipefail

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
