# PLAN.md — Mohammad (`ir.mohammad.vpn`) — v2.0

**Date:** 2026-10-31 · **Basis:** `SPEC.md` v2.0 + `docs/RESEARCH_CATALOG.md` shortlist
**Current release:** 0.11.2 (versionCode 29) · **Target release:** 0.12.0 … 0.18.0 (one minor per milestone)

Milestone order is fixed by the approved shortlist. It is not a preference: M4 depends on M1's
diagnostics, and M7 exists to deliver M1–M6 to installed users without a release.

---

## Milestone map

| ID | Milestone | Shortlist | Version | Product-visible change | Device needed to close? |
|---|---|---|---|---|---|
| M0 | Harness correction | — | 0.11.3 | none (gate quality only) | no |
| M1 | MTU / MSS / sockopt | #1, #2 | 0.12.0 | connection stops cutting | **yes** (behaviour) |
| M2 | uTLS fingerprint | #3 | 0.13.0 | quieter on the wire | no |
| M3 | REALITY + Vision | #4 | 0.14.0 | survives protocol filtering | **yes** (survival) |
| M4-W | Widget + quick-settings tile | — | 0.14.1 | connect from the home screen | **yes** (launcher) |
| M4-N | Tile reliability, notification, telemetry | — | 0.14.2 | live notification, working counters | **yes** (shade) |
| M4-C | Config compatibility + `oob` dialMode | — | 0.14.3 | the current bundle can actually start | **yes** (DPI) |
| M4 | State machine + path policy | #5, #6, #7 | 0.15.0 | multi-path, honest states | no |
| M5 | Pre-flight netcheck | #8 | 0.16.0 | fewer wrong assumptions | **yes** (measurement) |
| M6 | Second path (WS+TLS via CDN) | #10 | 0.17.0 | works when the IP dies | **yes** (survival) |
| M7 | Signed remote config | #13 | 0.18.0 | operator changes behaviour remotely | no |
| M-DEV | Device verification protocol | — | — | none (evidence) | **is** the device work |
| OP-1 | BBR on the server | #9 | — | throughput | operator action |
| OP-2 | AWG server + per-server v3 params | #11 | — | fallback path exists | operator action |

`M-DEV` is not a code milestone. It is the standing procedure that turns any `NOT VERIFIED` row in
`SPEC.md §11` into a verified one, and it is the only way AC-38 can ever pass.

---

## M0 — Harness correction (0.11.3)

**Goal.** Make the gates trustworthy before changing behaviour. No product behaviour change.

**Covers:** AC-1 (fixture), AC-34, AC-35, AC-36; and **proves the mapping only** for AC-2, AC-11,
AC-16, AC-17, AC-29, AC-31 — each `--tests` filter must select at least one real test before the
milestone that owns it begins.

**Tasks**

1. Widen `scripts/check-no-placeholders`: add `.skip`, `.only`, `xit`, `xfail`, `@Ignore`,
   `pytest.mark.skip`, `assertTrue(true)`, `@Suppress` without a reason, and empty function bodies.
   Keep the existing patterns.
2. Add `scripts/apk-size-check` (threshold 20 MB, reads `app/build/outputs/apk/debug/app-debug.apk`).
3. Re-point the AC table rows at the real test classes that exist today (already done in `SPEC.md`;
   this task proves each `--tests` filter actually selects ≥ 1 test).
4. Resolve the three `BundleLoadTest` failures **without weakening anything** (D-57):
   - add a test fixture builder that encrypts a synthetic bundle with a fixed test password, so the
     loader's success path is always exercised;
   - keep one test that asserts the *shipped* bundle decrypts **when the operator password is
     configured**, and asserts the typed `operator password not configured` failure when it is not.
     Both branches are real assertions; neither is skipped.
5. Record the real baseline in `PROGRESS.md` (commands + output, not claims).

**Tests first:** `BundleLoadTest` rewritten to cover both branches; a meta-test that asserts every
`--tests` filter in `SPEC.md §9.2` matches at least one test class.

