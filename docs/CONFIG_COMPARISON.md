# Config comparison — Mohammad vs. the reference client

Evidence: the reference's own shipped assets (`assets/v2ray_config.json`,
`assets/custom_routing_iran_white`, the preference keys used by its TUN builder) and
Mohammad's `XrayConfig.kt` / `TunPlan.kt`. No code was copied; only configuration shape
and defaults are compared.

---

## 1. Tunnel plumbing — the fundamental difference

| | Reference | Mohammad |
|---|---|---|
| Data path | Java/native **tun2socks**: TUN fd → user-space TCP/IP stack → **SOCKS inbound** of Xray | TUN fd handed **directly** to Xray's native `tun` inbound (`proxy/tun`, new in Xray 26.x) |
| Xray inbounds | `socks` on 10808 + `http` on 10809 | `tun` only |
| fd handoff | fd passed to the tun2socks native library | `XRAY_TUN_FD` env var, Xray duplicates the fd itself |
| Maturity | years of production use | new code path in the pinned core |

**This is the riskiest difference in the project.** If the native `tun` inbound misbehaves
on a given device, the whole tunnel dies while the core still reports "started".

## 2. Xray configuration

| Element | Reference | Mohammad (0.6.2) |
|---|---|---|
| `log.loglevel` | `warning` | `warning` ✅ |
| `stats` | `{}` | `{}` ✅ |
| `policy.system.statsOutbound*` | true / true | true / true ✅ |
| `policy.levels` | `8: {handshake 4, connIdle 300, uplinkOnly 1, downlinkOnly 1}` | `0: {same values}` ✅ (level id differs, behaviour identical because inbounds use level 0) |
| sniffing | `http, tls` | `http, tls, quic` (stronger) |
| `dns` | fake-DNS + local DNS + domestic/remote split, user-configurable | single server list + `queryStrategy` ⚠️ simpler |
| routing strategy | implicit | `IPIfNonMatch` ✅ |

## 3. Routing rules

Reference default (Iran preset), in order:

1. block `udp:443`
2. block `geosite:category-ads-all`
3. direct `geoip:private`
4. direct `geosite:private`
5. direct Iran domains (`geosite:category-ir`)
6. direct Iran IPs (`geoip:ir`)
7. proxy everything else

Mohammad (geo mode), in order:

1. block `geosite:category-ads-all` ✅
2. block `udp:443` ✅
3. direct `geoip:private` + `geosite:private` ✅ (one rule instead of two)
4. direct `domain:ir` + `geoip:ir` ✅ (TLD suffix instead of the `category-ir` set — works even when the dataset lacks that category)
5. proxy `tcp,udp` ✅

**Net: equivalent behaviour.** The one deliberate deviation is `domain:ir` instead of
`geosite:category-ir`, because that category is absent from several popular rule sets and a
missing category is a startup hazard.

## 4. TUN interface

| | Reference | Mohammad |
|---|---|---|
| MTU default | 1500 | 1400 (bundle value; was 1500 in 0.6.0 and reverted) |
| IPv4 address | 8-entry pool | 8-entry pool ✅ |
| IPv6 | added when the device/server supports it | opt-in (`tunIpv6`), default off |
| DNS on the interface | from preferences | from bundle ✅ |
| Self-exclusion | yes | yes ✅ |
| Underlying network | yes | yes ✅ |

## 5. Statistics

| | Reference | Mohammad |
|---|---|---|
| Counters | `queryStats` per tag through JNI | `QueryTraffic()` on `outbound>>>proxy>>>traffic>>>up/downlink` |
| Visibility | works | **0 in the field** — instrumented in 0.6.1 with `StatsDebug()` (`VisitCounters` dump) to be diagnosed from the next report |

## 6. Diagnosis of the 0.6.0 outage

The 0.6.0 report shows the geo download starting **after** `tunnel connected` and still
running 15 s later. That download ran on the same single-thread worker that serves the
stats poll and the watchdog, and it traversed the tunnel itself.

Two independent defects, both fixed in 0.6.1:
1. worker starvation (download now runs on its own executor),
2. 28 MB of traffic injected into a cold tunnel right after connect (now staggered).

0.6.0 also raised MTU to 1500; reverted to 1400.

## 7. If 0.6.2 still fails

Fall back to the reference architecture: run Xray with SOCKS/HTTP inbounds and move the
TUN↔SOCKS bridge into a user-space stack. That is a substantial change (new native
dependency, extra fd plumbing) and is only worth doing if the native `tun` inbound is
proven to be the culprit — which the `stats` block in the next self-test will show.
