# CONNECTION PARITY AUDIT — Mohammad v0.3.0 vs. mature Android Xray client behaviour

**Scope:** read-only. No production file was modified to produce this document.
**Baseline:** `ir.mohammad.vpn` 0.3.0 (versionCode 4), Xray-core `v26.10.29` (`0afec66`), amneziawg-go `v3.1.20260828` (`b5928ef`).
**Reference:** the behavioural patterns of mainstream Android Xray clients (TUN ownership, DNS strategy, routing, stats, proxy-only mode). No v2RayTun code, resource, string or asset was read into this project; the reference is used only to describe *what a mature client does*.

Legend: **PARITY** = Mohammad already behaves equivalently · **PARTIAL** = present but weaker/incomplete · **GAP** = absent · **N/A** = not applicable, reason given.
Evidence is `file:line` in this repository. Anything that needs a device or a real server is marked `NOT VERIFIED` with manual steps.

---

## A1. TUN interface

| # | Behaviour | Mohammad today | Class | Evidence / required fix |
|---|---|---|---|---|
| A1.1 | MTU source and default | `Tunnel.defaultMtu` from bundle (`defaultMtu`, clamped 1200–1500, default **1400**) | **PARTIAL** | `data/Model.kt:106`, `vpn/MohammadVpnService.kt:243-247`. A mature client defaults to **1500** and lets the profile override per-server. Gap: no per-profile MTU; default is conservative. |
| A1.2 | IPv4 TUN address and prefix | Hard-coded `10.10.14.1/30` | **PARTIAL** | `vpn/MohammadVpnService.kt:22-23`. Mature clients keep **8 address pools** because `10.10.14.1/30` collides with real LANs (a user on `10.10.14.0/24` gets a broken tunnel). Requires a pool + collision fallback. |
| A1.3 | IPv6 TUN address and prefix | absent | **GAP** | No `addAddress(ipv6, 126)` anywhere. See A2.3 — this is a **leak vector**, not just a missing feature. |
| A1.4 | IPv4 default route `0.0.0.0/0` | present | **PARITY** | `vpn/MohammadVpnService.kt:252` |
| A1.5 | IPv6 default route `::/0` | absent | **GAP** | On IPv6-capable networks (many mobile carriers) apps with AAAA records keep their traffic on the native interface. |
| A1.6 | DNS servers on the TUN | from `defaultDnsServers`, fallback `1.1.1.1/8.8.8.8` | **PARITY** | `vpn/MohammadVpnService.kt:255-258` |
| A1.7 | Self-exclusion to avoid loops | `addDisallowedApplication(packageName)` | **PARITY** | `:259-260`. Also the reason the engine's own sockets need no `protect()`. |
| A1.8 | `setBlocking` decision | `setBlocking(false)` | **PARTIAL** | `:253`. Xray's `proxy/tun` reads through `os.File` + the Go netpoller, so non-blocking is correct and required; but there is no comment or test locking this decision, and AmneziaWG's `CreateTUNFromFile` is also fed the same fd. Needs an explicit invariant + test. |
| A1.9 | Underlying network | never set | **GAP** | `setUnderlyingNetworks` is not called. On handover (Wi-Fi→LTE) the kernel may keep sourcing from the dead network until the socket times out. Mature clients set the current `Network` on connect and on every `onAvailable`. |
| A1.10 | Always-on VPN metadata | declared `true` | **PARITY** | `AndroidManifest.xml:37-39` |
| A1.11 | `establish()` returns null | treated as fatal, resources released, `TunnelError.TunInterfaceFailed` | **PARITY** | `:136-142` |
| A1.12 | System revokes VPN | `onRevoke()` → synchronous teardown → `DISCONNECTED` | **PARITY** | `:412-417` |
| A1.13 | Cleanup on service destroy | closes core, fd, timers, executors; publishes `DISCONNECTED` | **PARITY** | `:421-443` |

**A1 verdict:** the construction is correct but the *defaults* are the weak point (IPv6 missing, single address pool, MTU 1400, no underlying network).

---

## A2. DNS

