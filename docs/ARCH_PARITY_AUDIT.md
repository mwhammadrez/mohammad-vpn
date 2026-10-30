# Architectural Parity Audit — Mohammad 0.7.0 vs. reference client 5.26.83

**Mode:** read-only. **No production file was changed to produce this document.**
**Build audited:** `ir.mohammad.vpn` 0.7.0 (versionCode 11), commit `3bfd09c`, working tree clean.
**Reference:** `com.v2raytun.android` 5.26.83 (versionCode 83), local artifact.
**Verdicts are separated** into structural / behavioural / feature. They must not be merged.

Results vocabulary: `MATCH_STATIC` · `MATCH_RUNTIME` · `DIFFERENT_APPROVED` · `DIFFERENT_UNAPPROVED` · `MISSING` · `UNKNOWN` · `BLOCKED` · `NOT_APPLICABLE`.

---

## 0. Artifact identity — the task's expected hash is wrong

| Source | SHA-256 |
|---|---|
| Artifact actually on disk | `f3715c37bef091f785a9ed2c9d76df0fb42e8aa894c53cce29d3fc29d7cc6021` |
| Project record (`docs`/`REPORT.md`, written at download time) | same, `…9d3fc29d7cc6021` |
| Expected value in the task text | `…29d3fc5c2e` — 42 hex chars, truncated and therefore impossible |

Per the task's own instruction not to accept a substituted digest, the **project record is authoritative** and the task's string is treated as a transcription error. Identity: **MATCH_STATIC** against our own record; the artifact was not re-downloaded.

---

## 1. The contradiction about the reference data path — resolved

Three claims existed: (a) fd handed to a custom `CoreController`, (b) tun2socks → SOCKS, (c) a SOCKS/HTTP asset that might only describe proxy mode.

Evidence from the reference artifact's own `libgojni.so` (arm64, 34 MB) and its shipped assets:

| Observation | Value | Meaning |
|---|---|---|
| `dep gvisor.dev/gvisor v0.0.0-20260122175437-89a5d21be8f0` | present | a **user-space TCP/IP stack** is linked in |
| `strings | grep -c gvisor` | 7 192 | not a stray symbol; the stack is compiled in |
| `strings | grep -ci tun2socks` | 0 | the library's *name* is not embedded (expected) |
| `proxy/tun` package strings | 104 hits, incl. `read Android Tun Fd` | Xray's native tun inbound is **also** compiled in (xray-core ships it) |
| `assets/v2ray_config.json` inbounds | `socks:10808`, `http:10809` | a SOCKS listener is the configured entry point |
| JNI surface | `startLoop(String json, int fd)` | the fd crosses into Go |

**Finding:** gVisor is not a dependency of xray-core or of amneziawg-go, so its presence can only come from a tun2socks-style component. Combined with the SOCKS inbound in the shipped config and the fd parameter, the reference's **active** path is `TUN fd → gVisor-based stack → SOCKS`, i.e. claim (b).

The `proxy/tun` strings prove only that the package exists in the linked core, not that it is used. **Static evidence supports (b); runtime activation cannot be observed without instrumenting the reference on a device → `UNKNOWN` for behavioural proof.**

Result: **MATCH_STATIC** (architecture), **BLOCKED** (runtime confirmation of the reference).

Mohammad's 0.7.0 now implements the same shape. Structural parity: **MATCH_STATIC**.

---

## D01 — Build, provenance, packaging

