# SPEC.md — Mohammad (`ir.mohammad.vpn`)

**Version:** 1.0 (Phase 1)
**Status:** awaiting operator approval
**Upstream spec:** the operator's master prompt pack + the twelve Phase-0 approvals (see `DECISIONS.md`).
**Rule:** this file is normative. Any conflict with the master prompt resolves here, and only after the operator approves the change.

---

## 1. Purpose

A single-screen Android VPN client named **Mohammad** that connects one operator-provided profile, reports tunnel state, streams a readable connection log, and measures latency. The architecture must scale to subscriptions, editors, and per-app routing in later releases without rewrites.

## 2. Scope

### 2.1 In scope (v1)

Connect/Disconnect · tunnel state machine · Xray and AmneziaWG cores · profile selection · latency test · connection log · always-on declaration · encrypted operator bundle · unit/integration tests · documentation.

### 2.2 Out of scope (v1)

Subscription URLs · QR import · manual/raw profile editor · routing editor · per-app proxy · widgets · Quick Settings tile · Tasker/Locale · geo-asset manager · remote config fetch · Persian UI (structure only) · release signing.

## 3. Architecture

```
ui/ (Compose)  ──▶ feature/ (ViewModels, state) ──▶ domain/ (use cases, models)
                                                        │
                                    ┌───────────────────┴───────────────────┐
                                    ▼                                       ▼
                          data/ (bundle, DataStore)                 vpn/ (service, controller)
                                                                            │
                                                                     core/ (CoreBridge)
                                                                            │
                                                              libgojni.so (Xray / AWG)
```

Dependency rule: `ui → feature → domain`; `data` and `vpn` implement domain interfaces; `core` is only reachable through `CoreBridge`. No JNI type crosses into `ui` or `domain`.

## 4. Data model

### 4.1 Profile

| Field | Type | Null | Default | Constraint |
|---|---|---:|---|---|
| `id` | String | no | — | non-empty, unique |
| `name` | String | no | — | 1–64 chars |
| `protocol` | enum `Protocol` | no | — | VLESS, VMESS, TROJAN, SHADOWSOCKS, SOCKS, HTTP, WIREGUARD, AMNEZIAWG, HYSTERIA2 |
| `core` | enum `CoreKind` | no | — | `XRAY` \| `AMNEZIAWG` |
| `serverHost` | String | no | — | hostname or IP |
| `serverPort` | Int | no | — | 1–65535 |
| `outbound` | JsonObject | no | — | core-specific outbound object |
| `createdAtUtc` / `updatedAtUtc` | Instant | no | now | ISO-8601 UTC |

### 4.2 Operator bundle (v1 format, `schemaVersion = 1`)

```json
{
  "schemaVersion": 1,
  "defaultProfileId": "p1",
  "defaultDnsServers": ["1.1.1.1", "8.8.8.8"],
  "defaultMtu": 1400,
  "profiles": [ { "id": "...", "name": "...", "protocol": "VLESS", "core": "XRAY",
                  "serverHost": "...", "serverPort": 443, "outbound": { } } ]
}
```

Validation rules: `schemaVersion` must equal 1; `profiles` non-empty; every `id` unique; `defaultProfileId` must exist in `profiles`; `defaultDnsServers` 1–4 entries, each a valid IPv4/IPv6 literal; `defaultMtu` ∈ [1200, 1500]; `core` must be consistent with `protocol` (WIREGUARD/AMNEZIAWG → `AMNEZIAWG`, everything else → `XRAY`).

### 4.3 Bundle encryption at rest (v1)

- Plaintext bundle exists **only** outside version control (`operator/bundle.source.json`, mode `0600`, git-ignored).
- Build-time script `scripts/encrypt-operator-bundle` produces `app/src/main/assets/operator/bundle.enc`:
  `magic "MHB1"` · `salt[16]` · `iv[12]` · ciphertext(AES-256-GCM) · `tag[16]`;
  key = PBKDF2-HMAC-SHA256(password = `MOHAMMAD_OPERATOR_BUNDLE_PASSWORD`, salt, 210_000 iters, 32 bytes).
