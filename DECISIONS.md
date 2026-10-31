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

## D-42 — The watchdog probes a neutral address and needs traffic evidence
**Date:** 2026-10-30
0.10.0 probed `profile.host:profile.port` — i.e. asked the proxy to connect back to its own
listening port. Many providers reject that, so the probe failed on a perfectly working tunnel; two
failures 30 s apart tore the tunnel down and reconnected it, which on a device looks like
"works for a few seconds, then cuts, forever".

Fixes:
- probe `1.1.1.1:443` (a neutral literal), never the server itself;
- three consecutive failures (≈90 s), not two;
- reconnect only when the traffic counters have also been silent for 60 s, so a busy tunnel is
  never torn down by probe noise.

`DataPathPolicy` holds the rule as a pure function and is unit-tested.

---

## D-43 — The research catalog is the roadmap, not a reading list
**Date:** 2026-10-31 · **Source:** operator approval of Phase 0.5
`docs/RESEARCH_CATALOG.md` (146 entries) and its 15-item shortlist are normative for milestone order.
Every milestone in `PLAN.md` must cite the catalog entries it implements. A technique that is not in
the shortlist needs a new decision, not a quiet addition.

## D-44 — Persian UI and RTL are out of scope
**Date:** 2026-10-31 · **Source:** operator instruction, Phase 1
The UI stays English. FR-52, NFR-26, NFR-40 and AC-31 are withdrawn; `strings.xml` extraction stays
required (NFR-2/UI-2) because it is a code-quality rule, not a localization one.
Rationale: localization improves no measured quality dimension, and the four priorities are
connection quality, stability, censorship resistance and operability. Reverting this decision is a
scope change, not a bug fix.

## D-45 — MTU default returns to 1400 (supersedes D-32)
**Date:** 2026-10-31 · **Source:** catalog 103–105, 114; open bug report
D-32 raised the MTU to 1500 for parity with a reference client. It was never device-verified on the
current architecture, and the reported symptom ("works a few seconds, then cuts") is the textbook
signature of a PMTU black hole. 1400 is the conservative value that the earlier architecture measured.
Resolution order is profile → bundle → 1400, and the source is logged on every connect.
Revert path: `defaultMtu` in the bundle, or a profile-level `mtu`.
**Status:** the fix is `NOT VERIFIED` until a device captures a large transfer at both MTUs.

## D-46 — MSS is clamped in the renderer, not in the kernel
**Date:** 2026-10-31 · **Source:** catalog 105, 115
The app cannot touch `iptables`. Xray's `sockopt.tcpMaxSeg` is the supported equivalent for the
outbound leg, so the renderer sets `tcpMaxSeg = min(mtu − 40, 1440)`. This is the MSS clamp expressed
in the only layer we own.
Consequence: the clamp covers Xray→server. The gVisor→SOCKS leg is governed by the TUN MTU (D-45).

## D-47 — `tcpUserTimeout` is mandatory
**Date:** 2026-10-31 · **Source:** catalog 106
Without it, a silently dead path looks alive to the OS for up to ~15 minutes, which is exactly how a
"stable" connection becomes a frozen screen. Default 30 s, bounded, rendered on every outbound.

## D-48 — A uTLS fingerprint is required whenever TLS is on
**Date:** 2026-10-31 · **Source:** catalog 65, 87
Go's default ClientHello is a fingerprint no browser produces, and JA3/JA4 is the cheapest signal a
DPI box can compute. `security ∈ {tls, reality}` without `fingerprint` is a validation error, not a
silent default — a silent default is how we ended up shipping the loud one.
The allow-list is derived from the pinned Xray build, never from documentation.

## D-49 — REALITY may not ship without a fallback path
**Date:** 2026-10-31 · **Source:** catalog 86, 49
REALITY is the strongest client-side change available, and its IPs are graylisted in Iran at specific
ISPs (MCI first). Shipping M3 without M4 would replace "one weak path" with "one strong path that
dies". The M3 release note must state the dependency.

## D-50 — Ten states before any path policy
**Date:** 2026-10-31 · **Source:** the 0.7.0 lesson; SPEC ST-1…ST-9
`DEGRADED`, `RECOVERING` and `BLOCKED_BY_POLICY` do not exist in the current five-state model, so
FR-44/PATH-* cannot be expressed. Building policy on the old model would repeat 0.7.0: both ends
built, middle unwired, every test green.

