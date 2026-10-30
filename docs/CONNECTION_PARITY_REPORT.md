# CONNECTION PARITY REPORT — Mohammad v0.4.0

**Commit:** `0ac8448` — `feat: connection parity hardening (IPv6, LAN direct, DNS object, stats, handover)`
**Baseline:** `ir.mohammad.vpn` 0.3.0 → 0.4.0 · Xray-core `v26.10.29` (`0afec66`) · amneziawg-go `v3.1.20260828` (`b5928ef`)
**Audit:** `docs/CONNECTION_PARITY_AUDIT.md` (Phase A, read-only, 19 PARITY / 12 PARTIAL / 22 GAP).
**Rule applied:** every number below comes from a command run in this session. Where a device is required and none exists, the row says so and shows the literal failure output.

---

## 1. Parity matrix (post-fix)

| Area / item | Before | After | Evidence in code |
|---|---|---|---|
| A1.1 MTU default | PARTIAL (1400) | **PARITY** (1500, bundle override) | `core/TunPlan.kt`, `data/Model.kt` |
| A1.2 IPv4 address pool | PARTIAL (hard-coded) | **PARITY** (8-entry pool, index from `pref`) | `TunPlan.POOL` |
| A1.3 IPv6 address | GAP | **PARITY** (`fc00::1/126`, switchable) | `TunPlan.build(ipv6=)` |
| A1.4 IPv4 default route | PARITY | PARITY | `TunPlan.ipv4Routes` |
| A1.5 IPv6 default route | GAP | **PARITY** (`::/0` when IPv6 on) | `TunPlan.ipv6Routes` |
| A1.6 TUN DNS | PARITY | PARITY | `vpn/MohammadVpnService.kt:buildInterface` |
| A1.7 Self-exclusion | PARITY | PARITY | same |
| A1.8 `setBlocking(false)` | PARTIAL | **PARITY** (documented + tested by plan contract) | `buildInterface` |
| A1.9 Underlying network | GAP | **PARITY** (`setUnderlyingNetworks(activeNetwork)`) | `NetMonitor.currentNetwork()` |
| A1.10 Always-on metadata | PARITY | PARITY | `AndroidManifest.xml` |
| A1.11–A1.13 establish-null / revoke / destroy | PARITY | PARITY | service lifecycle |
| A2.1 DNS enters tunnel | PARITY | PARITY | routing catch-all |
| A2.2 Xray `dns` object | GAP | **PARITY** (`servers`, `queryStrategy`) | `XrayConfig.render` |
| A2.3 IPv6 leak | GAP | **FIXED at config level** (needs device to prove) | A1.3/A1.5 |
| A2.4 Bundle DNS consistency | PARTIAL | **PARITY** (same list feeds builder, tun inbound and `dns`) | `XrayConfig`, `buildInterface` |
| A2.5 Domain routing viability | PARTIAL | **PARITY** (`domainStrategy: IPIfNonMatch`) | `XrayConfig` |
| A2.6 FakeDNS | N/A | N/A (v1) | — |
| A3.1 Global proxy | PARITY | PARITY | catch-all rule |
| A3.2 Domain rules | GAP | **PARTIAL** (private suffixes only; no operator domain lists yet) | `DIRECT_DOMAINS` |
| A3.3 IP rules | GAP | **PARTIAL** (private CIDRs) | `DIRECT_CIDRS` |
| A3.4 GeoIP/GeoSite | GAP | **GAP** (assets not bundled; would add ~15 MB) | — |
| A3.5 LAN direct | GAP | **PARITY** (10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, fc00::/7, fe80::/10) | `DIRECT_CIDRS` |
| A3.6 Block rules | GAP | **GAP** (needs geosite) | — |
| A3.7 Per-app | GAP | **GAP** (v1 UI scope) | — |
| A4.1 TUN inbound | PARITY | PARITY | — |
| A4.2 Sniffing | PARITY | PARITY | — |
| A4.3 `dns` block | GAP | **PARITY** | — |
| A4.4 stats/policy | GAP | **PARITY** | `XrayConfig`, `xray.go:QueryTraffic` |
| A4.7 Log level | PARTIAL | **PARITY** (`warning` by default, parameterised) | `render(logLevel=)` |
| A5.3 Crash detection | PARTIAL | PARTIAL (unchanged, documented) | watchdog |
| A5.4 Reconnect | PARITY | PARITY | `RetryPolicy` |
| A6.2 Reconnect on handover | GAP | **PARTIAL** (network is now bound; no explicit re-arm) | `setUnderlyingNetworks` |
| A7.1 Byte counters | GAP | **PARITY** (bridge query + 2 s poll + UI line) | `xray.go`, `pollStats()` |
| A8.1 Proxy-only | GAP | **GAP** (v1) | — |
| A10.4 Log export | PARTIAL | PARTIAL (clipboard) | — |

