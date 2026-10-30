# DECISIONS.md — Mohammad (`ir.mohammad.vpn`)

ADR-style record. Each entry is durable: it changes only when the operator overturns it. Dates are UTC.

---

## D-01 — Identity: name and package
**Date:** 2026-10-30 · **Source:** operator approval #1
App name **Mohammad**; applicationId **`ir.mohammad.vpn`**. Irreversible after first public release (Play/keystore identity). No placeholder or `com.example` id anywhere in the tree.

## D-02 — v1 scope
**Date:** 2026-10-30 · **Source:** approval #2
v1 contains only: Connect/Disconnect, tunnel state, connection log, profile selection, ping test, always-on declaration, encrypted operator bundle.
Explicitly excluded: subscription management, QR import, manual profile editor, routing editor, per-app proxy, widgets, Quick Settings tile, Tasker automation, remote config fetch, release signing, Persian UI (resource structure only).

## D-03 — Both cores ship in v1
**Date:** 2026-10-30 · **Source:** approval #3
Xray-core (all non-WireGuard protocols) **and** amneziawg-go (WireGuard + AWG obfuscation) are both bundled. Accepted cost: larger APK, longer first build.

## D-04 — Configuration delivery
**Date:** 2026-10-30 · **Source:** approval #4
A single local encrypted bundle at `app/src/main/assets/operator/bundle.enc`. No remote fetch in v1. Therefore no `INTERNET`-based config sync, no auth flow, no update channel.

## D-05 — No device available
**Date:** 2026-10-30 · **Source:** approval #5
No physical device and no `/dev/kvm`. Consequences:
- `connectedDebugAndroidTest`, `installDebug`, `am start`, cold-start timing, and any "real tunnel" proof are **`NOT VERIFIED (no device)`** until a device is attached.
- Device evidence will never be fabricated. A conditional AC reported on a device must include the command and its literal output.
- Unit/integration/Compose tests are the primary evidence in the meantime.

## D-06 — Debug signing only
**Date:** 2026-10-30 · **Source:** approval #6
Builds use the Android debug keystore. No release keystore is created, requested, or stored. `assembleRelease` is out of scope for v1.

## D-07 — Hysteria2 subset
**Date:** 2026-10-30 · **Source:** approval #7 + Phase 0 finding
Xray-core `v26.10.29` ships `proxy/hysteria` with a minimal surface (`ClientConfig{server}`, `ServerConfig{users}`). v1 supports **only** what that surface accepts. `obfs`, `bandwidth`, `up/down`, and Salamander obfuscation are **not** implemented. Validation rejects unknown keys with a typed error instead of silently forwarding them.
Revisit only if the operator explicitly requests it and a verified endpoint proves support.

## D-08 — Upstream pins
**Date:** 2026-10-30 · **Source:** approval #8, SHAs read with `git ls-remote`

| Dependency | Tag | Commit | License | Required Go |
|---|---|---|---|---|
| XTLS/Xray-core | `v26.10.29` | `0afec66085a85191e9dd2a4a6646c7c9a94688d1` | MPL-2.0 | 1.27 |
| amnezia-vpn/amneziawg-go | `v3.1.20260828` | `b5928efb6ca19f0153958460c3d141f04abc5c2e` | MIT | 1.25.0 (module `.../v3`) |

Both tag and SHA appear in `scripts/` and in `core-*/build.sh`; the build fails if the checked-out HEAD differs from the recorded SHA. MPL-2.0 obligations: modifications to Xray files, if any, must be published under MPL-2.0; the app ships it unmodified.

## D-09 — ABI policy
**Date:** 2026-10-30 · **Source:** approval #9
Debug builds: `arm64-v8a` + `x86_64`. `armeabi-v7a` is added only on an explicit release request (it roughly doubles native build time and APK size).

## D-10 — Build-once core artifacts
**Date:** 2026-10-30 · **Source:** approval #10
Each core is built once into an `.aar`, its SHA-256 recorded in `core-*/artifacts/CHECKSUMS.txt`, and reused. Gradle only invokes `gomobile` when `-PbuildCores=true`. This keeps incremental builds possible on a low-RAM host and makes the native layer reproducible.

## D-11 — Bundle encryption model (trade-off, documented)
**Date:** 2026-10-30
The bundle is encrypted with AES-256-GCM; the key is derived (PBKDF2-HMAC-SHA256, 210k iterations) from `MOHAMMAD_OPERATOR_BUNDLE_PASSWORD`, injected at build time for debug builds.
**Honest limitation:** the app must be able to derive the key at runtime, so this is obfuscation against casual extraction, not protection against an attacker who reverse-engineers the APK. It does keep the profile out of the APK's plaintext assets, out of logs, and out of backups.
**Planned replacement (v2):** per-device Keystore key + one-time provisioning, or server-side config delivery with per-install tokens.
Nothing in this model permits the password, UUID, or `encryption` key to appear in source control, docs, tests, or logs.

## D-12 — Host constraints drive build settings
**Date:** 2026-10-30 · **Source:** approval #11
3.8 GiB RAM, 4 cores, 20 GiB free disk. Therefore:
`org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m`, `org.gradle.parallel=false`, `org.gradle.workers.max=2`, `kotlin.incremental=false` for native-heavy modules, `GOMAXPROCS=2`, `go build -p 2`, serial per-ABI builds. Any setting that would need more memory is rejected even if it is faster.

