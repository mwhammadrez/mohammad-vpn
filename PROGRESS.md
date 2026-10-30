# PROGRESS.md — Mohammad (`ir.mohammad.vpn`)

**Last updated:** 2026-10-30 (UTC)
**Current phase:** Phase 1 complete → awaiting operator approval to start M0.

## Milestone status

| ID | Milestone | Status | Gates | Notes |
|---|---|---|---|---|
| P0 | Baseline / exploration | ✅ complete | baseline report written | toolchain absent, no device, no KVM |
| P1 | Spec & plan | ✅ complete | awaiting approval | SPEC / PLAN / DECISIONS / PROGRESS |
| M0 | Walking skeleton | ⬜ not started | — | needs toolchain provisioning |
| M1 | Core integration | ⬜ not started | — | highest risk; device needed for proof |
| M2 | Operator bundle | ⬜ not started | — | depends on M1 profile model |
| M3 | UI states / a11y | ⬜ not started | — | |
| M4 | Diagnostics | ⬜ not started | — | |
| M5 | Hardening | ⬜ not started | — | |
| M6 | Independent review | ⬜ not started | — | |
| M7 | Handover | ⬜ not started | — | |

Legend: ✅ done · 🟡 in progress · ⬜ not started · 🔴 blocked

## Gate status (current)

| Gate | Command | Result |
|---|---|---|
| Build | `./gradlew assembleDebug` | `NOT VERIFIED` — no project, no JDK 17, no SDK |
| Unit | `./gradlew testDebugUnitTest` | `NOT VERIFIED` — same |
| Lint | `./gradlew lintDebug` | `NOT VERIFIED` — same |
| Placeholder | `./scripts/check-no-placeholders` | `NOT VERIFIED` — created in M0 |
| Size | `./scripts/apk-size-check` | `NOT VERIFIED` — no APK |
| Device | `adb …` | `NOT VERIFIED (no device)` |

## Chronological log

**2026-10-30 · Phase 0**
- Inspected host: Ubuntu 22.04, 4 cores, 3.8 GiB RAM, 20 GiB free, no KVM.
- Toolchain: only JRE 11; no `javac`, Gradle, SDK, NDK, Go, gomobile, adb.
- Verified upstream: Xray-core `v26.10.29` (`0afec66…`, MPL-2.0, go 1.27); amneziawg-go `v3.1.20260828` (`b5928ef…`, MIT, module `/v3`, go 1.25); AGP 9.4.1 requires JDK 17 / Gradle 9.6.0 / build-tools 36.0.0; AGP max API 37.
- Found: Xray-core now ships `proxy/hysteria` → Hysteria2 needs no extra core; `proxy/wireguard` exists too.
- Wrote `docs/BASELINE_REPORT.md`; output `STOP`.

**2026-10-30 · Phase 1**
- Operator approved Phase 0 with 12 decisions (recorded as D-01…D-16).
- Recorded exact tag+commit pins with `git ls-remote`.
- Parsed the operator profile (VLESS, `example.com:2095`, ws, path `/`, Host `example.org`, security `none`, `mlkem768x25519plus` encryption) into `operator/bundle.source.json` (mode `0600`, git-ignored, never printed in docs or logs).
- Wrote `SPEC.md` (29 FR + 30 NFR, 48 ACs, device-conditional rows marked), `PLAN.md` (M0–M7), `DECISIONS.md`.
- No application code exists yet; no build attempted.

## Next action

Awaiting approval of the four Phase-1 files. On approval: provision toolchain (D-13), then M0.

## Known blockers

1. **Toolchain absent** — JDK 17, SDK 36, build-tools, NDK 28.2, Go 1.27.3, gomobile. `sudo` is available; provisioning estimated ~5.3 GiB.
2. **No device / no KVM** — real-path and instrumented evidence impossible here (D-05).
3. **3.8 GiB RAM** — gomobile builds must be serial and memory-capped (D-12).

---

**2026-10-30 · Build session (toolchain + M0/M1 core path)**

Provisioned: JDK 17.0.20.1, Android SDK 36 + build-tools 36.0.0 + platform-tools, NDK 28.2.13676358, Go 1.27.3, gomobile, Gradle 8.14.6. Added a 4 GiB swapfile (host OOM-killed the Gradle daemon at 1536 MiB heap; cgroup limit is ~2 GiB).

Built `core-xray/artifacts/xray.aar` (45 MB) with `gomobile bind -target=android/arm64,android/amd64 -androidapi 26`.
- Workaround recorded: `github.com/wlynxg/anet v0.0.5` fails on Go 1.27 (`invalid reference to net.zoneCache`); patched copy in `third_party/anet` with the two `go:linkname` lines removed and wired via `replace` in `core-xray/go.mod`.