- Runtime `OperatorBundleLoader` decrypts inside `Dispatchers.IO` with a 5 s timeout and validates the schema before any profile is exposed.
- **Documented limitation (D-11):** a key that the app must be able to derive at runtime is obfuscation, not secrecy against a determined attacker with the APK. v2 replaces it with per-device Keystore provisioning + remote bundle delivery.

## 5. Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | On start, load operator profiles from the encrypted bundle (`assets/operator/bundle.enc`). |
| FR-2 | If the bundle is missing, corrupt, undecryptable, or fails schema validation → `ERROR(InvalidProfile)`; show `Configuration is unavailable. Contact the app operator.`; Connect disabled. |
| FR-3 | With ≥2 profiles, show a selector listing name, protocol, and host. |
| FR-4 | Persist the selected profile id and use it for the next Connect. |
| FR-5 | With no selection, disable Connect and show `Select a profile to connect.` |
| FR-6 | Connect with a valid profile and VPN permission → `CONNECTING` → create TUN → start the selected core. |
| FR-7 | Xray-supported protocol → render Xray config → `XrayBridge.start`. |
| FR-8 | WireGuard/AmneziaWG → render AWG config → `AwgBridge.start`. |
| FR-9 | Core start success → `CONNECTED`; log `Tunnel connected`. |
| FR-10 | Disconnect while connected → stop core, close fd, `DISCONNECTED`. |
| FR-11 | Permission missing/denied → stay `DISCONNECTED`; log `VPN permission required`; show `Allow VPN permission to connect.` |
| FR-12 | `establish()` null/throws → `ERROR(TunInterfaceFailed)`; release resources; no secret in log. |
| FR-13 | Core start timeout (15 s) → stop core, close fd, `ERROR(CoreStartFailed)`. |
| FR-14 | Core fatal error / unexpected exit while connected → `ERROR(CoreCrashed)`; close fd. |
| FR-15 | System revokes VPN → stop core, close fd, `DISCONNECTED`, log `VPN revoked by system`. |
| FR-16 | Connect while `CONNECTED` → no-op, state retained. |
| FR-17 | Disconnect while `DISCONNECTED` → no-op. |
| FR-18 | Every state change appends a timestamped human-readable log entry. |
| FR-19 | Core status messages are appended redacted. |
| FR-20 | Test Ping runs against the selected profile → `Ping: <n> ms` or `Ping failed`. |
| FR-21 | Ping exceeding 5 s → cancel, `Ping failed`, log `Ping timed out`. |
| FR-22 | Clear log removes displayed entries but keeps tunnel state. |
| FR-23 | TUN interface configured from profile or defaults: MTU, IPv4, IPv6 when applicable, routes, DNS. |
| FR-24 | If the profile defines no DNS, use bundle `defaultDnsServers`. |
| FR-25 | Exclude the app's own package from the tunnel (`addDisallowedApplication`). |
| FR-26 | Declare Always-on support via `android.net.VpnService.SUPPORTS_ALWAYS_ON`. |
| FR-27 | On service destroy: stop core, close fd, cancel coroutines, persist state. |
| FR-28 | All Connect/Disconnect/ping operations are serialized on one dispatcher; concurrent requests resolve to a deterministic final state. |
| FR-29 | Hysteria2 profiles use only the configuration subset accepted by Xray-core `v26.10.29`; unsupported keys are rejected at validation, not at runtime. |

## 6. Non-functional requirements

Security NFR-1…NFR-10 and reliability/performance/type-safety/accessibility/observability NFR-11…NFR-28 are carried over unchanged from the master prompt, with these refinements:

| ID | Refinement |
|---|---|
| NFR-2 | Keystore-backed encryption for any persisted secret; the operator bundle asset itself is encrypted per §4.3. |
| NFR-3 | `allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` explicitly deny the operator bundle. |
| NFR-4 | Redaction covers: UUIDs, `encryption` keys (`mlkem…`), passwords, private/public keys, full endpoints (`host:port`), and PSK material. |
| NFR-11 | Timeouts: core start 15 s · ping 5 s · bundle load 5 s · TUN establish 5 s. |
| NFR-18 | Release APK ≤ 80 MB before native optimization; **debug** APK ceiling 120 MB (two cores, two ABIs). |
| NFR-19 | Log list renders 1,000 entries without dropped frames (Compose test, `Macrobenchmark` not required). |
| NFR-29 | Build must succeed on a 3.8 GiB host: `org.gradle.jvmargs=-Xmx1536m`, `org.gradle.parallel=false`, `org.gradle.workers.max=2`, `kotlin.incremental=false`. |
| NFR-30 | Core AARs are built once, checksummed (SHA-256), and cached under `core-xray/artifacts/` and `core-awg/artifacts/`; Gradle never invokes `gomobile` unless `-PbuildCores=true`. |

## 7. State machine

```
DISCONNECTED --Connect(valid,perm)--> CONNECTING --ok--> CONNECTED
     ^                                   |                  |
     |                                   +--fail/timeout--> ERROR
     |                                   +--perm denied----> DISCONNECTED
     +---- Disconnect / revoke / crash / fatal <---- CONNECTED
CONNECTED --Disconnect--> DISCONNECTING --> DISCONNECTED
ERROR --Retry/Disconnect--> CONNECTING | DISCONNECTED
```

Forbidden transitions raise `InvalidStateTransition` and log `TUNNEL_OPERATION_REJECTED`.

## 8. UI contract

| State | Status text | Button | Enabled |
|---|---|---|---|
| DISCONNECTED | `Disconnected` | `Connect` | profile selected |
| CONNECTING | `Connecting…` | `Cancel` | yes |
| CONNECTED | `Connected` | `Disconnect` | yes |
| DISCONNECTING | `Disconnecting…` | `Disconnect` | no |
| ERROR | `Connection failed` | `Retry` | profile selected |

Messages: `Select a profile to connect.` · `Allow VPN permission to connect.` · `Configuration is unavailable. Contact the app operator.` · `Could not create the VPN interface.` · `Could not start the VPN engine.` · `The VPN engine stopped unexpectedly.` · `Ping: 123 ms` · `Ping failed`.

Layout order: title → status card → primary button → profile selector → ping → log → clear log. 48 dp targets, content descriptions on every control, auto-scroll to newest, dark/light via Material 3.

## 9. Protocol matrix

| Protocol | Core | v1 |
|---|---|---|
| VLESS (+ Reality, XTLS) | Xray | yes |
| VMess | Xray | yes |
| Trojan | Xray | yes |
| Shadowsocks | Xray | yes |
| SOCKS / HTTP | Xray | yes |
| WireGuard | AmneziaWG core (also available in Xray) | yes |
| AmneziaWG (Jc/Jmin/Jmax/S1–S4/H1–H4) | AmneziaWG | yes |
| Hysteria2 | Xray `proxy/hysteria` **subset only** | yes, limited |

## 10. Acceptance traceability matrix

Legend — **Level:** U = unit, I = integration, C = Compose UI, D = device-only. **Device-dependent rows are conditional**: with no device attached they are reported `NOT VERIFIED (no device)` and never as passing.

