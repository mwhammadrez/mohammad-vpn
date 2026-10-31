# Universal connection-quality research — 2026-10-30

Research only: no code, config or dependency was changed.

Scope: improvements that apply to **any** profile the app supports (VLESS, VMess, Trojan,
Shadowsocks, SOCKS, HTTP, WireGuard/AmneziaWG), not to any particular server.

Already implemented and therefore **out of scope** (per the brief):
fragment, mux, FakeDNS, domestic DNS for `.ir`, QUIC block, health probe, handover policy,
retry/backoff, TUN fd ownership, uid/socket isolation, MTU 1500, split routes.

---

## 1. Executive summary

Seven changes are worth doing, ordered by (quality gain ÷ risk):

| # | Change | Layer | Effort | Risk |
|---|---|---|---|---|
| 1 | `sockopt.happyEyeballs` (250 ms, 4 concurrent, IPv4 first) | core config | 1 line + 1 test | low |
| 2 | `tcpKeepAliveIdle: 25` + `tcpKeepAliveInterval: 15` | core config | 1 line | low |
| 3 | `tcpUserTimeout: 5000` (from 10 000) | core config | 1 line | low |
| 4 | `tcpMaxSeg: 1440` while TUN MTU is 1500 | core config | 1 line | low |
| 5 | `hosts` entry for the server domain (skip one DNS lookup per connect) | core config | 1 line | low |
| 6 | Parallel dial race across shipped profiles | app | ~40 lines | medium |
| 7 | `setMetered()` + `setBlocking(true)` on the TUN builder | app | 2 lines | low |

Three commonly-suggested changes are **wrong for this app** and are documented as traps:
`sniffing.routeOnly: true` (breaks FakeDNS), `tcpFastOpen` (inert on Android), and
`tcpcongestion: bbr` (needs a kernel module the app cannot load).

---

## 2. Findings

Scoring 1–5 per criterion, total /30.
Criteria: gain (G), universal (U, works for every config type), compatibility (C, with pinned
Xray v26.10.29 and our architecture), risk (R, 5 = safest), cost (K, 5 = no measurable cost),
evidence (E).

