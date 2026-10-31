# PROGRESS.md — Mohammad (`ir.mohammad.vpn`)

**Last updated:** 2026-10-31 (UTC)
**Current phase:** M4-C complete → M4 not started
**Current release:** 0.11.2 (versionCode 29)

> **Correction notice (2026-10-31).** This file was frozen at Phase 1 ("no application code exists
> yet", "toolchain absent", "awaiting approval") while 43 commits and eleven minor releases shipped.
> `STATUS.md` repeated the error by counting 55 tests against the real 95. The log below is rebuilt
> from `git log` and from commands run in this session; where a number could not be reproduced it is
> marked `NOT VERIFIED` rather than guessed. Trackers had drifted from git; git and the test run win.

---

## Milestone status — v2.0

| ID | Milestone | Status | Gate | Notes |
|---|---|---|---|---|
| M0 | Harness correction | ✅ done | green | gates + AC→test mapping + `BundleLoadTest` fixture |
| M1 | MTU / MSS / `sockopt` | ✅ done | green | config verified; device behaviour NOT VERIFIED |
| M2 | uTLS fingerprint | ✅ done | green | allow-list from the pinned core |
| M3 | REALITY + Vision | ✅ done | green | ships together with M4 (D-49) |
| M4-W | Widget + quick-settings tile | ✅ done | green | two system surfaces, one entry point |
| M4-N | Tile reliability, notification, telemetry | ✅ done | green | three defects fixed, one surface added |
| M4-C | Config compatibility + `oob` + `merge` | ✅ done | green | core patched, current bundle can start |
| M4-U | Paste import + real-delay test | ✅ done | green | any link can be tried before connecting |
| M4-U2 | Import fidelity: encryption, httpupgrade, fm | ✅ done | green | real links now import correctly |
| M4-U3 | Parity audit + test-all | ✅ done | green | every PattNG core feature accounted for |
| M6-DNS | DNS tunnel second path | ✅ done | green | engine in APK; needs a delegated domain to go live |
| M6-UI | Mode switch + Mode 2 environment | ✅ done | green | proxy and full-tunnel from one screen |
| M4 | State machine + path policy | ⬜ not started | — | largest milestone |
| M5 | Pre-flight netcheck | ⬜ not started | — | measurement, not guesswork |
| M6 | Second path (WS+TLS via CDN) | ⬜ not started | — | resilience to IP death |
| M7 | Signed remote config | ⬜ not started | — | delivery mechanism |
| M-DEV | Device verification protocol | 🔴 blocked | — | no device, no KVM |
| OP-1 | BBR on server | 🔴 operator | — | not a client milestone |
| OP-2 | AWG server + per-server v3 params | 🔴 operator | — | blocks AWG verification |
| OP-3 | REALITY decoy decision | 🔴 operator | — | blocks M3 deployment |
| OP-4 | Bundle password rotation | 🔴 operator | — | hygiene after 0.8.0 APK |

Legend: ✅ done · 🟡 in progress · ⬜ not started · 🔴 blocked

---

## Gate status — measured 2026-10-31 in this workspace

Environment: Ubuntu 22.04, x86_64, JDK 17.0.21+9, Gradle 8.14.6, AGP 8.13.2, Kotlin 2.0.21,
SDK 36, NDK 28.2.13676358, Go 1.27.3, 3 GiB RAM, **no device, no `/dev/kvm`**.

| Gate | Command | Exit | Result |
|---|---|---:|---|
| Version | `./gradlew --version` | 0 | Gradle 8.14.6, JVM 17.0.21, Kotlin 2.0.21 |
| Clean build | `./gradlew clean :app:assembleDebug` | 0 | `app-debug.apk` **14.3 MB** (limit 20) |
| Unit | `./gradlew :app:testDebugUnitTest` | 0 | **335 tests, 335 passed, 0 skipped** (after M6-UI) |
| Lint | `./gradlew :app:lintDebug` | 0 | **0 errors, 17 warnings** |
| Placeholder | `./scripts/check-no-placeholders` | 0 | `No banned markers found` |
| APK size | `./scripts/apk-size-check` | 0 | 14.3 MB, within 20 MB |
| Go vet | `go vet ./...` (core-go) | 0 | no findings |
| Staticcheck | `staticcheck ./...` | 0 | no findings |
| govulncheck | `govulncheck ./...` | 0 | 0 reachable (3 in deps, uncalled) |
| mobsfscan | `mobsfscan app/src/main/java` | 0 | 6 × INFO, no secrets |
| Device | `adb` | — | **NOT VERIFIED** — no device |

**Resolved in M0.** The three failures were one cause: `BundleLoadTest` needed
`assets/operator/bundle.enc`, which is gitignored. M0 replaced them with a fixture-driven suite
(D-57, D-63): 5 bundle tests + 3 acceptance-traceability tests, none skipped, none weakened.

---

## Chronological log

### 2026-10-30 · Phase 0
- Host inspected: Ubuntu 22.04, 4 cores, 3.8 GiB RAM, no KVM, JRE 11 only.
- Upstream pins verified with `git ls-remote`; baseline report written.

### 2026-10-30 · Phase 1
- `SPEC.md` v1.0 (30 FRs), `PLAN.md` (M0–M7), `DECISIONS.md` (D-01…D-16).
- No application code; no build attempted.

### 2026-10-30 … 10-31 · implementation, 0.1.0 → 0.11.2 (43 commits)
Reconstructed from `git log`; per-release detail was never written to this file.

| Range | Commits | Substance |
|---|---|---|
| 0.1–0.4 | 39a3032 … 8be6f22 | first connection, both cores in one `.so`, IPv6/LAN/DNS/stats parity |
| 0.5–0.6 | 65373a1 … 15ab154 | in-app self-test, traffic-counter fix, geo presets |
| 0.7.0–0.7.4 | a0ce8db … f60fddd | **architecture change** to tun2socks→SOCKS; fd dup; bridge actually started; handover hardening; config parity |
| 0.8.0–0.8.3 | 27e4394 … f067675 | Compose redesign, APK packaging, crash recorder |
| 0.9.0–0.9.3 | 783986d … d7c5665 | DPI-evasion card, self-test fixes, JVM rendering, behavioural suites |
| 0.10.0–0.10.2 | bf01e43 … 73bf67f | green only after a SOCKS probe, domestic DNS, R8, perf |
| 0.11.0–0.11.2 | 6988257 … 689833a | keepalive/pinning/metered, watchdog no longer probes through its own tunnel |

Known-missing from the log: any written evidence for 0.10–0.11 on a device. Every claim in that range
is `NOT VERIFIED` except where `STATUS.md` cites a captured log.

### 2026-10-31 · takeover
- Handover read; repo cloned; docs audited. Found the drift above and corrected the record here.
- Toolchain provisioned (JDK 17, SDK 36, NDK 28.2, Go 1.27.3, detekt, mobsfscan) and every gate ran.
- Pinned upstream sources restored at their exact commits:
  `xray-core 0afec66` (v26.10.29), `amneziawg-go b5928ef` (v3.1.20260828), `tun2socks 8dda19e` (v2.7.0).

### 2026-10-31 · Phase 0.5 research
- `docs/RESEARCH_CATALOG.md`: 146 entries, 9 categories, 15-item shortlist, explicit rejections.
- Key finding: Iran moved to a **protocol whitelist** (arXiv 2507.14183), so random-looking transports
  are now worse than TLS-shaped ones, and REALITY IPs get graylisted (Xray #2054/#2768/#3269).

### 2026-10-31 · M0 — harness correction (0.11.3)
- `scripts/apk-size-check` added (20 MB budget, exits 2 when the APK is missing).
- `scripts/check-no-placeholders` hardened: banned markers, `@Ignore`/`@Disabled`/`Assume.*`/`.skip(`/
  `xit(`/`xfail`/`pytest.mark.skip`, vacuous assertions, empty catch blocks, empty test bodies.
  Verified it *fails* on a probe file containing `@Ignore` and returns green after the probe was removed.
- `OperatorBundleLoader` split into `readAsset` + `decrypt` (internal, D-63); `BundleLoadTest` rewritten
  around a generated fixture: success, wrong password, empty password, corrupt header, and one test that
  pins the shipped-asset behaviour for whichever configuration the build has.
- `AcTraceabilityTest` added: every AC must have a runnable command, and every referenced test class
  must exist in sources or be planned. It caught two AC rows with shorthand instead of a command.
- Gate: **100/100 tests, lint 0 errors / 15 warnings, APK 14.3 MB, placeholder exit 0.**

### 2026-10-31 · M4-U3 — full parity audit and test-all
- Audited their fork commit by commit (`docs/AUDIT_PATTNG_PARITY.md`): 5 non-upstream features; 3 ported,
  1 equivalent, 2 deliberately not ported (DoH `+one`, ECH `+one` — dead code without DoH).
- `TEST ALL n` measures every profile sequentially; skipped where another profile is live (D-109).
- Import now understands `xhttp`/`splithttp` and carries `fm` for trojan too; a fragment mask on a
  REALITY link is dropped with a warning instead of breaking the handshake.
- Renderer emits the core's exact `finalmask` key (D-110).

### 2026-10-31 · M4-U2 — the importer was lossy
- Operator pasted three working links; none worked in the app. Cause: `encryption` forced to `none`
  (post-quantum VLESS), `httpupgrade` written as `wsSettings`, `fm` ignored.
- All three fixed; concatenated clipboard links (no newline) are now split correctly.
- Verified end to end: the three rendered configs pass `xray run -test` and return **HTTP 204**
  through the real server (1.08–1.32 s). From this host, not from Iran.

### 2026-10-31 · M4-U — paste import and a delay number that can fail
- `ConfigUri` parses vless/vmess/trojan/ss; `ImportBatch` isolates a bad line instead of dropping the
  whole clipboard; `ProfileStore` persists and dedupes.
- The TCP-handshake "ping" is gone. `RealDelay` measures first byte through the proxy: live tunnel if
  it carries that profile, otherwise a temporary core on 11808, never two cores at once.
- UI: PASTE in the profiles header, a delay chip per row, per-row busy state. Rendered on the JVM
  (`profiles_paste.png`) because there is no device.
- Gate: **309/309 tests**, lint 0 errors / 17 warnings, APK 14.3 MB.

### 2026-10-31 · M4-C (second pass) — `merge`, `finalMask`, `unsafe`
- Parity audit against the reference core's actual source, not its README. Three gaps closed:
  `merge` TCP mask (`0003`), per-profile `finalMask` with validation, `unsafe` fingerprint.
- `merge` proven on this host: three 2-byte writes became one 6-byte read after 241 ms.
- Patches now ship Go tests, and `scripts/build-core.sh` runs them before binding.
- Correction to D-73 recorded as D-102: `unsafe` is valid for TLS (the core special-cases it) and
  invalid for REALITY. Allow-list is 62 names.
- Gate: **282/282 tests**, lint and placeholder clean.

### 2026-10-31 · M4-C — operator config compatibility and `oob`
- **Found by running the pinned core**: the shipped profile shape (plaintext VLESS to a public host) is
  refused at config load. The app could not start its core at all.
- `core-go/patches/0001` downgrades that guard to a warning; `0002` adds `sockopt.dialMode = "oob.<b…>"`.
  `scripts/build-core.sh` applies them idempotently at the pinned commit and rebuilds the AAR (28 MB,
  43.3 MB per-ABI `.so`).
- `oob.0` proven end to end on this host: a local listener received exactly the 5 bytes sent.
- Kotlin: `Profile.dialMode` / `Profile.cipherSuites`, `DialModeRules` validation, renderer support.
- Gate: **270/270 tests, lint 0 errors / 15 warnings, placeholder exit 0, APK 15 MB.**
- **NOT VERIFIED:** whether `oob` defeats a specific ISP; that needs a device.

### 2026-10-31 · M4-N — tile reliability, notification, telemetry
- Research first: `docs/RESEARCH_TILE_NOTIFICATION_TELEMETRY.md`, 21 findings with sources.
- **Tile**: `TileSyncController` (one push per distinct state, forced render on listen, 5 s staleness
  refresh), weak reference cleared on stop. Root cause: it only rendered from `onStartListening`.
- **Notification**: `VpnNotifier` — state, both byte totals, Disconnect, Pause/Resume; one immutable
  snapshot; 1 Hz from the same poll as the UI. Pause = documented disconnect + Resume (D-90).
- **Telemetry**: the counters were never broken — `%.2f GB` printed 5 MB as `0.00 GB` (D-93).
  `ByteFormat` is unit-adaptive, `TrafficSnapshot.available` distinguishes zero from unknown, and the
  counters reset per session.
- Gate: **263/263 tests, lint 0 errors / 15 warnings, placeholder exit 0.**
- **NOT VERIFIED:** shade behaviour, notification swiping, Doze — all need a device.

### 2026-10-31 · M4-W — widget + quick-settings tile
- `surfaces/`: `SurfaceState` (7 projections), `SurfaceStateMapper`, `TileStateMapper`, `TunnelController`
  (the single entry point), `SurfaceStore` (cold-start profile lookup), `SurfaceUpdater` (one reactive
  collector on `Tunnel.state`; no polling).
- `widget/MohammadWidgetProvider` (RemoteViews, 2x1 and 4x1, resizable) and
  `tile/MohammadTileService` (listening flag, no references kept after `onStopListening`).
- `MainActivity` now delegates to `TunnelController`; the service resolves `ACTION_TOGGLE` with a pure
  function so the dial, widget and tile provably share one path (D-84).
- Zero new permissions: `aapt2 dump permissions` still lists the same five.
- Gate: **224/224 tests, lint 0 errors / 15 warnings, APK 14.3 MB, placeholder exit 0.**
- Snapshots: `app/build/reports/surfaces/widget_{idle,connecting,connected,failed}.png`.
- **NOT VERIFIED:** real launcher rendering, tile binding by the system, and Doze/reboot behaviour —
  all need a device.
- Open defect recorded: the AmneziaWG branch reports connected without a probe (D-86); AC-W9
  (tile long-press) is not implementable on the public API (D-87).

### 2026-10-31 · trial build for the operator (0.14.0)
- Inspected the bundle shipped in the public 0.8.0 APK: decrypts, **1 profile**, VLESS over WS,
  `security: none`, and **`defaultMtu: 1500`** — which overrode M1's new default (D-80).
- Re-encrypted that bundle with the same password and only `defaultMtu` changed to 1400; embedded it
  in a local build. `app-debug.apk` now contains `assets/operator/bundle.enc`.
- Gate: 169/169 tests (the shipped-bundle test now runs the success path), APK 14.3 MB.
- The password and the bundle are git-ignored; `git check-ignore` confirms both.

### 2026-10-31 · D-78 constraint harness (pre-M4)
- `NoHardcodedEndpointsTest`: scans production string literals for UUIDs, non-infrastructure IPs,
  unknown hostnames, 43-char base64url and 8–16 char hex. Added as AC-39 and to the continuous gate.
- It found and removed two dead constants: `TUN_ADDRESS = "10.10.14.1"` and `TUN_PREFIX = 30` in
  `MohammadVpnService`, which disagreed with `TunPlan.IPV4_ADDRESS = "10.10.10.1"`.
- SPEC gains SRC-1…SRC-5 and AC-39…AC-41; PLAN's M4 acceptance now requires 1/2/N-profile coverage.

### 2026-10-31 · M3 — REALITY + XTLS Vision (0.14.0)
- `core/RealityRules`: field rules taken from the pinned core's config layer — hostname-shaped
  `serverName`, base64url-unpadded 32-byte `publicKey`, hex `shortId` ≤ 16 chars (empty allowed),
  `spiderX` starting with `/`, and Vision as the only flow (D-74).
- `validateReality` rejects a REALITY profile with a missing/invalid field, or combined with a
  `finalmask` fragment (D-76); error messages never echo the key or the short id.
- `Profile.flow` added; the renderer writes it onto every VLESS user and suppresses fragment for
  REALITY, logging `fragment suppressed` instead of silently degrading (D-76).
- Correction: `hellogolang` removed from the fingerprint allow-list — the core's config layer rejects
  it, so 62 → 61 (D-73).
- `docs/RELEASE_NOTES.md` states that 0.14.0 ships with the 0.15.0 fallback (D-49).
- Gate: **159/159 tests, lint 0 errors / 15 warnings, APK 14.3 MB, placeholder exit 0.**
- **NOT VERIFIED:** whether REALITY survives any specific ISP; whether Vision improves throughput.

### 2026-10-31 · M2 — uTLS fingerprint (0.13.0)
- `core/TlsFingerprint`: 62-name allow-list **parsed from the pinned Xray source**
  (`transport/internet/tls/tls.go`), not from documentation; `unsafe` excluded because the core never
  assigns it a value (D-69).
- `Profile.fingerprint` added (additive). Validation: required for `tls`/`reality`, rejected when
  `security: none`, checked against the allow-list; `reality` now requires a `realitySettings` object
  (D-70, D-71).
- `XrayConfig` writes the profile-level fingerprint into `tlsSettings` or `realitySettings`.
- `golden/security-none.json` captured from the **M1** build first; the golden test proves M2 changed
  zero bytes for existing profiles (D-72).
- Gate: **138/138 tests, lint 0 errors / 15 warnings, APK 14.3 MB, placeholder exit 0.**
- **NOT VERIFIED:** whether the new ClientHello actually survives a specific ISP's classifier.

### 2026-10-31 · M1 — MTU / MSS / `sockopt` (0.12.0)
- `core/MtuPolicy`: resolution profile → bundle → **1400**, strict 1200–1500 validation, `segmentSize`,
  and the `mtu resolved:` diagnostic line. 8 tests.
- `Profile.mtu` added as an optional field (additive, D-65). `defaultMtu` default changed to 1400 and
  out-of-range values are now rejected instead of clamped (D-67).
- `XrayConfig.applySockopt(outbound, mtu)` now emits `tcpMaxSeg = min(mtu−40, 1440)` and
  `tcpUserTimeout = 30000`, merging with explicit operator values; keepalive stays 25/15. 5 tests.
- `MohammadVpnService` resolves the MTU once per connect and logs
  `mtu resolved: configured=… effective=… source=profile|bundle|default`.
- `BundleParseTest`: 1500 → 1400 (D-64, logged before the edit) + 5 new parse/validation tests.
- Gate: **119/119 tests, lint 0 errors / 15 warnings, APK 14.3 MB, placeholder exit 0.**
- **NOT VERIFIED:** whether 1400 actually fixes the stall — needs a device run (SPEC §11, J-12).

### 2026-10-31 · Phase 1 (v2.0)
- `SPEC.md` v2.0: 12 requirement groups, 10-state machine, path policy, remote-config contract,
  38 ACs mapped to real test classes, 14 failure journeys, device register.
- `PLAN.md` v2.0: M0–M7 in the approved order, M-DEV, four operator actions.
- `DECISIONS.md`: D-43…D-60 added (research adoption, scope cut, MTU supersede, test-fixture policy).
- Persian/RTL removed from scope by operator decision.

---

## Next action

**M4 — state machine + path policy (0.15.0).** Awaiting operator approval. Scope: ST-1…ST-9,
HP-1…HP-6, PATH-1…PATH-8, OBS-1…OBS-3, UI-3, NFR-15. Ten states before any policy (D-50), ordered
failover with jitter and hysteresis, per-path circuit breaker, ordered shutdown.

## Known blockers

1. **No device / no KVM** — every row in `SPEC.md §11` stays `NOT VERIFIED`. M-DEV defines the procedure.
2. **AmneziaWG server** — never supplied; OP-2 blocks AWG-4.
3. **`/tmp` source pins** — `core-go/go.mod` points at `/tmp/xray-src`, `/tmp/awg-src`, `/tmp/t2s`.
   They vanish on restart; D-51 tracks vendoring them into the repo.
4. **Bundle asset** — `assets/operator/bundle.enc` is gitignored, so a clean clone cannot produce a
   working APK. Intentional; the operator supplies it.
5. **3 GiB RAM** — serial builds only; `gomobile bind` untested at this size.