## D-13 — SDK/toolchain baseline
**Date:** 2026-10-30
`compileSdk 36`, `targetSdk 36`, `minSdk 26`, AGP **9.4.1** (Gradle min 9.6.0 → pin **9.8.2**), build-tools **36.0.0**, NDK **28.2.13676358** (AGP default), JDK **17**, Go **1.27.3**, gomobile `v0.0.0-20261011034528-197770444867`. AGP 9.5.0 is preview and is not used.

## D-14 — No code or asset reuse from other clients
**Date:** 2026-10-30
The implementation is original. No source, strings, icons, presets, routing files, or UI assets are copied from any third-party client. Protocol/config knowledge comes from the upstream cores' own documentation and public protocol specs.

## D-15 — Test integrity
**Date:** 2026-10-30
Tests are not weakened to pass. An existing test changes only when `SPEC.md` changes, and the change is recorded here with the operator's approval. Mocks are limited to true boundaries: JNI core, clock, filesystem encryption, Android system services.

## D-16 — Evidence rule
**Date:** 2026-10-30
No claim of success without the literal command, exit code, and relevant output excerpt from the current session. Anything not executed is labelled `NOT VERIFIED` with the reason and the manual command.

---

## Open items

| ID | Item | Owner | Status |
|---|---|---|---|
| O-1 | Release signing key | operator | deferred (debug only) |
| O-2 | Physical device for real-path proof | operator | blocked |
| O-3 | `armeabi-v7a` for release | operator | deferred |
| O-4 | Hysteria2 `obfs`/`bandwidth` | operator | out of scope |
| O-5 | Persian localization | operator | structure only |
| O-6 | v2 per-device bundle provisioning | operator | planned |

## D-17 — Single native library for both cores
**Date:** 2026-10-30 · **Source:** build finding
`gomobile bind` always names its output `libgojni.so`. Shipping one AAR per core would produce two files with the same name and break packaging (and `System.loadLibrary("gojni")` resolves to one of them). Both cores are therefore compiled into one module, `core-go/libmohammad`, exposing `XrayController` and `AwgController` side by side. Consequence: the cores cannot be updated independently without rebuilding the merged AAR; the version pins in D-08 still govern.

## D-18 — Strip DWARF from the shipped native library
**Date:** 2026-10-30
`gomobile` emits a `.so` with full debug information (47.9 MB arm64). Shipping it uncompressed pushed the APK to 54 MB, above the distribution limit. `scripts/strip-aar.sh` runs `llvm-strip --strip-debug` on the AAR's libraries before they are packaged: 47.9 MB → 37.9 MB, APK 45 MB. Dynamic symbols were verified intact (19 `Java_*` exports) after stripping. Debug-symbol artifacts, if ever needed, must be archived separately before stripping.

## D-19 — Accepted architecture deviations from SPEC §3
**Date:** 2026-10-30
The independent review flagged that the implementation does not use the `ui/feature/domain/data/vpn/core` module split, does not wrap the JNI layer in a `CoreBridge` interface, and keeps profile state in process singletons rather than ViewModels. This is accepted for v0.3.0: a single-module Compose app on a 3.8 GiB host builds reliably, while the multi-module split multiplies build time and memory. The `CoreBridge` seam is effectively the `libmohammad` AAR boundary. Revisit when the project moves to a multi-module layout with instrumentation tests.
Also accepted: IPv4-only TUN, no IPv6 route, and no `domain`/`usecase` layer.

## D-20 — Watchdog semantics and its limit
**Date:** 2026-10-30
The 5 s watchdog treats "core handle is null" as death. `IsRunning` on both bridges is a handle check, so a hung-but-alive core is not detected. A true liveness probe (packet counter delta or a cheap in-tunnel HTTP request) is deferred; it needs a device to calibrate false positives.

## D-21 — IPv6 is enabled by default with a ULA address
**Date:** 2026-10-30 · **Source:** parity audit A1.3/A1.5/A2.3
The tunnel now carries IPv6 (`fc00::1/126`, route `::/0`) so AAAA traffic cannot escape on IPv6-capable carriers. `XrayConfig` sets `dns.queryStrategy` to `UseIP` when IPv6 is on and `UseIPv4` otherwise. `TunPlan.build(ipv6 = false)` exists for operators who must stay IPv4-only. Device verification is pending (no device available).

## D-22 — Direct routing is literal, not geo-based
**Date:** 2026-10-30
Private address space is expressed as literal CIDRs (10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, fc00::/7, fe80::/10) plus four local suffixes, instead of `geoip:private` / `geosite:private`. Reason: those selectors require `geoip.dat`/`geosite.dat` (~15 MB) to be present, and a missing data file makes Xray refuse to start. Literal rules are deterministic, testable, and add no assets. Adopting geo selectors requires shipping the data files first (audit A3.4).

## D-23 — Default MTU raised to 1500
**Date:** 2026-10-30
Audit A1.1 found 1400 while mature clients default to 1500. The bundle may still override per deployment (clamped 1200–1500). `BundleParseTest` was updated from 1400 to 1500 — an intentional behaviour change, not a weakened test.

## D-24 — Traffic counters read from the core, not from Android
**Date:** 2026-10-30
`XrayController.QueryTraffic()` reads `outbound>>>proxy>>>traffic>>>uplink|downlink` from Xray's stats manager (enabled by `stats {}` + `policy.system`). The service polls every 2 s while connected. Android's `TrafficStats` was rejected because it measures the whole UID, including retries and the engine's own handshake, which would overstate tunnel traffic.
