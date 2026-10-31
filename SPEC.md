# SPEC.md — Mohammad (`ir.mohammad.vpn`) — v2.0

**Version:** 2.0 · **Date:** 2026-10-31 · **Supersedes:** v1.0 (2026-10-30, 30 FRs)
**Basis:** `docs/RESEARCH_CATALOG.md` (146 entries) + the prioritized shortlist approved on 2026-10-31.
**Rule:** this file is normative. A conflict with any earlier document resolves here, and only after
the operator approves the change. Every requirement carries an ID; every ID maps to a real test or a
named `NOT VERIFIED` reason. Nothing in this file may be implemented by weakening an existing test.

---

## 1. Purpose

A single-screen Android VPN client that connects one operator-provided profile, reports *real* state,
and stays connected on Iranian networks through protocol whitelisting, IP graylisting and PMTU black
holes.

The v2.0 objective, in priority order:

1. **Stay connected** — eliminate the "works a few seconds, then cuts" class of failure.
2. **Survive detection** — stop emitting fingerprints that are trivially classifiable.
3. **Survive blocking** — never depend on one server, one IP, or one transport.
4. **Be operable** — let the operator change behaviour for ~1,000 installed users without an APK.

Features that do not serve 1–4 are out of scope, regardless of how easy they are to add.

---

## 2. What changed from v1.0

| Area | v1.0 | v2.0 | Why |
|---|---|---|---|
| MTU | default 1500, parity with a reference client | **default 1400**, per-profile override, MSS clamped | PMTU black hole is the prime suspect for the open bug (catalog 103–105, 114) |
| `sockopt` | unset | `tcpMaxSeg`, `tcpUserTimeout`, keepalive rendered | free correctness (catalog 106, 115) |
| TLS fingerprint | Go default | explicit `fingerprint`, required when TLS/REALITY | the loudest unforced error (catalog 65, 87) |
| Transport | VLESS over plaintext WS only | WS+TLS, REALITY+Vision supported; plaintext WS retained for backward compatibility | catalog 49, 50, 73 |
| State model | 5 states | **10 states**, incl. `DEGRADED`, `RECOVERING`, `BLOCKED_BY_POLICY` | FR-44 is unimplementable on 5 states |
| Path selection | none | operator priority + fallback + hysteresis | single path of failure (catalog 117, 121, 123) |
| Remote config | none | signed, versioned, atomic, with built-in fallback | ship fixes to 1,000 users without a release |
| Persian / RTL | designed, never implemented | **removed from scope** (operator decision 2026-10-31) | English UI stays; localization is not a quality lever |
| AmneziaWG | shipped, never verified | stays shipped, **`NOT VERIFIED`** until real device traffic with per-server v3 parameters | catalog 146 |

Withdrawn requirements (explicitly, so nothing is silently dropped):

| Withdrawn | Reason |
|---|---|
| FR-52 (Persian UI, RTL) | Operator decision 2026-10-31 |
| NFR-26 (Persian strings from resources) | same |
| NFR-40 (RTL rendering) | same |
| v1 AC-31 (Persian RTL test) | same — v2's AC-31 is a different, unrelated row |
| v1 AC-37's Persian half (accessibility stays, RTL does not) | same |

---

## 3. Scope

### 3.1 In scope (v2.0)

- MTU / MSS / PMTU correctness on the Xray path.
- `sockopt` rendering: segment size, user timeout, keepalive.
- uTLS fingerprint selection, with validation.
- VLESS + REALITY + XTLS Vision: bundle schema, config rendering, validation.
- Ten-state connection machine with an explicit transition table.
- Path policy: operator priority, fallback list, anti-flap hysteresis, per-path circuit breaker.
- Pre-flight network check (MTU, NAT type, UDP reachability), cached per network.
- Second path: WebSocket + TLS through a CDN, selectable as a fallback profile.
- Signed remote configuration (Ed25519), strict versioning, atomic apply, built-in fallback.
- Diagnostics sufficient to distinguish *watchdog false positive* from *PMTU black hole* from
  *protocol block* — without a debugger attached.

### 3.2 Out of scope (v2.0)

- Persian UI, RTL layout, localization of any kind. **English only.**
- User-facing profile editor, QR import, subscription URLs entered by the user.
- A third VPN core.
- Release signing, Play Store distribution.
- Non-`arm64-v8a` ABIs.
- Analytics, telemetry upload, crash reporting to a third party.
- BBR / server sysctl tuning — an **operator action**, tracked in `PLAN.md`, never a client milestone.
- Any claim that AmneziaWG works. It stays `NOT VERIFIED` until paragraph 9.4 is satisfied.

---

## 4. Architecture (unchanged)

```
app traffic → VpnService TUN (mtu = plan.mtu)
            → tun2socks (gVisor netstack, fd-backed)
            → SOCKS5 127.0.0.1:10808
            → Xray-core (VLESS/VMess/Trojan/SS/SOCKS/HTTP)
            → server
```

Second core `AmneziaWG` shares the same TUN and the same native library (`libgojni.so`).