| # | Finding | Source | G | U | C | R | K | E | Total | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `sockopt.happyEyeballs` races A/AAAA | https://xtls.github.io/en/config/transports/sockopt.html | 4 | 5 | 5 | 4 | 5 | 5 | 28 | **Adopt** |
| 2 | `tcpKeepAliveIdle/Interval` for NAT keepalive | https://xtls.github.io/en/config/transports/sockopt.html | 5 | 5 | 5 | 5 | 4 | 5 | 29 | **Adopt** |
| 3 | `tcpUserTimeout` lower bound | https://xtls.github.io/en/config/transports/sockopt.html | 3 | 5 | 5 | 4 | 5 | 4 | 26 | **Adopt** |
| 4 | `tcpMaxSeg` clamp | https://xtls.github.io/en/config/transports/sockopt.html | 3 | 5 | 5 | 4 | 5 | 4 | 26 | **Adopt** |
| 5 | `hosts` static entry for server domain | https://xtls.github.io/en/config/dns.html | 3 | 5 | 5 | 5 | 5 | 4 | 27 | **Adopt** |
| 6 | Parallel dial race across profiles | https://developer.android.com/reference/android/net/ConnectivityManager | 4 | 4 | 5 | 3 | 4 | 4 | 24 | **Adopt** |
| 7 | `Builder.setMetered()` | https://developer.android.com/reference/android/net/VpnService.Builder | 2 | 5 | 5 | 5 | 5 | 4 | 26 | **Adopt** |
| 8 | `Builder.setBlocking(true)` | https://developer.android.com/reference/android/net/VpnService.Builder | 2 | 5 | 4 | 4 | 5 | 3 | 23 | Conditional |
| 9 | `sniffing.routeOnly: true` | https://xtls.github.io/en/config/routing.html | 3 | 3 | 2 | 2 | 4 | 4 | 18 | **Trap with FakeDNS** |
| 10 | `tcpFastOpen: true` | https://xtls.github.io/en/config/transports/sockopt.html | 2 | 5 | 5 | 3 | 5 | 2 | 22 | Inert on Android |
| 11 | `tcpcongestion: bbr` | https://xtls.github.io/en/config/transports/sockopt.html | 3 | 4 | 3 | 2 | 4 | 3 | 19 | Kernel-dependent |
| 12 | `tcpMptcp` | https://xtls.github.io/en/config/transports/sockopt.html | 2 | 3 | 2 | 3 | 4 | 2 | 16 | No |
| 13 | `addressPortStrategy` (SRV/TXT) | https://xtls.github.io/en/config/transports/sockopt.html | 2 | 3 | 4 | 3 | 4 | 2 | 18 | No (needs DNS records) |
| 14 | Xray `observatory`/`burstObservatory` | https://xtls.github.io/en/config/observatory.html | 3 | 4 | 5 | 4 | 3 | 4 | 23 | Only with 2+ profiles |
| 15 | `policy.levels` timeouts | https://xtls.github.io/en/config/policy.html | 3 | 5 | 5 | 5 | 5 | 4 | 27 | Already tuned; re-check |
| 16 | DNS `queryStrategy: UseIPv4` for IPv4-only servers | https://xtls.github.io/en/config/dns.html | 3 | 4 | 5 | 4 | 5 | 4 | 25 | Conditional |
| 17 | `domainStrategy: AsIs` on the proxy outbound | https://xtls.github.io/en/config/transports/sockopt.html | 3 | 4 | 4 | 3 | 5 | 4 | 23 | Conditional (needs care) |
| 18 | Pre-resolve server host at app start | https://developer.android.com/reference/java/net/InetAddress | 2 | 4 | 5 | 4 | 5 | 3 | 23 | Nice-to-have |
| 19 | Health probe on the real target instead of 1.1.1.1 | `docs/HANDBOOK.txt` §8 | 3 | 5 | 5 | 4 | 5 | 4 | 26 | Consider |
| 20 | Probe parallelism (run probe while dialing) | `docs/HANDBOOK.txt` §8 | 2 | 5 | 5 | 3 | 5 | 3 | 23 | No (adds complexity) |
| 21 | `VpnService.setUnderlyingNetworks` on every change | https://developer.android.com/reference/android/net/VpnService | 4 | 5 | 5 | 5 | 5 | 5 | 29 | Already done (0.7.3) |
| 22 | `Network.openConnection` for probes | https://developer.android.com/reference/android/net/Network | 4 | 5 | 5 | 5 | 5 | 5 | 29 | Already done (0.9.1) |
| 23 | TCP_NODELAY on bridge sockets | https://github.com/xjasonlyu/tun2socks | 2 | 5 | 3 | 3 | 5 | 2 | 20 | gVisor defaults |
| 24 | Larger gVisor buffers | https://github.com/xjasonlyu/tun2socks | 3 | 5 | 3 | 3 | 4 | 2 | 20 | Needs benchmarking |
| 25 | Multi-server latency ranking | https://xtls.github.io/en/config/observatory.html | 3 | 4 | 5 | 4 | 3 | 4 | 23 | Phase C |
| 26 | Connection pre-warming (dial at app start) | none — no implementation to cite | 2 | 3 | 3 | 2 | 3 | 2 | 15 | **Rejected** (battery, privacy) |

---

## 3. Detailed analysis of the adopted items

### 3.1 Happy Eyeballs (`sockopt.happyEyeballs`)

Xray ships an RFC 8305 implementation. Defaults: `tryDelayMs: 0` (disabled),
`maxConcurrentTry: 4`, `interleave: 1`, `prioritizeIPv6: false`.
The documentation recommends `tryDelayMs: 250`.

Why it matters universally: when the server is a hostname with both A and AAAA records — or
simply a slow first address — Go's built-in dialing waits 300 ms before trying the other family.
Xray's implementation races them and starts the next attempt immediately on failure.

**Caveat from the docs:** it only works when `sockopt.domainStrategy != "AsIs"` and must not be
combined with `dialerProxy`.

### 3.2 TCP keep-alive