App implemented: Compose Connect screen, TunnelState/TunnelError, MMKV-free in-memory state, encrypted operator bundle loader (PBKDF2-SHA256 210k + AES-256-GCM), Xray config renderer (tun inbound + VLESS outbound), `MohammadVpnService` (VpnService + foreground + fd handoff), redacting log, TCP ping.

Verified with real commands:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (31 MB APK, arm64-v8a only).
- `aapt2 dump badging` → `ir.mohammad.vpn`, versionName 0.1.0, targetSdk 36.
- `apksigner verify` → debug certificate.
- Bundle decrypt round-trip in Python → matches `operator/bundle.source.json`.
- `xray -test -c` on the rendered config → `Configuration OK`.

NOT VERIFIED: runtime tunnel on a device (no device by operator decision D-05), cmake/lint/unit-test gates not run, `armeabi-v7a` absent by design.

---

**2026-10-30 · v0.2.0 — AmneziaWG + quality gates**

- Merged both cores into a single gomobile module `core-go/libmohammad` (XrayController + AwgController) because two AARs would collide on `libgojni.so`. One native library now carries both cores (`libgojni.so`, 47.9 MB arm64, up from 46.7 MB).
- AmneziaWG bridge: `tun.CreateTUNFromFile(fd)` + `device.NewDevice` + UAPI (`private_key`, `public_key`, `endpoint`, `allowed_ip`, `persistent_keepalive_interval`) plus obfuscation keys `jc/jmin/jmax/s1..s4/h1..h4`. Keys are base64 in the bundle and converted to hex in Go. Compiles and binds for android/arm64 + android/amd64.
- Service now branches on `Profile.core` (`XRAY` → XrayController, `AMNEZIAWG` → AwgController), with per-core logs tagged `XRAY` / `AWG`.
- Added 16 JVM unit tests: bundle schema validation, redaction, and both config renderers.
- `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, 16/16.
- `./gradlew lintDebug` → BUILD SUCCESSFUL, report written, no errors.
- `./gradlew assembleDebug` → BUILD SUCCESSFUL, `ir.mohammad.vpn` 0.2.0 (versionCode 3), 31 MB, arm64-v8a.

NOT VERIFIED: AmneziaWG runtime (no AWG server profile and no device), general device runtime.

---

**2026-10-30 · v0.3.0 — hardening, independent review, handover**

- Review (M6): independent reviewer produced `REVIEW.md` — 4 blockers, 12 majors, 14 minors.
- All 4 blockers fixed (AWG fd dup, unreachable connect timeout, retry after stop, destroy/revoke races).
- Majors fixed: permission-denial state, bundle defaults + Hysteria2 allow-list, selection persistence, IO threading, redaction gaps, render-error mapping, volatile state, endpoint validation, log cap, password escaping, notification permission.
- Added `NetMonitor` (ConnectivityManager callbacks) + watchdog (5 s) + bounded retry (3 s / 6 s / 12 s).
- Gates: `clean`, `testDebugUnitTest` (20 tests), `lintDebug`, `assembleDebug` — all green.
- Native symbols preserved after DWARF strip (`scripts/strip-aar.sh`); APK 45 MB (was 54 MB).
- Wrote `docs/VERIFICATION_REPORT.md`, `README.md`.

Remaining open items are listed in `docs/VERIFICATION_REPORT.md` §"Known limitations" and `DECISIONS.md` D-19.

---

**2026-10-30 · v0.4.0 — connection parity hardening**

- Phase A audit: `docs/CONNECTION_PARITY_AUDIT.md` (19 PARITY / 12 PARTIAL / 22 GAP / 5 N/A).
- Phase B fixes: `TunPlan` (IPv4 pool + IPv6 `fc00::1/126` + `::/0`), MTU 1500 default, Xray `dns` object with `queryStrategy`, `IPIfNonMatch`, private-CIDR/suffix `direct` rule, `stats`+`policy`, log level `warning`, `setUnderlyingNetworks`, traffic counters (`QueryTraffic` + 2 s poll + UI line).
- Tests: 16 → 32 (all green). Two existing assertions realigned to intentional behaviour changes (MTU default 1500; catch-all rule is now last).
- Gates: clean ✅ · testDebugUnitTest 32/32 ✅ · lintDebug ✅ · assembleDebug ✅ · check-no-placeholders ✅.
- Device gates: `installDebug` → `DeviceException: No connected devices!`; `adb devices` → empty list. Recorded as `NOT VERIFIED`.
- Report: `docs/CONNECTION_PARITY_REPORT.md` — verdict **PARITY PARTIAL**.

**2026-10-30 · AWG test plan authored**
- `docs/AWG_TEST_PLAN.md`: exact AWG profile schema, required/optional fields, redacted example, build/install commands, execution checklist with expected log lines, failure signatures, and what evidence to return.
- Device verification **not** received: the operator's message contained the empty template only. Verdict stays `PARITY PARTIAL`.

**2026-10-30 · v0.5.0 — in-app self-test (device evidence without adb)**
- `diagnostics/SelfTest.kt`: captures carrier egress IP before connect, current egress while connected, DNS timings, 1 MiB core-counter delta, ping, IPv6 escape flag, redacted log tail; emits a JSON report with Copy/Share buttons.
- `EgressVerdict` is pure and unit-tested (5 tests): changed/no-tunnel/inconclusive + IPv6 leak flag.
- Tests 32 → 37, all green. lint/assemble/placeholder gates green.
- Guide: `docs/SELF_TEST_GUIDE.md` (steps + field-to-row mapping).

**2026-10-30 · v0.5.1 — fixes from the first real device report**

Device report analysis (realme RMX3709, Android 16, VLESS):
- tunnel proven: egress 95.135.125.40 → 205.252.135.182 (`conclusion: tunnel`), DNS 48–78 ms, ping 85 ms.
- **Bug 1 (fixed):** `traffic.up/down` were 0 despite a successful 1 MiB download. Root cause: `GetFeature` was called with `(*app/stats.Manager)(nil)` while Xray matches on `features/stats.ManagerType()`; the lookup returned nil every time, so `QueryTraffic` short-circuited to zeros.
- **Bug 2 (mitigated):** IPv6 was enabled unconditionally; the device has a local IPv6 address but the server gives no IPv6 egress (`ipv6.egress: null`), so `::/0` routing made apps prefer AAAA and stall. IPv6 is now **opt-in** via bundle `"tunIpv6": true`, default false.
- Added `connect.timeline` (stage → ms) to the self-test report so connect latency can be attributed.

Tests 37 → 38. Gates green.

**2026-10-30 · v0.6.0 — default-preset parity with a mature client**
- `GeoAssets`: one-time download of geosite/geoip (28 MB total) into `filesDir/xray`, `XRAY_LOCATION_ASSET` wired through `XrayController.SetAssetDir`.
- `XrayConfig(geo = true)`: ads blocked (`geosite:category-ads-all`), UDP/443 blocked, private + Iran (`geosite:category-ir`, `geoip:ir`) direct, catch-all proxy — the reference client's default shape.
- Fallback: literal CIDR/suffix rules when the assets are missing, so startup never fails.
- Bundle: `defaultMtu` removed (1500 default), `tunIpv6: false`.
- Tests 38 → 41, all green. lint/assemble/placeholder gates green.

**2026-10-30 · v0.6.1 — outcome of the 0.6.0 device report**

Report facts: connect 487 ms total (tun 183, core 304), egress flipped to the server IP, DNS 50–60 ms, ping 57 ms — but `traffic` stayed 0 and the user saw a total outage after connecting.

- **Fixed:** the 28 MB geo download ran on the *same single-thread worker* as the stats poll and the watchdog (`runBlocking` inside `submit`). For the whole download the poll never ran, so counters stayed 0 and a dead core could not be detected. Downloads now run on a dedicated executor.
- **Added:** `XrayController.StatsDebug()` (`VisitCounters` dump) and its inclusion in the self-test report — the next report will show whether the counters exist at all.
- **Reverted:** default MTU back to 1400 (the value that worked before 0.6.0).
- Tests 41, lint/assemble/placeholder green.

**2026-10-30 · v0.6.2 — config comparison + rule hardening**
- `docs/CONFIG_COMPARISON.md`: element-by-element comparison with the reference client's shipped config and Iran preset.
- Geo rules hardened: `domain:ir` (TLD suffix, always available) replaces `geosite:category-ir`, which is missing from several datasets and is a startup hazard.
- `policy.levels` added to match the reference template's handshake/connIdle/uplinkOnly/downlinkOnly values.
- Tests 41 green; lint/assemble/placeholder green.

**2026-10-30 · v0.7.0 — tun2socks → SOCKS architecture (reference parity)**

Data path is now the one mature clients use:
`TUN fd → tun2socks (gVisor netstack) → 127.0.0.1:10808 socks → Xray outbound`.

- New Go bridge `libmohammad.TunBridge` on `xjasonlyu/tun2socks v2.7.0` (commit `8dda19e8`), MIT-compatible, pinned; it duplicates nothing itself — the service keeps the `ParcelFileDescriptor` and passes the fd number, the bridge opens its own device view over it.
- `XrayController.Start` now accepts `tunFd == 0` (SOCKS-only mode); the native `tun` inbound is no longer used by the app.
- `XrayConfig`: `socks` 127.0.0.1:10808 + `http` 127.0.0.1:10809 inbounds, sniffing `http,tls,fakedns`, FakeDNS `198.18.0.0/15`, `dns.servers` starts with `fakedns`, rule order = block udp:443 → block ads → direct private → direct IR → proxy.
- Bridge runs on the tunnel worker after the core starts; failures return `CoreStartFailed` and tear everything down.
- Tests 41 → 43 (socks inbound, fakedns-first, sniffing, pool); all green. lint/assemble/placeholder green.
- APK grew 45 MB → 50 MB (gVisor netstack, arm64 `.so` 37.9 → 43.3 MB after strip).

`NOT VERIFIED`: runtime behaviour on a device; the new path has never carried traffic on hardware.

**2026-10-30 · v0.7.1 — P0: TUN fd duplication (double-close fix)**
- `TunBridge.Start` now `unix.Dup`s the caller's descriptor, sets the duplicate non-blocking, and gives only the duplicate to `fdbased.Open`. Every failure branch closes the duplicate; the caller's fd is never touched.
- `TunBridge.Stop` closes the device (duplicate) and is idempotent (`b.stk == nil` guard).
- Prerequisite fixed: `proxy/socks5` blank import — without it `proxy.Parse("socks5://…")` returned `unknown protocol` and the bridge could never start (D-27).
- Host tests: `go test ./libmohammad/` → 3/3 PASS (original fd survives stop, double stop is a no-op, failed start leaks nothing).
- Gradle gates: testDebugUnitTest ✅ · lintDebug ✅ · assembleDebug ✅ · check-no-placeholders ✅.
- Behavioural parity remains **UNKNOWN**; nothing was run on a device.

**2026-10-30 · v0.7.2 — wire the bridge into the connect path**
- Device log from 0.7.1 showed `xray started in 117 ms` but no `tun2socks up`, and egress unchanged (`no-tunnel`). Cause: `startXray` still passed the descriptor to the core while the renderer had already dropped the tun inbound — nothing read the TUN device.
- `startXray` now starts the core in SOCKS-only mode (`start(config, 0)`), waits up to 2 s for `127.0.0.1:10808` to accept, then starts `TunBridge` on the duplicated descriptor.
- Failure path stops the bridge (if created) and the core; Java remains the sole owner of the original fd.

**2026-10-30 · v0.7.3 — handover hardening (P0 scope: four defects)**
- `setUnderlyingNetworks` now rebound on every active-network change via a new `NetMonitor.activeNetwork` flow (previously set once at interface build).
- `HandoverPolicy` (pure) decides `Ignore` / `HoldForNetwork` / `Resume` / `Exhausted`; the service only executes.
- Network loss no longer stops the service: retries are cancelled, state becomes `Failed(NetworkUnavailable)`, notification shows "Waiting for network".
- `scheduleRetry` defers instead of `stopSelf()` when offline.
- Watchdog now probes the data path (`SocksProbe`, SOCKS5 CONNECT through 10808): two consecutive failures every 30 s trigger reconnect. Process-alive is no longer treated as path-alive.
- Log events: `NETWORK_LOST`, `NETWORK_RESTORED`, `RECONNECT_STARTED`, `RECONNECT_SUCCEEDED`, `RECONNECT_FAILED`.
- Tests: 7 `HandoverPolicyTest` + 5 `SocksProbeTest` written first (8 failed for the right reasons), now green; 55 unit tests total.
- Device verification: **NOT VERIFIED** — no device attached.

**2026-10-30 · v0.7.4 — configuration parity closure**
- Device evidence (operator-supplied): 0.7.3 carries traffic (`95.135.125.40`, same exit as the reference client) and recovers 3.83 s after airplane mode.
- MTU 1400 → **1500** (D-32, supersedes D-23).
- `0.0.0.0/0` → **45 split routes** with private/reserved ranges excluded (D-33).
- Two resolvers → **`8.8.8.8` only**; `:10809` HTTP inbound removed (D-34).
- TUN address fixed at `10.10.10.1/30`.
- `setUnderlyingNetworks(null)` on network loss, rebind on restore.
- `TunPlanTest` rewritten: asserts no default route, excluded ranges, public coverage, no duplicates.
- Device verification of 0.7.4: **NOT VERIFIED**.
