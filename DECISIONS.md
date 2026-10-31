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

## D-25 — Geo rule sets are downloaded, not bundled
**Date:** 2026-10-30 · **Source:** parity request ("behave like a mature client by default")
`geosite.dat` (11.3 MB) + `geoip.dat` (16.5 MB) would push the APK past the 45 MB it already costs and past the 50 MB distribution limit. They are therefore downloaded once into `filesDir/xray` on first use (URLs configurable per bundle via `geoAssets`, default: Loyalsoldier `latest`), and `XRAY_LOCATION_ASSET` is pointed at that directory from the Go bridge (`SetAssetDir`).
**Fallback:** when the files are absent or the download fails, the renderer emits literal private-CIDR/suffix rules instead of `geoip:`/`geosite:` selectors — a missing data file would otherwise make Xray refuse to start.
**Consequence:** the default preset (ads blocked, QUIC/443 UDP blocked, private + Iran direct, rest proxied) only becomes active after the first successful download.

## D-26 — Default MTU is 1500 again
**Date:** 2026-10-30
The operator bundle's `defaultMtu: 1400` was removed so the parity default (1500) applies. MTU remains overridable per bundle (clamped 1200–1500) and is reported in the self-test.

## D-27 — Blank import of `proxy/socks5` (discovered during P0)
**Date:** 2026-10-30 · **Scope:** prerequisite for P0's success-path test
`tun2socks` registers proxy schemes from package `init` functions, so `proxy.Parse` returned `unknown protocol` for `socks5://127.0.0.1:10808` unless `proxy/socks5` is imported. The first version of `TunBridge` did not import it, meaning the bridge could never have started on a device — the defect was invisible until the host test exercised the success path.
This was outside the literal P0 scope (fd ownership) but is required for P0's evidence to be meaningful, and it is a one-line, side-effect-free import. Recorded here rather than folded silently into the P0 commit.

## D-28 — TUN descriptor ownership contract
**Date:** 2026-10-30
Java (`ParcelFileDescriptor`) is the single owner and closer of the VpnService descriptor. The Go bridge duplicates it with `unix.Dup` and owns only the duplicate; `TunBridge.Stop` closes the device (hence the duplicate) and is idempotent. No other component may close the original number. Enforced by three host tests in `core-go/libmohammad/tunbridge_test.go`.

## D-29 — Handover policy is a pure decision object
**Date:** 2026-10-30
`HandoverPolicy` takes a `HandoverContext` snapshot and returns an action (`Ignore`, `HoldForNetwork`, `Resume`, `Exhausted`). It holds no Android types, so the entire handover matrix is unit-testable without a device. The service owns side effects (rebinding, teardown, scheduling) and never makes the decision itself.
Rationale: the previous failure mode was untestable service glue; extracting the decision makes the interesting logic provable on the JVM.

## D-30 — Data-path watchdog uses an end-to-end SOCKS probe
**Date:** 2026-10-30
`XrayController.isRunning` only proves the core process exists. After a handover the core stays alive while its outbound sockets are dead, so the old watchdog never fired. `SocksProbe` performs a SOCKS5 greeting plus `CONNECT` to the active profile's host through `127.0.0.1:10808`; success proves the chain answers end-to-end. Two consecutive failures (checked every 30 s) trigger the reconnect path.
The probe target comes from memory and is never logged; failures log only a counter.

## D-31 — Network loss never stops the service
**Date:** 2026-10-30
`scheduleRetry` previously called `stopSelf()` when offline, so a phone call killed the tunnel permanently. Now an offline retry sets `waitingForNetwork`, keeps the foreground service, and the `NetMonitor` collector resumes with a bounded attempt when connectivity returns. Only an exhausted retry budget stops the service.

## D-32 — MTU 1500 (supersedes D-23)
**Date:** 2026-10-30 · **Scope:** tunnel interface
The reference client runs MTU 1500 on the same device, network and server. D-23's 1400 was measured
under the old architecture where Xray itself owned the tun inbound; that code path is gone and the
descriptor now goes through the same gVisor bridge the reference client uses. Parity therefore costs
nothing that the old measurement was protecting. Revert path: `defaultMtu` in the bundle.

## D-33 — Split routes instead of 0.0.0.0/0
**Date:** 2026-10-30
The interface carries the minimal exact route set for all public IPv4 space, excluding `10/8`,
`127/8`, `169.254/16`, `172.16/12`, `192.168/16` and `224/4`. No default route exists, so LAN,
link-local, loopback and multicast traffic never enters the tunnel — printers, casting and
ADB-over-Wi-Fi keep working without an app bypass list. The reference client behaves the same way.

## D-34 — Single resolver, single listener
**Date:** 2026-10-30
DNS is `8.8.8.8` only and the core exposes one loopback listener (`:10808`). The second resolver and
the HTTP inbound were ours, not the reference client's; removing them matches the observed interface
exactly and shrinks the loopback surface.