**DoD:** all four gates green with no operator password present; `PROGRESS.md` shows the command output.
**Rollback:** `git revert` of the M0 commit; no product code touched.

---

## M1 — MTU / MSS / `sockopt` (0.12.0)

**Goal.** Remove the PMTU black hole. This is the fix for "works a few seconds, then cuts".

**Covers:** MTU-1 … MTU-8, NFR-11, AC-3 … AC-7, AC-29.

**Why this is first.** The symptom is exactly a black hole: handshake and small responses fit, the
first large response does not, and nothing reports an error because ICMP is filtered on Iranian mobile
networks (catalog 103). Our TUN advertises 1500, tun2socks performs no MSS clamping (catalog 42), and
Xray's `sockopt` is never rendered (catalog 115).

**Tasks**

1. `MtuPolicy` (pure): resolution order profile → bundle → 1400, range validation 1200–1500,
   `source` enum for logging.
2. `Bundle` default becomes 1400; `defaultMtu` range check tightened (no silent clamp to 1500).
3. Render `sockopt`: `tcpMaxSeg = min(mtu − 40, 1440)`, `tcpUserTimeout = 30_000`,
   `tcpKeepAliveIdle = 25`, `tcpKeepAliveInterval = 15`, without overriding explicit profile values.
4. Diagnostics: one log line per connection `mtu resolved: configured=… effective=… source=…`.
5. Stall detector stub wired to the existing data-path policy (full ST-6 behaviour lands in M4).

**Tests first:** `MtuPolicyTest` (ordering, bounds, source), `XraySockoptTest` (four keys, precedence,
absence when `security:none` is irrelevant), `TunPlanTest` extension for MTU.

**Explicit non-goal:** no PLPMTUD yet. M5 adds measurement; M1 only stops the bleeding.

**Verification:** `./gradlew :app:testDebugUnitTest --tests "*MtuPolicyTest" --tests "*XraySockoptTest"`,
then the full suite, lint, assemble.

**Risk.** A lower MTU costs throughput if 1500 was actually fine. Mitigation: the value is
config-driven, and the remote config (M7) can change it for all users in one publish.

**Rollback:** revert the M1 commit; `defaultMtu` returns to 1500.

---

## M2 — uTLS fingerprint (0.13.0)

**Goal.** Stop emitting a ClientHello that no browser produces.

**Covers:** TLS-2, TLS-7, NFR-1, AC-9, AC-11.

**Tasks**

1. `fingerprint` becomes a first-class field, validated against a fixed allow-list
   (`chrome`, `firefox`, `safari`, `ios`, `android`, `edge`, `random` — the exact set is fixed by the
   Xray version we pin, and must be read from it, not guessed).
2. Validation: `security ∈ {tls, reality}` without `fingerprint` → `BundleException` with a message
   naming the profile id only.
3. Renderer emits `streamSettings.tlsSettings.fingerprint` / `realitySettings.fingerprint`.
4. Redacted log records the profile name, never the fingerprint string or JA3.
5. Golden test that `security:none` output is byte-identical to 0.12.0 (backward compatibility).

**Tests first:** `TlsFingerprintTest` (accept, reject, precedence), `ConfigRenderTest` golden.

**Risk.** A fingerprint that does not match the pinned Xray build is worse than none. The allow-list is
derived from the pinned core, not from documentation.

**Rollback:** revert; `security:none` profiles are untouched by construction.

---

## M3 — REALITY + XTLS Vision (0.14.0)

**Goal.** Make the client survive protocol filtering, not just IP filtering.

**Covers:** TLS-1 … TLS-6, CFG-3, AC-8, AC-10, AC-11.

**Tasks**

1. `RealityConfig` model + validation (`serverName` hostname, `publicKey` non-empty, `shortId` hex).
2. Renderer emits `realitySettings` with `serverName`, `publicKey`, `shortId`, `fingerprint` and
   `spiderX` if present.
3. `flow: xtls-rprx-vision` accepted only with `security: reality`; rejected elsewhere.
4. Hard validation: `reality` + `finalmask.fragment` is rejected (TLS-5).
5. Bundle schema v2 accepted alongside v1; v1 bundles keep working.