Module boundaries (must not be crossed):

| Module | May depend on | Must not depend on |
|---|---|---|
| `core/` (pure config + policy) | nothing Android | `vpn/`, `ui/`, `data/` |
| `data/` (bundle, state) | `core/` | `vpn/`, `ui/` |
| `vpn/` (service, probes) | `core/`, `data/` | `ui/` |
| `ui/` | `data/` (read-only) | `vpn/` internals |

Every policy decision in this spec must live in a **pure** class under `core/` or `vpn/*Policy.kt` so
it is testable on the JVM without a device. Android types stay out of policy code.

---

## 5. Functional requirements

### 5.1 Configuration and bundle (CFG)

| ID | Requirement |
|---|---|
| CFG-1 | On start, load operator profiles from the encrypted bundle (`assets/operator/bundle.enc`). |
| CFG-2 | Bundle missing, corrupt, undecryptable or schema-invalid → `FAILED(OPERATOR_BUNDLE_UNAVAILABLE)`, no native component starts, Connect disabled. |
| CFG-3 | `schemaVersion` other than a supported value is rejected as a whole; no partial application. |
| CFG-4 | Duplicate profile `id` rejects the entire bundle. |
| CFG-5 | Selected profile id persists across launches; if it no longer exists, fall back to `preferredProfileId`, then the first profile. |
| CFG-6 | The plaintext operator bundle and its password never appear in any log, crash report, or build output. |

### 5.2 Data path and MTU (MTU) — **highest priority**

| ID | Requirement |
|---|---|
| MTU-1 | Effective MTU resolution order: profile `mtu` → bundle `defaultMtu` → **1400**. Values outside 1200–1500 are rejected at validation, not clamped silently. |
| MTU-2 | The TUN is created with the resolved MTU (`Builder.setMtu`). |
| MTU-3 | The rendered Xray config sets `sockopt.tcpMaxSeg` to `min(resolvedMtu - 40, 1440)` unless the profile overrides it. |
| MTU-4 | The rendered Xray config sets `sockopt.tcpUserTimeout` to a bounded value (default 30 s) so a silently dead path is torn down instead of hanging. |
| MTU-5 | Keepalive is explicit: `tcpKeepAliveIdle` ≤ 25 s, `tcpKeepAliveInterval` ≤ 15 s. |
| MTU-6 | A pre-flight MTU probe (see NET-3) may lower the effective MTU for the current network, but may never raise it above the configured value. |
| MTU-7 | Every MTU decision is logged as `mtu resolved: configured=X effective=Y source=profile\|bundle\|default\|probe` — no endpoint, no host. |
| MTU-8 | If a stall is detected while a large transfer is active, the diagnostic logs `path_stall_suspected` with the effective MTU, and enters `RECOVERING` per ST-6. |

### 5.3 Transport, TLS and fingerprint (TLS)

| ID | Requirement |
|---|---|
| TLS-1 | `streamSettings.security` supports `none`, `tls`, `reality`. |
| TLS-2 | When `security` is `tls` or `reality`, a `fingerprint` is **required** and must be one of the supported uTLS profiles; absence is a validation error, not a silent default. |
| TLS-3 | `security: reality` requires `realitySettings` with `serverName`, `publicKey`, `shortId`; all three are validated and none is ever logged. |
| TLS-4 | `security: reality` with `flow` set to `xtls-rprx-vision` renders the flow on both the outbound and the client settings. |
| TLS-5 | `finalmask.fragment` must not be emitted together with `reality` (known defect: `tlshello` breaks REALITY). Emitting both is a validation error. |
| TLS-6 | Existing profiles with `security: none` keep working unchanged (backward compatibility). |
| TLS-7 | The chosen `fingerprint` appears in the redacted diagnostic log as a profile name only (e.g. `chrome`), never as a raw JA3. |

### 5.4 Connection state machine (ST)

| ID | Requirement |
|---|---|
| ST-1 | The machine has exactly ten states: `IDLE`, `REQUESTING_PERMISSION`, `STARTING`, `CONNECTING`, `CONNECTED`, `DEGRADED`, `RECOVERING`, `DISCONNECTED`, `FAILED`, `BLOCKED_BY_POLICY`. |
| ST-2 | Only `CONNECTED` may render as connected. No state may be rendered green without a successful end-to-end probe (SOCKS connect + TLS handshake + HTTP status). |
| ST-3 | Transitions follow section 7 exactly; any transition not in that table is a programming error and must fail a test rather than be tolerated. |
| ST-4 | `DEGRADED` = at least one probe failure but below the reconnect threshold, or traffic flowing while probes fail. |
| ST-5 | `RECOVERING` = failure threshold reached and a recovery action (restart core, re-resolve, switch path) is in progress. |
| ST-6 | Stall detection: no bytes in either direction for `stallTimeoutMs` while `CONNECTED` → `DEGRADED` (first) / `RECOVERING` (threshold). |
| ST-7 | `BLOCKED_BY_POLICY` is used when no configured path is permitted (all failed and attempts exhausted, or policy forbids the remaining paths). It is distinct from `FAILED`. |
| ST-8 | A duplicate connect request while not in `IDLE`/`DISCONNECTED`/`FAILED` is ignored and logged `connect_request_ignored_duplicate`. |
| ST-9 | Every state change emits one structured event with a correlation id (OBS-1). |

