# METEOR EXTREME SHIELD — jar protection pipeline

One command turns the built Fabric addon jar into a protected, signed release jar:

```bash
./protection/pipeline.sh
```

Everything lands in `dist/`:

| file | what it is |
| --- | --- |
| `meteor-extreme-shield-<version>-<sha>-protected.jar` | the release jar |
| `SHA256SUMS` | SHA-256 of the release jar |
| `PROTECTION-REPORT.md` | per-layer result table for this build |
| `mapping.txt` | Umbra name mapping (needed to read obfuscated stack traces) |
| `umbra-report.json`, `umbra-compat.json` | Umbra audit + layout report |
| `decompilers/COMPARISON.md` | CFR / Vineflower / Procyon / JADX before-vs-after |
| `logs/` | one log per layer |

## Layers

| # | layer | state | why |
| --- | --- | --- | --- |
| 1 | build fingerprint + watermark | on | build id, git sha, seed written into `META-INF/extreme-shield.build`; Umbra also embeds its own RSA-blind watermark |
| 2 | **Umbra 3.4.0** | **on, required** | main engine: rename, per-site string encryption, flow hardening, anti-LLM noise, Minecraft mod-container adaptation |
| 3 | Aidsfuscator v2.10.0 | **on** (default) | member renaming, local names, line-number removal; class renaming is off so the loader entrypoint and `MeteorAddon#getPackage()` stay valid |
| 4 | BytecodeVM 2.2.3 | evaluated, off | safe matcher config virtualizes 0 methods; unrestricted it virtualizes all 337 methods and grows the jar from 218 KB to 66 MB, so it is rejected by the size/link guard |
| 5 | Kafuscator 1.2.0 | unavailable here | the release is a Windows x64 jpackage app (`.exe` + Windows runtime), there is no Linux/macOS build to run and Fabric compatibility is unverified |
| 6 | SHA-256 + `keytool`/`jarsigner` | on | free JDK signing, signature verified after signing |
| 7 | decompiler comparison | on | CFR, Vineflower, Procyon, JADX on the input and the protected jar |

Caesium is not used anywhere.

## Fabric safety

Umbra detects the `fabric.mod.json` + mixin container and, by default for mods:

- keeps the Meteor entrypoint class `com.example.addon.AddonTemplate` and rewrites loader metadata only when needed,
- keeps mixin configs, mixin classes, targets and target members,
- disables archive class-encryption, standalone integrity seal, native code, reference hiding and resource rewriting,
- keeps package names because `MeteorAddon#getPackage()` returns `com.example.addon` as a string.

The pipeline checks the entrypoint in `fabric.mod.json` after every layer and aborts if it was damaged.

## Environment switches

| variable | default | meaning |
| --- | --- | --- |
| `UMBRA_PRESET` | `balanced` | `light`, `balanced`, `aggressive`, `maximum`, `nocturne` |
| `UMBRA_REQUIRED` | `1` | fail instead of shipping an unprotected jar |
| `ENABLE_AIDS` | `1` | Aidsfuscator second pass (needs a JDK 25, auto-detected) |
| `ENABLE_VM` | `0` | BytecodeVM virtualization pass (auto-rejected when it exceeds 3x size or fails the link check) |
| `ENABLE_KAFUSCATOR` | `0` | Kafuscator pass |
| `SIGN` | `1` | `keytool` + `jarsigner` |
| `RUN_DECOMPILERS` | `1` | decompiler comparison |
| `SEED` | derived | Umbra seed, must be a number |
| `JAVA_HOME`, `JAVA25_HOME` | — | JDK used to run the tools |

Tools are downloaded once into `tools/protection/` by `protection/fetch-tools.sh`
(pinned versions, all free: Umbra MIT, Aidsfuscator free, BytecodeVM MIT,
Kafuscator GPL, CFR/Vineflower/Procyon/JADX free).

## Measured on the current build

`dist/decompilers/COMPARISON.md` for build `MES-0.1.0-...`:

| metric | before | after |
| --- | --- | --- |
| module names / keywords still readable (of 8) | 7 | 2 |
| long user-facing sentences still readable (of 4) | 2 | 0 |
| Umbra output verifier | — | clean |

Known remaining leak: the private nested records (`ChickenStorageESP$Entry`,
`$EntityBox`, `$TracerTarget`) keep their record names because Umbra preserves
record component metadata; the module classes themselves are renamed (`a`, `b`, `c`).

## What this pipeline cannot test here

The build machine has no Minecraft installation and no display, so these remain
manual checks on a real client:

- Minecraft 1.21.11 launch with Meteor and this addon,
- `clear-render`, `cluster-bypass`, `storage-esp-bypass` in-game behaviour,
- mixin injection at runtime (the addon currently registers no mixins),
- Windows, macOS and Android/Termux runs.

If a layer turns out to break the game, disable that layer with its switch
above; Umbra stays as the required engine.