Xray's outbound default is Chrome's: 45 s idle, 45 s interval. Iranian mobile carriers use CGNAT
with idle timeouts commonly in the 30–60 s range; a tunnel that is idle for a minute can be
silently dropped, and the next packet dies without notification.

Setting `tcpKeepAliveIdle: 25` and `tcpKeepAliveInterval: 15` keeps the mapping warm at a cost of
two tiny packets per 15 s while idle. This is the single highest-value change in this report
because it attacks a failure mode the health probe only notices after the fact.

### 3.3 `tcpUserTimeout`

Milliseconds of unacknowledged data before the kernel gives up on the connection. Xray's example
uses 10 000. On a lossy mobile link, 5 000 detects a dead path roughly twice as fast at the cost
of aborting on a genuinely slow retransmit. Recommended 5 000 with a note to revisit if users
report aborts on very high-RTT links.

### 3.4 `tcpMaxSeg: 1440`

With TUN MTU 1500 and a VLESS/TLS outer header, the path MTU can be exceeded on links that clamp
below 1500. Clamping the outbound MSS to 1440 avoids PMTU black holes without touching the TUN
MTU (which the brief forbids).

### 3.5 `hosts` for the server domain

`dns.hosts` maps a name to a fixed IP without a lookup. The operator already knows the server
address (it is in the bundle), so a hosts entry removes one DNS round trip from every connect and
one dependency on the resolver working at the moment of connection.

### 3.6 Parallel dial race

If the bundle carries two or more profiles, dialing them concurrently and keeping the first TCP
connection that completes removes the "primary is dead, wait for it" delay. Implementation is
app-side (Kotlin), no new dependency: two `Socket` connects on `Dispatchers.IO`, first success
wins, the loser is closed. Risk: doubling connection attempts looks like scanning to a strict
network; keep it to at most two candidates and only when the user picks "auto".

### 3.7 `setMetered`

Tells Android whether the VPN counts as metered. Correctness signal for the system's data-saver
and job scheduling; two lines, no cost.

---

## 4. Traps — do not implement these

| Trap | Why it is wrong here |
|---|---|
| `sniffing.routeOnly: true` | With FakeDNS the "original destination" is a fake IP from `198.18.0.0/15`. `routeOnly` keeps that IP and skips the domain rewrite, so the outbound would try to connect to a fake address. The two features are mutually exclusive in this architecture |
| `tcpFastOpen` | Needs `net.ipv4.tcp_fastopen` with the client bit set. Android apps cannot change that sysctl, and stock kernels ship it disabled, so the option is a no-op — not a bug, but not an improvement either |
| `tcpcongestion: bbr` | Requires the kernel module; on Android it is usually absent. Setting it can fail the dial entirely rather than silently falling back |
| `tcpMptcp` | Needs Linux 5.6+ with MPTCP enabled; Android kernels generally lack it, and carriers do not support it |
| Pre-warming the tunnel at app start | Connects the user without consent (privacy) and burns battery for a VPN they may not use |
| Disabling the health probe to "save 200 ms" | The probe is what makes the green state honest; removing it re-creates the 0.9.x problem |

---

## 5. The ten questions, answered

The Phase 5 text of the brief was not included in the material provided, so the ten questions
below are reconstructed from the brief's own sections (2.1 speed, 2.2 stability, plus the hard
rules). Each answer is an opinion with its reason, not a measurement.

**Q1. Should any optimisation be on by default for every profile?**
Yes — five: `happyEyeballs`, `tcpKeepAliveIdle/Interval`, `tcpUserTimeout`, `tcpMaxSeg`,
`dns.hosts`. All are config-only, cost nothing, and apply to every Xray protocol. AmneziaWG is
the exception: it needs its own keepalive, since it never touches Xray's socket layer.

**Q2. How much does connection setup improve?**
Only the DNS `hosts` entry and Happy Eyeballs touch setup; expect one skipped lookup (typically
20–80 ms on Iranian mobile) and up to ~300 ms saved when the first address of a dual-stack
server is unreachable. Everything else improves steady-state, not setup.