**Tests first:** `RealityConfigTest` (full render, missing field, flow with non-reality, reality+fragment).

**Explicitly out:** choosing the decoy SNI, standing up the server, and any claim that REALITY survives
a specific ISP. Those are operator actions plus M-DEV evidence.

**Risk.** Catalog 86: REALITY IPs get graylisted in Iran. Therefore M3 ships **only together with**
M4's fallback — the release note must say so.

**Rollback:** revert; v1 bundles and `security:none` still work.

---

## M4-C — Operator-config compatibility and `oob` (0.14.3)

**Goal.** Make the cores accept the configuration the operator actually ships, and give the client one
transport trick worth having.

**Trigger.** Running the pinned core against the shipped profile shape proved it never starts:

```
infra/conf: vless without TLS or other encryption is prohibited unless the server address is a private IP or domain
```

Upstream added that guard in #6303; the operator's bundle is exactly the rejected shape.

**Covers:** FR-O1 … FR-O5, AC-O1 … AC-O4. Audit: `docs/AUDIT_PATTNG.md`.

**Tasks**
1. `core-go/patches/0001-allow-unencrypted-outbound.patch` — downgrade the guard to a warning.
2. `core-go/patches/0002-oob-dialmode.patch` — `sockopt.dialMode` with `oob.<bytes>`.
3. `scripts/build-core.sh` — clone at the pin, apply patches idempotently, run each patch's Go tests,
   `gomobile bind`, strip, copy.
4. `core-go/patches/0003-merge-finalmask.patch` — a `merge` TCP mask in the core's existing
   `finalmask` framework, so the client can coalesce instead of only fragmenting.
5. Kotlin: `Profile.dialMode`, `Profile.cipherSuites`, `Profile.finalMask`, `DialModeRules` and
   `FinalMaskRules` validation, renderer support. `unsafe` is accepted for TLS only.

**Verification that is real:** `oob.0` was proven end to end on this host by running the patched CLI
core against a local listener: the listener received exactly the 5 bytes sent, proving the urgent byte
is stripped and the stream is intact.

**Verification for merge:** three 2-byte writes through the patched core against a local listener
arrived as **one** 6-byte read after 241 ms (delay 200 ms) — the coalescing window and payload
integrity both hold.

**Not verified:** that `oob` or `merge` defeats any specific ISP; that either survives REALITY on a
real line; that a carrier-grade NAT keeps URG-bearing segments. All device work.

## M4-N — Tile reliability, notification and telemetry (0.14.2)

**Goal.** Three reported defects and one new surface: a tile that lies about being connected, a
notification that says nothing useful, and counters that read zero while data flows.

**Covers:** FR-N1 … FR-N14, AC-N1 … AC-N14. Research: `docs/RESEARCH_TILE_NOTIFICATION_TELEMETRY.md`.

**Root causes found before writing code**

| Symptom | Actual cause |
|---|---|
| Tile stays inactive after connecting | it only rendered from `onStartListening`; a change while the shade was closed was never pushed (D-91) |
| In-app tiles show "nothing" | not a counter bug: `%.2f GB` rendered 5 MB as `0.00 GB` (D-93) |
| Notification is uninformative | it carried two static strings and no actions (D-92) |

**Tasks**

1. `TileSyncController` + weak service reference: one push per distinct state, unconditional push on
   listen, 5 s staleness refresh, cleared on stop.
2. `VpnNotifier`: one snapshot → one immutable notification; connection state, both totals, Disconnect
   and Pause/Resume actions routed through `TunnelController`.
3. `TrafficSnapshot` with an explicit `available` flag; `ByteFormat` with unit-adaptive, locale-aware
   output; `TelemetryCounter` for deltas with reset handling.
4. Service: 1 Hz stats poll feeding both the notification and the UI from one read; counters reset at
   the top of every connect; `trafficUnavailable()` on a failed read.
5. UI: adaptive units and `—` when unavailable.

