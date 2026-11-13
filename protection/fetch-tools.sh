#!/usr/bin/env bash
# Downloads the free protection tooling used by pipeline.sh, with pinned
# versions so a build is reproducible. Everything lands in tools/protection/
# which is git-ignored.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools/protection"
mkdir -p "$TOOLS"
cd "$TOOLS"

get() { # get <url> <file>
    [ -f "$2" ] && { echo "have $2"; return 0; }
    echo "fetch $2"
    curl -sSL -o "$2" "$1" || { echo "failed: $1"; rm -f "$2"; return 1; }
}

# --- protection engines (all free, no license) -----------------------------
get "https://github.com/Deobff/umbra/releases/download/v3.4.0/umbra.jar" umbra.jar || exit 1
get "https://github.com/NHCM-dev/BytecodeVM/releases/download/2.2.3/BytecodeVM-2.2.3.jar" BytecodeVM-2.2.3.jar || exit 1

if [ ! -f aidsfuscator/v2.10.0/aidsfuscator.jar ]; then
    get "https://github.com/LvStrnggg/aidsfuscator/releases/download/v2.10.0/v2.10.0.zip" aids.zip \
        && unzip -oq aids.zip -d aidsfuscator && rm -f aids.zip
fi

# --- ASM used by the sanitizer pass ----------------------------------------
get "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.8/asm-9.8.jar" asm-9.8.jar || exit 1
get "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.8/asm-commons-9.8.jar" asm-commons-9.8.jar || exit 1
get "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.8/asm-tree-9.8.jar" asm-tree-9.8.jar || exit 1

# --- decompilers used by decompile-compare.sh ------------------------------
get "https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar" cfr-0.152.jar || true
get "https://github.com/Vineflower/vineflower/releases/download/1.12.0/vineflower-1.12.0.jar" vineflower-1.12.0.jar || true
get "https://github.com/mstrobel/procyon/releases/download/v0.6.0/procyon-decompiler-0.6.0.jar" procyon-decompiler-0.6.0.jar || true

if [ ! -x jadx/bin/jadx ]; then
    get "https://github.com/skylot/jadx/releases/download/v1.5.6/jadx-1.5.6.zip" jadx.zip \
        && unzip -oq jadx.zip -d jadx && rm -f jadx.zip && chmod +x jadx/bin/jadx
fi

echo "tools ready in $TOOLS"