## D-51 — Pin the native sources inside the repository
**Date:** 2026-10-31 · **Source:** `REVIEW.md:336`, Phase 0 reproducibility finding
`core-go/go.mod` replaces three modules with `/tmp/xray-src`, `/tmp/awg-src`, `/tmp/t2s`. A clean
clone therefore cannot rebuild `libgojni.so`. Fix: `go mod vendor` (or module requirements pinned to
the same commits) checked in, with the `replace` lines kept only as an optional local override.
Owner: M0 or a dedicated chore commit. Until then the native build stays `NOT VERIFIED` outside this host.

## D-52 — Path selection is ordered and deterministic; no racing
**Date:** 2026-10-31 · **Source:** catalog 117, 118, 121, 122
Operator priority + fallback list + hysteresis, exactly as `PathPolicy` in SPEC §8.3. Parallel racing
(catalog 122) is rejected for v2.0: it multiplies probe traffic, drains battery, and looks like
scanning to a DPI box — the opposite of what a whitelist-style filter rewards.
Revisit only with device-measured connect-time data showing sequential failover is too slow.

## D-53 — Backoff gets jitter (amends D-38-era `RetryPolicy`)
**Date:** 2026-10-31 · **Source:** catalog 112
`RetryPolicy` is exponential but deterministic, so every client retries in lockstep after a national
outage. Add full jitter in `[0, delay]`, keep the cap, keep the same public API so existing tests keep
their meaning.

## D-54 — Pre-flight netcheck may lower the MTU, never raise it
**Date:** 2026-10-31 · **Source:** catalog 104, 19, 110
Measurement is allowed to correct a too-optimistic configuration; it is never allowed to override the
operator. Budget 2.5 s, cached 24 h per network identity, SSID hashed only, never fatal.

## D-55 — The second path is a profile, not a code path
**Date:** 2026-10-31 · **Source:** catalog 73, 60, 95
A CDN relay is declared as an ordinary profile with `transport: websocket` and `security: tls`, and
entered into `fallbackProfileIds`. No special-case branch in the service, no second connect flow.
This keeps one state machine, one probe and one diagnostic path for every transport.

## D-56 — Remote config: Ed25519 + JCS, two key slots, built-in bundle as the floor
**Date:** 2026-10-31 · **Source:** SPEC §5.9, NFR-3, NFR-16
Detached Ed25519 over RFC 8785 canonical JSON; two key slots so rotation does not brick installed
apps; strictly increasing `configVersion`; atomic apply; built-in bundle is always the final fallback.
A signing key is a remote kill switch — treat it as one.

## D-57 — `BundleLoadTest` gets a fixture; nothing is skipped
**Date:** 2026-10-31 · **Source:** 3 failing tests in the 2026-10-31 baseline
The shipped bundle is gitignored, so its test can never pass in a clean clone. Resolution, without
weakening:
1. a fixture builder encrypts a synthetic bundle with a fixed **test** password, exercising the loader
   success path on every run;
2. a separate test asserts the shipped bundle decrypts **when** `mohammad.operatorPassword` is set,
   and asserts the typed `operator password not configured` failure when it is not.
Both branches are real assertions. No `@Ignore`, no `assumeTrue`, no conditional skip.

## D-58 — An acceptance criterion must name a test that exists
**Date:** 2026-10-31 · **Source:** Phase 0 discrepancy #2 (~37 of 40 ACs pointed at nonexistent classes)
`SPEC.md §9.2` maps every AC to a real class, present today or created by the named milestone. A meta
test asserts that every `--tests` filter in the table selects at least one test. A missing class is a
spec defect, never a reason to skip the check.

## D-59 — BBR is an operator action, not a milestone
**Date:** 2026-10-31 · **Source:** catalog 108, 145; operator instruction
Server-side sysctl. Tracked as OP-1 with the required evidence (`sysctl net.ipv4.tcp_congestion_control`).
It never appears in a client milestone, and no client behaviour may depend on it being enabled.

## D-60 — AmneziaWG stays `NOT VERIFIED`, with per-server v3 parameters
**Date:** 2026-10-31 · **Source:** catalog 146, AWG-1…AWG-5
`amneziawg-go v3.1.20260828` is already in the binary, but no AWG server has ever carried traffic.
v3 exists because v2's static parameters became fingerprintable in mid-2026; therefore parameters must
be generated per server and must never ship as shared defaults. Status changes only after a captured
device trace proves bytes flowed.