### 5.5 Health probing (HP)

| ID | Requirement |
|---|---|
| HP-1 | The probe is a real SOCKS5 request to an operator-defined HTTPS target through `127.0.0.1:10808`. |
| HP-2 | Success requires: SOCKS established, TLS handshake completed, HTTP status received, within `timeoutMs`. |
| HP-3 | The probe target must not be the VPN server itself (the 0.11.2 defect must not return). |
| HP-4 | Interval, timeout, failure threshold and success threshold come from `healthProbe` in the bundle; defaults 15 s / 5000 ms / 3 / 1. |
| HP-5 | Probe results are logged with duration bucket and failure class only — never host, IP, or response body. |
| HP-6 | A probe never counts as user traffic for the purposes of `TRAFFIC_GRACE`. |

### 5.6 Path policy and fallback (PATH)

| ID | Requirement |
|---|---|
| PATH-1 | `pathPolicy.preferredProfileId` selects the default path; `fallbackProfileIds` is an ordered list. |
| PATH-2 | On health failure of the active path, advance to the next permitted path, at most `maxRecoveryAttempts` times, with backoff `retryBackoffBaseMilliseconds` → `retryBackoffCapMilliseconds` **with jitter**. |
| PATH-3 | Anti-flap: leave a path only after `failureThreshold` consecutive failures; return to the preferred path only after `successThreshold` consecutive successes and a minimum dwell of 60 s. |
| PATH-4 | `returnToPreferredWhenHealthy: true` restores the preferred path; `false` keeps the surviving path until the next manual connect. |
| PATH-5 | Per-path circuit breaker: `closed → open → half-open`; an open path is skipped for a cooldown, then probed once. |
| PATH-6 | Path selection is deterministic and explainable: the same inputs must produce the same choice. No racing between paths in v2.0 (battery, probe noise, and it looks like scanning to a DPI box). |
| PATH-7 | When every permitted path is exhausted → `BLOCKED_BY_POLICY`, with the number of attempts in the log. |
| PATH-8 | The active path label shown in the UI is the profile display name, never the endpoint. |

### 5.7 Pre-flight network check (NET)

| ID | Requirement |
|---|---|
| NET-1 | Before creating the TUN, measure the current network once: NAT/CGNAT indication, IPv4/IPv6 reachability, UDP/443 reachability, and a candidate MTU. |
| NET-2 | Results are cached per network identity (transport type + interface + SSID hash) and reused for 24 h. |
| NET-3 | The MTU probe binary-searches a small range using a DF-set ICMP/UDP probe where permitted; if probing is inconclusive, keep the configured MTU and log `mtu_probe_inconclusive`. |
| NET-4 | The probe has a hard budget (default 2.5 s) and never blocks connection beyond it. |
| NET-5 | Failure of the check is never fatal; it only downgrades what policy may choose. |
| NET-6 | Results never leave the device and contain no SSID in clear text (hash only). |

### 5.8 Second path (SP)

| ID | Requirement |
|---|---|
| SP-1 | A profile may declare `transport: websocket` with `security: tls` and a CDN-friendly `host`/`path`. |
| SP-2 | The second path is a normal profile in `fallbackProfileIds`; it needs no separate code path. |
| SP-3 | The client must not assume the CDN terminates TLS at the origin (SNI and Host may differ); both are configurable and validated. |
| SP-4 | A CDN path is never preferred over a direct REALITY path unless the direct path is unhealthy. |
| SP-5 | The UI must distinguish "connected via relay" from "connected directly" using a label only, with no host in it. |

### 5.9 Remote configuration (RC)

| ID | Requirement |
|---|---|
| RC-1 | `GET {REMOTE_CONFIG_URL}` over HTTPS only; no secret header. |
| RC-2 | Response ≤ 256 KiB, JSON content type, `schemaVersion` supported, `configVersion` strictly greater than the active one. |
| RC-3 | A detached Ed25519 signature over the canonical payload is required; the public key is compiled in and never fetched. |
| RC-4 | Invalid signature, lower/equal version, wrong content type, oversize body or timeout → configuration **not applied**, last known-good retained, one redacted log line. |
| RC-5 | Apply is atomic: validate fully, then swap. A partially applied configuration is a defect. |
| RC-6 | The built-in bundle is always the final fallback; a remote config can never leave the app with zero profiles. |
| RC-7 | Timeouts: 10 s connect, 15 s read; retry max 3 with exponential backoff + jitter, idempotent GET only. |
| RC-8 | The last known-good remote config is stored encrypted (Android Keystore-backed), never in plain preferences. |
| RC-9 | Fetch is attempted at most once per 24 h unless triggered by a failed connection (then at most once per 6 h). |

### 5.10 Telemetry, logs and diagnostics (OBS)

