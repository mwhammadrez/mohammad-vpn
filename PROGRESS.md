# PROGRESS.md — Mohammad (`ir.mohammad.vpn`)

**Last updated:** 2026-10-31 (UTC)
**Current phase:** M0 complete → M1 not started
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
| M1 | MTU / MSS / `sockopt` | ⬜ not started | — | the open user-facing bug |
| M2 | uTLS fingerprint | ⬜ not started | — | cheapest detection win |
| M3 | REALITY + Vision | ⬜ not started | — | ships together with M4 |
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
| Unit | `./gradlew :app:testDebugUnitTest` | 0 | **100 tests, 100 passed, 0 skipped** (after M0) |
| Lint | `./gradlew :app:lintDebug` | 0 | **0 errors, 15 warnings** |
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

### 2026-10-31 · Phase 1 (v2.0)
- `SPEC.md` v2.0: 12 requirement groups, 10-state machine, path policy, remote-config contract,
  38 ACs mapped to real test classes, 14 failure journeys, device register.
- `PLAN.md` v2.0: M0–M7 in the approved order, M-DEV, four operator actions.
- `DECISIONS.md`: D-43…D-60 added (research adoption, scope cut, MTU supersede, test-fixture policy).
- Persian/RTL removed from scope by operator decision.

---

## Next action

**M1 — MTU / MSS / `sockopt` (0.12.0).** Awaiting operator approval. Scope: MTU-1…MTU-8,
`MtuPolicy`, `tcpMaxSeg`/`tcpUserTimeout`/keepalive rendering, the `mtu resolved:` diagnostic line.
No MTU value may be claimed as fixed until a device captures a large transfer.

## Known blockers

1. **No device / no KVM** — every row in `SPEC.md §11` stays `NOT VERIFIED`. M-DEV defines the procedure.
2. **AmneziaWG server** — never supplied; OP-2 blocks AWG-4.
3. **`/tmp` source pins** — `core-go/go.mod` points at `/tmp/xray-src`, `/tmp/awg-src`, `/tmp/t2s`.
   They vanish on restart; D-51 tracks vendoring them into the repo.
4. **Bundle asset** — `assets/operator/bundle.enc` is gitignored, so a clean clone cannot produce a
   working APK. Intentional; the operator supplies it.
5. **3 GiB RAM** — serial builds only; `gomobile bind` untested at this size.