| # | Behaviour | Mohammad today | Class | Evidence / required fix |
|---|---|---|---|---|
| A2.1 | Do app DNS queries enter the tunnel? | Yes. `0.0.0.0/0` + `addDnsServer` ⇒ queries to `1.1.1.1:53` are written into the TUN, then matched by the catch-all routing rule and sent to the `proxy` outbound | **PARITY** | `vpn/MohammadVpnService.kt:252,255`, `core/XrayConfig.kt:49-58` |
| A2.2 | TUN DNS vs. Xray `dns` consistency | TUN has DNS servers; the Xray config has **no `dns` object at all** | **GAP** | `core/XrayConfig.kt` builds `log/inbounds/outbounds/routing` only. Consequences: no resolver cache, no `queryStrategy`, no per-domain split, no `hosts` overrides, and every query is a fresh proxied UDP flow. |
| A2.3 | Leak surface while connected | **IPv6 leaks.** No `::/0` route and no IPv6 address ⇒ AAAA traffic and IPv6-only resolvers bypass the tunnel | **GAP** | Follows from A1.3/A1.5. Highest-severity finding in this audit. |
| A2.4 | Bundle `defaultDnsServers` used consistently | used for the TUN builder and the `tun` inbound `settings.dns`; **not** used in an Xray `dns` object (there is none) | **PARTIAL** | Same as A2.2 |
| A2.5 | Can domain-based routing work? | No. Sniffing is on (`core/XrayConfig.kt:31-37`) but `domainStrategy` is `AsIs` and the only rule is catch-all `network: tcp,udp` | **PARTIAL** | Sniffed domain is never consulted by a rule. Requires `domainStrategy: IPIfNonMatch` + domain rules (see A3). |
| A2.6 | FakeDNS | not implemented | **N/A (v1)** | With no `dns` object, FakeDNS cannot be enabled. Current behaviour: all names resolve remotely through the proxy — correct but slower and it defeats per-domain direct rules. Enable only together with A2.2 + A3.2. |

**A2 verdict:** DNS *works* but is unmanaged. The IPv6 hole matters more than the missing resolver.

---

## A3. Routing and traffic steering

| # | Behaviour | Mohammad today | Class | Evidence / required fix |
|---|---|---|---|---|
| A3.1 | Global proxy mode | single catch-all rule → `proxy` | **PARITY** | `core/XrayConfig.kt:49-58` |
| A3.2 | Domain-based rules | none | **GAP** | Needs `routing.domainStrategy` + `rules[].domain` |
| A3.3 | IP-based rules | none | **GAP** | Needs `rules[].ip` |
| A3.4 | GeoIP / GeoSite | no `geoip.dat`/`geosite.dat` shipped, no rules | **GAP** | Xray loads them via `geoip:` / `geosite:` selectors; without assets those selectors fail at start |
| A3.5 | Direct rules (LAN/local) | none — LAN traffic is pushed through the proxy | **PARTIAL** | A mature client always adds `geoip:private` → `direct`; today printing to a LAN printer goes through the server |
| A3.6 | Block rules (ads/QUIC) | none | **GAP** | Typical: `geosite:category-ads-all` → `block`, `udp:443` → `block` |
| A3.7 | Per-app proxy | none | **GAP** | No `addAllowedApplication` path, no app picker (out of v1 UI scope, but the *builder* seam is missing) |
| A3.8 | DNS-based routing | none | **GAP** | Depends on A2.2 |

**A3 verdict:** only global-proxy exists. This is the largest functional gap after IPv6.

---

## A4. Xray configuration generation

| # | Behaviour | Mohammad today | Class | Evidence / required fix |
|---|---|---|---|---|
| A4.1 | Inbound = `tun` with fd from `XRAY_TUN_FD` | yes | **PARITY** | `core/XrayConfig.kt:21-37`, `core-go/libmohammad/xray.go:100-103` |
| A4.2 | Sniffing (`http,tls,quic`) | enabled | **PARITY** | `:31-37` |
| A4.3 | `dns` block | absent | **GAP** | see A2.2 |
| A4.4 | `policy` / `stats` (traffic counters) | absent | **GAP** | no `stats: {}` and no `policy.system.statsOutbound{Uplink,Downlink}` ⇒ the bridge cannot report bytes |
| A4.5 | Per-profile `streamSettings` passthrough | yes, verbatim from bundle | **PARITY** | `core/XrayConfig.kt:18` |
| A4.6 | Mux / fragmentation / sockopt knobs | not exposed | **N/A (v1)** | Deliberate: the bundle carries a complete outbound object, so an operator can add them inside `settings`/`streamSettings` |
| A4.7 | Log level | hard-coded `debug` | **PARTIAL** | `:39` — floods the clipboard-able log on a busy tunnel; should be `warning` by default with a debug switch |