## D-61 — No third core and no QUIC-first transport in v2.0
**Date:** 2026-10-31 · **Source:** catalog 89, 32, 33, 70
UDP/443 is throttled or dropped in Iran, so Hysteria/TUIC/MASQUE are secondary at best; and a third
core multiplies the verification surface we cannot test without a device. Revisit after M-DEV
produces real measurements.

## D-62 — Documentation numbers come from commands, not from history
**Date:** 2026-10-31 · **Source:** PROGRESS/STATUS drift found at takeover
Any count in `PROGRESS.md`, `STATUS.md` or a release note must be pasted from a command run in the
same session, with date and exit code. Test counts, warning counts and APK size are the common
offenders: this file previously claimed 55 tests against a real 95, and "no application code exists"
against a shipped 0.11.2.

## D-63 — The bundle loader exposes an internal decrypt seam
**Date:** 2026-10-31 · **Source:** M0, D-57
`OperatorBundleLoader.load(context)` is split into `readAsset(context)` + `decrypt(bytes, password)`
and `load` is now `decrypt(readAsset(context), BuildConfig.OPERATOR_PASSWORD)`. Both new functions are
`internal`, so they are invisible outside the module and unreachable from any production call site
other than `load`.
Why: the operator's bundle is git-ignored, so the success path of decryption could never be exercised
in a clean clone — which is why three tests were red on every machine but the operator's. Extracting
the seam makes the success path and every failure branch testable with a generated fixture.
Behaviour is unchanged by construction: the same checks run in the same order with the same messages,
and `BundleLoadTest` asserts each message. No product feature touched in M0.

## D-64 — `BundleParseTest` expects 1400, not 1500
**Date:** 2026-10-31 · **Source:** M1, D-45 (SPEC MTU-1)
`BundleParseTest.parsesValidBundle` asserted `assertEquals(1500, bundle.mtu)` because 1500 was the
hard-coded bundle default. D-45 changes that default to 1400, so the assertion must change with it.
This is the only test edit M1 is allowed to make for this reason, and it is a *spec* change, not a
convenience: the expectation follows the normative document, and the new value is asserted explicitly
rather than loosened to a range.
Logged before the edit, per the test-integrity rule.

## D-65 — `mtu` is an additive, optional profile field; no schema version bump
**Date:** 2026-10-31 · **Source:** M1, SPEC MTU-1
`Profile.mtu: Int?` is optional and defaults to null ("use the bundle default"). Bundles written for
0.11.2 keep parsing byte-for-byte; only the *default* changed (1500 → 1400, D-45). Adding a field that
older bundles omit is not a compatibility break, so `schemaVersion` stays 1. A future field that
changes meaning would need version 2.

## D-66 — `tcpMaxSeg` is `min(mtu − 40, 1440)`
**Date:** 2026-10-31 · **Source:** M1, catalog 105/115
40 bytes covers the outer IP + TCP headers plus the TLS/WS framing the core adds above the tunnel
payload. The 1440 cap is the classic Ethernet payload ceiling: with MTU 1500 the outbound would
otherwise advertise 1460, which is more than a real path usually carries once PPPoE or a carrier
tunnel is in the way. An explicit `sockopt.tcpMaxSeg` in the operator's profile always wins — the
renderer merges, never overwrites.

## D-67 — An out-of-range MTU is rejected, not clamped (behaviour change)
**Date:** 2026-10-31 · **Source:** M1, SPEC MTU-1
0.11.2 accepted any `defaultMtu` and silently substituted 1500 when the value was outside 1200–1500.
M1 throws `BundleException("defaultMtu out of range")` instead, and the same for a profile `mtu`.
Why: a silent substitution is how a misconfigured bundle ships for a month without anyone noticing,
and the operator now has remote config (M7) to correct it.
Risk: a deployed bundle carrying an out-of-range value would now fail to load entirely. Mitigation:
validate the bundle before publishing (`scripts/encrypt-operator-bundle` path) and keep the previous
bundle as the fallback. This is the only user-visible behaviour change in M1.

## D-68 — M1 does not implement PLPMTUD
**Date:** 2026-10-31 · **Source:** M1 scope; catalog 103/104
M1 stops the bleeding with a conservative configured value and an MSS clamp. Probing the real path MTU
belongs to M5 (pre-flight netcheck), which is the only place allowed to lower the effective value.
Doing it now would add a probe before there is a policy to cache or bound it.
Consequence: MTU-1…MTU-8 are verified as *configuration*, not as *behaviour*; the device register in
`SPEC.md §11` keeps "effective MTU on a real Iranian mobile path" as `NOT VERIFIED`.

