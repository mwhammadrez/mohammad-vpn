# REVIEW.md — Mohammad (`ir.mohammad.vpn`) v0.3.0 / SPEC v1.0

**Scope reviewed (read-only):** `app/src/main/java/ir/mohammad/vpn/{MainActivity.kt, data/Model.kt, data/Tunnel.kt,
core/XrayConfig.kt, core/AwgConfig.kt, vpn/MohammadVpnService.kt, vpn/NetMonitor.kt, vpn/RetryPolicy.kt}`,
`app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `app/src/main/res/**`,
`core-go/libmohammad/{xray.go, awg.go}`, plus `app/src/test/**`, `gradle.properties`, `settings.gradle.kts`,
`core-*/artifacts/`, and the pinned upstream sources used to validate JNI/fd semantics
(`/tmp/xray-src/proxy/tun/tun_android.go`, `/tmp/awg-src/tun/*.go`, `/tmp/awg-src/device/uapi.go`).
No source file was modified; `REVIEW.md` is the only file written.

**Verdict:** the wiring is plausible and the happy path is coherent, but the tunnel lifecycle is not safe under failure,
the 15 s core-start timeout is structurally unreachable, crash detection (FR-14) cannot fire, and the AmneziaWG fd
handoff is a double-close hazard. Not shippable in this state.

| Severity | Count |
|---|---|
| Blocker | 4 |
| Major | 12 |
| Minor | 14 |

---

## BLOCKERS

### B-1 — AmneziaWG adopts the app's own fd without dup; closing the `ParcelFileDescriptor` kills the tunnel and double-closes a raw fd
**Files:** `core-go/libmohammad/awg.go:180-187`, `vpn/MohammadVpnService.kt:342-347` (also `:203-210`)
**Evidence:**
```go
file := os.NewFile(uintptr(tunFd), "tun")          // awg.go:180 — takes ownership, no dup
tunDev, err := tun.CreateTUNFromFile(file, int(mtu)) // awg.go:184
```
```kotlin
tun?.let { runCatching { it.close() } ... }        // MohammadVpnService.kt:342-346
```
Xray, by contrast, explicitly dup's: `unix.FcntlInt(uintptr(fd), unix.F_DUPFD_CLOEXEC, 0)` (`tun_android.go:35`,
"the descriptor stays with the application").
**Impact:** two owners of one fd number. (a) Any path that closes the PFD while AWG is running closes the fd the
device is reading — the tunnel dies silently. (b) `AwgController.Start` failure calls `dev.Close()` (`awg.go:191-198`),
which closes the fd; `closeResources()` then calls `ParcelFileDescriptor.close()` on the same, already-closed number.
Android's `ParcelFileDescriptor` has no knowledge of the Go-side close (`mClosed` is still false), so `IoUtils.closeQuietly`
issues `close(fd)` again — if the number were reused meanwhile, an unrelated socket/binder fd is closed. This is exactly
the class of bug that produces "random" `EBADF`/hung-network reports. It also means the Xray and AWG paths have different
ownership contracts behind one call site.
**Required fix:** pick one owner. Either dup in Go (`unix.FcntlInt(unix.F_DUPFD_CLOEXEC)`) before `os.NewFile`, or
`detachFd()` in Kotlin and hand ownership over permanently (never close the PFD again), and document the contract next
to `AwgController.Start`. Add a test that asserts `closeResources()` is idempotent per path.

### B-2 — The 15 s core-start timeout (FR-13 / NFR-11) can never fire
**Files:** `vpn/MohammadVpnService.kt:235-247`, `:85-141`
**Evidence:**
```kotlin
connectTimeout = scheduler.schedule({
    worker.execute {                      // :238 — only re-queues onto the same single thread
        synchronized(this) { if (Tunnel.state.value is TunnelState.Connecting) { ... failAndStop(...) } }
```
`connectBlocking()` runs on the single-threaded `worker` and holds `synchronized(this)` for the entire
`buildInterface()` + `core.start()` duration (`:86`, `:103-139`). The scheduled runnable never performs the check; it
only enqueues it behind the very task it is supposed to interrupt. `Executors.newSingleThreadExecutor` executes it after
`connectBlocking` returns — at which point the state is `Connected` (or already `Failed`), so the timeout branch is dead
code.
**Impact:** a hung `Builder.establish()` or `XrayController.start` blocks forever, holds the service monitor (so
Disconnect/Cancel also block on the worker), never produces `ERROR(CoreStartFailed)`, and leaves the FGS notification at
"Connecting…". FR-13, J-5, AC-13 are unimplementable as written.
**Required fix:** evaluate the timeout on the scheduler thread itself (`if (deadlineExceeded) { forceTeardown() }`)
against an atomic "connect in progress since <elapsedRealtime>" token, and make the teardown path not require the worker
monitor. Add a virtual-clock test that never lets `connectBlocking` return.

### B-3 — A retry / second queued connect can establish a tunnel after `stopSelf()` and after a user disconnect
**Files:** `vpn/MohammadVpnService.kt:74-78`, `:85-141`, `:279-296`, `:362-371`
**Evidence:**
```kotlin
ACTION_DISCONNECT -> {
    attempt = Int.MAX_VALUE          // :75 — only ever read by scheduleRetry()
    worker.execute { disconnectBlocking("disconnect requested") }
    stopSelf()                       // :77 — onDestroy()/shutdownNow() may run before that task starts
```
```kotlin
scheduler.schedule({ worker.execute { connectBlocking() } }, delay, ...)   // :295
```
`connectBlocking()` never consults `attempt`, a "user stopped" flag, or the current state beyond `is Connected`
(`:87`). So: (1) a retry task already handed to `worker` executes even after `scheduler.shutdownNow()`
(`shutdownNow` returns queued tasks but cannot stop one that already started); (2) `shutdownNow()` also *interrupts* an
in-flight `connectBlocking`, and the Go JNI call is not interruptible, so the connect continues on a destroyed service
and then assigns `xray = core` / `tun = descriptor` on an object whose `onDestroy` already ran — an orphan native core
plus a live fd with no service and no foreground notification; (3) a second `ACTION_CONNECT` queued behind a failing
first attempt re-establishes the tunnel after `failAndStop()`'s `stopSelf()`. The tunnel then stays up, `Tunnel` says
`Connected`, and nothing can ever stop it except process death.
**Required fix:** a monotonically increasing `sessionId`/`connectGeneration` incremented on every user Disconnect/revoke
and checked inside `connectBlocking` immediately before and after `establish()`/`core.start()`, with the late result
torn down inline; plus an explicit `userStopped` boolean that `connectBlocking` refuses to cross. Do the retry hop
through the same generation check.

### B-4 — Disconnect / `onRevoke` teardown races `onDestroy`, drops the queued task, and can leave `Tunnel` stuck at `CONNECTED`/`CONNECTING`
**Files:** `vpn/MohammadVpnService.kt:74-78`, `:309-325`, `:352-360`, `:362-371`
**Evidence:**
```kotlin
override fun onDestroy() {
    cancelConnectTimeout(); cancelHealthCheck()
    closeResources()            // :366 — unsynchronized, no Tunnel.update(...)
    worker.shutdownNow()        // :368
```
```kotlin
super.onRevoke()                // :359 — VpnService's default implementation calls stopSelf()
```
**Impact:** three distinct failures. (1) `shutdownNow()` frequently wins the race against the just-queued
`disconnectBlocking`, so `Tunnel` is never set to `DISCONNECTED`: the UI keeps showing `Connected` (or `Connecting…`)
with no service behind it, and the button label stays "Disconnect". (2) `closeResources()` (which mutates
`tun/xray/awg` and calls JNI `stop()`) runs on the main thread with no monitor while the worker may be inside the same
method under `synchronized(this)` (`:310`, `:327-348`) — two threads closing the same `ParcelFileDescriptor` and calling
`stop()` on the same controller. (3) `onRevoke` is *supposed* to end in `DISCONNECTED` + `VPN revoked by system`
(FR-15/J-8); instead the state update lives in a task the default `stopSelf()` very likely cancels, and `onDestroy`
does not update state at all. FR-27 ("stop core, close fd, cancel coroutines, persist state") is only half met: no
state persistence exists anywhere.
**Required fix:** make teardown single-threaded and idempotent — do the work on the worker, call
`stopForeground` + `stopSelf(startId)` *after* the worker task completes (post to main), have `onDestroy` join the
worker briefly (`shutdown()` + `awaitTermination`, `shutdownNow()` only as a fallback) and set
`DISCONNECTED` with the appropriate log entry, and route `onRevoke`'s state update through the same path before
calling `super.onRevoke()`.

---

## MAJOR

### M-1 — Permission denial puts the machine in `ERROR`, not `DISCONNECTED` (FR-11, §7, §8)
`MainActivity.kt:66-71`:
```kotlin
} else {
    LogStore.add("E", "VPN permission denied")
    Tunnel.update(TunnelState.Failed(ir.mohammad.vpn.data.TunnelError.VpnPermissionDenied))
```
SPEC FR-11: "stay `DISCONNECTED`; log `VPN permission required`; show `Allow VPN permission to connect.`".
§7 forbids `DISCONNECTED --(perm denied)--> ERROR`. The copy is right only because `statusDetail` happens to map
`Failed(VpnPermissionDenied)`; the state, button label ("Retry" instead of "Connect"), and log text are wrong, and the
event name `VPN_PERMISSION_DENIED` (J-3) is absent. Fix: keep `TunnelState.Disconnected` and carry the message
separately (or a `Disconnected(reason)` variant), and emit the structured event.

### M-2 — Bundle validation is incomplete and three documented defaults are ignored (FR-2, FR-23, FR-24, FR-29, §4.2)
`data/Model.kt:75-113`, `vpn/MohammadVpnService.kt:37-38`, `:216-226`
- `defaultDnsServers`, `defaultMtu`, `defaultProfileId` are never read from the bundle; the service hardcodes
  `DEFAULT_MTU = 1400` and `DEFAULT_DNS = listOf("1.1.1.1","8.8.8.8")`. FR-24 and the `defaultProfileId` rule are
  unimplemented (verified: the shipped `bundle.source.json` contains all three).
- Core/protocol consistency is not checked: `if (core !in setOf("XRAY","AMNEZIAWG")) ...` (`:102`) accepts
  `protocol=WIREGUARD, core=XRAY`, which §4.2 forbids.
- `defaultDnsServers` literal validation (1–4 valid IPv4/IPv6) is missing.
- The bundle's core-specific block is read from `"xray"` / `"awg"` (`:105-108`) while SPEC §4.2 defines a single
  `"outbound"` object — spec and implementation disagree about the on-disk format.
- FR-29 is entirely absent: no Hysteria2 key allow-list. Unsupported keys travel through `XrayConfig.render` into
  `serial.LoadJSONConfig` and fail at runtime as `CoreStartFailed`, which is exactly what FR-29 forbids.

### M-3 — No persistence of the selected profile and no state persistence (FR-4, FR-27)
`Tunnel.selected` is a `@Volatile var` in a process singleton (`data/Tunnel.kt:37-38`). There is no DataStore /
SharedPreferences anywhere in the module; after process death the selection is gone and FR-4's "use it for the next
Connect" is unmet. Because profiles are loaded only by `MainActivity.loadProfiles()` (`MainActivity.kt:80-90`), the
service cannot know a profile when started without the Activity (always-on, `am startservice`, system restart) — see
M-10.

### M-4 — Bundle decryption + PBKDF2(210k) + JSON parse run on the main thread (NFR-15, §4.3, NFR-11)
`MainActivity.kt:76-77` → `loadProfiles()` → `OperatorBundleLoader.load()` (`Model.kt:40-73`) in `onCreate`.
SPEC §4.3 explicitly requires "decrypts inside `Dispatchers.IO` with a 5 s timeout"; NFR-11 repeats the 5 s bundle
budget. As written this is blocking disk I/O plus ~210k PBKDF2 iterations on the UI thread before `setContent`, which
directly attacks AC-39 (< 2000 ms cold start) and can ANR on slow devices. There is also no timeout, so a pathological
bundle hangs startup.

### M-5 — Redaction does not cover the secret classes NFR-4 names (NFR-4, J-2, AC-31)
`data/Tunnel.kt:72-91`:
```kotlin
private val longTokenPattern = Regex("[A-Za-z0-9_\\-+/=]{40,}")
private val endpointPattern = Regex("\\b[a-zA-Z0-9._-]+\\.[a-zA-Z]{2,}:\\d{1,5}\\b")
```
Verified by executing the three regexes against representative inputs:

| Input | Result |
|---|---|
| `target=10.10.14.1:443` | **not redacted** (last label is numeric) |
| `target=[2001:db8::1]:443`, `2001:db8::1:443` | **not redacted** (IPv6 literals unmatched) |
| `encryption=mlkem768x25519plus` | **not redacted** (< 40 chars) — the exact class NFR-4 names |
| `key=cHJpdmF0ZQ==` (8-byte base64) | **not redacted** (< 40 chars, `=` alone is not enough) |
| `key=AbCdEf0123456789AbCdEf0123456789` (16-byte hex) | **not redacted** |
| `uuid 0722d93a-…`, 64-char key, 44-char PSK | redacted |

`MohammadVpnService.kt:101` logs `target=${profile.host}:${profile.port}` — with an IPv4/IPv6 `serverHost` the full
endpoint reaches the on-screen log and the "Copy" clipboard dump (`MainActivity.kt:222-226`), which is readable by other
apps on Android 13+. Also no rule covers `password=`/`pw=`/`psk=` key-value pairs. `RedactionTest` covers only a
domain-name endpoint and a 64-char token, so the gap is invisible to CI. Fix: redact by *field*, not by shape — pass a
`tag`+message API where known-secret fields are substituted before formatting, add IPv4/IPv6 literal patterns, and add
`mlkem\w+` and short-base64/hex heuristics with tests for each row above.

### M-6 — Config-render failures are reported as `CoreStartFailed` (FR-2 vs FR-13 copy mapping)
`MohammadVpnService.kt:145-151` sets `Tunnel.update(Failed(InvalidProfile))` and returns `false`, then the caller
(`:129-132`) immediately does `failAndStop(TunnelError.CoreStartFailed)`, overwriting it. The user sees
"Could not start the VPN engine." for what is a configuration/validation failure that should surface
"Configuration is unavailable. Contact the app operator." (§8). `startAwg` has the same pattern (`:183-189`). Fix:
return a typed result (`CoreStartResult.RenderFailed` vs `StartFailed`) instead of `Boolean`.

### M-7 — Unsynchronized field access, a non-volatile cross-thread counter, and `RejectedExecutionException` after shutdown
**Files:** `vpn/MohammadVpnService.kt:46-57`, `:66-78`, `:238`, `:257`, `:295`, `:362-371`
`tun`, `xray`, `awg` are mutated under `synchronized(this)` on the worker but read/closed without the monitor from
`onDestroy` (main) and written from `onStartCommand` (`attempt`, main). `attempt` is a plain `Int` written on the main
thread and incremented on worker/scheduler threads — no visibility guarantee, lost increments. After
`worker.shutdownNow()`, every `worker.execute { ... }` (connect timeout, health check, retry) throws
`RejectedExecutionException` on the scheduler thread; the exception is swallowed into the `ScheduledFuture` and the task
simply disappears with no log (`NFR-12` spirit). Fix: one command queue with explicit ownership, `@Volatile`/`Atomic*`
for `attempt`, and a rejection handler that logs.

### M-8 — Architecture departs from SPEC §3: no `ui/`/`feature/`/`domain/`, no `CoreBridge`
`MainActivity.kt:49-54` imports `data.LogStore`, `data.OperatorBundleLoader`, `vpn.MohammadVpnService` directly;
`MohammadVpnService.kt:21-22` imports the JNI types `libmohammad.XrayController`/`AwgController` with no bridge
interface. §3 mandates `ui → feature → domain` and "core is only reachable through `CoreBridge`", and no JNI type may
cross into `ui`/`domain`. Consequences beyond style: none of AC-03/05/06/09/21/42/44 (Compose/UI) or AC-16/27
(concurrency) can be written, and there is no seam to fake the core. The whole service is a 434-line untestable class.

### M-9 — TUN interface is hardcoded and ignores the profile (FR-8, FR-23)
`MohammadVpnService.kt:214-231`:
```kotlin
.setMtu(DEFAULT_MTU).addAddress(TUN_ADDRESS, TUN_PREFIX).addRoute("0.0.0.0", 0)
```
`TUN_ADDRESS=10.10.14.1/30` is fine for the Xray inbound's `gateway`, but an AmneziaWG profile carries its own
interface address/subnet and often a dual-stack `allowedIPs` set; the device gets 10.10.14.1/30 while the peer expects
e.g. `10.8.0.2/32`. No IPv6 address or `addRoute("::", 0)` is ever configured, so `allowedIPs: ["::/0"]` silently
blackholes IPv6. FR-23 says the interface is "configured from profile or defaults: MTU, IPv4, IPv6 when applicable,
routes, DNS" — the implementation is from constants only.

### M-10 — AmneziaWG obfuscation keys are silently dropped; zero values are skipped
`core-go/libmohammad/awg.go:48-60`, `:98-119`
```go
type obfJSON struct { Jc, Jmin, Jmax, S1..S4, H1..H4 int }
if o.Jc > 0 { fmt.Fprintf(&b, "jc=%d\n", o.Jc) }
```
The pinned device accepts far more (`/tmp/awg-src/device/uapi.go:438-537`): `i1..i5`, `header_protection_key`,
`content_padding_addition`, `rekey_after_time`, `rekey_timeout`, `reject_after_time`, `keepalive_timeout`,
`max_handshake_attempts`, `random_trailers`, `disable_cookies`. Real AmneziaWG server configs use several of these; they
are accepted by `AwgConfig.render` (`awg.optJSONObject("obf")` is forwarded wholesale) and then discarded in the bridge,
producing a tunnel that fails to handshake with no diagnostic. The `> 0` guard also silently drops legitimate `0`
(e.g. `Jc=0`, `S1=0`) instead of emitting them. Fix: model every supported key, forward zero where the UAPI accepts it,
and reject unknown keys at bundle validation (FR-29 principle) rather than dropping them.

### M-11 — UAPI is built by raw string interpolation from bundle data (config injection) and validated at runtime instead of at load (FR-2)
`core-go/libmohammad/awg.go:134-142`:
```go
if p.Endpoint != "" { fmt.Fprintf(&b, "endpoint=%s\n", p.Endpoint) }
for _, ip := range p.AllowedIPs { fmt.Fprintf(&b, "allowed_ip=%s\n", ip) }
```
A newline in `endpoint` or `allowedIPs` injects arbitrary UAPI directives (a second `private_key=`, extra peers,
`replace_peers=true`). The bundle is operator-controlled and encrypted, so this is defense-in-depth, not a live CVE —
but it is also the only place key material is checked at all: `keyToHex` rejects non-32-byte keys only at
`Start` time (`:70-79`), so a bundle with an empty/short `privateKey` passes `parse()` (`Model.kt:107-108` only checks
that the object exists) and surfaces as `CoreStartFailed` instead of `InvalidProfile`+disabled Connect (FR-2).

### M-12 — FR-14 (core crash) cannot be detected
`vpn/MohammadVpnService.kt:266-277`:
```kotlin
val alive = xray?.isRunning ?: awg?.isRunning ?: false
```
`XrayController.IsRunning` is `c.inst != nil` (`xray.go:152-156`) and `AwgController.IsRunning` is `c.dev != nil`
(`awg.go:227-231`). Neither clears on internal failure — `inst`/`dev` are only nil'd by `Stop()`. A core that exits,
panics asynchronously, or whose device tears itself down after a fatal UAPI/handshake error keeps reporting "running",
so the 5 s watchdog can only ever fire after `closeResources()` already ran (where `alive` is false but state is no
longer `Connected`). FR-14/J-6/AC-14 are unimplementable in this shape. Fix: add a fatal-error `StatusHandler`
callback (level ≥ 3) from Go that pushes `TunnelFailed` into the service, and/or expose a real "core exited" signal
(e.g. `inst.Wait()` in a goroutine).

---

## MINOR

**m-1 Stale foreground notification on a redundant Connect.** `MohammadVpnService.kt:68-72` always
`startForeground(..., "Connecting…", "Starting VPN engine")`, and `connectBlocking`'s already-connected early return
(`:87-90`) never calls `updateNotification`. A second `ACTION_CONNECT` (or a retry while connected) leaves the FGS
notification permanently reading "Connecting…"/"Starting VPN engine". Fix: update the notification on the early-return
branch, or refuse the start before calling `startForeground`.

**m-2 Offline retry leaves the service alive, non-foreground, and never stops.** `:286-290`:
`LogStore.add("W", ...); stopForegroundCompat(); return` — no `stopSelf()`. The service stays started in the background
with no notification, state `Failed`, and will never retry (the same branch is taken every time). Combined with
`NetMonitor.isCurrentlyOnline()` (`NetMonitor.kt:61-65`) reporting `activeNetwork` capabilities, the VPN's own network
may lack `NET_CAPABILITY_INTERNET` while up, permanently suppressing reconnect.

**m-3 Observability/state-machine gaps (FR-18, §7, NFR-26/27/28, J-1…J-10).** `Tunnel.update` (`Tunnel.kt:40-42`) is a
bare `_state.value = next` — no forbidden-transition rejection (`InvalidStateTransition` /
`TUNNEL_OPERATION_REJECTED` are never emitted), no central "every state change appends a timestamped log entry"
(entering `Connecting` and `Failed` are not logged as transitions). Log lines are free text only: no event name, no
correlation id, none of the `OPERATOR_BUNDLE_MISSING` / `VPN_PERMISSION_DENIED` / `CORE_START_TIMEOUT` /
`CONNECT_IGNORED_ALREADY_CONNECTED` names from §11 exist, so AC-45 and AC-17 cannot pass.

**m-4 Ping quality (FR-20/FR-21, NFR-11).** `MainActivity.kt:246-256`: `catch (e: Exception) { -1L }` swallows every
failure *and* `CancellationException`, so the `withContext(Dispatchers.IO)` job does not cancel. The 5000 ms is only
the `Socket.connect` timeout — DNS resolution in `InetSocketAddress(profile.host, port)` is unbounded, so the 5 s budget
can be exceeded. And `:206` logs `Ping timed out` for *any* failure (refused, DNS, no route), not just the §8 J-7 case.
Since the app excludes itself from the tunnel (FR-25), the measurement also does not traverse the VPN at all.

**m-5 Compose state hoisting.** `MainActivity.kt:125` `var selected by remember { mutableStateOf(Tunnel.selected) }`
takes a one-shot snapshot of a non-observable `@Volatile` field; `LaunchedEffect(Unit)` at `:135-137` re-reads it once
and is redundant. `Tunnel.profiles` is read directly in composition (`:181`), so a later/bundle-refresh never
recomposes. `context as MainActivity` (`:120`) crashes in previews/tests. `DropdownMenu` is a sibling in the `Column`
rather than anchored to the button in a `Box` (`:173-191`), so the popup is positioned against the column, not the
selector. Fix: expose `StateFlow<List<Profile>>` + `StateFlow<Profile?>` from a ViewModel and collect them.

**m-6 Accessibility and theming (§8, AC-44).** No `contentDescription`, `semantics`, or `stateDescription` anywhere in
`MainActivity.kt`; the status card (`:149-155`) is plain `Text` with no live-region semantics, and the log list has no
semantics role. `res/values/themes.xml` uses the platform `android:Theme.Material.Light.NoActionBar` with a hardcoded
white `windowBackground`, and there is no `values-night`: Material 3's default `lightColorScheme` is used, so §8
"dark/light via Material 3" is unmet.

**m-7 Log cap contradicts NFR-19.** `Tunnel.kt:69` `MAX_ENTRIES = 800`; NFR-19/AC-42 require 1,000 entries rendered.
`items(logs)` (`MainActivity.kt:233`) has no stable `key`, and the auto-scroll effect keys on `logs.size` only.

**m-8 Silent failure paths (NFR-12).** `runCatching { ... }` with no `onFailure`: `MohammadVpnService.kt:367`
(`netMonitor.stop()`), `:409-413` (`updateNotification`), `NetMonitor.kt:58` (`unregisterNetworkCallback`). The
`catch (e: Exception)` blocks in `Model.kt:60-62,68-70,78-80` and `:43-45` discard the cause entirely (no
`BundleException(msg, e)`), so J-2's diagnosis has nothing to work with. None of these are *empty* catches, so
`check-no-placeholders` (AC-36) will pass — which is the problem: the gate does not catch them.

**m-9 `BuildConfig` password interpolation.** `app/build.gradle.kts:25`:
`buildConfigField("String", "OPERATOR_PASSWORD", "\"$operatorPassword\"")` — unescaped. A password containing `"` or `\`
produces invalid generated Java (build failure) or, worse, arbitrary Java in `BuildConfig`. The empty default
(`?: ""`) means a misconfigured build silently produces an APK that can only ever show `InvalidProfile` at runtime; a
`doFirst` check that the asset decrypts with the injected password would fail the build instead.

**m-10 Go hygiene.** `xray.go:1` still says `Package libv2ray` (copy-paste). `os.Setenv("XRAY_TUN_FD", ...)` (`:101`) and
`log.RegisterHandler` (`:106`) are process-global and never reverted/removed on `Stop()`, so logs emitted after teardown
are delivered to a dead controller object. `XrayConfig.kt:39` pins `"loglevel":"debug"` and
`MohammadVpnService.kt:161` sets level 0, so *every* Xray debug line is forwarded into the on-screen log and the clipboard
— maximizing exposure of the M-5 redaction gaps. Default to `warning` and make debug opt-in.

**m-11 AWG Go error handling.** `awg.go:210-215` swallows a panic in `Stop` and returns `err = nil`, so a failed
teardown is indistinguishable from success (`closeResources` logs "awg.stop ok"). `awg.go:180-187` leaks the `os.File`
when `tun.CreateTUNFromFile` fails (its own `nameSlow` error path does not close the file either). `h1..h4` are emitted
as `%d`, which `/tmp/awg-src/device/noise-types.go:106-131` accepts as a single-number range — correct today, but the
bundle may legitimately carry `"1000-2000"` ranges, which `optInt` silently turns into `0` → header protection silently
disabled. Same `optInt`/`optString` problem for every AWG field in `AwgConfig.render` (`:19-34`).

**m-12 Build/process drift from SPEC and DECISIONS.** `gradle.properties:1-3` uses `-Xmx1024m` and `workers.max=1`
(NFR-29 specifies 1536m / 2; `kotlin.incremental=false` is absent). `core-*/artifacts/` contain no `CHECKSUMS.txt`
despite NFR-30 requiring recorded SHA-256 (I verified `app/libs/mohammad-core.aar` and
`core-go/artifacts/mohammad-core.aar` are byte-identical — the checksum just is not recorded). `core-go/go.mod` ends with
`replace github.com/xtls/xray-core => /tmp/xray-src` and `... => /tmp/awg-src`: the build is not reproducible outside
this machine. AGP is `8.13.2` vs D-13's `9.4.1`; `abiFilters` is `arm64-v8a` only vs D-09's arm64 + x86_64. And there is
no `.git` at the project root, so SPEC §13's "a commit exists" is unmet.

**m-13 Model typing and dead code.** `Profile.protocol` and `Profile.core` are `String` with `setOf(...)` membership
checks (`Model.kt:93-103`) where §4.1 specifies enums — typos surface at runtime as `CoreStartFailed`. `Tunnel.isBusy`
(`Tunnel.kt:44-45`) is unused. `Profile` has no `createdAtUtc`/`updatedAtUtc` (§4.1).

**m-14 `POST_NOTIFICATIONS` never requested.** Declared at `AndroidManifest.xml:8` but no runtime request exists; on
API 33+ the ongoing tunnel notification is silently hidden, so the user has no always-visible indicator of an active
VPN (arguably a safety issue for a VPN app). Request it on first Connect.

---

## Answers to the focus questions

**fd ownership Java ↔ Go.** Xray path: safe by construction. `tun_android.go:30-40` does `SetNonblock` on the app's fd
and then `F_DUPFD_CLOEXEC`; the duplicate shares the open file description, the TUN interface stays alive while any fd is
open, and closing the PFD merely drops one reference — so `closeResources()`'s "stop core, then close PFD" order is
correct there. Two caveats: the `SetNonblock` error branch does `unix.Close(fd)` on the *app's* fd, which the PFD will then
close again (double close), and the dup is only created inside `inst.Start()`, so a failure between `core.New` and
`inst.Start` cannot leak one (the `inst.Close()` path covers it). AWG path: unsafe — see B-1; it is the same fd, not a
dup, and `dev.Close()` closes what Java still owns.

**Threading.** One correct idea (all tunnel work on `worker` behind `synchronized(this)`) undermined in three ways:
`onDestroy`/`onStartCommand` mutate the same fields without the monitor (M-7, B-4); the timeout and watchdog re-enter
the worker instead of acting on their own thread (B-2); and `attempt` is a racy plain `Int`. I found no true deadlock —
`stopSelf()` does not run `onDestroy` synchronously, and the main thread never takes the service monitor — but the
unsynchronized `closeResources()` race is the practical equivalent (two `stop()`/`close()` calls on the same objects).
`stopSelf`/`stopForeground` ordering: `ACTION_DISCONNECT` stops the service before the transition it exists to perform,
and `onDestroy` never publishes `DISCONNECTED`, which is how the UI ends up stale.

**Auto-reconnect.** `RetryPolicy` itself is fine and well tested (`app/src/test/.../RetryPolicyTest.kt`). The
integration is not: the budget resets to 0 on every successful connect (`:135`), so a flapping core reconnects forever
at the 3 s floor with no jitter; `hasAttemptsLeft(attempt + 1)` and `delayFor(attempt)` are consistent but the
`attempt = Int.MAX_VALUE` "user said stop" sentinel is only consulted by `scheduleRetry`, never by `connectBlocking`
(B-3); offline handling removes the notification but keeps the service (m-2); and a queued or already-running retry can
outlive `stopSelf()` (B-3). On user disconnect, correctness currently depends on `shutdownNow()` winning a race.

**Security.** AES-GCM/PBKDF2 usage itself is sound (random per-bundle salt/IV, tag verified, header checked, password
required non-empty, `allowBackup=false` + `fullBackupContent=false` + extraction rules excluding `root/.`, service
`exported=false` with `BIND_VPN_SERVICE`, activity exported only for LAUNCHER). The weaknesses are (1) redaction
coverage (M-5), (2) the plaintext password in `BuildConfig` being fed into a shell of an unescaped Gradle string (m-9),
(3) 210k-iteration PBKDF2 on the main thread (M-4), and (4) debug-level core logs piped to a copyable UI buffer (m-10).

**Spec compliance summary.** Met: FR-1 (load), FR-3 (selector shows name/protocol/host), FR-6/7/8/9 (happy path),
FR-12 (establish null → TunInterfaceFailed, resources released), FR-16/17 (idempotent in the common path), FR-20
(success copy), FR-22 (clear log), FR-25 (`addDisallowedApplication`), FR-26 (`SUPPORTS_ALWAYS_ON` present),
NFR-3, AC-30/AC-25 greps. Not met or unverifiable: FR-2 (partly), FR-4, FR-11, FR-13, FR-14, FR-15, FR-18, FR-19,
FR-21, FR-23, FR-24, FR-27, FR-28 (races), FR-29, NFR-4, NFR-11, NFR-12 (partly), NFR-15, NFR-19, NFR-26–28,
§3 architecture, §8 accessibility/dark mode.

**Test evidence.** Only 4 JVM test classes exist (17 tests): bundle parse, config render, redaction, retry. Bundle
decrypt-at-rest, service lifecycle, disconnect/revoke, fd cleanup, timeout, retry-after-stop, concurrency (AC-27) and
all Compose ACs (03/05/21/42/44) have no test at all — expected, since none of those seams exist yet (M-8). AC-31
("no secret pattern in any emitted log") would fail today against an IPv4 or IPv6 host.

## Suggested fix order

1. B-1 (fd ownership contract) and B-4 (single-threaded, idempotent teardown that always publishes `DISCONNECTED`).
2. B-2 (real timeout) and B-3 (connect generation guard + `userStopped`), then M-12 (fatal-core callback) so retry has a
   real trigger.
3. M-5 (field-based redaction) and M-10/M-4 (off the main thread, debug logging off by default).
4. M-2/M-3 (bundle validation + defaults + persistence + FR-29 allow-list), M-1/M-6 (state and copy mapping).
5. M-8/M-9 (extract `CoreBridge` + ViewModel, drive the TUN config from the profile), then backfill the ACs.