---

## A5. Core lifecycle and crash recovery

| # | Behaviour | Mohammad today | Class | Evidence / required fix |
|---|---|---|---|---|
| A5.1 | Start/stop with fd handoff | yes, Xray dup's the fd, AWG dups it explicitly | **PARITY** | `xray.go:100`, `awg.go:180-196` |
| A5.2 | Connect timeout | 15 s, enforced on the scheduler thread with generation invalidation | **PARITY** | `vpn/MohammadVpnService.kt:268-284` |
| A5.3 | Crash detection | 5 s watchdog on `isRunning` (a **handle check**, not liveness) | **PARTIAL** | `:309-324`. A hung-but-alive core is invisible (documented as D-20) |
| A5.4 | Bounded auto-reconnect | 3 s / 6 s / 12 s, max 3, offline-aware | **PARITY** | `vpn/RetryPolicy.kt`, `:326-350` |
| A5.5 | Retry loop cannot outlive a user stop | `generation` + `userStopped` | **PARITY** | `:99` |
| A5.6 | Kill switch (block traffic when core dies) | none | **GAP** | Tunnel stays up with a dead core ⇒ blackhole, not a leak, but the user gets no signal beyond the state |
| A5.7 | Reconnect on network change | none | **GAP** | `NetMonitor` only logs; `onAvailable` does not re-arm the tunnel |

---

## A6. Network change handling

| # | Behaviour | Mohammad today | Class | Evidence |
|---|---|---|---|---|
| A6.1 | Detect loss/return | `ConnectivityManager.NetworkCallback` → `online` flag + log | **PARTIAL** | `vpn/NetMonitor.kt:18-50` |
| A6.2 | Reconnect after handover | not triggered | **GAP** | The core survives most handovers by itself, but a dead core during handover waits for the 5 s watchdog then retries only if `online` |
| A6.3 | Update `setUnderlyingNetworks` | never | **GAP** | see A1.9 |

---

## A7. Statistics and status reporting

| # | Behaviour | Mohammad today | Class | Evidence |
|---|---|---|---|---|
| A7.1 | Up/down byte counters | none | **GAP** | Bridge exposes `start/stop/isRunning/setLogLevel` only (`core-go/libmohammad/xray.go:65-155`); no `stats` in config |
| A7.2 | Latency measurement through the core | UI-side TCP connect only | **PARTIAL** | `MainActivity.kt:measure()` — measures the *server port*, not the tunnel |
| A7.3 | Status model | 5-state sealed class, single source of truth | **PARITY** | `data/Tunnel.kt` |

---

## A8. Proxy-only mode (SOCKS/HTTP without TUN)

| # | Behaviour | Mohammad today | Class | Evidence |
|---|---|---|---|---|
| A8.1 | Local SOCKS/HTTP listener | absent | **GAP** | No second service; `XrayConfig` emits a `tun` inbound only |
| A8.2 | `protect()` for the listener sockets | n/a | **N/A** | Only needed once A8.1 exists |

---

## A9. Per-app split tunneling readiness

| # | Behaviour | Mohammad today | Class | Evidence |
|---|---|---|---|---|
| A9.1 | Builder seam for allow-list | absent (only `addDisallowedApplication(packageName)`) | **GAP** | `:259` |
| A9.2 | `QUERY_ALL_PACKAGES` + app picker | permission not requested, no UI | **N/A (v1)** | deliberately out of the v1 UI scope |

---

## A10. Diagnostics and logs