| ID | Item | Result | Evidence |
|---|---|---|---|
| D01.1 | Xray pinned | **MATCH_STATIC** | `v26.10.29` = `0afec66085a85191e9dd2a4a6646c7c9a94688d1`, `replace` in `core-go/go.mod` |
| D01.2 | AmneziaWG pinned | **MATCH_STATIC** | `v3.1.20260828` = `b5928efb6ca19f0153958460c3d141f04abc5c2e` |
| D01.3 | tun2socks pinned | **MATCH_STATIC** | `v2.7.0` = `8dda19e8e4613e014f0b12f3e624fdff5e5f23b3` |
| D01.4 | Licences | **MATCH_STATIC** | Xray MPL-2.0 · amneziawg-go MIT · tun2socks MIT (`LICENSE`, "Copyright (c) 2019 Jason Lyu") · **gVisor Apache-2.0 → NOTICE obligation, currently unshipped** |
| D01.5 | Native ABI coverage | **DIFFERENT_APPROVED** | debug ships arm64-v8a only (D-09); reference ships 3 ABIs |
| D01.6 | 16 KB page compatibility | **MATCH_STATIC** | every `LOAD` segment aligned `0x4000` (`llvm-readelf -l`) |
| D01.7 | Debug symbols | **DIFFERENT_APPROVED** | stripped via `scripts/strip-aar.sh`; no crash-symbol archive is kept → symbolication of native crashes impossible |
| D01.8 | APK size | **DIFFERENT_UNAPPROVED** | 52 102 164 bytes, up from 45 MB after gVisor; no distribution limit is asserted here, but this is close to common store thresholds |
| D01.9 | Reproducibility | **DIFFERENT_UNAPPROVED** | `go.mod` uses `replace … => /tmp/…` paths; a clean clone cannot rebuild the AAR without editing them |

## D02 — Components and process boundaries

| ID | Item | Result | Evidence |
|---|---|---|---|
| D02.1 | Reference runs the core in `:RunSoLibV2RayDaemon` | **MATCH_STATIC** (reference side) | reference manifest reviewed earlier |
| D02.2 | Mohammad process model | **DIFFERENT_UNAPPROVED** | single process; a native crash takes the UI with it |
| D02.3 | Foreground service type / permission | **MATCH_STATIC** | `specialUse` + `BIND_VPN_SERVICE` |
| D02.4 | `onStartCommand` | **MATCH_STATIC** | `START_NOT_STICKY`, explicit action routing |
| D02.5 | Revoke / destroy | **MATCH_STATIC** | synchronous teardown, state published |
| D02.6 | Always-on | **MATCH_STATIC** | `SUPPORTS_ALWAYS_ON` meta-data present |
| D02.7 | Cross-process state | **NOT_APPLICABLE** | single process |

## D03 — Real packet paths

| Path | Mohammad 0.7.0 | Result |
|---|---|---|
| Xray TCP | TUN → `TunBridge` (Go, worker thread) → gVisor TCP → `127.0.0.1:10808` → Xray socks inbound → outbound | **MATCH_STATIC** (reference runtime `BLOCKED`) |
| Xray UDP | same, gVisor UDP → SOCKS5 UDP ASSOCIATE (`udp: true`) | **UNKNOWN** — association lifetime untested |
| App DNS | app → TUN (1.1.1.1:53) → bridge → socks UDP → Xray DNS engine → `fakedns` first | **UNKNOWN** end-to-end |
| FakeDNS | allocation `198.18.0.0/15`, mapping recovered by sniffing `fakedns` | **UNKNOWN** |
| IPv4 / IPv6 | IPv4 always; IPv6 only with `tunIpv6` (off by default) | IPv6 **MISSING** by policy |
| AmneziaWG | separate controller, fd → `tun.CreateTUNFromFile` | **UNKNOWN** (never carried traffic) |
| Direct / block | routing rules → `direct`/`block` outbounds | **MATCH_STATIC** |
| Proxy-only | HTTP inbound exists, no service | **MISSING** |

## D04 — Descriptor ownership — **FIXED in 0.7.1**

Claim under audit: *"the bridge opens its own view on the same fd number while Java retains ownership."* This is **false** for 0.7.0.

Evidence (`xjasonlyu/tun2socks v2.7.0`, `core/device/fdbased/fd_unix.go`):

```go
func (f *FD) Close() {
    if !f.closed {
        defer f.LinkEndpoint.Close()
        _ = unix.Close(f.fd)   // closes the caller's descriptor number
        f.closed = true
    }
}
```

and `Open(name, mtu, offset)` wraps `os.NewFile(uintptr(fd), …)` around the number we pass. There is **no `unix.Dup`**.

Consequences:

| ID | Item | Result |
|---|---|---|
| D04.1 | Duplication on adopt | **FIXED** — `unix.Dup` before `fdbased.Open`; the device owns only the duplicate |
| D04.2 | Two owners of one fd | **FIXED** — `Stop()` closes the device (duplicate) only; `ParcelFileDescriptor.close()` remains the sole closer of the original |
| D04.3 | Startup failure path | **FIXED** — duplicate closed on every failure branch; the caller's descriptor is never touched |
| D04.4 | Repeated start/stop | **UNKNOWN** — stack is recreated, global `tunnel.T()` proxy is replaced |
| D04.5 | Stop unblocks pending reads | **UNKNOWN** — `stack.Close()+Wait()` should, untested |

