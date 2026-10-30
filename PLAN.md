# PLAN.md — Mohammad (`ir.mohammad.vpn`)

**Version:** 1.0 (Phase 1) · **Status:** awaiting operator approval

Rule inherited from the master prompt: one milestone at a time, test-first, real command evidence, `STOP` after each milestone report. No milestone may start before the previous one's gates are green.

---

## Milestone map

| ID | Name | Outcome | Device needed | Est. |
|---|---|---|---|---|
| M0 | Walking skeleton | Installable debug APK, Connect screen, state machine, bundle loader (validated but empty-safe), lint/test/placeholder gates | no | 1 session |
| M1 | Core integration | Real Xray + AWG AARs, TUN creation, connect/disconnect against the operator profile | **yes** (for proof) | 2–3 sessions |
| M2 | Operator bundle | Encrypted asset pipeline, schema validation, profile selector, redaction | no | 1 session |
| M3 | UI states & errors | Full state matrix, exact copy, accessibility, log panel with 1,000-entry test | no | 1 session |
| M4 | Diagnostics | Ping (real, 5 s timeout), category-filtered log, clear-log, traffic counters | partly | 1 session |
| M5 | Hardening | Security audit, dependency audit, failure injection, timeouts, concurrency, redaction audit, performance | partly | 1 session |
| M6 | Independent review | Read-only review of the full diff vs SPEC, `REVIEW.md` | no | 1 session |
| M7 | Handover | README, `.env.example`, verification report, acceptance checklist, tag | no | 1 session |

---

## M0 — Walking skeleton

**Goal:** a real, installable app with the full state machine but a fake core.

**Deliverables**
- Gradle project: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper 9.8.2, AGP 9.4.1, Kotlin, JDK 17 toolchain.
- Modules: `:app`, `:core-api` (interfaces only, no JNI).
- `TunnelState` sealed hierarchy, `TunnelError` typed errors, `TunnelController` with serialized dispatcher.
- `ConnectScreen` with exact copy from SPEC §8, `ConnectionViewModel`, `LogPanel`.
- `ProfileRepository` interface + in-memory fake; `OperatorBundleLoader` stub returning a **validated empty** result (no secrets in M0).
- `scripts/check-no-placeholders`, `scripts/apk-size-check`, `scripts/verify-env`.
- CI-ready config: `org.gradle.jvmargs=-Xmx1536m`, `parallel=false`, `workers.max=2`.
- `docs/VERIFICATION_REPORT.md` (empty section per command).

**Acceptance:** AC-05, AC-16, AC-17, AC-27, AC-36, AC-41, AC-43, AC-48; `assembleDebug` produces an APK; UI screenshot from Compose preview test.

**Exit gate:** `./gradlew clean testDebugUnitTest lintDebug assembleDebug` green + placeholder gate green.

---

## M1 — Core integration (highest risk)

**Goal:** the app actually tunnels traffic with the operator's VLESS profile.

**Steps**
1. Provision toolchain (JDK 17, SDK 36, build-tools 36.0.0, NDK 28.2.13676358, Go 1.27.3, gomobile).
2. `core-xray/build.sh`: `git clone --depth 1 --branch v26.10.29 https://github.com/XTLS/Xray-core` → `gomobile bind -target android/arm64,android/amd64 -androidapi 26` → `xray.aar`; record SHA-256.
3. `core-awg/build.sh`: `git clone --branch v3.1.20260828 https://github.com/amnezia-vpn/amneziawg-go` → gomobile wrapper module `libv2ray` → `awg.aar`; record SHA-256.
4. Commit checksums only; AARs cached under `artifacts/` (git-ignored) and never rebuilt unless `-PbuildCores=true`.
5. `XrayBridge` / `AwgBridge` implementing `CoreBridge` with `start(config, fd)`, `stop()`, `isRunning`, `measureDelay`, status callback.
6. `VpnInterfaceFactory`: MTU, loopback-free IPv4 (e.g. `10.10.14.1/30` from bundle), `0.0.0.0/0` route, DNS from bundle, own package disallowed.
7. `ConfigRenderer`: profile.outbound → Xray JSON (`log/stats/policy/inbounds[socks]/outbounds[routing]/dns`); VLESS+ws+`security=none` for the operator profile.
8. Connect the operator profile and prove traffic (device required).