## D-35 — Cyber-emerald design system
**Date:** 2026-10-30
The UI is rebuilt on a supplied design system: dark `#050807` canvas, glass panels
(`#0E1311` at 82 % with a 1 dp hairline), mint `#34F5C5` for the connected/egress state, violet
`#A78BFA` for upload, amber `#F59E0B` for transitions and rose for failure. Type is Orbitron for
display, Chakra Petch for labels and metrics. Fonts are bundled under the SIL Open Font License
(`licenses/OFL-*.txt`), so no network font fetching at runtime.

Layout: header, hero card with a 60-tick / 28-segment rotary power dial, four bento telemetry tiles
(live sparkline for up and down, cumulative totals with a ruler bar), a profile list with radio
selection, and the connection log restyled as a console.

The design is dark-only on purpose; the system light theme is ignored.

## D-36 — UI keeps real data, not mock data
**Date:** 2026-10-30
The design mock shows invented servers, speeds and totals. The implementation binds every element to
live state: session timer to `Connected`, speed tiles to a 1 s delta of the core's traffic counters,
totals to the same counters, and the profile list to the decrypted bundle. Nothing is simulated; when
the tunnel is down the dial shows zero segments and the sparklines flatten.

## D-37 — DPI evasion is applied at render time, off by default
**Date:** 2026-10-30
`TransportTuning` (pure data) is injected into the outbound by `XrayConfig.applyTuning`:
`finalmask.tcp[] = {type:"fragment", settings:{packets, lengths, delays, maxSplit}}` and
`outbound.mux = {enabled, concurrency, xudpConcurrency, xudpProxyUDP443}`.

Defaults are inert so an untouched install behaves exactly as before. The toggle lives in the UI and
in `SharedPreferences`; a change takes effect on the next connect, which the app states in the card.

Rationale: fragmenting the first writes defeats pattern-based middleboxes without any server change.
Mux reduces handshake count on lossy links. ECH, REALITY and XHTTP were deliberately **not** added:
they require the server to present the matching configuration, and shipping a client-side switch that
cannot work would be a lie in the UI.

## D-38 — Rendering is verified on the JVM, not on a device
**Date:** 2026-10-30
`RenderTest` uses Robolectric with native graphics to build the real Compose tree and Roborazzi to
write a PNG. This is the only way to see the UI without an attached device or a KVM-capable host, and
it already caught two defects that no unit test would have (invisible LED segmentation, unreadable
digits). The screenshots in `docs/screenshots/` are generated from the same code the APK ships.
Limitation: Robolectric does not exercise the VPN service or the native bridge, so behavioural parity
is still device-only.

## D-39 — R8 runs on the debug build
**Date:** 2026-10-30
`debug` now has `isMinifyEnabled` and `isShrinkResources` with
`proguard-android-optimize.txt`. The debug key is unchanged; only code quality changes.
`proguard-rules.pro` keeps `libmohammad.**` and `go.**` because gomobile resolves JNI symbols by
class name. Verified with `mapping.txt`: every gomobile class maps to itself (`X -> X`), so nothing
was renamed. Result: APK 20 MB → 15 MB, DEX 1.39 MB, 10 618 methods.

## D-40 — Draw work is cached, not recomputed per frame
**Date:** 2026-10-30
The backdrop produced a new radial `Brush` on every frame; `Glass` produced a new vertical gradient on
every composition; the console rebuilt 40 text rows per log line. All three were replaced:
`drawWithCache` builds the shader once per size, the glass brush is a single top-level value, and the
console renders 12 rows from a `remember(logs.size)` slice.
The Compose compiler report was checked first: with Kotlin 2.0 strong skipping every composable in the
app is already skippable, so recomposition was **not** the bottleneck — allocation and overdraw were.

## D-41 — Three config-level quality defaults
**Date:** 2026-10-30
1. **Keep-alive 25 s / 15 s** (`sockopt.tcpKeepAliveIdle/Interval`). Xray's default is Chrome's 45 s,
   which sits inside the 30–60 s window where Iranian CGNAT expires idle mappings. Two small packets
   per interval are the cost. Merged into an existing `sockopt` block, never overwritten.
2. **`dns.hosts` for the server name.** The address is resolved once at connect time (capped at
   1.2 s, on a dedicated executor so it never queues behind the geo download) and injected as a
   static mapping, removing one resolver round trip inside the core. Skipped when the server address
   is already a literal. Neither the name nor the address is logged.
3. **`Builder.setMetered(false)`** (API 29+ guard, so minSdk 26 stays valid), unconditionally:
   a tunnel the user deliberately routed is not subject to Data Saver throttling.

   **Superseded variant:** 0.11.0 shipped a network-aware version (metred on cellular). The operator
   asked for the unconditional form. Trade-off recorded honestly: on a cellular link this tells
   Android the VPN is unmetered, so Data Saver will not restrict tunnelled traffic. That is the
   intended product semantic here, not an accident; if data-saving behaviour is wanted later, the
   network-aware branch in git history is the revert path.

All three are configuration-level. None has been measured on a device; the claims are
"field is emitted correctly", not "connection is faster".
