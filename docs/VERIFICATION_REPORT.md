# VERIFICATION REPORT — Mohammad v0.3.0

All entries below were executed in this session. Anything not executed is marked
`NOT VERIFIED` with the reason. No claim is made beyond the literal output.

## Build and test gates

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `./gradlew clean` | PASS | `BUILD SUCCESSFUL` |
| 2 | `./gradlew testDebugUnitTest` | PASS | `BUILD SUCCESSFUL`, 20 tests, 0 failures |
| 3 | `./gradlew lintDebug` | PASS | `BUILD SUCCESSFUL`; report at `app/build/reports/lint-results-debug.html` |
| 4 | `./gradlew assembleDebug` | PASS | `app-debug.apk`, 45 MB |
| 5 | `./scripts/strip-aar.sh core-go/artifacts/mohammad-core.aar` | PASS | arm64 `47 921 104 → 37 887 336` bytes |
| 6 | `llvm-readelf --dyn-syms … libgojni.so` | PASS | 19 `Java_*` JNI symbols still exported after stripping |
| 7 | `aapt2 dump badging app-debug.apk` | PASS | `ir.mohammad.vpn`, versionCode 4, versionName 0.3.0, targetSdk 36 |
| 8 | `apksigner verify` | PASS | debug certificate (`CN=Android Debug`) |
| 9 | `xray -test -c rendered.json` (pinned v26.10.29) | PASS | `Configuration OK` |
| 10 | `core.New()` on the same rendered config (Go, linux) | PASS | `CORE_NEW_OK` |
| 11 | Bundle decrypt round-trip (Python, PBKDF2+AES-GCM) | PASS | plaintext equals `operator/bundle.source.json` |
| 12 | `gomobile bind` (merged module, arm64+amd64) | PASS | `mohammad-core.aar` 46 MB → 39 MB compressed in APK |

`NOT VERIFIED`:

- `./gradlew connectedDebugAndroidTest` — no device and no `/dev/kvm`.
- `adb install` / `am start` / cold-start timing — no device.
- AmneziaWG runtime against a real AWG server — no AWG profile, no device.
- Cold-start < 2000 ms (NFR-16) — device-only.

## Unit test inventory (20)

| Suite | Covers |
|---|---|
| `BundleParseTest` (9) | valid bundle + defaults, wrong schema, empty profiles, bad port, protocol/core mismatch, duplicate ids, missing xray, AWG profile |
| `ConfigRenderTest` (4) | Xray tun inbound + outbound tags/protocols + routing, AWG key passthrough + defaults, AWG without peers, JSON well-formedness |
| `RedactionTest` (4) | UUID, endpoint, long key token, plain text preserved |
| `RetryPolicyTest` (3–4) | exponential growth, cap, exhaustion, boundary |

## Review resolution (M6)

The independent review (`REVIEW.md`) raised 4 blockers and 12 majors. Status:

| ID | Finding | Status |
|---|---|---|
| B-1 | AWG adopted the caller's fd without dup → double close | **Fixed**: `unix.Dup` + `SetNonblock` in `awg.go`; Java keeps ownership |
| B-2 | 15 s connect timeout unreachable (hopped onto the blocked worker) | **Fixed**: timeout now evaluates on the scheduler thread, invalidates the generation, closes the TUN to unblock the core |
| B-3 | Retry could run after `stopSelf()` / user disconnect | **Fixed**: `generation` + `userStopped` checked before and after establish and after core start |
| B-4 | `onDestroy` raced the worker; revoke path could be dropped | **Fixed**: disconnect executes synchronously on the caller; `onDestroy` closes under the same lock and publishes `DISCONNECTED` |
| M-1 | Permission denial reported as ERROR | **Fixed**: `Tunnel.permissionDenied` + `DISCONNECTED`, copy per SPEC §8 |
| M-2 | Bundle defaults / Hysteria2 allow-list ignored | **Fixed**: `defaultDnsServers`, `defaultMtu`, `defaultProfileId` parsed and used; xray keys allow-listed |
| M-3 | Selection not persisted | **Fixed**: SharedPreferences `selected_profile` |
| M-4 | PBKDF2/AES on the main thread | **Fixed**: `Dispatchers.IO` + 5 s `withTimeout` |
| M-5 | Redaction gaps (IPv6, base64, bare endpoints) | **Fixed**: IPv6, bracketed endpoints and base64 tokens added; endpoint no longer logged |
| M-6 | Render failure surfaced as CoreStartFailed | **Fixed**: `StartResult.INVALID_PROFILE` |
| M-7 | Non-volatile `attempt`, swallowed rejection | **Fixed**: `@Volatile` + explicit `RejectedExecutionException` logging |
| M-8 | No domain/ui modules, JNI visible to the service | **Accepted deviation** — documented in `DECISIONS.md` D-19 |
| M-9 | IPv4-only, no per-profile TUN addresses | **Accepted deviation** — documented |
| M-10 | AWG drops `i1..i5`/`header_protection_key` | **Accepted deviation** — the pinned bridge exposes only the supported subset |
| M-11 | UAPI built by interpolation | **Partly fixed**: endpoint validated with `net.SplitHostPort`, control characters rejected; keys validated to 32 bytes |
| M-12 | `IsRunning` cannot detect a crash | **Open** — documented as a known limitation; the watchdog only catches a device that reports false |
| Minor | log cap 800 → 1000, password escaping, notification permission | **Fixed** |
| Minor | Compose state hoisting, contentDescription, dark theme | **Open** — tracked as UI polish |

## Known limitations (honest list)

1. No device verification of the tunnel; only the core/config layer is proven.
2. AmneziaWG has never carried traffic.
3. `IsRunning` is a handle check, not a liveness probe.
4. IPv4-only TUN (`10.10.14.1/30`, route `0.0.0.0/0`), no IPv6 route.
5. Debug signing only; `armeabi-v7a` excluded.
6. No instrumentation tests; UI behaviour is covered only by unit tests of pure logic.
7. Bundle password is compile-time; see D-11 for why this is obfuscation, not secrecy.