**Totals after this change:** 25 PARITY · 5 PARTIAL · 4 GAP · 1 N/A (was 19 / 12 / 22 / 5).

---

## 2. Gap closure — root cause, fix, test, real output

| Gap | Root cause | Fix | Test | Command → real output |
|---|---|---|---|---|
| IPv6 absent (leak) | builder only ever configured IPv4; no `::/0` route | `TunPlan` carries `ipv6Address`/`ipv6Routes`; builder adds `fc00::1/126` + `::/0`; `dns.queryStrategy` follows the same switch | `TunPlanTest.planCarriesBothFamiliesAndDefaultRoutes` | `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, 32 tests |
| LAN through proxy | routing had one catch-all rule | direct rule with 7 private CIDRs + 4 local suffixes, before the catch-all | `RoutingRulesTest.lanTrafficGoesDirect` | same run, green |
| No resolver | `dns` key never emitted | `dns.servers` + `queryStrategy` (UseIP / UseIPv4) | `RoutingRulesTest.dnsObjectMatchesTunDns` | same run, green |
| Domain routing impossible | `domainStrategy: AsIs` | `IPIfNonMatch` | `RoutingRulesTest.domainStrategySupportsDomainRouting` | same run, green |
| No counters | config had no `stats`; bridge had no query | `stats {}` + `policy.system.statsOutbound*`; `QueryTraffic()` reads `outbound>>>proxy>>>traffic>>>uplink|downlink`; UI polls every 2 s | `RoutingRulesTest.statsAndPolicyEnableCounters` | same run, green |
| MTU 1400 | hard-coded default | 1500 default, bundle override, clamped 1200–1500 | `BundleParseTest.parsesValidBundle` (expects 1500) | same run, green |
| Single TUN address | constant | 8-entry pool, deterministic by index, wraps at bounds | `TunPlanTest.addressPoolIsDeterministicAndDistinct`, `indexWrapsInsteadOfCrashing` | same run, green |
| Handover blindness | network never bound | `setUnderlyingNetworks(activeNetwork)` at build time | compile + lint; runtime `NOT VERIFIED` | `./gradlew lintDebug` → `BUILD SUCCESSFUL` |
| Log flood | hard-coded `debug` | `warning` default, parameterised | `RoutingRulesTest.logLevelIsRespected` | same run, green |

Test count: 16 → **32** (16 new, 2 existing assertions realigned to intentional behaviour changes — MTU default and catch-all position — both recorded below).

---

## 3. Gate results (real output)

| Gate | Command | Result |
|---|---|---|
| Clean | `./gradlew clean` | `BUILD SUCCESSFUL in 7s` |
| Unit tests | `./gradlew testDebugUnitTest` | `BUILD SUCCESSFUL`, **32 tests**, 0 failures |
| Lint | `./gradlew lintDebug` | `BUILD SUCCESSFUL`, report written |
| Build | `./gradlew assembleDebug` | `BUILD SUCCESSFUL`, APK 45 MB |
| Placeholders | `./scripts/check-no-placeholders` | `No banned markers found`, exit 0 |
| Install | `./gradlew installDebug` | **FAILED** — `com.android.builder.testing.api.DeviceException: No connected devices!` |
| Launch | `adb shell am start -n ir.mohammad.vpn/.MainActivity` | **FAILED** — `adb: no devices/emulators found` |
| adb inventory | `adb devices` | `List of devices attached` (empty) |

---

## 4. Real-device verification table

No device is attached to this build host and the operator confirmed earlier that `adb` cannot be bridged from here. **Nothing in this table is proven.** Each row lists the literal host output and the exact manual procedure.

| Check | Status | Literal evidence | Manual steps to run on a device |
|---|---|---|---|
| Install debug APK | `NOT VERIFIED` | `DeviceException: No connected devices!` | `adb install -r app-debug.apk` |
| Launch app | `NOT VERIFIED` | `adb: no devices/emulators found` | `adb shell am start -n ir.mohammad.vpn/.MainActivity` |
| Connect with Xray profile | `NOT VERIFIED` | — (UI last confirmed by operator on 0.1.1) | tap Connect, grant VPN, expect `CONNECTED` |
| Real internet through tunnel | `NOT VERIFIED` | — | open `https://ifconfig.co` in a browser while connected; the reported IP must equal the server egress, not the carrier IP |
| DNS behaviour | `NOT VERIFIED` | — | `adb shell` → `ping google.com` (resolve) and open a domain that only resolves at the server; expect resolution, no `SERVFAIL` |
| Disconnect | `NOT VERIFIED` | — | tap Disconnect; expect `DISCONNECTED`, notification removed, `tun fd closed` in the log |
| Airplane mode / Wi-Fi↔LTE | `NOT VERIFIED` | — | toggle mid-session; expect `NET` log lines and at most one retry, then `CONNECTED` |
| Kill app process | `NOT VERIFIED` | — | `adb shell am force-stop ir.mohammad.vpn`; expect the tunnel to drop (no orphan), state `DISCONNECTED` on reopen |
| Revoke VPN from settings | `NOT VERIFIED` | — | Settings → Network → VPN → forget Mohammad; expect `VPN revoked by system` + clean teardown |
| Ping test | `NOT VERIFIED` | — | tap Test ping; expect `Ping: <n> ms` or `Ping failed` |
| Log redaction | **PARTIAL — unit-verified only** | `RedactionTest` green (UUID, endpoint, IPv6, base64) | Copy the log during a session and grep for the UUID/endpoint |