**Q3. Does keep-alive really help, or is it cargo cult?**
It helps against carrier NAT, which is the dominant silent-failure mode on mobile. Xray's default
is 45 s idle (Chrome's value). CGNAT mappings commonly expire at 30–60 s. Dropping to 25 s idle
with a 15 s interval costs two small packets per interval and removes a class of "connected but
dead" states. This is the highest-value single change in this document.

**Q4. Should the app pick a protocol automatically?**
No. The operator ships one profile per bundle in practice; automatic protocol selection only
makes sense with a multi-profile bundle, and even then the right primitive is a dial race (item 6),
not a protocol decision tree. Guessing protocols is how clients become unpredictable.

**Q5. Are there faster handshakes available in the pinned Xray?**
Yes, but they are transport choices, not client switches: REALITY + Vision on TCP 443 has the
best handshake behaviour, and 0-RTT/TLS session resumption is handled by the TLS stack for TLS
transports. Fragment adds latency by design (it splits and delays); it buys reachability, not
speed, and should stay off when not needed.

**Q6. Is parallel connection racing worth the complexity?**
Only when more than one profile exists, and capped at two candidates. It converts "primary is
dead, wait for timeout" into "first success wins". Beyond two it looks like scanning and burns
battery.

**Q7. Should we lower `tcpUserTimeout` to fail faster?**
Yes, to 5 000 ms, but this is the one item that can hurt: on a genuinely high-RTT path an early
abort looks like flapping. It needs a device run on a lossy link before it ships.

**Q8. Should the health probe change?**
Two candidates: probe the real destination instead of `1.1.1.1` (more representative, but leaks
the destination into the probe) and probe in parallel with dialing (saves ~200 ms, adds state
machinery). Recommendation: keep `1.1.1.1` (no operator data in the probe) and leave the timing
as is; the 200 ms is the price of an honest green state.

**Q9. Does anything here increase APK size, memory or battery?**
APK and memory: no — all five config changes are JSON fields. Battery: keep-alive sends two small
packets per 15 s while idle; everything else is neutral. No new dependency is proposed anywhere
in this report.

**Q10. What is the single highest-impact change, and what must be measured first?**
Highest impact: keep-alive tuning (Q3). What must be measured first, on a device and on an
Iranian carrier: (a) whether 25 s idle keeps a CGNAT mapping alive without waking the radio
excessively, (b) whether 5 s `tcpUserTimeout` aborts on high-RTT paths, (c) whether
`tcpMaxSeg 1440` changes throughput at MTU 1500. Until those three numbers exist, items 2–4 are
hypotheses with good provenance, not verified improvements.

## 6. Roadmap

**Phase A — config only (no app code beyond one renderer function)**
1. `sockopt.happyEyeballs { tryDelayMs: 250, maxConcurrentTry: 4 }`
2. `tcpKeepAliveIdle: 25`, `tcpKeepAliveInterval: 15`
3. `tcpUserTimeout: 5000`
4. `tcpMaxSeg: 1440` when MTU is 1500
5. `dns.hosts` entry for the profile's server name

Each needs one renderer test asserting the JSON shape, and one device run to confirm no regression.

**Phase B — app**
6. Parallel dial race (only with 2+ profiles, capped at 2 candidates).
7. `Builder.setMetered(...)`.

**Phase C — later**
8. Observatory-style latency ranking once the bundle ships multiple profiles.
9. Revisit `queryStrategy` if the operator ever runs an IPv4-only server.

---

## Sources

All URLs returned HTTP 200 during this task.

- https://xtls.github.io/en/config/transports/sockopt.html
- https://xtls.github.io/en/config/routing.html
- https://xtls.github.io/en/config/dns.html
- https://xtls.github.io/en/config/policy.html
- https://xtls.github.io/en/config/observatory.html
- https://developer.android.com/reference/android/net/VpnService.Builder
- https://developer.android.com/reference/android/net/ConnectivityManager
- https://github.com/XTLS/Xray-core/discussions/3269
- https://github.com/2dust/v2rayN/issues/10407

## Method note

Field semantics and defaults above are quoted from the Xray documentation pages fetched during
this task, not recalled. Statements about Android kernels (TFO, BBR, MPTCP availability) are
documented as platform constraints by those same pages and are marked as needing a device check
before they are relied upon.
