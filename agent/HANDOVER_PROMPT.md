# HANDOVER PROMPT — paste this into a fresh session

You are taking over a working Android VPN project. You have no prior context. Everything below
was done in a previous session; verify it before trusting it.

---

## 1. The project in one paragraph

`Mohammad` (`ir.mohammad.vpn`) is a personal Android VPN client with two cores compiled into one
native library: **Xray-core** (VLESS / VMess / Trojan / Shadowsocks / SOCKS / HTTP) and
**AmneziaWG**. The data path is `VpnService TUN → tun2socks (gVisor) → 127.0.0.1:10808 SOCKS →
Xray → server`. The user never enters a config: an encrypted operator bundle ships inside the APK
and is decrypted at runtime. The UI is a single-screen "cyber-emerald" Compose design with a
rotary dial, telemetry tiles, a profile list, a DPI-evasion card and a log console.

## 2. Where everything lives

| What | Where |
|---|---|
| Repository (private) | https://github.com/mwhammadrez/mohammad-vpn |
| Branch / HEAD | `main` / `689833a` (42 commits, 131 tracked files) |
| Full handbook (read this first) | `docs/HANDBOOK.txt` |
| Verify status | `STATUS.md` (verified vs not verified) |
| Why things are the way they are | `DECISIONS.md` (42 entries, D-01 … D-42) |
| Release-by-release history | `PROGRESS.md` |
| Agent memory + skills | `agent/` (run `bash agent/setup.sh`) |
| Research reports | `docs/IRAN_CIRCUMVENTION_RESEARCH.md`, `docs/CONNECTION_QUALITY_RESEARCH.md` |
| Testing toolchain | `docs/TESTING_TOOLCHAIN.md`, `agent/skills/android-headless-debug/SKILL.md` |
| Performance audit | `docs/PERF_QUALITY_REPORT.md` |
| Security scans | `docs/SECURITY_SCAN_REPORT.md` |

## 3. Current state (verify, do not assume)

```bash
git clone https://github.com/mwhammadrez/mohammad-vpn   # private: needs a token
cd mohammad-vpn
./gradlew testDebugUnitTest lintDebug assembleDebug     # 95 tests must pass
```

- **Version:** 0.11.2 (versionCode 29), debug-signed, arm64-v8a only, ~15 MB APK.
- **Device-verified:** the tun2socks data path (0.7.3 carried traffic, egress matched the server,
  recovery after airplane mode in 3.83 s). Everything after that is *not* device-verified.
- **Not verified:** AmneziaWG with real traffic (no AWG server was ever provided), the 0.10–0.11
  line as a whole, FPS/jank/startup/memory numbers, and any claim that the config-level tweaks made
  connections faster.

## 4. What was actually built, in order

| Version | Change | Why |
|---|---|---|
| 0.1.0 | First connection; fixed `proxyman.InboundConfig is not registered` by importing `main/distro/all` + `proxy/tun` | Xray needs those registrations |
| 0.2.0 | AmneziaWG controller | second core |
| 0.3.0 | Hardening + 4 fixes from an independent review | correctness |
| 0.4.0 | IPv6, LAN direct, DNS object, stats | parity |
| 0.5.x | In-app self-test; traffic counter fix (`GetFeature(stats.ManagerType())`) | counters were always zero |
| 0.6.x | Geo preset, geo download moved off the tunnel worker, `domain:ir` | 28 MB download on the worker starved the tunnel |
| 0.7.0 | **Architecture change to tun2socks → SOCKS** | match the reference client |
| 0.7.1 | `unix.Dup` the TUN fd; discovered `proxy/socks5` was never imported | double-close risk; the bridge could never have started |
| 0.7.2 | Actually wired the bridge: core in socks-only mode, wait for port 10808, then start the bridge | 0.7.0 changed both ends but not the middle — nothing read the TUN |
| 0.7.3 | Handover hardening: `HandoverPolicy`, `SocksProbe`, never `stopSelf()` on network loss | phone calls killed the tunnel permanently |
| 0.7.4 | Six configuration deltas closed (MTU 1500, split routes, single DNS, one listener, …) | parity with the reference client |
| 0.8.0 | Full Compose redesign (cyber-emerald) | product |
| 0.8.1–0.8.3 | APK packaging, idle-animation removal, `shutdownNow` → `shutdown`, crash recorder | app closed itself on disconnect |
| 0.9.0 | DPI evasion card: `finalmask` fragment + mux | Iran-specific |
| 0.9.1–0.9.3 | Two self-test bugs, JVM rendering (Robolectric+Roborazzi), behavioural test suites | first real look at the UI without a device |
| 0.10.0 | Self-test removed; **green only after a SOCKS health probe**; domestic DNS for `.ir` | honesty of the green state |
| 0.10.1–0.10.2 | R8 on debug, draw caching, dead SDK branches, app icon, recomposition proof | performance |
| 0.11.0–0.11.1 | Keep-alive 25/15, `dns.hosts` pre-resolution, `setMetered(false)` | connection quality |
| 0.11.2 | **Watchdog no longer probes the server through its own tunnel** | false "dead path" every minute caused a reconnect loop |

## 5. Tools that were installed and used (with versions)