## D-69 — The fingerprint allow-list is generated from the pinned core, not from docs
**Date:** 2026-10-31 · **Source:** M2; xray-core v26.10.29 @ 0afec66
`TlsFingerprint.VALID` is exactly the set of names `GetFingerprint` can resolve in
`transport/internet/tls/tls.go`: `PresetFingerprints` + `ModernFingerprints` + `OtherFingerprints`,
de-duplicated — **62 names**. It was produced by parsing the pinned source, not transcribed from
xtls.github.io, which lists only the twelve GUI presets.
`unsafe` is deliberately excluded: it is a key of `PresetFingerprints` whose value stays `nil` because
`init()` never assigns it, so the core rejects it at connect time. Accepting it in validation would
only move the failure later, where it looks like a network problem.
Consequence: `hellogolang` and the `hello*_55` family are formally valid. Rejecting them would reject a
bundle the core accepts, so they are allowed but not in `RECOMMENDED`.
Maintenance: the list goes stale when the core is re-pinned. Regeneration is a scripted parse of the
same file; the pin lives in D-08.

## D-70 — A fingerprint is mandatory, and a profile-level value wins
**Date:** 2026-10-31 · **Source:** M2, SPEC TLS-2
`security ∈ {tls, reality}` without an effective fingerprint is a `BundleException`, never a silent
default. The effective value is `profile.fingerprint` if present, otherwise
`tlsSettings.fingerprint` / `realitySettings.fingerprint` from the transport JSON.
A profile-level `fingerprint` with `security: none` is rejected as meaningless rather than ignored:
silently dropping operator intent is the same defect class as silently substituting a default.
The renderer writes a profile-level value into the matching settings block, so one field changes the
ClientHello without hand-editing transport JSON.

## D-71 — `security: reality` requires a `realitySettings` object at parse time
**Date:** 2026-10-31 · **Source:** M2, pre-empting SPEC TLS-3
M2 only renders a fingerprint, but accepting `security: reality` with no settings block would emit
`"realitySettings": {"fingerprint": …}` — a config the core cannot use, discovered on a device rather
than in a test. The parser now requires the block to exist. Full field validation
(`serverName`, `publicKey`, `shortId`) is M3.

## D-72 — The `security:none` golden is byte-for-byte and is not a snapshot
**Date:** 2026-10-31 · **Source:** M2, SPEC TLS-6 / AC-11
`app/src/test/resources/golden/security-none.json` was captured from the **M1** build before any M2
change and is compared with `assertEquals` against the full rendered string. It is the proof that M2
did not perturb the path every installed user is on today.
It is not a Roborazzi-style snapshot: there is no "record" mode in the gate. Regenerating it requires
changing the file by hand and justifying the diff here.
Known limitation: `org.json` backs `JSONObject` with a hash map, so key order is stable for a given
JDK and library version but not guaranteed across upgrades. If a JDK bump reorders keys, regenerate and
record why — do not loosen the assertion to a structural comparison without a decision.

## D-73 — `hellogolang` is not a valid fingerprint (corrects D-69)
**Date:** 2026-10-31 · **Source:** M3; `infra/conf/transport_security.go` in the pinned core
D-69 built the allow-list from the three `*Fingerprints` maps. Reading the config layer shows the
core rejects two names explicitly before it ever calls `GetFingerprint`:
`Fingerprint == "unsafe" || Fingerprint == "hellogolang"` → error.
So the valid set is **61**, not 62. `unsafe` was already excluded; `hellogolang` is now excluded too.
This is a correction of M2's list, not a scope change: a bundle using `hellogolang` never worked.
`TlsFingerprintTest` asserted the opposite and was corrected with the same reasoning recorded here.

## D-74 — REALITY field rules mirror the core, not intuition
**Date:** 2026-10-31 · **Source:** M3; `infra/conf/transport_security.go`
`publicKey` is base64 **RawURL**: padding is rejected, and the decoded value must be exactly 32 bytes.
`shortId` is hexadecimal, at most 16 characters, and **an empty string is accepted** — the core
decodes it into eight zero bytes, so rejecting it would reject a configuration the core runs. The key
must be present; the value may be empty.
`spiderX`, when present, must start with `/`.
Error messages never echo the public key or the short id (NFR-5); they name the field only.