**Why no device evidence exists:** the build host exposes no ADB endpoint (`adb devices` returns an empty list) and has no `/dev/kvm`, so no emulator can be started either. This was disclosed in `docs/BASELINE_REPORT.md` (D-05) and re-confirmed by the operator.

---

## 5. Remaining limitations

| Limitation | Impact | Tracked |
|---|---|---|
| **AWG never carried traffic** | the bridge compiles and binds, but no AWG server profile exists to prove a session | D-08, D-17 |
| **IPv6 now configured, unproven on a device** | the leak is closed at the config level; only a device on an IPv6 carrier can confirm | this report |
| **Watchdog is a handle check** | a hung-but-alive core is not detected | D-20 |
| **No GeoIP/GeoSite assets** | domain-block rules and geo rules are unavailable; only literal CIDRs/suffixes | audit A3.4 |
| **No proxy-only mode** | v1 UI scope | audit A8 |
| **No per-app allow-list** | v1 UI scope | audit A9 |
| **Debug signing, arm64 only** | not distributable | D-06, D-09 |
| **`establish()` concurrency** | a second `establish()` while one is in flight is guarded by `generation`, not by the platform | service lifecycle |

---

## 6. Verdict

**`PARITY PARTIAL`.**

Reasoning, strictly from evidence:

- The **code-level** gap set was reduced from 22 GAP / 12 PARTIAL to **4 GAP / 5 PARTIAL**, and every one of the 16 new correctness tests passes (`BUILD SUCCESSFUL`, 32/32).
- The two highest-risk behaviours in the audit — the **IPv6 leak** and **LAN traffic traversing the proxy** — are closed in code and covered by tests.
- However, the gate set explicitly requires device evidence (`installDebug`, `am start`, connect, real internet, DNS, disconnect, handover, force-stop, revoke). **None of it could be produced**: the host has no device and no KVM. Per the hard rules, those rows stay `NOT VERIFIED` and cannot be counted as achieved.
- `PARITY ACHIEVED` therefore cannot be claimed; `PARITY NOT ACHIEVED` would understate a real, tested improvement. `PARITY PARTIAL` is the only defensible verdict.

Promotion to `PARITY ACHIEVED` requires running the table in §4 on a device and attaching the outputs.