**Tests first:** `TileStateSyncTest`, `NotificationContentTest`, `NotificationActionTest`,
`TelemetryCounterTest`, `TelemetryTileTest`, `SecretLeakTest`, `ResearchDocTest`.

**Explicitly not done:** pause does not keep a half-stopped bridge (D-90); the ten-state machine, path
policy and REALITY are untouched.

**Rollback:** revert the M4-N commit; the notification returns to two static strings and the tile to
`onStartListening`-only rendering.

## M4-W — Widget + quick-settings tile (0.14.1)

**Goal.** Connect and disconnect without opening the app, on two system surfaces that cannot lie about
state and cannot drift from the dial.

**Covers:** FR-W1 … FR-W10, AC-W1 … AC-W10. Inserted before M4 because it depends only on the existing
five-state machine; M4 then extends it through the projection instead of rewriting it.

**Tasks**

1. `surfaces/SurfaceState` + `SurfaceStateMapper`: `TunnelState` → what a surface draws. Precedence:
   no profile, then no consent, then the tunnel state. **This is the seam M4 plugs into** (D-83).
2. `surfaces/TunnelController`: the single entry point. `MainActivity` delegates; the widget and tile
   send its intents unchanged (D-84).
3. `widget/MohammadWidgetProvider`: RemoteViews, resizable 2x1/4x1, per-state icon/title/action, one
   immutable PendingIntent that falls back to opening the app when nothing can be toggled.
4. `tile/MohammadTileService`: listening flag, no retained references, three tile statuses.
5. `surfaces/SurfaceStore`: cold-start profile lookup from the persisted selection (D-85).
6. `surfaces/SurfaceUpdater`: one reactive collector on `Tunnel.state`; no polling.

**Tests first:** `SurfaceStateMapperTest`, `TileStateMapperTest`, `WidgetInteractionTest`,
`TileInteractionTest`, `SurfaceColdStartTest`, `SurfaceResourceScanTest`, `WidgetResizableTest`,
`SurfaceProbeGateTest`, `SurfaceLifecycleTest`, `WidgetRenderTest` (incl. four snapshots).

**Acceptance:** the ten AC-W rows in `SPEC.md §9.2`; zero new permissions
(`aapt2 dump permissions`); lint warnings not above the M3 baseline of 15.

**Deliberate exclusions:** no ten-state machine, no path policy, no REALITY change, no Persian/RTL, no
new dependency (RemoteViews chosen over Glance for exactly that reason, D-81).

**Rollback:** revert the M4-W commit; the manifest, provider and tile disappear together and the app
returns to Activity-only entry.

## M4 — State machine + path policy + anti-flap (0.15.0)

**Goal.** Ten honest states and deterministic multi-path behaviour. This is the milestone that makes
`BLOCKED_BY_POLICY` and `DEGRADED` mean something.

**Covers:** ST-1 … ST-9, HP-1 … HP-6, PATH-1 … PATH-8, OBS-1 … OBS-3, UI-3, NFR-15, AC-12 … AC-21,
AC-30, AC-31, AC-33.

**Why the state machine comes before the policy.** Building `PathPolicy` on the current five states
would repeat the 0.7.0 failure: policy, UI and service each built and never wired to each other.

**Tasks**

1. `ConnectionStateMachine` (pure): transition table exactly as `SPEC.md §7.2`, rejecting unknown
   edges. No Android imports.
2. `PathPolicy` (pure): ordered failover, capped backoff **with jitter**, `minDwellMs`, return-to-preferred.
3. `CircuitBreaker` (pure): closed/open/half-open per path.
4. `StallDetector` (pure): bytes-in/bytes-out timestamps → `DEGRADED`/`RECOVERING`.
5. Wire the machine into `MohammadVpnService`; UI derives the dial from the state, not from booleans.
6. Ordered shutdown (NFR-15) with a test that asserts the order.
7. Telemetry tile tests: live values and zeros.