| AC | Req | Level | Command | Expected |
|---|---|---|---|---|
| AC-01 | FR-1 | I | `./gradlew testDebugUnitTest --tests *OperatorBundle*` | bundle decrypts, 1 profile parsed |
| AC-02 | FR-2 | U | same | malformed bundle → `InvalidProfile`, Connect disabled |
| AC-03 | FR-3 | C | `./gradlew testDebugUnitTest --tests *ProfileSelector*` | 2 names + protocols + hosts rendered |
| AC-04 | FR-4 | U | `--tests *SelectProfile*` | selection persisted, reused on next connect |
| AC-05 | FR-5 | C | `--tests *ConnectScreen*` | empty selection → message + disabled button |
| AC-06 | FR-6 | U | `--tests *ConnectUseCase*` | state DISCONNECTED→CONNECTING, TUN+FakeCore started |
| AC-07 | FR-7 | U | `--tests *ConfigRenderer*` | VLESS profile → valid Xray JSON (schema-checked) |
| AC-08 | FR-8 | U | same | AWG profile → AWG JSON with interface/peer keys |
| AC-09 | FR-9 | U | `--tests *TunnelController*` | start success → CONNECTED + `Tunnel connected` |
| AC-10 | FR-10 | U | same | Disconnect → DISCONNECTING→DISCONNECTED, fd closed |
| AC-11 | FR-11 | U | `--tests *Permission*` | null consent → DISCONNECTED + message |
| AC-12 | FR-12 | U | `--tests *TunFailure*` | null fd → ERROR(TunInterfaceFailed), fd released |
| AC-13 | FR-13 | U | `--tests *CoreTimeout*` (virtual clock) | 15 s → ERROR(CoreStartFailed), core stopped |
| AC-14 | FR-14 | U | `--tests *CoreCrash*` | fatal callback → ERROR(CoreCrashed), fd closed |
| AC-15 | FR-15 | U | `--tests *Revoke*` | onRevoke → DISCONNECTED + `VPN revoked by system` |
| AC-16 | FR-16/17 | U | `--tests *Idempotency*` | duplicate calls → no state change, no restart |
| AC-17 | FR-18 | U | `--tests *Log*` | each transition emits one timestamped entry |
| AC-18 | FR-19 | U | `--tests *Redaction*` | UUID/key/endpoint replaced by `***` |
| AC-19 | FR-20 | U | `--tests *Ping*` | success → `Ping: 87 ms` |
| AC-20 | FR-21 | U | `--tests *PingTimeout*` (virtual clock) | 5 s → `Ping failed` + `Ping timed out` |
| AC-21 | FR-22 | C | `--tests *ClearLog*` | entries cleared, state retained |
| AC-22 | FR-23 | U | `--tests *VpnInterfaceFactory*` | MTU/addr/route/DNS exactly as configured |
| AC-23 | FR-24 | U | same | empty profile DNS → bundle defaults |
| AC-24 | FR-25 | U | same | own package in disallowed list |
| AC-25 | FR-26 | static | `grep SUPPORTS_ALWAYS_ON app/src/main/AndroidManifest.xml` | attribute present with `true` |
| AC-26 | FR-27 | U | `--tests *ServiceDestroy*` | core stopped, fd closed, state persisted |
| AC-27 | FR-28 | U | `--tests *Concurrency*` (100 shuffled ops) | exactly one core start, final state deterministic |
| AC-28 | FR-29 | U | `--tests *Hysteria2Validation*` | unsupported keys → validation error before start |
| AC-29 | NFR-1 | U | `--tests *BundleSchema*` (20 bad inputs) | all rejected with typed errors |
| AC-30 | NFR-2/3 | static | `grep allowBackup app/src/main/AndroidManifest.xml` | `false` |
| AC-31 | NFR-4 | U | `--tests *RedactionAudit*` | no secret pattern in any emitted log |
| AC-32 | NFR-5 | static | `./gradlew lintDebug` | no Room raw-query warnings |
| AC-33 | NFR-9 | static | `./gradlew lintDebug` | no exported-component warnings |
| AC-34 | NFR-10 | static | `grep -r "javax.crypto.Cipher" app/src/main/java` | only AES-GCM via platform API |
| AC-35 | NFR-11 | U | `--tests *Timeouts*` | every operation has an explicit timeout |
| AC-36 | NFR-12 | static | `./scripts/check-no-placeholders` | no empty catch blocks |
| AC-37 | NFR-13/14 | U | `--tests *ResourceCleanup*` | fd closed on every failure path |
| AC-38 | NFR-15 | U | `--tests *MainThreadTest*` (StrictMode) | no disk/network on main |
| AC-39 | NFR-16 | D | `adb shell am start -W -n ir.mohammad.vpn/.MainActivity` | `TotalTime` < 2000 ms — **conditional** |
| AC-40 | NFR-17 | U | `--tests *ConnectLatency*` | state flips to CONNECTING < 200 ms |
| AC-41 | NFR-18 | script | `./scripts/apk-size-check` | debug APK ≤ 120 MB |
| AC-42 | NFR-19 | C | `--tests *LogList1000*` | renders 1000 items, no jank assertion failure |
| AC-43 | NFR-20/21/22 | build | `./gradlew compileDebugKotlin` | explicit API mode passes |
| AC-44 | NFR-23/24/25 | C | `--tests *Accessibility*` | descriptions present, ≥48 dp targets |
| AC-45 | NFR-26/27/28 | U | `--tests *StructuredLog*` | event name + correlation id + no PII |
| AC-46 | FR-6..10 (real path) | D | install + grant + Connect + observe | real tunnel — **conditional** |
| AC-47 | S-5 (log on device) | D | `adb logcat -s Mohammad:*` | lifecycle events visible — **conditional** |
| AC-48 | NFR-29/30 | build | `./gradlew assembleDebug` on 3.8 GiB host | build completes, cores not rebuilt |

