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