**Tests first:** `ConnectionStateMachineTest`, `PathPolicyTest`, `AntiFlapTest`, `CircuitBreakerTest`,
`StallDetectionTest`, `ShutdownOrderTest`, `TelemetryTileTest`, `AccessibilityTest`.
`PathPolicyTest` must exercise **1, 2 and N (≥4) profiles**, an empty fallback list, a single-profile
policy whose only path fails, and duplicate/unknown ids — SRC-1 and SRC-5 are acceptance criteria, not
advice.

**Risk.** This is the largest milestone. Mitigation: split into four commits
(machine → policy → service wiring → UI) with the full gate run between them.

**Rollback:** the machine is additive; reverting the service-wiring commit restores 0.14 behaviour
without losing the pure classes.

---

## M5 — Pre-flight netcheck (0.16.0)

**Goal.** Stop guessing the network. Measure once per network, cache, and let policy use the result.

**Covers:** NET-1 … NET-6, MTU-6, AC-22, AC-23.

**Tasks**

1. `NetcheckPolicy` (pure): cache key derivation (hashed SSID, transport, interface), 24 h TTL,
   2.5 s budget, "inconclusive is not fatal".
2. Probe implementation behind an interface so tests use a fake clock and a fake transport.
3. Feed the result into `MtuPolicy` (may lower, never raise) and into path selection (skip UDP-only
   paths when UDP/443 is measured dead).

**Tests first:** `NetcheckPolicyTest` (cache, TTL, budget expiry, no-raise invariant).

**Risk.** Probing costs a few packets at connect and looks like scanning if done aggressively — the
budget and the 24 h cache exist to bound that.

**Rollback:** revert; MTU falls back to the configured value.

---

## M6 — Second path: WebSocket + TLS via CDN (0.17.0)

**Goal.** Survive the death of one IP. This is the concrete answer to catalog 86 and 91.

**Covers:** SP-1 … SP-5, TLS-1, AC-24.

**Tasks**

1. `transport: websocket` + `security: tls` with independent SNI and Host.
2. Validation that a CDN profile is *not* preferred over a direct REALITY profile (SP-4).
3. UI label "via relay" derived from the path, with no host in it.
4. Documentation of the server side: what the operator must stand up (CDN in front of WS+TLS origin).

**Tests first:** `SecondPathConfigTest` (render with SNI ≠ Host, preference ordering).

**Risk.** CDN terms of service, added latency, and the CDN itself being throttled. This is a fallback,
never the primary.

**Rollback:** profiles simply stop referencing the relay.

---

## M7 — Signed remote configuration (0.18.0)

**Goal.** Deliver MTU, path and transport changes to ~1,000 installed users without an APK.

**Covers:** RC-1 … RC-9, NFR-3, NFR-6, NFR-7, NFR-13, NFR-16, AC-25 … AC-28.

**Tasks**

1. `RemoteConfigVerifier`: Ed25519 over RFC 8785 JCS-canonical JSON, key compiled in.
2. `RemoteConfigVersionPolicy`: strictly increasing `configVersion`; equal/lower ignored.
3. `RemoteConfigApplier`: validate → atomically swap → keep last known-good; built-in bundle is the
   floor.
4. HTTP client: HTTPS only, 10 s connect / 15 s read, 256 KiB cap, content-type check, 3 retries with
   jitter.
5. Encrypted storage for the cached config (Keystore-backed), removed on "clear data".
6. Fetch cadence: 24 h, or 6 h after a failed connection.

**Tests first:** `RemoteConfigVerifierTest`, `RemoteConfigVersionPolicyTest`, `RemoteConfigApplierTest`,
`RemoteConfigFailureTest`.

**Risk.** A signing-key compromise is a remote kill switch. Mitigation: key rotation is designed now
(two key slots), and the app refuses unsigned config by construction.

**Rollback:** remove the URL from the bundle → the feature is dormant, built-in bundle applies.

---

## M-DEV — Device verification protocol (blocked)

Not implementable in this workspace. The deliverable is a procedure, not code.

1. `docs/DEVICE_TEST_PLAN.md` with numbered, repeatable steps.
2. For J-12 (PMTU): `iperf3 -M 1400` and `-M 1500` against the server, plus a `tcpdump` capture on the
   server during a large download.