The AmneziaWG bridge (`awg.go`) *does* `unix.Dup` + `SetNonblock`; the Xray/TunBridge path does not. This asymmetry is the bug.

**Fix applied (0.7.1):** `TunBridge.Start` duplicates with `unix.Dup`, sets the duplicate non-blocking, and hands the duplicate to `fdbased.Open`; `Stop` closes the device (the duplicate) and is idempotent; every failure branch closes the duplicate.

Host evidence (`cd core-go && go test ./libmohammad/ -run 'TestStopDoesNotCloseCallerFD|TestDoubleStopIsIdempotent|TestStartFailureReleasesResources' -v`):

```
--- PASS: TestStopDoesNotCloseCallerFD (0.00s)
--- PASS: TestDoubleStopIsIdempotent (0.00s)
--- PASS: TestStartFailureReleasesResources (0.00s)
PASS
ok  	ir.mohammad.corego/libmohammad	0.018s
```

**Prerequisite defect found while writing the test:** `proxy.Parse("socks5://…")` failed with `proxy: unknown protocol`, because tun2socks registers protocols through package init. Without a blank import of `proxy/socks5` the bridge could never start — the success-path test skipped. The import was added; see `DECISIONS.md` D-27.

## D05 — TUN construction

| ID | Item | Result |
|---|---|---|
| D05.1 | MTU | **MATCH_STATIC** — 1400 from bundle (preserve per instruction) |
| D05.2 | IPv4 pool | **MATCH_STATIC** — 8 entries, /30 |
| D05.3 | IPv6 | **DIFFERENT_APPROVED** — opt-in, off by default (leak trade-off documented) |
| D05.4 | Default routes | **MATCH_STATIC** — `0.0.0.0/0`, `::/0` only when IPv6 on |
| D05.5 | Blocking mode | **MATCH_STATIC** — `setBlocking(false)`; gVisor reads via its own poller |
| D05.6 | Self-exclusion | **MATCH_STATIC** — `addDisallowedApplication(packageName)`; this is why no `protect()` is used, and the distinction is deliberate |
| D05.7 | `protect()` | **NOT_APPLICABLE** — only required when the app's own sockets are inside the tunnel |
| D05.8 | `setUnderlyingNetworks` | **DIFFERENT_APPROVED** — set at build time, never refreshed on handover |
| D05.9 | Loop guard | **UNKNOWN** — nothing asserts that the active network ≠ the VPN network after handover |

## D06 — tun2socks / SOCKS semantics

| ID | Item | Result |
|---|---|---|
| D06.1 | Dependency pinned & licensed | **MATCH_STATIC** — MIT, v2.7.0 |
| D06.2 | TCP forwarding | **UNKNOWN** (no device) |
| D06.3 | SOCKS UDP association | **UNKNOWN** — listener has `udp: true`, association never exercised |
| D06.4 | Auth mode | **MATCH_STATIC** — `noauth`, loopback only |
| D06.5 | Listener readiness race | **DIFFERENT_UNAPPROVED** — bridge starts immediately after `core.start()`; nothing waits for `10808` to accept. First packets can hit a closed port |
| D06.6 | Partial startup rollback | **DIFFERENT_APPROVED** — failure path stops the core and closes the fd |
| D06.7 | Shutdown bound | **UNKNOWN** — `stack.Close()+Wait()` unbounded |
| D06.8 | HTTP listener | **MISSING** in behaviour (bound but unused) |

## D07 — DNS / FakeDNS end-to-end

| ID | Item | Result |
|---|---|---|
| D07.1 | Query reaches Xray DNS engine | **UNKNOWN** — depends on the bridge forwarding UDP to the socks association |
| D07.2 | `dns.servers` starts with `fakedns` | **MATCH_STATIC** (unit-tested) |
| D07.3 | Sniffing recovers the domain from a fake IP | **UNKNOWN** — `destOverride` includes `fakedns` |
| D07.4 | Proxy-server bootstrap | **UNKNOWN** — the outbound address is a hostname; resolution must not recurse through FakeDNS |
| D07.5 | Android Private DNS / app DoH | **UNKNOWN** — can bypass the tunnel's resolver entirely |
| D07.6 | Leak claim | **NOT CLAIMED** — no measurement method exists without a device |