**Coverage check:** every FR (1–29) and every NFR (1–30) appears in ≥1 AC.

## 11. Failure journeys

| ID | Trigger | Result | Log event |
|---|---|---|---|
| J-1 | bundle missing | Connect blocked, error copy | `OPERATOR_BUNDLE_MISSING` |
| J-2 | wrong password | safe failure | `OPERATOR_BUNDLE_DECRYPT_FAILED` |
| J-3 | permission denied | no TUN | `VPN_PERMISSION_DENIED` |
| J-4 | establish() null | resources released | `TUN_ESTABLISH_FAILED` |
| J-5 | core timeout | core stopped | `CORE_START_TIMEOUT` |
| J-6 | core crash | TUN closed | `CORE_CRASHED` |
| J-7 | ping timeout | cancelled | `PING_TIMEOUT` |
| J-8 | system revoke | full teardown | `VPN_REVOKED` |
| J-9 | duplicate Connect | no-op | `CONNECT_IGNORED_ALREADY_CONNECTED` |
| J-10 | concurrent ops | serialized | `TUNNEL_OPERATION_SERIALIZED` |

## 12. Verification commands (canonical)

```
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./scripts/check-no-placeholders
./scripts/apk-size-check
./gradlew installDebug              # device only
adb shell am start -n ir.mohammad.vpn/.MainActivity   # device only
```

## 13. Definition of done

A milestone is done when: all its ACs pass with real output; full regression suite passes; lint/build/placeholder/size gates are green; device-dependent ACs are either verified on a device **or** explicitly reported `NOT VERIFIED (no device)`; no unlogged assumption; trackers updated; a commit exists.

## 14. Open items (tracked in `DECISIONS.md`)

O-1 signing key (none; debug only) · O-2 device availability (none) · O-3 release ABI expansion · O-4 Hysteria2 `obfs`/`bandwidth` (excluded) · O-5 Persian localization (structure only).

## Handover behaviour (0.7.3)

| Situation | Required behaviour |
|---|---|
| Network lost (call, tunnel switch, airplane mode) | Service stays alive; state becomes `Failed(NetworkUnavailable)`; retries are cancelled, not fired |
| Network restored | Underlying network rebound; one bounded reconnect through `RetryPolicy`; generation and `userStopped` respected |
| User disconnects during the outage | No reconnect on restoration |
| Core alive but data path dead | SOCKS probe detects it; two consecutive failures trigger reconnect |
| Retry budget exhausted | `RECONNECT_FAILED`; service stops instead of looping |

## Connection quality defaults (0.11.0)

| Field | Value | Reason |
|---|---|---|
| `sockopt.tcpKeepAliveIdle` | 25 s | CGNAT expires idle mappings at 30–60 s |
| `sockopt.tcpKeepAliveInterval` | 15 s | probe cadence once idle |
| `dns.hosts` | server name → resolved address | one resolver round trip removed per connect |
| `Builder.setMetered` | always `false` | a deliberately routed tunnel must not be Data-Saver throttled (API 29+) |

Applies to Xray outbounds only; AmneziaWG has its own socket layer.