| ID | Requirement |
|---|---|
| OBS-1 | Every connection event emits a structured record: `event`, `state`, `correlationId`, `durationBucket`, `failureClass`. |
| OBS-2 | Volume and speed are locale-formatted but derived only from real counters; no estimates, no extrapolation. |
| OBS-3 | Zero traffic renders zero. |
| OBS-4 | The log console is redacted at the boundary, once, and the redactor is unit-tested. |
| OBS-5 | A user-initiated "copy diagnostics" produces a text report containing state history, MTU resolutions, path decisions and failure classes — and no endpoint, UUID, key or hostname. |
| OBS-6 | Crash banner appears above the main screen, never replaces it. |

### 5.11 UI (UI) — English only

| ID | Requirement |
|---|---|
| UI-1 | One screen: title/state, rotary dial, active path label, four telemetry tiles, profile list, DPI-evasion card, collapsible log console, crash banner. |
| UI-2 | All user-facing strings are English and live in `strings.xml` (resource extraction is required even though localization is not). |
| UI-3 | The dial's enabled/disabled/colour state is derived from the state machine, not from ad-hoc booleans. |
| UI-4 | No endpoint, host, UUID or key is ever displayed. |
| UI-5 | Accessibility: content descriptions on dial, tiles, profile rows, expand controls and banner actions; logical focus order; touch targets ≥ 48 dp. |

### 5.12 Server data and multiplicity (SRC) — operator constraint 2026-10-31

| ID | Requirement |
|---|---|
| SRC-1 | The app supports **any number** of operator profiles (1, 2 or N). No state, policy, probe or renderer may assume a single server. |
| SRC-2 | No server address, IP, hostname, UUID, publicKey or shortId appears in Kotlin source. All of it comes from the encrypted bundle or the signed remote config, and nowhere else. |
| SRC-3 | Validation is generic: any combination the pinned core accepts must be accepted. Nothing is special-cased to the operator's current server. |
| SRC-4 | Adding, removing or changing a server requires only a new bundle or a remote-config publish — never a new APK. |
| SRC-5 | Every policy, state and rendering test covers 1, 2 and N profiles, including the empty-set case. |

### 5.13 System surfaces: widget and tile (FR-W) — milestone M4-W

| ID | Requirement |
|---|---|
| FR-W1 | A home-screen widget shows the current connection state with an icon, a title and an action, in the existing cyber-emerald palette. |
| FR-W2 | A quick-settings tile shows the same state as active / inactive / unavailable, with a label and subtitle. |
| FR-W3 | A tap on either surface connects or disconnects through **the same entry point as the in-app dial** — no second logic path. |
| FR-W4 | Neither surface may display a connected state unless the SOCKS health probe has passed; the projection is derived from `TunnelState`, which the service only sets after the probe. |
| FR-W5 | Both surfaces work from a cold process: a tap starts the service, and a surface rendered before any Activity reads the persisted profile selection rather than showing "not set up". |
| FR-W6 | Neither surface displays or embeds an endpoint, host, IP, UUID, publicKey or shortId. |
| FR-W7 | The widget is resizable and lays out correctly at 2x1 and 4x1. |
| FR-W8 | Both surfaces update from the connection `StateFlow`; no polling. |
| FR-W9 | Both surfaces depend on a projection (`SurfaceState`), never on the state machine directly, so M4's ten states require no change in `widget/` or `tile/`. |
| FR-W10 | No new Android permission is requested for either surface. |

### 5.14 AmneziaWG (AWG) — `NOT VERIFIED`