## D08 — Xray runtime config

| ID | Item | Result |
|---|---|---|
| D08.1 | Inbounds | **MATCH_STATIC** — socks:10808, http:10809 on loopback |
| D08.2 | Sniffing | **DIFFERENT_APPROVED** — `http,tls,fakedns` vs reference `http,tls` |
| D08.3 | Policy level id | **MATCH_STATIC (equivalent)** — ours `"0"` with `userLevel 0`; reference `"8"` with `userLevel 8`; both resolve consistently |
| D08.4 | Stats/policy counters | **MATCH_STATIC** (config) / **UNKNOWN** (runtime) |
| D08.5 | Geolocation assets | **DIFFERENT_APPROVED** — downloaded at runtime, not bundled; literal fallback |
| D08.6 | Validation against pinned core | **MATCH_STATIC** — `xray -test` on the previous shape; the 0.7.0 shape is covered by unit tests only |

## D09 — Routing and geodata

| ID | Item | Result |
|---|---|---|
| D09.1 | Order: udp/443 → ads → private → IR → proxy | **MATCH_STATIC** (unit-tested) |
| D09.2 | `domain:ir` vs `geosite:category-ir` | **DIFFERENT_APPROVED** — documented in D-22; not equivalent by construction |
| D09.3 | Ads blocking without dataset | **DIFFERENT_APPROVED** — falls back to no ads rule when assets are absent |
| D09.4 | Atomic dataset replacement | **MATCH_STATIC** — temp file + rename, size gate |
| D09.5 | Download isolation | **MATCH_STATIC** — dedicated executor after the 0.6.1 regression |

## D10 — Protocol / transport matrix

Rows present in the reference and their status in Mohammad:

| Protocol | Import | Model | Renderer | Runtime |
|---|---|---|---|---|
| VLESS | yes | yes | yes | **verified by operator on 0.1.1 only** (old data path) |
| VMess / Trojan / Shadowsocks / SOCKS / HTTP | yes | yes | yes | **UNKNOWN** |
| WireGuard | yes | yes | AWG core | **UNKNOWN** |
| AmneziaWG | yes | yes | yes | **UNKNOWN** |
| Hysteria2 | yes | yes | yes (Xray subset) | **UNKNOWN** |
| Reality / XTLS | passthrough | passthrough | passthrough | **UNKNOWN** |
| Transports (ws/grpc/h2/httpupgrade) | passthrough | passthrough | passthrough | only `ws` exercised |

---

## Verdicts

| Axis | Verdict | Basis |
|---|---|---|
| **Structural** | **PARTIAL MATCH** | data-path shape matches (`MATCH_STATIC`); process isolation differs; fd ownership is wrong (D04) |
| **Behavioural** | **UNKNOWN** | the 0.7.0 path has never carried a packet on hardware; all prior device evidence belongs to the pre-0.7.0 path |
| **Feature** | **PARTIAL** | no proxy-only service, no per-app, no IPv6 by default, AWG unproven, single-ABI build |

**No runtime parity claim is made.** Device-dependent rows are `BLOCKED` (no device, no KVM; `adb devices` empty, `installDebug` → `DeviceException: No connected devices!`).

## Proposed work, awaiting approval

| P | Item | Change | Test |
|---|---|---|---|
| **P0** | D04 fd double-close | `unix.Dup` in `TunBridge.Start`; close only the duplicate | host test: bridge stop leaves the original open; double stop is a no-op |
| P1 | D06.5 readiness race | poll `127.0.0.1:10808` until accept before starting the bridge, 2 s cap | unit test with a fake listener |
| P1 | D01.4 NOTICE | ship gVisor's Apache-2.0 NOTICE in the APK assets and repo | file check gate |
| P2 | D01.9 reproducibility | replace `/tmp/...` `replace` directives with vendored trees or published pseudo-versions | `go mod verify` in a clean clone |
| P2 | D02.2 process isolation | evaluate a `:core` process | needs a device |
| P3 | D06.3/D07 | UDP association, FakeDNS, handover | **BLOCKED** on device |

No implementation has been performed for any item above.