| # | Behaviour | Mohammad today | Class | Evidence |
|---|---|---|---|---|
| A10.1 | Structured log with levels and tags | `HH:mm:ss.SSS L/TAG msg`, tags APP/NET/VPN/XRAY/AWG | **PARITY** | `data/Tunnel.kt:LogStore` |
| A10.2 | Secret redaction | UUID, endpoint, IPv6, base64 tokens | **PARITY** | `data/Tunnel.kt:redact` |
| A10.3 | Copy to clipboard | yes | **PARITY** | `MainActivity.kt` log header |
| A10.4 | Export to file / share | absent | **PARTIAL** | Clipboard only |
| A10.5 | Log volume control | core always `debug` | **PARTIAL** | see A4.7 |
| A10.6 | Error taxonomy in reports | typed `TunnelError` | **PARITY** | `data/Tunnel.kt` |

---

## Summary

| Area | PARITY | PARTIAL | GAP | N/A |
|---|---:|---:|---:|---:|
| A1 TUN | 6 | 3 | 4 | 0 |
| A2 DNS | 1 | 2 | 3 | 1 |
| A3 Routing | 1 | 1 | 6 | 0 |
| A4 Xray config | 2 | 1 | 2 | 2 |
| A5 Lifecycle | 4 | 1 | 2 | 0 |
| A6 Network change | 0 | 1 | 2 | 0 |
| A7 Stats | 1 | 1 | 1 | 0 |
| A8 Proxy-only | 0 | 0 | 1 | 1 |
| A9 Per-app | 0 | 0 | 1 | 1 |
| A10 Diagnostics | 4 | 2 | 0 | 0 |
| **Total** | **19** | **12** | **22** | **5** |

## Gap backlog, ordered by user-visible risk

| P | ID | Gap | Why it matters |
|---|---|---|---|
| P0 | A1.3/A1.5/A2.3 | IPv6 address + `::/0` route (or explicit leak-block) | **silent traffic leak** on IPv6 networks |
| P0 | A3.5 | `geoip:private` / `geosite:private` → `direct` | LAN, printers, casts currently tunnel through the server |
| P1 | A2.2/A4.3 | Xray `dns` block (servers, `queryStrategy`, cache) | correctness + latency; prerequisite for domain routing |
| P1 | A3.2/A3.4/A3.6 | domain/IP/geo rules + bundled `geoip.dat`/`geosite.dat` | the main reason to run Xray instead of raw proxy |
| P1 | A6.2/A1.9 | re-arm on `onAvailable`, `setUnderlyingNetworks` | handover resilience |
| P2 | A7.1/A4.4 | `stats` + `policy` + counters in the bridge | status reporting, parity with mature clients |
| P2 | A5.6 | kill-switch semantics on core death | UX truthfulness |
| P2 | A1.1/A1.2 | MTU 1500 default + address pool | LAN collision avoidance |
| P3 | A8.1 | proxy-only mode | feature parity, not required for v1 UI |
| P3 | A9.1 | per-app allow-list seam | future UI |
| P3 | A10.4/A4.7 | log export + level control | support workflow |

## NOT VERIFIED (needs a device / real server)

| Item | Why it cannot be verified here | Manual steps |
|---|---|---|
| IPv6 leak before/after fix | no device, no IPv6 network | connect on an IPv6 carrier, then `curl -6 ifconfig.co` and compare with the server's egress |
| Handover reconnect | no device | enable tunnel, toggle Wi-Fi↔LTE, watch `NET`/`VPN` tags, confirm one retry maximum |
| DNS actually resolving through the proxy | no device | `adb shell` + `nslookup` on the device, or open a domain that only resolves at the server |
| Stats counters | no device | read the counter after a 10 MB download and compare with `TrafficStats` |
| Proxy-only SOCKS | not implemented yet | — |

## Proposed Phase-B order

1. **IPv6 close-out** (A1.3, A1.5, A2.3) + leak test in unit form.
2. **Routing fundamentals** (A3.5, A3.2, A3.6) with bundled geo assets.
3. **DNS object** (A2.2, A4.3) consistent with the TUN builder.
4. **Handover** (A1.9, A6.2).
5. **Stats** (A7.1, A4.4) behind the existing bridge.
6. Proxy-only, per-app seam, log export.
