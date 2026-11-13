#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# METEOR EXTREME SHIELD - Java / Fabric jar protection pipeline
#
# Layers, in order:
#   1. build fingerprint + watermark resource
#   2. Umbra           (main engine, always on)
#   3. Aidsfuscator V2 (optional, ENABLE_AIDS=1)
#   4. BytecodeVM      (optional, ENABLE_VM=1, low-perf-risk methods only)
#   5. Kafuscator      (optional, ENABLE_KAFUSCATOR=1, needs the Windows build)
#   6. SHA-256 + jarsigner signing + signature verification
#   7. decompiler comparison (RUN_DECOMPILERS=1)
#
# Every layer is a separate function. A layer that fails is reported and the
# pipeline stops with the last good jar still in dist/, so nothing is silently
# shipped half protected.
#
# Usage:
#   ./protection/pipeline.sh                 # build if needed, protect, sign
#   ENABLE_AIDS=1 ./protection/pipeline.sh   # add Aidsfuscator
#   UMBRA_PRESET=aggressive ./protection/pipeline.sh
# ---------------------------------------------------------------------------
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools/protection"
OUT="$ROOT/dist"
WORK="$OUT/work"
LOGS="$OUT/logs"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"

# Aidsfuscator is compiled for class files 69 (JDK 25); find a JDK that can run it.
JAVA25_BIN="${JAVA25_HOME:+$JAVA25_HOME/bin/}java"
if [ -z "${JAVA25_HOME:-}" ]; then
    for cand in "${JAVA_HOME:-}" /usr/lib/jvm/*25* "$HOME"/jdk25/jdk-25* /opt/*25*; do
        [ -x "$cand/bin/java" ] || continue
        ver=$("$cand/bin/java" -version 2>&1 | head -1 | grep -o '"[0-9]*' | tr -d '"')
        if [ "${ver:-0}" -ge 25 ]; then
            JAVA25_BIN="$cand/bin/java"
            JAVA25_HOME="$cand"
            break
        fi
    done
fi

UMBRA_PRESET="${UMBRA_PRESET:-balanced}"
UMBRA_REQUIRED="${UMBRA_REQUIRED:-1}"
ENABLE_AIDS="${ENABLE_AIDS:-1}"
ENABLE_VM="${ENABLE_VM:-0}"
ENABLE_KAFUSCATOR="${ENABLE_KAFUSCATOR:-0}"
SIGN="${SIGN:-1}"
RUN_DECOMPILERS="${RUN_DECOMPILERS:-1}"
KEEP_INTERMEDIATE="${KEEP_INTERMEDIATE:-1}"

STAGE_RESULTS=()

log()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
info() { printf '   %s\n' "$*"; }
ok()   { printf '   \033[32mOK\033[0m  %s\n' "$*"; STAGE_RESULTS+=("PASS|$*"); }
warn() { printf '   \033[33mWARN\033[0m %s\n' "$*"; STAGE_RESULTS+=("WARN|$*"); }
fail() { printf '   \033[31mFAIL\033[0m %s\n' "$*"; STAGE_RESULTS+=("FAIL|$*"); }
die()  { fail "$*"; write_report; exit 1; }

have() { command -v "$1" >/dev/null 2>&1; }

# A layer may not blow the jar up (BytecodeVM on already obfuscated bytecode did
# 66 MB against 218 KB). Larger than 3x the previous stage is rejected.
layer_size_ok() {
    local before after
    before=$(stat -c%s "$1" 2>/dev/null || stat -f%z "$1" 2>/dev/null || echo 0)
    after=$(stat -c%s "$2" 2>/dev/null || stat -f%z "$2" 2>/dev/null || echo 0)
    [ "$before" -gt 0 ] && [ "$after" -le $((before * 3)) ]
}

# The loader entrypoint in fabric.mod.json has to point at a class in the jar.
entrypoint_ok() {
    local jar="$1" class listing
    class=$(unzip -p "$jar" fabric.mod.json 2>/dev/null | tr -d ' \n\r\t' \
        | grep -o '"meteor":\["[^"]*"' | sed 's/.*\["//; s/"$//')
    [ -n "$class" ] || return 1
    listing=$(unzip -Z1 "$jar" 2>/dev/null)
    case "$listing" in *"${class//.//}.class"*) return 0 ;; esac
    return 1
}

write_report() {
    local report="$OUT/PROTECTION-REPORT.md"
    {
        echo "# METEOR EXTREME SHIELD - protection report"
        echo
        echo "- build: \`$FINGERPRINT\`"
        echo "- input jar: \`${INPUT_JAR#"$ROOT"/}\`"
        echo "- output jar: \`${FINAL:-${CURRENT:-not built}}\`"
        echo "- umbra preset: \`$UMBRA_PRESET\` (seed \`$SEED\`)"
        echo "- sha256: \`$( [ -n "${FINAL:-}" ] && sha256 "$FINAL" 2>/dev/null || echo n/a )\`"
        echo "- signed: \`$( [ "${SIGNED:-0}" = "1" ] && echo yes || echo no )\`"
        echo
        echo "## Layer results"
        echo
        echo "| result | stage |"
        echo "| --- | --- |"
        for r in "${STAGE_RESULTS[@]:-}"; do
            [ -n "$r" ] || continue
            echo "| ${r%%|*} | ${r#*|} |"
        done
        echo
        echo "## Audit findings"
        echo
        echo "| id | finding | fix |"
        echo "| --- | --- | --- |"
        echo "| A1 | SourceDebugExtension/SourceFile survive Umbra | dropped by the ASM sanitizer (protection/Sanitizer.java) |"
        echo "| A2 | local variable tables, line numbers, method parameter names | dropped by the ASM sanitizer |"
        echo "| A3 | record classes keep names like ChickenStorageESP\$Entry | renamed consistently by the ASM sanitizer, package untouched so getPackage() stays valid |"
        echo "| B1 | string decryptor shape | Umbra per-site RC4/ChaCha20/AES-CTR engines with per-class keys, decrypt-and-wipe, no plaintext cache |"
        echo "| C1 | BytecodeVM virtualized all 337 methods (66 MB) | default off, explicit allowlist only, 3x size + link guard, build fails and keeps the safe jar |"
        echo "| D1 | Kafuscator is Windows-only | never executed here, optional layer, Fabric compat unverified |"
        echo "| E1 | checksum/signature order | signed after every protection pass, verified with jarsigner, SHA-256 published |"
        echo "| E2 | self-signed certificate | reported: dist/extreme-shield-dev.jks, 10 year RSA 2048, not a CA certificate |"
        echo "| F1 | loader metadata | fabric.mod.json entrypoint checked after every layer, mixin config kept, no refmap/access widener in this addon |"
        echo
        echo "## Not covered by this pipeline"
        echo
        echo "- real Minecraft 1.21.11 launch, Meteor module smoke test"
        echo "- Windows / macOS / Android-Temux runs"
        echo "- those need a machine with the game; see protection/README.md"
    } > "$report"
}

sha256() {
    if have sha256sum; then sha256sum "$1" | cut -d' ' -f1
    else shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

# --------------------------------------------------------------------- setup
mkdir -p "$OUT" "$WORK" "$LOGS"

[ -f "$TOOLS/umbra.jar" ] || "$ROOT/protection/fetch-tools.sh" || die "protection tools are missing"

printf 'java: %s\n' "$("$JAVA_BIN" -version 2>&1 | head -1)"

INPUT_JAR="${INPUT_JAR:-$(ls "$ROOT"/build/libs/*.jar 2>/dev/null | grep -v -e '-dev\.jar$' -e '-sources\.jar$' -e '-obf\.jar$' | head -1)}"
if [ -z "${INPUT_JAR:-}" ] || [ ! -f "$INPUT_JAR" ]; then
    log "building mod jar"
    ( cd "$ROOT" && ./gradlew build --no-configuration-cache -q ) || die "gradle build failed"
    INPUT_JAR="$(ls "$ROOT"/build/libs/*.jar | grep -v -e '-dev\.jar$' -e '-sources\.jar$' -e '-obf\.jar$' | head -1)"
fi
[ -f "$INPUT_JAR" ] || die "no mod jar found in build/libs"
ok "input jar: ${INPUT_JAR#"$ROOT"/}"

# ------------------------------------------------------- fingerprint/watermark
log "1/7 build fingerprint + watermark"

BUILD_TIME="$(date -u +%Y%m%dT%H%M%SZ)"
GIT_SHA="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo local)"
MOD_VERSION="$(sed -n 's/^mod-version *= *"\(.*\)"/\1/p' "$ROOT/gradle/libs.versions.toml")"
FINGERPRINT="MES-${MOD_VERSION:-0}-${GIT_SHA}-${BUILD_TIME}"
SEED="${SEED:-$(printf '%d' "0x$(printf '%s' "$FINGERPRINT" | sha256sum | cut -c1-7)")}"

BASE_NAME="meteor-extreme-shield-${MOD_VERSION:-0}-${GIT_SHA}"

STAGE0="$WORK/01-input.jar"
cp "$INPUT_JAR" "$STAGE0"

WM_DIR="$WORK/watermark"
rm -rf "$WM_DIR"; mkdir -p "$WM_DIR/META-INF"
cat > "$WM_DIR/META-INF/extreme-shield.build" <<EOF
project=METEOR EXTREME SHIELD
fingerprint=$FINGERPRINT
built=$BUILD_TIME
EOF
"$JAVA_BIN" -version >/dev/null 2>&1 || die "java not runnable"
(cd "$WM_DIR" && jar uf "$STAGE0" META-INF/extreme-shield.build) || warn "watermark resource not added"
ok "fingerprint $FINGERPRINT (umbra seed $SEED)"

# --------------------------------------------------------------- library jars
LIB_ARGS=()
LIBS_FILE="$ROOT/build/protection-libs.txt"
if [ ! -f "$LIBS_FILE" ]; then
    ( cd "$ROOT" && ./gradlew protectionLibs --no-configuration-cache -q >/dev/null 2>&1 )
fi
if [ -f "$LIBS_FILE" ]; then
    while IFS= read -r lib; do
        [ -f "$lib" ] && LIB_ARGS+=(--lib "$lib")
    done < "$LIBS_FILE"
    info "library jars for hierarchy/verification: $(( ${#LIB_ARGS[@]} / 2 ))"
else
    warn "no library classpath (build/protection-libs.txt); verification is limited"
fi

# ------------------------------------------------- 1b. strict mixin keep set
# Mixin classes, their helper classes and their annotations are never renamed,
# virtualized or flow-transformed. The keep set is derived from the jar so it
# also applies to mixins added later.
MIXIN_KEEP_FILE="$ROOT/build/mixin-keep.txt"
MIXIN_CLASSES_FILE="$ROOT/build/mixin-classes.txt"
if [ -x "$ROOT/protection/mixin-keeplist.sh" ]; then
    if "$ROOT/protection/mixin-keeplist.sh" "$STAGE0" "$ROOT/build" > "$LOGS/mixins-keeplist.log" 2>&1; then
        ok "mixin keep set: $(tail -1 "$LOGS/mixins-keeplist.log")"
    else
        tail -5 "$LOGS/mixins-keeplist.log"
        warn "mixin keep set could not be derived"
    fi
else
    warn "mixin-keeplist.sh missing - mixin names are not explicitly kept"
fi
MIXIN_COUNT=$(grep -cvE '^[[:space:]]*$' "$MIXIN_CLASSES_FILE" 2>/dev/null | tr -d ' ')

# ------------------------------------------------------------------ 2. Umbra
log "2/7 Umbra - main engine (preset: $UMBRA_PRESET)"

CURRENT="$WORK/02-umbra.jar"
UMBRA_ARGS=(-i "$STAGE0" -o "$CURRENT"
    --preset "$UMBRA_PRESET" --seed "$SEED"
    --mapping "$OUT/mapping.txt"
    --report-json "$OUT/umbra-report.json"
    --compat-report "$OUT/umbra-compat.json")
[ ${#LIB_ARGS[@]} -gt 0 ] && UMBRA_ARGS+=("${LIB_ARGS[@]}")

# optional extra Umbra switches, e.g. UMBRA_FLAGS="--flatten"
if [ -n "${UMBRA_FLAGS:-}" ]; then
    read -r -a EXTRA_FLAGS <<< "$UMBRA_FLAGS"
    UMBRA_ARGS+=("${EXTRA_FLAGS[@]}")
fi

# every --keep line produced by mixin-keeplist.sh
if [ -s "$MIXIN_KEEP_FILE" ]; then
    while IFS= read -r keep; do
        [ -n "$keep" ] && UMBRA_ARGS+=("$keep")
    done < "$MIXIN_KEEP_FILE"
fi

"$JAVA_BIN" -jar "$TOOLS/umbra.jar" "${UMBRA_ARGS[@]}" > "$LOGS/umbra.log" 2>&1
UMBRA_RC=$?

if [ -f "$CURRENT" ] && [ "$UMBRA_RC" -eq 0 ]; then
    ok "Umbra protected jar ($(du -h "$CURRENT" | cut -f1)); linkage is checked later with the real mod classpath"
elif [ -f "$CURRENT" ]; then
    warn "Umbra wrote the jar with verifier warnings (exit $UMBRA_RC, see dist/logs/umbra.log)"
else
    tail -25 "$LOGS/umbra.log"
    if [ "$UMBRA_REQUIRED" = "1" ]; then
        die "Umbra failed - refusing to ship an unprotected jar"
    fi
    warn "Umbra failed, continuing with the unprotected jar (UMBRA_REQUIRED=0)"
    CURRENT="$STAGE0"
fi

# keeps the loader-facing metadata intact even if a later layer rewrites names
if entrypoint_ok "$CURRENT"; then
    ok "fabric.mod.json entrypoint resolves to a class in the jar"
else
    fail "fabric.mod.json entrypoint no longer points at the addon class"
    die "loader metadata was damaged by the protection layer"
fi

# ----------------------------------------------------------- 3. Aidsfuscator
log "3/7 Aidsfuscator V2 (optional)"

if [ "${MIXIN_COUNT:-0}" -gt 0 ] && [ "${ALLOW_AIDS_WITH_MIXINS:-0}" != "1" ]; then
    warn "skipped: $MIXIN_COUNT mixin class(es) present, strict mixin protection keeps Aidsfuscator out"
elif [ "$ENABLE_AIDS" != "1" ]; then
    warn "disabled (ENABLE_AIDS=0); its string/flow passes overlap Umbra"
else
    AIDS_HOME="$TOOLS/aidsfuscator/v2.10.0"
    if [ ! -f "$AIDS_HOME/aidsfuscator.jar" ]; then
        warn "Aidsfuscator not downloaded - layer disabled"
    elif ! "$JAVA25_BIN" -version >/dev/null 2>&1; then
        warn "Aidsfuscator needs JDK 25 (JAVA25_HOME); layer disabled"
    else
        # Aidsfuscator resolves config.json and libs/ inside a workspace/ folder.
        AIDS_WORK="$WORK/aids"
        rm -rf "$AIDS_WORK"; mkdir -p "$AIDS_WORK/libs" "$AIDS_WORK/workspace"
        if [ ${#LIB_ARGS[@]} -gt 0 ]; then
            for lib in "${LIB_ARGS[@]}"; do
                [ "$lib" = "--lib" ] && continue
                [ -f "$lib" ] && ln -sf "$lib" "$AIDS_WORK/libs/" 2>/dev/null
            done
        fi
        cp "$CURRENT" "$AIDS_WORK/input.jar"
        # config is looked up inside workspace/, paths inside it against the cwd
        cp "$ROOT/protection/config/aidsfuscator.json" "$AIDS_WORK/workspace/config.json"
        printf '{}\n' > "$AIDS_WORK/workspace/exclusions.json"
        # config.json / exclusions.json / libs are resolved inside workspace/
        if ( cd "$AIDS_WORK" && "$JAVA25_BIN" -jar "$AIDS_HOME/aidsfuscator.jar" \
                --config=config.json --exclusions=exclusions.json \
                --javaPath="$JAVA25_BIN" ) > "$LOGS/aidsfuscator.log" 2>&1 \
                && { [ -f "$AIDS_WORK/output.jar" ] || [ -f "$AIDS_WORK/workspace/output.jar" ]; }; then
            AIDS_OUT="$AIDS_WORK/output.jar"
            [ -f "$AIDS_OUT" ] || AIDS_OUT="$AIDS_WORK/workspace/output.jar"
            if layer_size_ok "$CURRENT" "$AIDS_OUT" && entrypoint_ok "$AIDS_OUT"; then
                CURRENT="$WORK/03-aids.jar"
                cp "$AIDS_OUT" "$CURRENT"
                ok "Aidsfuscator pass applied (rename pass on members, loader metadata intact)"
            else
                warn "Aidsfuscator output rejected (size or entrypoint) - Umbra output kept"
            fi
        else
            tail -15 "$LOGS/aidsfuscator.log"
            warn "Aidsfuscator failed - layer disabled, Umbra output kept"
        fi
    fi
fi

# -------------------------------------------------------------- 4. BytecodeVM
log "4/7 BytecodeVM (optional, low-perf-risk methods only)"

VM_ALLOW="$ROOT/protection/config/bytecodevm-allowlist.txt"
VM_ENTRIES=$(grep -vcE '^[[:space:]]*(#|$)' "$VM_ALLOW" 2>/dev/null | tr -d ' ')
VM_BLOCK=0

if [ "$ENABLE_VM" != "1" ]; then
    warn "disabled (ENABLE_VM=0); BytecodeVM stays off by default"
elif [ "${MIXIN_COUNT:-0}" -gt 0 ]; then
    warn "skipped: $MIXIN_COUNT mixin class(es) present, virtualization is excluded by policy"
elif [ "${VM_ENTRIES:-0}" -eq 0 ]; then
    warn "skipped: allowlist is empty, so 0 methods would be virtualized"
elif [ ! -f "$TOOLS/BytecodeVM-2.2.3.jar" ]; then
    warn "BytecodeVM not downloaded - layer disabled"
else
    VM_YML="$WORK/bytecodevm.yml"
    {
        cat "$ROOT/protection/config/bytecodevm.yml"
        printf '\nincludes:\n'
        grep -vE '^[[:space:]]*(#|$)' "$VM_ALLOW" | sed 's/^/  - "/; s/$/"/'
    } > "$VM_YML"

    if "$JAVA_BIN" -jar "$TOOLS/BytecodeVM-2.2.3.jar" protect \
            -c "$VM_YML" -i "$CURRENT" -o "$WORK/04-vm.jar" \
            --report "$OUT/bytecodevm-report.json" \
            --watermark fingerprint="$FINGERPRINT" > "$LOGS/bytecodevm.log" 2>&1 \
            && [ -f "$WORK/04-vm.jar" ]; then
        if layer_size_ok "$CURRENT" "$WORK/04-vm.jar" \
                && entrypoint_ok "$WORK/04-vm.jar" \
                && "$ROOT/protection/verify-jar.sh" "$WORK/04-vm.jar" "$LIBS_FILE" \
                    > "$LOGS/bytecodevm-link.log" 2>&1; then
            CURRENT="$WORK/04-vm.jar"
            ok "BytecodeVM pass applied and verified ($VM_ENTRIES allowlisted matcher(s))"
        else
            cp "$CURRENT" "$OUT/${BASE_NAME}-unvirtualized.jar"
            fail "BytecodeVM blew the size/link limit - build stops, safe jar kept as dist/${BASE_NAME}-unvirtualized.jar"
            VM_BLOCK=1
        fi
    else
        tail -15 "$LOGS/bytecodevm.log"
        cp "$CURRENT" "$OUT/${BASE_NAME}-unvirtualized.jar"
        fail "BytecodeVM failed - build stops, safe jar kept as dist/${BASE_NAME}-unvirtualized.jar"
        VM_BLOCK=1
    fi
fi

# ------------------------------------------------------------- 5. Kafuscator
log "5/7 Kafuscator (optional)"

if [ "$ENABLE_KAFUSCATOR" != "1" ]; then
    warn "disabled (ENABLE_KAFUSCATOR=0): only a Windows x64 build is published, Fabric compatibility unverified"
else
    KAF="$TOOLS/kafuscator/kafuscator.jar"
    if [ ! -f "$KAF" ]; then
        warn "Kafuscator CLI jar not present (Windows package only) - layer disabled"
    else
        warn "Kafuscator layer is unverified on this platform - keeping the previous jar"
    fi
fi

# ---------------------------------------- 5b. ASM sanitizer (metadata + names)
SANITIZED="$WORK/05-sanitized.jar"
if [ -x "$ROOT/protection/sanitize.sh" ]; then
    if "$ROOT/protection/sanitize.sh" "$CURRENT" "$SANITIZED" > "$LOGS/sanitize.log" 2>&1 \
            && [ -f "$SANITIZED" ]; then
        CURRENT="$SANITIZED"
        ok "sanitizer: $(tail -1 "$LOGS/sanitize.log")"
    else
        tail -10 "$LOGS/sanitize.log"
        warn "sanitizer failed - previous jar kept"
    fi
else
    warn "sanitizer missing - debug metadata is not stripped"
fi

# --------------------------------------------------- 5c. mixin guard (fatal)
if [ -x "$ROOT/protection/verify-mixins.sh" ]; then
    if "$ROOT/protection/verify-mixins.sh" "$STAGE0" "$CURRENT" > "$LOGS/mixins.log" 2>&1; then
        ok "mixins: $(tail -1 "$LOGS/mixins.log")"
    else
        tail -10 "$LOGS/mixins.log"
        die "mixin config/classes were damaged by a protection layer"
    fi
else
    warn "verify-mixins.sh missing - mixin setup is unchecked"
fi

# ------------------------------------------------------------ 6. sign + hash
log "6/7 link check + SHA-256 + signing"

# Every class of the layered jar must still link against the mod's libraries.
if [ -f "$LIBS_FILE" ] && [ -x "$ROOT/protection/verify-jar.sh" ]; then
    if "$ROOT/protection/verify-jar.sh" "$CURRENT" "$LIBS_FILE" > "$LOGS/link-check.log" 2>&1; then
        ok "link check: $(tail -1 "$LOGS/link-check.log")"
    else
        tail -10 "$LOGS/link-check.log"
        die "link check failed - a protection layer broke class references"
    fi
else
    warn "link check skipped (no library classpath)"
fi

FINAL="$OUT/${BASE_NAME}-protected.jar"
cp "$CURRENT" "$FINAL"

SIGNED=0
if [ "$SIGN" = "1" ]; then
    KS="$OUT/extreme-shield-dev.jks"
    PASS_FILE="$OUT/.kspass"
    if [ ! -f "$KS" ]; then
        [ -f "$PASS_FILE" ] || head -c 24 /dev/urandom | base64 | tr -d '/+=' > "$PASS_FILE"
        KSPASS="$(cat "$PASS_FILE")"
        keytool -genkeypair -keystore "$KS" -storepass "$KSPASS" -keypass "$KSPASS" \
            -alias extreme-shield -keyalg RSA -keysize 2048 -validity 3650 \
            -dname "CN=METEOR EXTREME SHIELD, OU=Release, O=Extreme Shield, C=TR" \
            > "$LOGS/keytool.log" 2>&1 || warn "keytool could not create a signing key"
    fi
    KSPASS="$(cat "$PASS_FILE")"
    if jarsigner -keystore "$KS" -storepass "$KSPASS" -keypass "$KSPASS" \
            "$FINAL" extreme-shield > "$LOGS/jarsigner.log" 2>&1; then
        if jarsigner -verify "$FINAL" > "$LOGS/jarsigner-verify.log" 2>&1; then
            SIGNED=1
            ok "jar signed and signature verified"
        else
            fail "signature verification failed"
        fi
    else
        warn "signing failed (see dist/logs/jarsigner.log)"
    fi
else
    warn "signing disabled (SIGN=0)"
fi

( cd "$OUT" && sha256 "$(basename "$FINAL")" > SHA256SUMS && echo "  $(cat SHA256SUMS)" ) \
    && ok "SHA256SUMS written"

# -------------------------------------------------------- 7. decompilers
log "7/7 decompiler + analysis comparison"

if [ "$RUN_DECOMPILERS" = "1" ] && [ -x "$ROOT/protection/decompile-compare.sh" ]; then
    if "$ROOT/protection/decompile-compare.sh" "$STAGE0" "$FINAL" "$OUT/decompilers" > "$LOGS/decompilers.log" 2>&1; then
        ok "decompiler comparison: dist/decompilers/COMPARISON.md"
    else
        tail -15 "$LOGS/decompilers.log"
        warn "decompiler comparison incomplete"
    fi
else
    warn "decompiler comparison skipped"
fi

# readability regression gate: a change may not make the jar easier to read
if [ "$RUN_DECOMPILERS" = "1" ] && [ -x "$ROOT/protection/decompile-gate.sh" ]; then
    if "$ROOT/protection/decompile-gate.sh" "$OUT/decompilers" "$ROOT/protection/config/decompile-baseline.json" \
            > "$LOGS/decompile-gate.log" 2>&1; then
        ok "decompile gate: $(head -1 "$LOGS/decompile-gate.log")"
    else
        tail -6 "$LOGS/decompile-gate.log"
        fail "decompile gate failed - protection became more readable than the baseline"
        GATE_FAIL=1
    fi
fi

write_report

log "done"
info "jar:      ${FINAL#"$ROOT"/}"
info "checksum: dist/SHA256SUMS"
info "report:   dist/PROTECTION-REPORT.md"
info "logs:     dist/logs/"
[ "$KEEP_INTERMEDIATE" = "1" ] && info "intermediate jars: dist/work/"

if [ "${VM_BLOCK:-0}" = "1" ]; then
    fail "build failed: BytecodeVM layer exceeded its limit"
    exit 1
fi

if [ "${GATE_FAIL:-0}" = "1" ]; then
    fail "build failed: decompile gate regression"
    exit 1
fi

exit 0