Host: x86_64 Linux, 3.8 GiB RAM, JDK 17.0.20.1, Gradle 8.14.6, AGP 8.13.2, Kotlin 2.0.21,
**no device, no /dev/kvm**.

| Tool | Version | Used for |
|---|---|---|
| Robolectric | 4.14.1 | running the real `Activity`, `Service` and Compose tree inside the JVM |
| Roborazzi | 1.36.0 | rendering the UI to PNG (1.77 is incompatible with Kotlin 2.0) |
| Compose UI Test | BOM 2024.12.01 | tapping real nodes, asserting callbacks |
| detekt CLI | 1.23.8 | Kotlin static analysis (found the unused `forceIpv6`) |
| mobsfscan | 1.1.0 | Android security scan (6 INFO, no secrets) |
| staticcheck / govulncheck / go vet | latest | Go side: 0 findings, 0 reachable vulnerabilities |
| Android Lint | AGP 8.13.2 | 19 → 15 findings |
| Compose compiler reports | built-in | proved every composable is skippable |
| Headless Chrome | system | rendered an HTML design mock for comparison |
| uguu.se | — | hosted screenshots/APKs for the operator |

## 6. Traps already discovered (do not rediscover them)

1. `onRoot().captureToImage()` never works under Robolectric; use `captureRoboImage`.
2. Infinite animations block `waitForIdle`; set `mainClock.autoAdvance = false`.
3. Compose tests need `debugImplementation("androidx.compose.ui:ui-test-manifest")`.
4. `VpnService.Builder` is an inner class — tests need a helper on the service (`newBuilder()`).
5. `setMetered` needs API 29; `minSdk` is 26.
6. Adaptive icons must live in `mipmap-anydpi-v26`; moving them breaks AAPT.
7. R8 must keep `libmohammad.**` and `go.**` or JNI symbol lookup breaks. Verify in `mapping.txt`
   that classes map to themselves (`X -> X`).
8. `proxy.Parse("socks5://…")` returns "unknown protocol" unless `proxy/socks5` is imported.

## 7. Hard rules that must survive this handover

1. Never copy code, strings, icons, presets or assets from any proprietary client. Behavioural
   reference only; the analysis in `reference/` is our own writing.
2. Pin every dependency to an exact tag + commit; check the licence before adding.
3. Test first: failing test → implementation → real command output. Never weaken or skip a test,
   never add a suppression to hide a real problem.
4. Never log or commit secrets, UUIDs, passwords, private keys or full endpoints. The APK embeds
   the bundle password — it must never be published.
5. No device and no KVM: anything device-dependent is `NOT VERIFIED`, never assumed. Do not invent
   frame timings, cold-start times or memory numbers.
6. Structural, behavioural and feature-parity verdicts stay separate.
7. Debug signing only; no release keystore exists.

## 8. Secrets the operator must supply

| Secret | Where it goes | Why |
|---|---|---|
| bundle password | `local.properties` → `mohammad.operatorPassword=` | decrypts `bundle.enc` at build time |
| `operator/bundle.source.json` | same path, mode 0600 | plaintext profile (UUID, server) |

Both are git-ignored and must stay that way. Without them the app still builds and connects using
the committed `bundle.enc` + AAR, but the bundle cannot be re-encrypted.

## 9. Known problems and what to do next

1. **Reported instability (open).** The operator reported "works a few seconds, then cuts". 0.11.2
   fixed one real cause (the watchdog probing the server through its own tunnel, which forced a
   reconnect roughly every minute). If it still happens, the next suspect is **MTU 1500**: the
   symptom matches a PMTU black hole, and the earlier device evidence supported 1400. Ask for the
   log line `data path probe failed (x/3, silent Ns)` and whether
   `RECONNECT_STARTED` actually appears.
2. **AmneziaWG** has never carried traffic. It needs an AWG server profile before anything can be
   claimed about it.
3. **The profile is `security: none`** (plaintext WebSocket). Research in
   `docs/IRAN_CIRCUMVENTION_RESEARCH.md` says the single highest-impact change is moving the server
   to VLESS + REALITY; no client-side toggle substitutes for it.
4. **Remote config management** was requested but not implemented: the operator wants to change
   the config for ~1000 installed users without shipping a new APK. The design (signed remote
   bundle with version + fallback) is described in the chat history, not yet in code.
5. Research recommendations not yet implemented: `happyEyeballs`, `tcpUserTimeout`, `tcpMaxSeg`,
   parallel profile racing, XHTTP, AmneziaWG fallback path.

## 10. First 10 minutes in the new session

```bash
git clone https://github.com/mwhammadrez/mohammad-vpn && cd mohammad-vpn
bash agent/setup.sh                    # restore agent memory + skills
cat docs/HANDBOOK.txt                  # the whole project end to end
cat STATUS.md                          # verified vs not verified
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Then read `DECISIONS.md` before changing anything, and ask the operator which of §9 they want to
attack first.

## 11. The one lesson worth repeating

v0.7.0 shipped a new data path where both ends were built and unit-tested but the middle was never
wired: the config had no tun inbound and the bridge was only ever stopped, never started. Every test
passed; the app carried no traffic for a week. **After any architectural change, prove the chain,
not the parts.**