| ID | Requirement |
|---|---|
| AWG-1 | AWG config is validated against a schema before any native call. |
| AWG-2 | AWG exposes handshake and tunnel state through the same health abstraction as Xray. |
| AWG-3 | AWG failure enters `RECOVERING` and may never produce `CONNECTED` without a successful probe. |
| AWG-4 | Documentation and release notes label AmneziaWG `NOT VERIFIED` until real device traffic is captured. |
| AWG-5 | v3 parameters are per-server and must never be shipped as shared defaults (v2's static parameters are fingerprintable). |

---

## 6. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Every external input is validated for schema, type, length, format and allowed values before use. |
| NFR-2 | Config is generated from structured objects; no string interpolation of unvalidated values into config or commands. |
| NFR-3 | Remote config requires HTTPS + certificate validation + size limit + schema + Ed25519. |
| NFR-4 | Secrets never appear in logs, errors, crash reports, commits or build outputs. |
| NFR-5 | Redaction covers passwords, UUIDs, private keys, hostnames, IPs, ports and full payloads. |
| NFR-6 | Cached remote config and operational state use Keystore-backed encrypted storage. |
| NFR-7 | All network calls have explicit connect and read timeouts; SSRF-safe target validation. |
| NFR-8 | Every added dependency is checked for identity, licence, maintenance and known vulnerabilities. |
| NFR-9 | R8 keeps only what reflection/JNI/serialization require. |
| NFR-10 | No packet contents, destinations or browsing metadata are ever logged. |
| NFR-11 | Every network call has an explicit timeout. |
| NFR-12 | Every native start has a startup timeout and a forced-termination fallback. |
| NFR-13 | Retries are idempotent-only, exponential with jitter, hard-capped. |
| NFR-14 | A failing dependency has defined fallback behaviour; never a crash, never silent continuation. |
| NFR-15 | Shutdown order is defined and tested: core → tun2socks → TUN fd → coroutines → files. |
| NFR-16 | Configuration replacement preserves rollback to last known-good. |
| NFR-17 | Time from tap to `CONNECTING` ≤ 2 s on a mid-range device — `NOT VERIFIED` (no device). |
| NFR-18 | Telemetry updates never block the main thread. |
| NFR-19 | Debug APK ≤ 20 MB. |
| NFR-20 | Lint reports 0 errors; the 15 current warnings must not increase without a logged decision. |
| NFR-21 | Config validation completes before any VPN component starts. |
| NFR-22 | Kotlin strict compiler settings; no silent nullability. |
| NFR-23 | No unchecked assertions on nullable values; explicit handling. |
| NFR-24 | Unavoidable platform types are documented and validated. |
| NFR-25 | WCAG 2.2 AA for the implemented (English) UI. |
| NFR-26 | **WITHDRAWN** — Persian strings from resources (operator decision 2026-10-31). |
| NFR-27 | **WITHDRAWN** — bidi isolation of mixed-direction content. |
| NFR-28 | No personal analytics. |
| NFR-29 | Diagnostics contain no user- or traffic-identifying data. |
| NFR-30 | "Clear data" removes cached remote config and state per platform behaviour. |
| NFR-31 | Connection events carry correlation id + redacted fields. |
| NFR-32 | Probe events carry duration bucket, failure class, correlation id. |
| NFR-33 | Path switches emit from/to/reason classes + correlation id. |
| NFR-34 | Fatal errors emit a redacted crash event and show the banner. |
| NFR-35 | Module dependency direction is respected (section 4). |
| NFR-36 | Cross-module calls use typed interfaces. |
| NFR-37 | A new config field needs validation, tests, docs and a version decision. |
| NFR-38 | Android 8.0 → current target. |
| NFR-39 | `arm64-v8a` only. |
| NFR-40 | **WITHDRAWN** — RTL rendering. |

---

## 7. State machine (normative)

### 7.1 States

| State | Meaning | Dial | Telemetry |
|---|---|---|---|
| `IDLE` | Nothing started; a profile is selected and Connect is possible | enabled, neutral | zero |
| `REQUESTING_PERMISSION` | System VPN consent dialog is showing | disabled | zero |
| `STARTING` | TUN created, core starting | disabled, animated | zero |
| `CONNECTING` | Core up, first end-to-end probe in flight | disabled, animated | zero |
| `CONNECTED` | Probe succeeded, traffic permitted | enabled, green | live |
| `DEGRADED` | Probe failing below threshold, or no bytes for `stallTimeoutMs` | enabled, amber | live or zero |
| `RECOVERING` | Threshold reached; restart/switch in progress | disabled, animated | live or zero |
| `DISCONNECTED` | User-initiated stop completed | enabled, neutral | zero |
| `FAILED` | Terminal failure with a typed reason | enabled, red | zero |
| `BLOCKED_BY_POLICY` | Every permitted path exhausted | disabled | zero |

### 7.2 Transition table

| From | Event | To | Guard / side effect |
|---|---|---|---|
| `IDLE` | connect tapped | `REQUESTING_PERMISSION` | VPN permission absent |
| `IDLE` | connect tapped | `STARTING` | permission already granted |
| `REQUESTING_PERMISSION` | granted | `STARTING` | — |
| `REQUESTING_PERMISSION` | denied | `IDLE` | `vpn_permission_denied` logged |
| `STARTING` | TUN + core ok | `CONNECTING` | probe timer armed |
| `STARTING` | TUN or core failed | `FAILED(CORE_START_FAILED)` | resources released |
| `CONNECTING` | probe success | `CONNECTED` | correlation id emitted |
| `CONNECTING` | probe failure < threshold | `CONNECTING` | retry within budget |
| `CONNECTING` | probe failure ≥ threshold, recovery budget left | `RECOVERING` | restart core |
| `CONNECTING` | probe failure ≥ threshold, budget exhausted | `FAILED(ALL_PATHS_FAILED)` | or `BLOCKED_BY_POLICY` |
| `CONNECTED` | probe failure count = 1..threshold-1 | `DEGRADED` | amber |
| `CONNECTED` | stall detected | `DEGRADED` | `path_stall_suspected` |
| `DEGRADED` | probe success | `CONNECTED` | reset counters |
| `DEGRADED` | failure ≥ threshold | `RECOVERING` | recovery action |
| `RECOVERING` | recovery success + probe success | `CONNECTED` | switch marked as recovered |
| `RECOVERING` | fallback path available | `RECOVERING` → `CONNECTING` | path switch logged |
| `RECOVERING` | attempts exhausted | `BLOCKED_BY_POLICY` | if paths remain but are forbidden |
| `RECOVERING` | all paths failed | `FAILED(ALL_PATHS_FAILED)` | terminal |
| any active | disconnect tapped | `DISCONNECTED` | ordered shutdown (NFR-15) |
| `DISCONNECTED` | connect tapped | `STARTING` | — |
| `FAILED` | connect tapped | `STARTING` | counters reset |
| `BLOCKED_BY_POLICY` | connect tapped | `STARTING` | only after cooldown |

**Invariant:** no transition into `CONNECTED` exists except from `CONNECTING` or `RECOVERING`, and only
after a successful probe. This is the single most important line in this document.

---

## 8. Data model

### 8.1 `OperatorBundle` (schemaVersion 2, superset of 1)

| Field | Type | Default | Constraint |
|---|---|---|---|
| `schemaVersion` | Int | — | `1` or `2` accepted; `2` enables the new fields |
| `defaultMtu` | Int | **1400** | 1200–1500 |
| `defaultDnsServers` | List<String> | `1.1.1.1, 8.8.8.8` | valid IPs |
| `profiles` | List<Profile> | — | ≥ 1, unique ids |
| `preferredProfileId` | String? | first profile | must resolve |
| `pathPolicy` | PathPolicy? | built from profile order | validated |
| `healthProbe` | HealthProbeConfig? | 15 s / 5000 ms / 3 / 1 | validated |
| `remoteConfig` | RemoteConfigRef? | null | URL must be HTTPS |

### 8.2 `Profile` — added fields

| Field | Type | Default | Constraint |
|---|---|---|---|
| `mtu` | Int? | null → bundle → 1400 | 1200–1500 |
| `transport` | Enum | `websocket` | `tcp`, `websocket`, `grpc`, `xhttp` |
| `security` | Enum | `none` | `none`, `tls`, `reality` |
| `fingerprint` | String? | **required when security ≠ none** | known uTLS profile |
| `reality` | RealityConfig? | null | required when `security = reality` |
| `flow` | String? | null | only `xtls-rprx-vision` in v2.0 |
| `healthProbe` | HealthProbeConfig? | bundle value | validated |

### 8.3 `PathPolicy`

| Field | Type | Default | Constraint |
|---|---|---|---|
| `preferredProfileId` | String | — | must exist |
| `fallbackProfileIds` | List<String> | empty | each must exist, no duplicates |
| `maxRecoveryAttempts` | Int | 3 | 1–10 |
| `retryBackoffBaseMilliseconds` | Int | 1000 | 100–60000 |
| `retryBackoffCapMilliseconds` | Int | 30000 | > base |
| `returnToPreferredWhenHealthy` | Boolean | true | — |
| `failureThreshold` | Int | 3 | 1–10 |
| `successThreshold` | Int | 1 | 1–10 |
| `minDwellMs` | Long | 60000 | ≥ 0 |

### 8.4 `RemoteConfig`

`{ schemaVersion, configVersion, publishedAt, profiles[], pathPolicy, signature }` — signature is a
detached Ed25519 signature (base64) over the canonical JSON of every other field, in declaration order,
UTF-8, no whitespace normalisation beyond RFC 8785 JCS.

---

## 9. Verification

### 9.1 Commands (canonical)

| Purpose | Command | Expected |
|---|---|---|
| Version | `./gradlew --version` | exit 0, Gradle 8.14.6, JVM 17 |
| Clean build | `./gradlew clean :app:assembleDebug` | exit 0, APK ≤ 20 MB |
| Unit tests | `./gradlew :app:testDebugUnitTest` | exit 0 |
| Lint | `./gradlew :app:lintDebug` | exit 0, 0 errors, ≤ 15 warnings |
| Placeholder gate | `./scripts/check-no-placeholders` | exit 0 |
| APK size | `./scripts/apk-size-check` | exit 0 |
| Native build | `gomobile bind` (needs `/tmp` pins; see D-51) | exit 0, `libgojni.so` |
| Device | supervised `adb` procedure, `docs/DEVICE_TEST_PLAN.md` | **NOT VERIFIED** here |

### 9.2 Acceptance traceability

Every AC names a test class that exists today or is created by the milestone in the last column.

| AC | Requirement | Level | Command | Pass criterion | Milestone |
|---|---|---|---|---|---|
| AC-1 | CFG-1, CFG-2 | integration | `--tests "*BundleLoadTest"` | bundle loads; missing/wrong password ⇒ typed failure | M0 |
| AC-2 | CFG-3, CFG-4 | unit | `--tests "*BundleParseTest"` | invalid and duplicate-id bundles rejected whole | M0 |
| AC-3 | MTU-1 | unit | `--tests "*MtuPolicyTest"` | profile > bundle > 1400; out-of-range rejected | M1 |
| AC-4 | MTU-2 | unit | `--tests "*TunPlanTest"` | plan carries the resolved MTU | M1 |
| AC-5 | MTU-3 | unit | `--tests "*XraySockoptTest"` | `tcpMaxSeg` = min(mtu−40,1440) | M1 |
| AC-6 | MTU-4, MTU-5 | unit | `--tests "*XraySockoptTest"` | `tcpUserTimeout` and keepalive present and bounded | M1 |
| AC-7 | MTU-7 | unit | `--tests "*MtuPolicyTest"` | log line carries source, no endpoint | M1 |
| AC-8 | TLS-1..TLS-4 | integration | `--tests "*RealityConfigTest"` | REALITY renders serverName/publicKey/shortId/flow | M3 |
| AC-9 | TLS-2 | unit | `--tests "*TlsFingerprintTest"` | missing fingerprint for tls/reality ⇒ validation error | M2 |
| AC-10 | TLS-5 | unit | `--tests "*RealityConfigTest"` | reality + fragment ⇒ rejected | M3 |
| AC-11 | TLS-6 | integration | `--tests "*ConfigRenderTest"` | `security:none` output unchanged (golden) | M2 |
| AC-12 | ST-1, ST-3 | unit | `--tests "*ConnectionStateMachineTest"` | only table transitions accepted | M4 |
| AC-13 | ST-2 | integration | `--tests "*ConnectionStateMachineTest"` | no path to CONNECTED without probe success | M4 |
| AC-14 | ST-6 | unit | `--tests "*StallDetectionTest"` | silence > timeout ⇒ DEGRADED | M4 |
| AC-15 | ST-8 | unit | `--tests "*ConnectionStateMachineTest"` | duplicate connect ignored | M4 |
| AC-16 | HP-3 | unit | `--tests "*DataPathPolicyTest"` | server-as-probe-target rejected | M4 |
| AC-17 | HP-1, HP-2 | integration | `--tests "*SocksProbeTest"` | probe abstracted, success/failure typed | M4 |
| AC-18 | PATH-2 | unit | `--tests "*PathPolicyTest"` | ordered failover with capped backoff + jitter | M4 |
| AC-19 | PATH-3 | unit | `--tests "*AntiFlapTest"` | no switch below threshold; dwell respected | M4 |
| AC-20 | PATH-5 | unit | `--tests "*CircuitBreakerTest"` | closed→open→half-open with cooldown | M4 |
| AC-21 | PATH-7 | unit | `--tests "*PathPolicyTest"` | exhausted ⇒ BLOCKED_BY_POLICY | M4 |
| AC-22 | NET-2 | unit | `--tests "*NetcheckPolicyTest"` | cache key stable per network, 24 h TTL | M5 |
| AC-23 | NET-4 | unit | `--tests "*NetcheckPolicyTest"` | budget expiry ⇒ inconclusive, never fatal | M5 |
| AC-24 | SP-1, SP-3 | integration | `--tests "*SecondPathConfigTest"` | WS+TLS with differing SNI/Host renders correctly | M6 |
| AC-25 | RC-2, RC-3 | integration | `--tests "*RemoteConfigVerifierTest"` | valid signature accepted; tampered rejected | M7 |
| AC-26 | RC-2 | unit | `--tests "*RemoteConfigVersionPolicyTest"` | equal/lower version ignored | M7 |
| AC-27 | RC-5, RC-6 | integration | `--tests "*RemoteConfigApplierTest"` | atomic swap; built-in bundle is final fallback | M7 |
| AC-28 | RC-4, RC-7 | integration | `--tests "*RemoteConfigFailureTest"` | timeout/429/invalid ⇒ previous config retained | M7 |
| AC-29 | OBS-4, NFR-5 | unit | `--tests "*RedactionTest"` | secrets and sensitive fields redacted | M1 |
| AC-30 | OBS-2, OBS-3 | UI | `--tests "*TelemetryTileTest"` | live values and zero values render correctly | M4 |
| AC-31 | UI-3 | UI | `--tests "*UiInteractionTest"` | dial state derives from the machine | M4 |
| AC-32 | UI-5 | UI | `--tests "*AccessibilityTest"` | content descriptions + focus order | M4 |
| AC-33 | NFR-15 | integration | `--tests "*ShutdownOrderTest"` | core → bridge → fd → coroutines | M4 |
| AC-34 | NFR-19, NFR-20 | static | `./gradlew :app:lintDebug` + `./scripts/apk-size-check` | 0 errors, ≤ 15 warnings, ≤ 20 MB | every |
| AC-35 | placeholder ban | static | `./scripts/check-no-placeholders` | exit 0, no skipped tests | every |
| AC-36 | build | build | `./gradlew clean :app:assembleDebug` | exit 0 | every |
| AC-37 | AWG-4 | docs | `grep -R "AmneziaWG.*NOT VERIFIED" README.md docs` | match found | every |
| AC-W1 | FR-W1 | UI | `--tests "*SurfaceStateMapperTest"` | every state maps to one surface state | M4-W |
| AC-W2 | FR-W3 | integration | `--tests "*WidgetInteractionTest"` | widget tap attaches the shared toggle intent | M4-W |
| AC-W3 | FR-W2 | unit | `--tests "*TileStateMapperTest"` | only CONNECTED is active; unusable states are unavailable | M4-W |
| AC-W4 | FR-W3 | integration | `--tests "*TileInteractionTest"` | tile toggles through `TunnelController`, never its own Intent | M4-W |
| AC-W5 | FR-W4 | static+unit | `--tests "*SurfaceProbeGateTest"` | Connected has one writer, downstream of the probe | M4-W |
| AC-W6 | FR-W5 | Robolectric | `--tests "*SurfaceColdStartTest"` | cold process reads the persisted selection | M4-W |
| AC-W7 | FR-W6 | static | `--tests "*SurfaceResourceScanTest"` | no endpoint-shaped value in surface resources | M4-W |
| AC-W8 | FR-W7 | Robolectric | `--tests "*WidgetResizableTest"` | 2x1 and 4x1 both measure without clipping | M4-W |
| AC-W9 | FR-W2 | unit | `--tests "*TileInteractionTest"` | long-press is platform-owned; unused outcomes open the app | M4-W |
| AC-W10 | FR-W9 | static | `--tests "*AcTraceabilityTest"` | test count never decreases; matrix stays valid | every |
| AC-39 | SRC-1, SRC-2, SRC-3 | static | `--tests "*NoHardcodedEndpointsTest"` | no server data in production sources | every |
| AC-40 | SRC-1, SRC-5 | unit | `--tests "*PathPolicyTest"` | policy correct for 1, 2 and N profiles | M4 |
| AC-41 | SRC-4 | integration | `--tests "*RemoteConfigApplierTest"` | a publish adds a server without an APK | M7 |
| AC-38 | end-to-end chain | device | `grep -c "NOT VERIFIED" docs/DEVICE_TEST_PLAN.md` after the supervised procedure | **NOT VERIFIED** — no device | M-DEV |

**Rule:** an AC that names a test class which does not exist is a defect in this document, not a
reason to skip the test. M0 exists to remove that defect for the existing rows.

---

## 10. Failure journeys

| ID | Trigger | Behaviour | User-visible | Log class | Recovery |
|---|---|---|---|---|---|
| J-1 | Wrong bundle password | no component starts | Connect disabled | `operator_bundle_decrypt_failed` | operator rebuild |
| J-2 | Invalid remote signature | not applied | unchanged | `remote_config_signature_failed` | operator republishes |
| J-3 | Remote timeout / 429 | retain last good | none | `remote_config_timeout` | next scheduled fetch |
| J-4 | Probe timeout | no green | `DEGRADED` | `socks_probe_timeout` | restart or fallback |
| J-5 | Core start failure | no traffic claim | `FAILED` | `core_start_failed` | operator fixes profile |
| J-6 | Core exits unexpectedly | recovery | `RECOVERING` | `core_unexpected_exit` | restart/fallback |
| J-7 | AWG handshake failure | no green | `RECOVERING` | `awg_handshake_failed` | retry/fallback |
| J-8 | Airplane mode | recovery after network returns | `RECOVERING` → `CONNECTED` | `network_loss_detected` | automatic |
| J-9 | Duplicate connect tap | one flow | none | `connect_request_ignored_duplicate` | — |
| J-10 | Permission denied | nothing starts | `IDLE` + message | `vpn_permission_denied` | user grants |
| J-11 | Process death | clean start | `IDLE` | `session_interrupted` | user reconnects |
| J-12 | PMTU black hole | stall detected, recovery | `DEGRADED`/`RECOVERING` | `path_stall_suspected` | MTU policy |
| J-13 | All paths blocked | exact state | `BLOCKED_BY_POLICY` | `all_paths_blocked` | operator adds a path |
| J-14 | REALITY IP graylisted | fallback to next path | `RECOVERING` → `CONNECTED` (relay label) | `path_switch` | automatic |

---

## 11. Device-conditional register (all `NOT VERIFIED`)

| Item | Why it cannot be closed here |
|---|---|
| Real traffic through the full chain (AC-38) | no device, no `/dev/kvm` |
| Effective MTU on a real Iranian mobile path | needs the operator's network |
| PMTU black-hole reproduction | same |
| Reconnect time after airplane mode | same |
| AmneziaWG carrying traffic | no AWG server profile ever supplied |
| FPS / cold start / memory | no device |
| Battery cost of netcheck and probes | no device |
| REALITY surviving a real ISP | same |

Closing any row requires a captured device log pasted into `docs/DEVICE_TEST_PLAN.md` with the exact
command and output. Until then the row stays `NOT VERIFIED` in every report.

---

## 12. Definition of done (per milestone)

1. Failing test written first and shown failing for the right reason.
2. Implementation makes it pass; no existing test weakened, skipped or deleted.
3. `testDebugUnitTest`, `lintDebug`, `assembleDebug`, placeholder gate all green.
4. `PROGRESS.md` updated with real command output.
5. `DECISIONS.md` updated for every judgement call made during the milestone.
6. No new `TODO`, no stub, no "temporary" branch.
7. Milestone report posted with status, evidence, risks, next step.