3. For J-8: airplane-mode toggle, measure time to `CONNECTED`, capture the state history.
4. For AWG: a server profile with per-server v3 parameters, then a traffic capture proving bytes.
5. Evidence format: command, exit code, output excerpt, `NOT VERIFIED` otherwise.

---

## Operator actions (not milestones)

| ID | Action | Why | Evidence required |
|---|---|---|---|
| OP-1 | Enable BBR on the server (`net.ipv4.tcp_congestion_control=bbr`) | throughput on lossy Iran–Europe paths | `sysctl net.ipv4.tcp_congestion_control` output |
| OP-2 | Stand up an AmneziaWG server with **per-server v3** parameters | the fallback exists only on paper | a captured handshake + traffic proof |
| OP-3 | Decide the REALITY decoy (domain + SNI in the same region) | M3 needs a target | the decoy hostname, never committed |
| OP-4 | Rotate the bundle password after the 0.8.0 APK exposure | hygiene | new `bundle.enc` + new password in `local.properties` |

---

## Requirement coverage

| Milestone | Functional requirements covered |
|---|---|
| M0 | none (harness only) |
| M1 | MTU-1, MTU-2, MTU-3, MTU-4, MTU-5, MTU-7, MTU-8 |
| M2 | TLS-1, TLS-2, TLS-6, TLS-7 |
| M3 | TLS-1, TLS-3, TLS-4, TLS-5, CFG-3 |
| M4 | ST-1, ST-2, ST-3, ST-4, ST-5, ST-6, ST-7, ST-8, ST-9, HP-1, HP-2, HP-3, HP-4, HP-5, HP-6, PATH-1, PATH-2, PATH-3, PATH-4, PATH-5, PATH-6, PATH-7, PATH-8, OBS-1, OBS-2, OBS-3, UI-3 |
| M5 | NET-1, NET-2, NET-3, NET-4, NET-5, NET-6, MTU-6 |
| M6 | SP-1, SP-2, SP-3, SP-4, SP-5 |
| M7 | RC-1, RC-2, RC-3, RC-4, RC-5, RC-6, RC-7, RC-8, RC-9 |
| M4-W | FR-W1, FR-W2, FR-W3, FR-W4, FR-W5, FR-W6, FR-W7, FR-W8, FR-W9, FR-W10 |
| continuous | CFG-1, CFG-2, CFG-4, CFG-5, CFG-6, OBS-4, OBS-5, OBS-6, UI-1, UI-2, UI-4, UI-5, SRC-1, SRC-2, SRC-3, SRC-4, SRC-5 |
| M-DEV | AWG-1, AWG-2, AWG-3, AWG-4, AWG-5 (verification only; no client change) |

`continuous` means the requirement is upheld by every milestone and its test runs in the standard gate;
it is not deferred. AWG-1…AWG-5 are already implemented in code and remain `NOT VERIFIED`; M-DEV is the
only place their status can change.

## Sequencing constraints

1. **M0 before any behaviour change.** Gates must be trustworthy first.
2. **M1 before M3.** REALITY on a black-holed path produces flapping that looks like censorship.
3. **M4 before M6.** A second path is useless without a policy to select it.
4. **M7 last.** It is the delivery mechanism for M1–M6; building it first would ship untested knobs.
5. **Never two milestones in one commit.** Each has its own rollback point.
6. **Never weaken a test to make a milestone pass.** If a test blocks a milestone, the spec or the
   decision record changes first, with approval.

## Build pipeline (unchanged, pinned)

JDK 17 · Gradle 8.14.6 · AGP 8.13.2 · Kotlin 2.0.21 · compileSdk/targetSdk 36 · minSdk 26 · NDK 28.2 ·
Go 1.27.3 · `arm64-v8a` only · debug signing only.
`source /home/user/.workspace/env.sh` restores the toolchain paths on this host.
`core-go/go.mod` still depends on `/tmp/xray-src`, `/tmp/awg-src`, `/tmp/t2s`; D-51 tracks the fix.