## D-75 — Vision is allowed only with REALITY (deliberately narrower than the core)
**Date:** 2026-10-31 · **Source:** operator instruction, M3; SPEC TLS-4
The pinned core accepts `xtls-rprx-vision` with either TLS or REALITY, and the XTLS docs treat
VLESS+TLS+Vision as a normal configuration. This project restricts it to REALITY.
Why: every TLS profile we ship is REALITY-oriented, Vision's main benefit in Iran is record-boundary
matching against a REALITY handshake, and a wider rule would let a half-configured TLS profile pass
validation while providing none of the camouflage.
Cost: an operator who wants plain VLESS+TLS+Vision cannot express it. Reversing this is a one-line
change in `RealityRules.validateFlow` plus a SPEC edit — it is a scope decision, not a bug.

## D-76 — REALITY and `finalmask.fragment` cannot coexist
**Date:** 2026-10-31 · **Source:** M3, SPEC TLS-5
Fragmenting the ClientHello defeats the camouflage REALITY depends on (the handshake must reach the
server byte-intact to be indistinguishable from the decoy site). Two enforcement points:
1. a bundle declaring `security: reality` **and** a `finalmask.tcp` entry of type `fragment` fails to
   load with `reality cannot be combined with finalmask fragment`;
2. the renderer refuses to add a fragment to a REALITY profile even when the UI setting is on, and
   the service logs `fragment suppressed: it would break REALITY`.
The second point is a suppression rather than a failure because the UI toggle is global and the
profile is per-bundle: failing the connection would punish the user for a setting that is correct for
their other profiles.

## D-77 — M2 tests were updated because M3 tightened the schema
**Date:** 2026-10-31 · **Source:** M3, SPEC TLS-3
Three cases in `TlsProfileValidationTest` used a REALITY block containing only `serverName`. Once M3
required `publicKey` and `shortId`, those fixtures were invalid, so they now carry a complete block and
the assertions still test exactly what they tested before (the fingerprint rule). No assertion was
removed or relaxed; the fixtures became valid inputs for a stricter schema.

## D-78 — Multi-server by construction; no server data in source
**Date:** 2026-10-31 · **Source:** operator constraint, M4 onward
The app must serve any number of operator servers. Consequences, all normative from M4:
1. **Nothing server-specific in Kotlin.** No address, IP, hostname, UUID, publicKey or shortId. The
   only IPs allowed in production source are local infrastructure (TUN address, loopback, default
   resolvers) and route-table prefixes; the only host allowed is the geo-asset download host.
2. **Generic validation.** `RealityRules` and `TlsFingerprint` check *shapes* the core accepts, never
   the operator's particular values. A different server with a different key must pass unchanged.
3. **N profiles is the default assumption.** Path policy, the state machine, probing and rendering are
   written and tested for 1, 2 and N (≥4) profiles, including "single profile whose only path fails".
4. **Remote config is the growth path** (M7): adding a server must never require an APK.
Enforcement: `NoHardcodedEndpointsTest` scans string literals in `app/src/main/java` and fails on
UUID shapes, non-infrastructure IPs, unknown hostnames, 43-character base64url and 8–16 character hex
strings. It is an AC (AC-39) and runs in every gate.
The scan immediately found two dead constants in `MohammadVpnService` (`TUN_ADDRESS = "10.10.14.1"`,
`TUN_PREFIX = 30`) that disagreed with `TunPlan.IPV4_ADDRESS = "10.10.10.1"`. They were unused, so the
risk was latent rather than live — but a future call site would have built an interface whose address
did not match the route plan. Both removed.
Test sources are exempt from the scan: fixtures need concrete values, and the rule is about
production. Fixtures use `.invalid` hosts and generated keys, never the operator's data.

## D-79 — Multi-profile parsing is proven, not assumed
**Date:** 2026-10-31 · **Source:** SRC-1/SRC-5 (D-78)
`MultiProfileTest` covers a 1-profile bundle, a 2-profile bundle, and a 5-profile bundle with mixed
cores (four XRAY protocols plus one AMNEZIAWG), asserting order, per-profile hosts, default
resolution, empty-list rejection and duplicate rejection at a non-zero index.
Why it exists separately from `BundleParseTest`: that file tests *field validation*, this one tests
*cardinality*. A single-server assumption usually shows up as "the first profile is the only one",
which no field-level test would catch.
Still open until M4: policy behaviour with 1/2/N paths (`PathPolicyTest`, AC-40).