**Acceptance:** AC-06, AC-07, AC-08, AC-09, AC-10, AC-12, AC-13, AC-14, AC-22, AC-24, AC-26, AC-46 (conditional), AC-47 (conditional).

**Exit gate:** all non-device ACs green; device ACs explicitly `NOT VERIFIED (no device)` if no device; AAR checksums recorded.

**Risks:** gomobile + Go 1.27 on 3.8 GiB RAM (mitigate `GOMAXPROCS=2`, `GOFLAGS=-p=2`, build serially per ABI); Xray `mlkem768x25519plus` encryption requires the pinned version — do not downgrade.

---

## M2 — Operator bundle

**Goal:** the shipped app contains exactly one encrypted profile and survives tampering.

- `scripts/encrypt-operator-bundle` (PBKDF2-SHA256 210k + AES-256-GCM, format per SPEC §4.3).
- `OperatorBundleLoader`: decrypt in IO, 5 s timeout, strict schema validation, typed errors.
- Build-time guard: fail the build if the asset's plaintext hash equals a known-leaked fixture, or if `bundle.enc` is absent in a `debug`+`release` config.
- Profile selector UI + persistence (DataStore).
- Redaction utility + unit tests.

**Acceptance:** AC-01, AC-02, AC-03, AC-04, AC-18, AC-23, AC-29, AC-31.

---

## M3 — UI states, errors, accessibility

Complete the state matrix, exact copy, focus order, dark/light, 1,000-entry log performance test, `contentDescription` on all controls.

**Acceptance:** AC-19/21/42/44 plus regression of M0/M1 UI ACs.

---

## M4 — Diagnostics

Real ping through `measureDelay` (or a TCP/TLS handshake probe when the core is down — decide in-session), 5 s timeout with cancellation, structured log with correlation id, traffic counters, clear-log.

**Acceptance:** AC-19, AC-20, AC-45, plus J-7 journey test.

---

## M5 — Hardening

Security audit (bundle at rest, `allowBackup=false`, `dataExtractionRules`, exported components), dependency audit (`./gradlew dependencyCheckAnalyze` or manual CVE review of pinned versions), failure injection matrix (J-1…J-10), timeout tests with virtual clock, 100 randomized concurrent ops, redaction fuzz, `adb shell am start -W` cold-start (conditional), APK size gate.

**Acceptance:** all remaining ACs; `docs/HARDENING_REPORT.md`.

---

## M6 — Independent review

Read-only pass against `SPEC.md` only; `REVIEW.md` with Blocker/Major/Minor; fix blockers/majors; re-review.

## M7 — Handover

`README.md`, `.env.example` (names only), `docs/VERIFICATION_REPORT.md`, acceptance checklist, tag `v0.1.0-debug`.

---

## Sequencing constraints

1. M1 cannot be declared done without a device; it can be *implemented* and *unit-verified*.
2. M2 depends on M1's `Profile` model being final.
3. M5 depends on M3+M4.
4. Any change to `SPEC.md` re-opens the affected ACs.

## Build pipeline (pinned)

```
JDK 17 · AGP 9.4.1 · Gradle 9.8.2 · Kotlin (catalog) · compileSdk 36 · targetSdk 36
build-tools 36.0.0 · NDK 28.2.13676358 · Go 1.27.3 · gomobile @ v0.0.0-20261011034528-197770444867
Xray-core v26.10.29 (0afec66085a85191e9dd2a4a6646c7c9a94688d1) · MPL-2.0
amneziawg-go v3.1.20260828 (b5928efb6ca19f0153958460c3d141f04abc5c2e) · MIT
ABIs (debug): arm64-v8a, x86_64
```
