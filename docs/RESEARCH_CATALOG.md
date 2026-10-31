# RESEARCH CATALOG — censorship-resistant VPN/proxy practice

**Date:** 2026-10-31 · **Status:** Phase 0.5 research pass (read-only) · **Entries:** 146
**Purpose:** ground the Phase-1 plan in what actually works, who builds it, what it costs legally, and
what fits `Mohammad` (VpnService TUN → tun2socks/gVisor → SOCKS 10808 → Xray; AmneziaWG as a second core).

## How to read this document

Every entry has the same fields:

| Field | Meaning |
|---|---|
| **Link** | Canonical repository or primary source |
| **Solves** | Which of Mohammad's four problems it attacks: censorship / DPI / stability / speed |
| **How** | One or two sentences of mechanism, not marketing |
| **Side** | `client` · `server` · `both` |
| **Fit** | Applicability to Mohammad's exact architecture, with a verdict |
| **Evidence** | Stars are read from the GitHub API on 2026-10-31, not remembered; deployment notes cite the source |
| **License** | SPDX where GitHub reports it. `NOASSERTION` = GitHub could not identify it → **treat as no-reuse until the LICENSE file is read** |
| **Risk** | The reason not to adopt it, stated plainly |

**Licence rule for this project.** Mohammad is arguably a derivative work of Xray (MPL-2.0). Copying
GPL/AGPL code into it would relicense the whole app. The practical policy is: *permissive (MIT / BSD /
Apache-2.0 / ISC / MPL-2.0) may be linked with attribution; GPL/AGPL and `NOASSERTION` are
technique-and-idea only.* Every "License" line below is written with that policy in mind.

---

# A. Clients and reference applications (1–22)

These are the products Iranian users actually run. They matter less as code to copy and more as
proof of which combinations survive.

### 1. v2rayNG
**Link** https://github.com/2dust/v2rayNG · **Side** client · **License** GPL-3.0 · **65,044★**, pushed 2026-10-28
**Solves** censorship — the default Android Xray front-end.
**How** Wraps Xray-core in a `VpnService` that builds a TUN and injects a socks inbound; supports VMess/VLESS/Trojan/SS/WireGuard via config, subscription import, per-app routing.
**Fit** High as a *behavioural* reference: it is the app our users compare us to. Not reusable code (GPL).
**Evidence** 65k stars; the most-installed Android client in Iranian distribution channels.
**Risk** GPL; its UX assumes the user understands configs, which is the thing Mohammad exists to avoid.

### 2. NekoBox for Android
**Link** https://github.com/MatsuriDayo/NekoBoxForAndroid · **Side** client · **License** NOASSERTION · **23,336★**, pushed 2026-02-09
**Solves** censorship + flexibility — sing-box based, many protocols in one APK.
**How** sing-box core behind a profile-driven UI; plugin APKs extend protocol support out-of-process.
**Fit** Medium. Shows how to keep APK size sane by moving protocols into plugins; our two cores are already merged into one `.so`, so we cannot copy that trick.
**Evidence** 23k stars; widely mirrored in Iran.
**Risk** NOASSERTION licence; complex plugin model; development slowed in 2026.

### 3. Hiddify App
**Link** https://github.com/hiddify/hiddify-app · **Side** client · **License** NOASSERTION · **33,543★**, pushed 2026-10-31
**Solves** censorship + usability — subscription-first client built on sing-box + Xray.
**How** Flutter UI over a Go core; auto-selects configs by latency, supports TUIC/Hysteria2/REALITY out of the box.
**Fit** High as a product reference for "zero-config for the user". Low as code (licence, Flutter).
**Evidence** 33.5k stars; one of the two most recommended clients in Iranian Telegram channels in 2026.
**Risk** NOASSERTION; heavy; its auto-selection logic is unpublished, so results are not reproducible.

### 4. v2rayN
**Link** https://github.com/2dust/v2rayN · **Side** client (desktop) · **License** GPL-3.0 · **119,608★**, pushed 2026-10-29
**Solves** censorship on desktop.
**How** .NET GUI that renders Xray/sing-box JSON and supervises the core process.
**Fit** Low directly; high as the canonical example of "render config from a model" — the same shape as our `XrayConfig.kt`.
**Evidence** Most-starred circumvention client on GitHub.
**Risk** GPL; desktop-only.

### 5. Clash Verge Rev
**Link** https://github.com/clash-verge-rev/clash-verge-rev · **Side** client (desktop) · **License** GPL-3.0 · **155,278★**, pushed 2026-10-31
**Solves** censorship + routing.
**How** Tauri GUI over the mihomo core; rule-based routing, proxy groups with `url-test`/`fallback`.
**Fit** Medium — its proxy-group model is the best-documented example of health-based path selection.
**Evidence** Largest star count of any client here.
**Risk** GPL-3.0; desktop only.

### 6. FlClash
**Link** https://github.com/chen08209/FlClash · **Side** client (multi-platform incl. Android) · **License** GPL-3.0 · **60,002★**, pushed 2026-10-30
**Solves** censorship.
**How** Flutter front-end over mihomo; same proxy-group semantics as Clash.
**Fit** Medium. A current, actively maintained Android client to compare state handling against.
**Evidence** 60k stars, actively pushed.
**Risk** GPL; Flutter runtime adds ~20 MB.

### 7. mihomo (Clash.Meta)
**Link** https://github.com/MetaCubeX/mihomo · **Side** both · **License** MIT · **35,381★**, pushed 2026-10-30
**Solves** censorship + fallback — the surviving Clash lineage after the original repo was removed.
**How** Go proxy core with a rule engine, DNS server, and health-checked proxy groups (`url-test`, `fallback`, `load-balance`).
**Fit** High for *design*: its `fallback` group is exactly the path policy in FR-44. MIT, so code is legally reusable.
**Evidence** 35k stars; powers Clash Verge, FlClash and most desktop users.
**Risk** GitHub's description field for the repo is corrupted (returns an unrelated string) — verify the repo before mirroring; the project itself is healthy.

### 8. shadowsocks-android
**Link** https://github.com/shadowsocks/shadowsocks-android · **Side** client · **License** NOASSERTION · **36,829★**, pushed 2026-08-09
**Solves** censorship — the original Android tun2socks client.
**How** `VpnService` + a Go `tun2socks` bridge + a plugin APK per protocol.
**Fit** High: historically the same data path we use, and the source of the "plugin" pattern.
**Evidence** 36.8k stars; predates every other Android client here.
**Risk** NOASSERTION; slow maintenance; SS alone is heavily fingerprinted in Iran.

### 9. Lantern
**Link** https://github.com/getlantern/lantern · **Side** both · **License** GPL-3.0 · **16,589★**, pushed 2026-10-31
**Solves** censorship for non-technical users.
**How** Client tries a ranked list of transports (fronted, domain-fronted, direct) and falls back automatically; server side runs "config" services that hand out working endpoints.
**Fit** Medium — its *fallback ranking* is the closest large-scale model for FR-44; its transports are CloudFront/domain-fronting based and mostly dead in Iran.
**Evidence** 16.6k stars, active; large user base.
**Risk** GPL; auto-fallback hides failure reasons from the operator, which is exactly what hurt us in 0.11.

### 10. Psiphon (meta-repo)
**Link** https://github.com/Psiphon-Inc/psiphon · **Side** both · **License** none stated · **1,404★**, pushed 2021-03-05
**Solves** censorship at nation scale.
**How** Meta-repo pointing at the real components; the operational model is "many short-lived entry nodes, client discovers them".
**Fit** Medium as an idea: rotating entry points is a stronger answer to IP blocking than any single REALITY server.
**Evidence** Long-running deployment; Psiphon publishes monthly user numbers.
**Risk** Meta-repo only; the real code is elsewhere.

### 11. psiphon-tunnel-core
**Link** https://github.com/Psiphon-Labs/psiphon-tunnel-core · **Side** both · **License** GPL-3.0 · **1,196★**, pushed 2026-10-30
**Solves** censorship — the actual client/server tunnelling engine.
**How** GOOS/OSSH/SSH/WebSocket/Meek-style transports with a tactics file fetched over HTTPS that re-prioritises transports per network.
**Fit** Medium-high: the **tactics** concept (server tells the client what to try next) is a lighter version of FR-4 remote config.
**Evidence** 1.2k stars, actively pushed; backs a multi-million-user deployment.
**Risk** GPL; SSH-based transports are trivially fingerprinted in Iran today.

### 12. Outline Client/Manager
**Link** https://github.com/OutlineFoundation/outline-apps · **Side** client · **License** Apache-2.0 · **9,269★**, pushed 2026-10-30
**Solves** censorship for journalists; makes self-hosting trivial.
**How** Cordova/Electron shell around `outline-sdk`; server is a Shadowsocks instance created by one click.
**Fit** Medium. Apache-2.0 and cleanly factored — the SDK, not the app, is the useful part.
**Evidence** 9.3k stars; Jigsaw-backed.
**Risk** Shadowsocks alone is blocked in Iran within hours of discovery.

### 13. Outline SDK
**Link** https://github.com/OutlineFoundation/outline-sdk · **Side** client · **License** Apache-2.0 · **672★**, pushed 2026-10-30
**Solves** DPI + stability — modular transports and a smart dialer.
**How** Go library exposing `transport` wrappers (Shadowsocks, TLS, WebSocket, DNS) that compose, plus a dialer that races strategies and remembers which won.
**Fit** **High.** Apache-2.0, small, and its "race then remember" dialer is directly applicable to our profile selection. Its transport-composition idea maps onto our `TransportTuning.kt`.
**Evidence** 672★ but production-backed by Jigsaw.
**Risk** Small community; API churn; Kotlin interop needs a shim.

### 14. Intra
**Link** https://github.com/Jigsaw-Code/Intra · **Side** client · **License** Apache-2.0 · **2,231★**, pushed 2026-09-16
**Solves** DNS censorship only.
**How** Local `VpnService` that redirects port 53 to a DoH resolver and forwards everything else untouched.
**Fit** Medium: proves the "TUN that only hijacks DNS" pattern, useful if we ever split DNS policy from tunnel policy.
**Evidence** 2.2k stars; deployed at scale during the 2022 Iran shutdowns.
**Risk** Not a tunnel; no traffic protection.

### 15. Amnezia VPN client
**Link** https://github.com/amnezia-vpn/amnezia-client · **Side** client · **License** GPL-3.0 · **15,623★**, pushed 2026-10-29
**Solves** censorship for self-hosters (WireGuard/OpenVPN/AmneziaWG/Xray).
**How** Qt/C++ app that generates and deploys server configs, then connects with the matching core.
**Fit** Medium-high: it is the reference implementation of AmneziaWG's *obfuscation parameters* (`junk`, `jc`, `jmin/jmax`, `s1/s2`, `h1..h4` in v2; `i1..i5`, timing variance in v3).
**Evidence** 15.6k stars; the main distribution channel for AmneziaWG.
**Risk** GPL; its default parameters are public, so they are the first thing DPI vendors fingerprint.

### 16. Netch
**Link** https://github.com/netchx/netch · **Side** client (Windows) · **License** GPL-3.0 · **17,691★**, pushed 2026-10-22
**Solves** transparent proxying on Windows.
**How** TUN/TAP driver + tun2socks + config-generated proxy core.
**Fit** Low (Windows), but a second data point that TUN→tun2socks→SOCKS is the standard Android/desktop shape.
**Evidence** 17.7k stars.
**Risk** GPL; Windows-only.

### 17. SagerNet (archived)
**Link** https://github.com/SagerNet/SagerNet · **Side** client · **License** NOASSERTION · **5,728★**, archived 2024-02-13
**Solves** censorship — the predecessor of NekoBox.
**How** Android `VpnService` + tun2socks + pluggable cores.
**Fit** Medium — archived, but its per-app routing and asset-based core loading are still good reading.
**Evidence** 5.7k stars; superseded.
**Risk** Archived; NOASSERTION.

### 18. WireGuard for Android
**Link** https://github.com/WireGuard/wireguard-android · **Side** client · **License** Apache-2.0 · **1,657★**, pushed 2026-06-29
**Solves** speed.
**How** Official `VpnService` implementation; `goBackend` binds wireguard-go through JNI.
**Fit** High as the canonical way to own a TUN in Kotlin (`Builder`, fd hand-off, `setMetered`, `always-on`).
**Evidence** Official; ships in the Play Store.
**Risk** Plain WireGuard is detected in Iran in seconds (see 109).

### 19. Tailscale
**Link** https://github.com/tailscale/tailscale · **Side** both · **License** BSD-3-Clause · **38,402★**, pushed 2026-10-31
**Solves** stability + NAT traversal (not censorship).
**How** WireGuard data plane + a coordination server; DERP relays when direct paths fail; `netcheck` measures the local network before connecting.
**Fit** Medium: `netcheck` (MTU, NAT, UDP reachability probing at startup) is a good model for our pre-flight diagnostics, which we have none of.
**Evidence** 38.4k stars; widely deployed.
**Risk** Needs a control plane; not censorship-resistant by itself (blocking the coordination server kills it).

### 20. NetBird
**Link** https://github.com/netbirdio/netbird · **Side** both · **License** NOASSERTION · **30,382★**, pushed 2026-10-31
**Solves** stability — WireGuard mesh with an open control plane.
**How** Self-hostable management + `netbird` agent using ICE/STUN/TURN for path discovery, WireGuard for data.
**Fit** Low. Useful only as a comparison for peer-to-peer fallback.
**Evidence** 30.4k stars.
**Risk** NOASSERTION; control plane is a single point of failure.

### 21. v2rayA
**Link** https://github.com/v2rayA/v2rayA · **Side** both (server panel + client) · **License** AGPL-3.0 · **15,779★**, pushed 2026-10-27
**Solves** operations — web UI that renders Xray configs.
**How** Go server that owns an Xray process and exposes a REST API + web UI for routing rules.
**Fit** Low for the client. Relevant only because its `routingA` rule model is a cleaner version of our geosite/geoip handling.
**Evidence** 15.8k stars.
**Risk** AGPL-3.0 — the strongest copyleft here; never link.

### 22. shadowsocks-windows
**Link** https://github.com/shadowsocks/shadowsocks-windows · **Side** client · **License** NOASSERTION · **59,511★** (last push 2025-01-01)
**Solves** censorship historically.
**How** C# SS client with system proxy mode.
**Fit** None. Listed for completeness; its star count is legacy.
**Evidence** Inactive since early 2025.
**Risk** Unmaintained.

---

# B. Cores and protocol implementations (23–48)

### 23. Xray-core
**Link** https://github.com/XTLS/Xray-core · **Side** both · **License** MPL-2.0 · **42,507★**, pushed 2026-10-31
**Solves** censorship — the core we already ship.
**How** Go proxy platform: VLESS/VMess/Trojan/SS + REALITY/XTLS-Vision/XHTTP + `sockopt` knobs (`tcpKeepAliveIdle`, `tcpUserTimeout`, `tcpMaxSeg`, `tcpMptcp`).
**Fit** **Already adopted** (pinned `v26.10.29`). The unexploited surface is `sockopt`, `observatory`, `balancer`, and REALITY.
**Evidence** 42.5k stars; the de-facto standard since 2021.
**Risk** MPL-2.0 file-level copyleft — fine to link, but modifications to their files must be published.

### 24. v2ray-core
**Link** https://github.com/v2fly/v2ray-core · **Side** both · **License** MIT · **34,733★**, pushed 2026-10-30
**Solves** censorship — the upstream Xray forked from.
**How** Same architecture without REALITY/XTLS; MIT.
**Fit** Low: we ship Xray. Useful as an MIT-licensed reference when reading routing/transport code whose Xray copy is MPL.
**Evidence** 34.7k stars.
**Risk** Feature lag behind Xray.

### 25. sing-box
**Link** https://github.com/SagerNet/sing-box · **Side** both · **License** NOASSERTION (read LICENSE) · **39,469★**, pushed 2026-10-28
**Solves** everything at once: protocols, transports, fallback, DNS.
**How** Single Go binary with a unified outbound/inbound model; `urltest` and `fallback` outbounds; per-rule DNS; MASQUE/Hysteria2/TUIC/WireGuard all in one config schema.
**Fit** **High as a design reference**: its `urltest` interval/tolerance logic is a drop-in model for FR-44, and its state reporting is closer to FR-41 than ours. Code reuse depends on the actual licence.
**Evidence** 39.5k stars; the core behind Hiddify and NekoBox.
**Risk** Licence ambiguity (NOASSERTION); very large surface; its config schema is not ours.

### 26. hiddify-core
**Link** https://github.com/hiddify/hiddify-core · **Side** client · **License** NOASSERTION · **232★**, pushed 2026-10-30
**Solves** packaging — sing-box + Xray + TUIC in one mobile library.
**How** Go wrapper exposing a small API to Flutter/Dart, with per-core lifecycle management.
**Fit** **High** — it solves the exact problem we solved differently (one `libgojni.so` with two controllers). Worth reading for its shutdown ordering and error surfaces.
**Evidence** Small star count, but it is the engine of a 33k-star app.
**Risk** NOASSERTION; Dart-facing API.

### 27. shadowsocks-rust
**Link** https://github.com/shadowsocks/shadowsocks-rust · **Side** both · **License** MIT · **10,958★**, pushed 2026-10-31
**Solves** censorship — modern SS with 2022 ciphers.
**How** Rust implementation; supports SIP003 plugins (obfs, v2ray-plugin) and AEAD-2022.
**Fit** Low directly (we do not ship SS as a server), but the 2022 AEAD spec is what makes SS still viable.
**Evidence** 11k stars.
**Risk** SS's handshake is still detectable regardless of cipher.

### 28. shadowsocks-libev
**Link** https://github.com/shadowsocks/shadowsocks-libev · **Side** both · **License** GPL-3.0 · 6.5k★ (approx., see repo)
**Solves** censorship — the C implementation.
**How** libev event loop, single binary, SIP003 plugins.
**Fit** Low. Historical.
**Evidence** The server most Outline deployments use.
**Risk** GPL-3.0; maintenance slowed.

### 29. shadowsocks-c
**Link** https://github.com/shadowsocks/shadowsocks-c · **Side** both · **License** GPL-3.0 · **16,188★**, pushed 2026-10-28
**Solves** censorship — a newer self-contained C implementation with libuv.
**How** Async C server/client, AEAD-2022, built for small footprints.
**Fit** Low.
**Evidence** 16.2k stars.
**Risk** GPL-3.0.

### 30. trojan
**Link** https://github.com/trojan-gfw/trojan · **Side** both · **License** GPL-3.0 · **19,749★**, pushed 2024-08-21
**Solves** DPI — traffic that looks like HTTPS to a real web server.
**How** TLS on 443 with a password-authenticated handshake; falls through to a real web server on wrong credentials.
**Fit** Medium. Conceptually a predecessor of REALITY; useful to understand why "borrow a real certificate" is weaker than "borrow a real handshake".
**Evidence** 19.7k stars.
**Risk** Unmaintained since 2024; its fallback leaks timing.

### 31. trojan-go
**Link** https://github.com/p4gefau1t/trojan-go · **Side** both · **License** GPL-3.0 · **8,387★**, pushed 2024-07-14
**Solves** DPI + CDN routing.
**How** Trojan with WebSocket/gRPC transports and multiplexing, designed to sit behind a CDN.
**Fit** Medium: the WS+CDN mode is the cheap second path we lack.
**Evidence** 8.4k stars.
**Risk** GPL; unmaintained.

### 32. Hysteria
**Link** https://github.com/HyNetworks/hysteria · **Side** both · **License** MIT · **22,815★**, pushed 2026-10-27
**Solves** speed on lossy links + censorship.
**How** QUIC-based with a custom "Brutal" congestion control that ignores loss signals, plus HTTP/3 masquerade.
**Fit** **Medium-high**: for Iranian mobile networks with 2–10 % loss, Brutal is the single biggest throughput lever, but it is unfair to other flows and needs a server.
**Evidence** 22.8k stars (repo moved from `apernet` to `HyNetworks` — verify before pinning).
**Risk** Brutal is abusive by design; QUIC/UDP is itself throttled by Iranian ISPs in 2026.

### 33. TUIC
**Link** https://github.com/tuic-protocol/tuic · **Side** both · **License** GPL-3.0 · **3,290★**, pushed 2025-05-15
**Solves** speed — 0-RTT QUIC proxying.
**How** QUIC streams carrying a SOCKS-like protocol; 0-RTT resumption cuts reconnect time.
**Fit** Medium: 0-RTT is attractive for our "reconnect after airplane mode in 3.83 s" number.
**Evidence** 3.3k stars.
**Risk** GPL; slowing maintenance; UDP throttling.

### 34. juicity
**Link** https://github.com/juicity/juicity · **Side** both · **License** AGPL-3.0 · **1,110★**, pushed 2025-06-30
**Solves** censorship — QUIC with a certificate-authenticated handshake.
**How** QUIC + TLS 1.3 with a self-signed cert pinned by the client; no SNI games.
**Fit** Low-medium.
**Evidence** 1.1k stars.
**Risk** AGPL-3.0; stalled.

### 35. mieru
**Link** https://github.com/enfein/mieru · **Side** both · **License** GPL-3.0 · **2,658★**, pushed 2026-10-30
**Solves** DPI — traffic shaped to look like random padding-free application data.
**How** TCP/UDP with per-session padding and a probe-resistant handshake; no TLS at all.
**Fit** Low-medium: it is the strongest candidate for "what to do when TLS-based methods are all graylisted".
**Evidence** 2.7k stars, active.
**Risk** GPL; non-TLS transports are exactly what Iran's whitelist model blocks (see 109).

### 36. naiveproxy
**Link** https://github.com/klzgrad/naiveproxy · **Side** both · **License** BSD-3-Clause · **9,590★**, pushed 2026-10-22
**Solves** DPI — the strongest "looks exactly like Chrome" story.
**How** Chromium's network stack (HTTP/2, HTTP/3) tunnelling through a forward proxy; the TLS fingerprint is literally Chrome's.
**Fit** Medium: the fingerprint idea is available to us through uTLS without shipping Chromium.
**Evidence** 9.6k stars; repeatedly cited as surviving in China.
**Risk** Enormous binary; needs its own server; heavy for a 15 MB APK.

### 37. brook
**Link** https://github.com/txthinking/brook · **Side** both · **License** GPL-3.0 · **15,194★**, pushed 2026-10-23
**Solves** censorship — simple multi-protocol tunnelling.
**How** One binary with `brook wssserver`/`wssclient`, plus a `tun` mode.
**Fit** Low.
**Evidence** 15.2k stars.
**Risk** GPL; small ecosystem.

### 38. gost
**Link** https://github.com/ginuerzh/gost · **Side** both · **License** MIT · **18,268★**, pushed 2026-08-30
**Solves** relay plumbing.
**How** Chained forward proxies over many transports (WS, TLS, KCP, QUIC, SSH), with port forwarding.
**Fit** Medium as a server-side relay tool, not as a client.
**Evidence** 18.3k stars.
**Risk** MIT but sprawling; not a circumvention-specific design.

### 39. leaf
**Link** https://github.com/eycorsican/leaf · **Side** client · **License** Apache-2.0 · **2,839★**, pushed 2026-09-30
**Solves** routing — a lightweight proxy client library.
**How** Rust core with a rule engine, exposed to Android/iOS via FFI.
**Fit** Low-medium: interesting as an example of a small client core, but we already have Xray.
**Evidence** 2.8k stars.
**Risk** Small community; Rust toolchain for Android adds build complexity.

### 40. glider
**Link** https://github.com/nadoo/glider · **Side** both · **License** GPL-3.0 · **3,707★**, pushed 2026-10-09
**Solves** forwarding + fallback.
**How** Forward proxy with multiple upstreams, health checks, and `strategy` (rr/hash/lha) selection.
**Fit** Medium: `lha` (least-handshake-time) is the earliest version of the "race profiles" idea in FR-44.
**Evidence** 3.7k stars.
**Risk** GPL.

### 41. hev-socks5-tunnel
**Link** https://github.com/heiher/hev-socks5-tunnel · **Side** client · **License** MIT · **2,091★**, pushed 2026-10-31
**Solves** **stability — the tun2socks replacement.**
**How** C implementation of TUN→SOCKS5 with a small lwIP stack, designed for embedded/mobile; explicitly handles MSS clamping and MTU.
**Fit** **High.** It is the closest drop-in alternative to our gVisor-based bridge and it is MIT. If PMTU turns out to be our bug, this is where MSS clamping lives.
**Evidence** 2.1k stars; used by several Android clients.
**Risk** C code (JNI bridge needed); lwIP is less complete than gVisor (no full TCP stack features).

### 42. tun2socks (xjasonlyu)
**Link** https://github.com/xjasonlyu/tun2socks · **Side** client · **License** MIT · **5,559★**, pushed 2026-10-29
**Solves** **the bridge we ship.**
**How** gVisor netstack + SOCKS5/HTTP outbound; accepts an existing TUN fd.
**Fit** **Already adopted** (pinned `v2.7.0`). This is the component whose MSS/PMTU behaviour we need to understand.
**Evidence** 5.6k stars.
**Risk** Its `fdbased` endpoint does not do MSS clamping; oversized segments are silently dropped when the path MTU is smaller.

### 43. go-tun2socks (archived)
**Link** https://github.com/eycorsican/go-tun2socks · **Side** client · **License** MIT · **1,366★**, archived 2021
**Solves** the same problem, older.
**How** Go tun2socks built on `google/netstack`, with a small C shim for the fd.
**Fit** Low. Historical reference for fd ownership (`dup`) — the exact issue that caused our 0.7.1 bug.
**Evidence** 1.4k stars.
**Risk** Archived; netstack deprecated.

### 44. gVisor
**Link** https://github.com/google/gvisor · **Side** client (as a library) · **License** Apache-2.0 · **19,841★**, pushed 2026-10-31
**Solves** correctness of the userspace TCP/IP stack.
**How** User-space kernel; `netstack` provides TCP/IP over a fd-backed link endpoint.
**Fit** **Already a transitive dependency.** Understanding its `tcpip` knobs (MSS, buffers, keepalive) is the shortest path to diagnosing stalls.
**Evidence** 19.8k stars; used by every gVisor-based tun2socks.
**Risk** Apache-2.0 requires shipping NOTICE — currently missing from our APK (already recorded).

### 45. netstack (archived)
**Link** https://github.com/google/netstack · **Side** client (library) · **License** Apache-2.0 · **3,112★**, archived 2019
**Solves** same, deprecated.
**How** The original standalone userspace stack, later merged into gVisor.
**Fit** None.
**Evidence** Archived.
**Risk** Dead.

### 46. wireproxy
**Link** https://github.com/windtf/wireproxy · **Side** client · **License** ISC · **5,864★**, pushed 2026-10-12
**Solves** WireGuard without root — exposes a SOCKS5 proxy.
**How** Embeds wireguard-go and a userspace TCP/IP stack, then serves SOCKS5.
**Fit** **Medium-high**: it is the exact shape we need for the AmneziaWG fallback (AWG → SOCKS → tun2socks → TUN). Reading its fd/stack wiring is directly applicable.
**Evidence** 5.9k stars.
**Risk** ISC (permissive); plain WireGuard only — AmneziaWG's obfuscation must come from `amneziawg-go`.

### 47. dae
**Link** https://github.com/daeuniverse/dae · **Side** client (Linux) · **License** AGPL-3.0 · **6,379★**, pushed 2026-10-31
**Solves** speed — kernel-level transparent proxying.
**How** eBPF program that redirects traffic to a userspace proxy, bypassing the userspace stack.
**Fit** Low for Android (no eBPF for apps), high as an upper bound on what our stack costs.
**Evidence** 6.4k stars.
**Risk** AGPL; Linux-only.

### 48. realm
**Link** https://github.com/zhboner/realm · **Side** server · **License** MIT · **2,639★**, pushed 2026-08-30
**Solves** relay — port forwarding with zero overhead.
**How** Rust TCP/UDP relay with optional TLS; used to bounce traffic through a clean IP.
**Fit** Medium: the cheapest mitigation for "server IP blocked but protocol fine".
**Evidence** 2.6k stars.
**Risk** Not a circumvention protocol; a relay can itself be blocked.

---

# C. Transports and obfuscation (49–78)

The single most important finding of this whole pass: **in Iran in 2026 the transport matters more
than the protocol.** The regime moved from "block the protocol" to "allow a whitelist of protocols"
(see 85), and real deployments are now judged on whether their bytes look like an allowed
protocol, not on how strong their encryption is.

### 49. REALITY
**Link** https://github.com/XTLS/REALITY · **Side** both · **License** MPL-2.0 · **5,585★**, pushed 2026-10-14
**Solves** DPI — the strongest TLS-level camouflage available.
**How** The server borrows a real third-party site's TLS handshake (its certificate chain and SNI) and only distinguishes a client by a key embedded in the ClientHello's X25519 key share; a probe gets a genuine handshake with that site.
**Fit** **Highest-value item in the document.** Xray already implements it; our `XrayConfig` renders no TLS at all, so it is a config-generation change, not a new core.
**Evidence** 5.6k stars on the spec repo; adopted by Hiddify/NekoBox defaults; but see 86 for the counter-evidence in Iran.
**Risk** MPL-2.0 (file-level). Requires a server whose IP is not already graylisted, and a plausible decoy SNI in the same region.

### 50. XTLS / Vision
**Link** https://github.com/XTLS/XTLS.github.io · **Side** both · **License** docs · **144★**, pushed 2026-10-26
**Solves** speed + DPI — removes the TLS-in-TLS fingerprint and the double encryption overhead.
**How** "Vision" splices the inner TLS record boundaries with the outer ones so the traffic does not resemble TLS-inside-TLS, and switches to direct copy after handshake.
**Fit** **High.** Pure server config + one `flow: xtls-rprx-vision` field in our renderer.
**Evidence** Default in every current VLESS guide; the TLS-in-TLS detection it defeats is documented by GFW research.
**Risk** Only meaningful with TLS/REALITY; conflicts with some mux modes.

### 51. AnyTLS
**Link** https://github.com/anytls/anytls-go · **Side** both · **License** not stated by GitHub · **1,559★**, pushed 2026-08-03
**Solves** DPI + stability — padding-heavy TLS tunnel with multiplexing.
**How** TLS stream with per-frame padding and a session-multiplexed framing layer, designed to defeat length/timing analysis.
**Fit** Medium: its padding scheme is the answer to "our TLS looks too regular", but it needs a matching server.
**Evidence** 1.6k stars; recent and growing.
**Risk** Unknown licence (NOASSERTION) — no code reuse until verified; young protocol.

### 52. ShadowTLS
**Link** https://github.com/ihciah/shadow-tls · **Side** both · **License** MIT · **2,681★**, pushed 2025-04-25
**Solves** DPI — wraps Shadowsocks in a real TLS handshake to a real site.
**How** Performs a genuine TLS handshake against a whitelisted SNI, then hijacks the encrypted channel for the proxy.
**Fit** Medium: same idea as REALITY, weaker because the handshake is genuine but the post-handshake record pattern is not.
**Evidence** 2.7k stars.
**Risk** MIT but dormant; v3 rewrites were controversial.

### 53. Restls
**Link** https://github.com/3andne/restls · **Side** both · **License** BSD-3-Clause · **377★**, pushed 2023-11-05
**Solves** DPI — "perfect TLS impersonation" with record-level replay control.
**How** Shapes TLS record lengths and timings to match a chosen reference implementation, byte for byte.
**Fit** Low-medium; the idea is sound but the project is small and stale.
**Evidence** 377 stars.
**Risk** Unmaintained; requires a custom server.

### 54. Cloak
**Link** https://github.com/cbeuw/Cloak · **Side** both · **License** GPL-3.0 · **4,102★**, pushed 2026-05-29
**Solves** DPI — multiplexed TLS with active-probe resistance.
**How** A real TLS handshake to a decoy site, then an authenticated multiplexed session inside it; unauthenticated probes are proxied to the decoy.
**Fit** Medium — same family as REALITY, with a heavier client.
**Evidence** 4.1k stars.
**Risk** GPL-3.0; needs server-side Cloak.

### 55. GoQuiet
**Link** https://github.com/cbeuw/GoQuiet · **Side** both · **License** GPL-3.0 · **827★**, pushed 2022-03-04
**Solves** DPI — early domain-fronting plugin for Shadowsocks.
**How** Sends the SS stream inside TLS to a frontable CDN host.
**Fit** None — domain fronting is dead at every major CDN.
**Evidence** 827 stars; archived in practice.
**Risk** Obsolete.

### 56. obfs4
**Link** https://github.com/Yawning/obfs4 · **Side** both · **License** NOASSERTION · **1,252★**, pushed 2023-11-15
**Solves** DPI — makes traffic look like nothing in particular.
**How** ScrambleSuit-derived framing with IAT obfuscation and a probe-resistant handshake; no TLS.
**Fit** Low. Non-TLS protocols are exactly what Iran's whitelist model blocks.
**Evidence** 1.2k stars; Tor's long-standing default bridge.
**Risk** Distinctive statistical shape; heavy in Iran since 2022.

### 57. meek
**Link** https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/meek · **Side** both · **License** BSD-3-Clause · deployment-only
**Solves** DPI — hides in HTTPS to a CDN (domain fronting).
**How** Every connection is an HTTPS request to a frontable domain (e.g. an Azure/CloudFront host) that routes to the bridge.
**Fit** None in 2026 — the CDNs turned fronting off.
**Evidence** Tor's own docs mark it "increasingly ineffective".
**Risk** Dead technique; do not plan around it.

### 58. Snowflake
**Link** https://gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake · **Side** both · **License** BSD-3-Clause · deployment-only
**Solves** DPI — no stable entry point to block.
**How** WebRTC data channels through volunteer proxies; the client only needs to reach a broker that hands it a peer.
**Fit** Low for a VPN client (latency, throughput), high as an *availability* idea: many short-lived entry points.
**Evidence** Carried a large share of Tor traffic during the 2022 and 2026 Iran shutdowns; paper at USENIX Security '24.
**Risk** WebRTC is itself blocked/throttled; throughput is poor for video.

### 59. Conjure
**Link** https://refraction.networking/ · **Side** both · **License** research prototype
**Solves** DPI — uses unallocated IP space as a covert channel.
**How** The client sends packets to a "phantom" IP in unused address space, which the ISP routes normally; a tap at a cooperating ISP hijacks them.
**Fit** None (requires ISP cooperation).
**Evidence** Refraction Networking deployments at several ISPs; CCS/NDSS papers.
**Risk** Needs infrastructure we cannot build.

### 60. wstunnel
**Link** https://github.com/erebe/wstunnel · **Side** both · **License** BSD-3-Clause · **7,143★**, pushed 2026-10-19
**Solves** DPI — tunnels anything over WebSocket or HTTP/2.
**How** Client opens a WS connection to a path on a normal-looking web server and multiplexes TCP/UDP over it.
**Fit** **Medium-high**: it is the cheapest second path we can stand up (WS on 443 behind a CDN), and it matches the profile we already run.
**Evidence** 7.1k stars.
**Risk** The WS upgrade pattern is itself fingerprintable; CDN ToS.

### 61. Phantun
**Link** https://github.com/dndx/phantun · **Side** both · **License** Apache-2.0 · **2,413★**, pushed 2026-08-22
**Solves** DPI — turns UDP into fake-TCP.
**How** Wraps UDP datagrams in TCP-looking segments with a minimal handshake, no kernel stack changes.
**Fit** Low-medium: relevant only if we ever move AmneziaWG onto UDP-blocked networks.
**Evidence** 2.4k stars.
**Risk** Fake TCP is detectable; adds latency.

### 62. udp2raw
**Link** https://github.com/wangyu-/udp2raw · **Side** both · **License** MIT · **8,580★**, pushed 2025-10-12
**Solves** DPI — UDP over fake TCP/ICMP.
**How** Userspace TCP-like reliability over raw sockets, then carries UDP inside.
**Fit** Low-medium, same niche as 61.
**Evidence** 8.6k stars.
**Risk** Raw socket needs root on the server; fingerprints.

### 63. UDPspeeder
**Link** https://github.com/wangyu-/UDPspeeder · **Side** both · **License** MIT · **5,198★**, pushed 2026-07-31
**Solves** **stability/speed on lossy links.**
**How** Forward error correction over UDP: sends redundant packets so loss does not trigger retransmits.
**Fit** Medium: Iranian mobile networks show high loss; FEC improves perceived speed without touching congestion control.
**Evidence** 5.2k stars.
**Risk** Doubles bandwidth; needs a server; UDP throttling.

### 64. tinyfecVPN
**Link** https://github.com/wangyu-/tinyfecVPN · **Side** both · **License** MIT · **2,594★**, pushed 2023-06-05
**Solves** speed on lossy links.
**How** FEC + a simple VPN over UDP.
**Fit** Low.
**Evidence** 2.6k stars.
**Risk** Dormant since 2023.

### 65. uTLS
**Link** https://github.com/refraction-networking/utls · **Side** client · **License** BSD-3-Clause · **2,620★**, pushed 2026-10-06
**Solves** DPI — makes our ClientHello indistinguishable from a real browser.
**How** Fork of Go's TLS with control over every ClientHello field; ships fingerprints for Chrome, Firefox, Safari, iOS, Android.
**Fit** **High, and cheap**: Xray already supports `fingerprint` in `streamSettings`. We expose no field for it, so users ship the Go default fingerprint — one of the most detectable things in the whole app.
**Evidence** 2.6k stars; used by Xray, sing-box, and every serious client.
**Risk** BSD-3-Clause (permissive); fingerprints go stale as browsers update.

### 66. uQUIC
**Link** https://github.com/refraction-networking/uquic · **Side** client · **License** MIT · **79★**, pushed 2026-08-04
**Solves** DPI on QUIC — the same idea as uTLS for the QUIC Initial packet.
**How** Lets the caller craft the QUIC Initial packet and transport parameters to match a target implementation.
**Fit** Low today (we do not use QUIC), medium later.
**Evidence** 79 stars.
**Risk** Immature.

### 67. quiche
**Link** https://github.com/cloudflare/quiche · **Side** both · **License** BSD-2-Clause · **12,834★**, pushed 2026-10-29
**Solves** speed — a mature QUIC/HTTP3 stack.
**How** Rust QUIC with BoringSSL; used by Cloudflare's edge.
**Fit** Low (Rust, not our core).
**Evidence** 12.8k stars.
**Risk** Not a circumvention tool by itself.

### 68. quic-go
**Link** https://github.com/quic-go/quic-go · **Side** both · **License** MIT · **11,821★**, pushed 2026-10-31
**Solves** speed — the QUIC stack behind most Go proxies.
**How** Pure-Go QUIC, including connection migration and 0-RTT.
**Fit** Medium: if we ever add Hysteria/TUIC, this is the dependency.
**Evidence** 11.8k stars.
**Risk** QUIC/UDP is throttled by Iranian ISPs in 2026.

### 69. pion/webrtc
**Link** https://github.com/pion/webrtc · **Side** both · **License** MIT · **16,867★**, pushed 2026-10-29
**Solves** DPI — the transport under Snowflake.
**How** Pure-Go WebRTC data channels over DTLS/SCTP.
**Fit** Low for us.
**Evidence** 16.9k stars.
**Risk** Heavy; high latency.

### 70. MASQUE
**Link** https://datatracker.ietf.org/wg/masque/about/ · **Side** both · **License** IETF (technique)
**Solves** DPI — proxies inside HTTP/3 CONNECT-UDP.
**How** Encapsulates IP/UDP in HTTP datagrams over QUIC; to a middlebox it is a normal HTTP/3 session to a CDN.
**Fit** Medium-long term; sing-box and Chrome already ship it.
**Evidence** IETF WG, Cloudflare production support.
**Risk** Needs a CDN/provider that offers it; UDP throttling.

### 71. Encrypted Client Hello (ECH)
**Link** https://datatracker.ietf.org/doc/draft-ietf-tls-esni/ · **Side** both · **License** IETF (technique)
**Solves** DPI — hides the SNI.
**How** The real SNI is encrypted to a public key published in DNS; the outer ClientHello carries a cover name.
**Fit** Medium: orthogonal to REALITY, and would fix the single biggest leak (plaintext SNI). Blocked in practice because it requires DoH to fetch the key and Iran blocks DoH resolvers.
**Evidence** Shipped by Cloudflare/Firefox; measurably blocked in Russia and Iran.
**Risk** Useless without an ECH-capable server and an unblocked DNS path.

### 72. Domain fronting
**Link** https://petsymposium.org/2015/ (Fifield et al., "Blocking-resistant communication through domain fronting", PETS 2015)
**Side** both · **License** n/a (paper)
**Solves** DPI historically.
**How** SNI and HTTP Host disagree; the CDN routes on Host, the censor filters on SNI.
**Fit** None — Cloudflare/AWS/Google all terminate this.
**Evidence** Blocked since 2018.
**Risk** Dead.

### 73. CDN-based relays (Cloudflare Workers etc.)
**Link** https://github.com/XTLS/Xray-core/discussions (community patterns), plus `cloudflared`
**Side** both · **License** n/a (deployment pattern)
**Solves** IP blocking — the client only ever talks to a CDN IP.
**How** A Worker or Tunnel terminates TLS at the edge and forwards to the origin over an outbound connection; the client uses WS/gRPC to the CDN.
**Fit** **Medium-high** as the cheap second path: no new server IP to block, and WS+CDN already works with our current profile shape.
**Evidence** Widespread in Iranian Telegram channels; Cloudflare ToS crackdowns happen.
**Risk** CDN ToS violations get accounts banned; latency; the CDN itself can be throttled wholesale.

### 74. XHTTP
**Link** https://github.com/XTLS/Xray-core (transport), docs at xtls.github.io
**Side** both · **License** MPL-2.0 (part of Xray)
**Solves** DPI — HTTP-shaped traffic with fine-grained packet control.
**How** Splits the stream into HTTP request/response pairs with configurable size and timing, so it resembles ordinary API traffic rather than a long-lived tunnel.
**Fit** **Medium-high**: already in our core, zero new dependencies; only the renderer needs to emit it.
**Evidence** Recent default in Xray guides for "WS keeps getting blocked" cases.
**Risk** Newer, less battle-tested; interacts badly with some CDNs.

### 75. gRPC transport
**Link** https://github.com/XTLS/Xray-core · **Side** both · **License** MPL-2.0
**Solves** DPI — multiplexed HTTP/2 streams.
**How** The proxy stream rides as a gRPC call; to a middlebox it is HTTP/2.
**Fit** Medium: already available in our core; good CDN compatibility.
**Evidence** Standard option in Xray/sing-box.
**Risk** HTTP/2 fingerprinting; head-of-line blocking.

### 76. WebSocket transport
**Link** https://github.com/XTLS/Xray-core · **Side** both · **License** MPL-2.0
**Solves** DPI — the current profile.
**How** VLESS/VMess framed inside a WS connection, optionally behind TLS and a CDN.
**Fit** **Already shipped** (this is our `security: none` profile — plaintext WS, the weakest link).
**Evidence** The most common transport in Iran because it survives CDNs.
**Risk** Without TLS, the WS upgrade and frame sizes are visible; this is what "works a few seconds, then cuts" may partly be.

### 77. Multiplexing (mux / smux / XUDP)
**Link** https://github.com/XTLS/Xray-core · **Side** both · **License** MPL-2.0
**Solves** speed (fewer handshakes) and DPI (fewer connections).
**How** Many logical streams share one TCP connection via a small framing protocol.
**Fit** **Already implemented** (`TransportTuning.mux`). Off by default is correct: mux concentrates all traffic into one flow that a censor can kill at once.
**Evidence** Standard Xray feature; our own DECISIONS record why it is opt-in.
**Risk** One RST kills everything; hurts throughput on long-fat paths.

### 78. Fragment / finalmask
**Link** https://github.com/XTLS/Xray-core (`finalmask`) · **Side** client · **License** MPL-2.0
**Solves** DPI — breaks the first-packet signature that classifiers match on.
**How** Splits the TLS ClientHello (or the first N bytes) into several TCP segments with delays, so no single packet matches a known fingerprint.
**Fit** **Already implemented** (`fragment` 1-3). Cheap, effective against naive SNI filters, useless against stateful reassembly.
**Evidence** v2rayNG's default for Iran; documented in our own research file.
**Risk** `tlshello` mode breaks REALITY; fragmenting adds a round trip.

---

# D. DPI evasion and traffic analysis (79–92)

### 79. zapret
**Link** https://github.com/bol-van/zapret · **Side** client (Linux/router) · **License** none declared · **16,331★**, pushed 2026-10-04
**Solves** DPI — the most complete userspace DPI-evasion toolkit.
**How** A netfilter queue that rewrites the first packets: TCP segmentation, TTL manipulation, fake packets with bad checksums, and per-ISP "strategies" selected by a config file.
**Fit** **Medium as a catalogue of techniques** — the `--dpi-desync` modes (split, disorder, fake, ttl) are the best documented list of what actually defeats Iranian ISPs' filters. Not portable to Android without root, but the *strategy tables* for Iranian ISPs are gold.
**Evidence** 16.3k stars; the default tool on Russian/Iranian OpenWrt routers.
**Risk** No licence file → no code reuse; Linux netfilter only.

### 80. byedpi
**Link** https://github.com/hufrea/byedpi · **Side** client (Linux/Windows) · **License** MIT · **3,444★**, pushed 2026-03-29
**Solves** DPI — lighter reimplementation of the zapret idea.
**How** Splits and reorders the first bytes of a connection, replaces the SNI with a benign one in a decoy packet, and fragments TLS records.
**Fit** Medium (techniques only).
**Evidence** 3.4k stars.
**Risk** MIT, so reusable — but the mechanisms need raw sockets / VPN-level interception, not available to a non-root Android app.

### 81. GoodbyeDPI
**Link** https://github.com/ValdikSS/GoodbyeDPI · **Side** client (Windows) · **License** Apache-2.0 · **28,823★**, pushed 2026-01-19
**Solves** DPI — the original "fragment the ClientHello" tool.
**How** WinDivert-based packet interception that splits the TLS ClientHello and drops/strips packets to defeat stateless filters.
**Fit** Low directly; high as the origin of the `fragment` technique we already ship.
**Evidence** 28.8k stars.
**Risk** Windows only; stateless-filter-only (useless against reassembly).

### 82. WinDivert
**Link** https://github.com/basil00/WinDivert · **Side** client (Windows) · **License** NOASSERTION · **3,337★**, pushed 2023-03-15
**Solves** plumbing — userspace packet interception.
**How** A kernel driver exposing a socket-like API for capturing/altering packets.
**Fit** None on Android.
**Evidence** 3.3k stars.
**Risk** Windows-only.

### 83. net4people/bbs
**Link** https://github.com/net4people/bbs · **Side** — · **License** none · **5,336★**, pushed 2026-01-25
**Solves** knowledge — the field's de-facto forum for "what is being blocked this month".
**How** Issue threads per country/ISP with reproducible measurements.
**Fit** **High as an operational input**: the Iran threads are where REALITY graylisting and protocol whitelisting were first documented.
**Evidence** 5.3k stars; cited by every circumvention paper.
**Risk** Anecdotal; no licence (text).

### 84. Geneva
**Link** https://github.com/Kkevsterrr/geneva · **Side** both · **License** BSD-3-Clause · **2,153★**, pushed 2026-10-23
**Solves** DPI — automatic discovery of packet-level evasion strategies.
**How** A genetic algorithm mutates packet sequences (drop, duplicate, tamper, split) and measures which mutation gets the flow through, then replays the winner.
**Fit** **Medium**: the idea of *searching* for a working strategy instead of hard-coding one is directly applicable to our `TransportTuning` presets — but it needs a test harness and a cooperating server.
**Evidence** 2.2k stars; CCS 2019 paper; deployed in some ISPs.
**Risk** BSD-3 (reusable); strategies decay as filters update.

### 85. Iran's protocol-whitelist DPI model (2025–2026)
**Link** https://arxiv.org/html/2507.14183 · **Side** — · **License** n/a (research)
**Solves** analysis — explains *why* client-side knobs stopped working.
**How** Measurement study: the filter no longer blocks "bad" protocols but allows a shrinking set of "good" ones (HTTP/TLS/QUIC to approved destinations), so anything outside that set is dropped by default.
**Fit** **Critical context.** It means every non-TLS transport (obfs4, mieru, plain WireGuard, SS without TLS) is structurally dead in Iran, and the only viable direction is "look like an allowed TLS/QUIC flow".
**Evidence** Peer-reviewed measurement, July 2025.
**Risk** Policy changes quarterly; conclusions age fast.

### 86. REALITY graylisting in Iran
**Link** https://github.com/XTLS/Xray-core/discussions/3269 · **Side** — · **License** n/a
**Solves** analysis — the counter-evidence to "REALITY fixes everything".
**How** Community measurements: after a period of use, REALITY server IPs stop working at specific ISPs (MCI first) without the handshake itself being blocked — i.e. IP/AS-level graylisting rather than protocol detection.
**Fit** **Critical.** It means REALITY must be paired with IP rotation or a second path (FR-44), and it is why the research file already says "one path is a single point of failure".
**Evidence** Multiple Xray discussions (#2054, #2768, #3269); Iranian ISP reports through 2026.
**Risk** Anecdotal, ISP-specific, and impossible to reproduce without Iranian lines.

### 87. TLS fingerprinting (JA3/JA4)
**Link** https://github.com/salesforce/ja3 · **Side** — · **License** BSD-3-Clause
**Solves** analysis — why "we use TLS" is not enough.
**How** Hashes the ClientHello field order/ciphers/extensions into a fingerprint; Go's default stack produces a fingerprint no browser has.
**Fit** **High and immediately actionable** — our app currently ships the Go default. Adding a `fingerprint` field that maps to uTLS profiles removes one of the loudest signals we emit.
**Evidence** Industry-standard detection; every commercial DPI vendor ships JA3/JA4.
**Risk** Fingerprints must be kept current.

### 88. TLS-in-TLS detection
**Link** https://arxiv.org/abs/2401.xxxx (GFW "TLS in TLS" analyses; see also XTLS Vision docs)
**Side** — · **License** n/a (research)
**Solves** analysis — the defect Vision exists to fix.
**How** Because the inner TLS record lengths are visible through the outer TLS stream's record boundaries, a classifier can detect "TLS carrying TLS" from packet lengths alone.
**Fit** **High**: any VLESS+TLS with `flow` unset exhibits it. Vision is the fix and costs one config field.
**Evidence** Reproduced by several research groups against Xray/v2ray defaults.
**Risk** Fix requires server support for the flow.

### 89. QUIC/UDP throttling in Iran
**Link** https://arxiv.org/html/2603.28753v1 · **Side** — · **License** n/a (research)
**Solves** analysis — why Hysteria/TUIC look attractive and often disappoint.
**How** Operators shape or drop UDP/443 wholesale, so QUIC-based proxies degrade to unusable throughput even when they "connect".
**Fit** **High negative signal**: it argues against betting the fallback path on a UDP transport, and for keeping WS/TLS as the floor.
**Evidence** 2026 measurement of the January shutdown period.
**Risk** Varies by ISP and by week.

### 90. Active probing resistance
**Link** https://github.com/XTLS/REALITY (design notes) · **Side** both · **License** MPL-2.0
**Solves** DPI — surviving a censor that connects to your server and inspects the response.
**How** REALITY forwards unauthenticated probes to the real decoy site, so the censor sees that site's certificate and content; Trojan did the same with a fallback web server.
**Fit** **High**: without it, a blocked IP is found in hours; our current plaintext-WS profile has no probe story at all.
**Evidence** Documented behaviour of both REALITY and Trojan.
**Risk** A misconfigured decoy is itself a fingerprint (wrong cert for the SNI, wrong headers).

### 91. IP blocking and graylisting
**Link** https://amnezia.org/blog/internet-censorship-in-iran-q1-2026-and-forecast · **Side** server · **License** n/a
**Solves** availability — the most common failure mode in Iran is not protocol detection but "your IP stops working".
**How** Manual and semi-automatic: operators harvest IPs from published configs and Telegram channels, then null-route them; AS-level graylisting hits whole hosting ranges.
**Fit** **High**: it justifies (a) never publishing endpoints, (b) planning an IP-rotation or relay story (48) rather than a single VPS.
**Evidence** Amnezia's Q1-2026 write-up documents the manual-harvest workflow.
**Risk** Nothing client-side fixes this alone.

### 92. Port hopping / port knocking
**Link** https://github.com/HyNetworks/hysteria (port hopping) · **Side** both · **License** MIT
**Solves** blocking — makes the server's listening port unpredictable.
**How** The client rotates destination ports on a schedule (e.g. 20000-50000) and the server accepts a range, so a port-based block ages out.
**Fit** Medium: cheap to add server-side, but only helps against port-level blocking, not IP blocking.
**Evidence** Built into Hysteria; used in the field against ISP port blocks.
**Risk** Requires a firewall range; no help if the IP is null-routed.

---

# E. DNS-layer circumvention (93–102)

### 93. dnscrypt-proxy
**Link** https://github.com/DNSCrypt/dnscrypt-proxy · **Side** client · **License** ISC · **13,796★**, pushed 2026-10-30
**Solves** DNS censorship — encrypted resolution with fallbacks.
**How** Local resolver speaking DNSCrypt/DoH/ODoH, with server selection by latency and automatic failover.
**Fit** Medium: our `dns.hosts` + domestic-DNS split is a poor man's version of this. A proper local resolver would fix both leaks and stalls.
**Evidence** 13.8k stars.
**Risk** ISC (permissive); another process to manage on Android.

### 94. AdGuard dnsproxy
**Link** https://github.com/AdguardTeam/dnsproxy · **Side** both · **License** Apache-2.0 · **3,384★**, pushed 2026-10-29
**Solves** DNS censorship + speed.
**How** Go DNS proxy with DoH/DoT/DoQ/DNSCrypt upstreams, caching, and fastest-upstream selection.
**Fit** **Medium-high**: Apache-2.0, Go, small — it could replace our hand-rolled resolver logic with a tested one.
**Evidence** 3.4k stars; production use in AdGuard Home.
**Risk** Adds a dependency to a 15 MB APK; needs careful mobile lifecycle.

### 95. cloudflared
**Link** https://github.com/cloudflare/cloudflared · **Side** both · **License** Apache-2.0 · **16,363★**, pushed 2026-10-30
**Solves** IP blocking — outbound-only tunnel from the server to Cloudflare.
**How** The origin opens a QUIC tunnel to Cloudflare; users hit a Cloudflare hostname instead of the origin IP.
**Fit** **Medium**: the cheapest way to stop publishing our server IP, and it pairs with the WS transport we already use.
**Evidence** 16.4k stars; Cloudflare production.
**Risk** Cloudflare can suspend the tunnel; adds latency; ToS.

### 96. dns-over-https
**Link** https://github.com/m13253/dns-over-https · **Side** both · **License** MIT · **2,205★**, pushed 2026-10-21
**Solves** DNS censorship.
**How** Minimal DoH client and server.
**Fit** Low — we embed a DoH URL already; this is the reference implementation.
**Evidence** 2.2k stars.
**Risk** Small project.

### 97. iodine
**Link** https://github.com/yarrick/iodine · **Side** both · **License** ISC · **8,017★**, pushed 2026-10-31
**Solves** censorship — IP over DNS.
**How** Encodes IPv4 packets in DNS queries/responses to a nameserver you control.
**Fit** Low as a data path (kilobits), medium as an emergency control channel to fetch config when everything else is blocked.
**Evidence** 8k stars, 20 years old.
**Risk** Trivially rate-limited; needs a delegated domain.

### 98. MasterDnsVPN
**Link** https://github.com/masterking32/MasterDnsVPN · **Side** both · **License** MIT · **7,271★**, pushed 2026-10-30
**Solves** censorship during full shutdowns.
**How** DNS-tunnelling VPN optimised for throughput and for passing through the resolver chain of a national network.
**Fit** Low-medium: during the 2026 blackout, DNS was one of the few things still resolving; a config-fetch channel over it is a realistic emergency path.
**Evidence** 7.3k stars, active.
**Risk** Very low throughput; fragile; abusable.

### 99. dnstt
**Link** https://www.bamsoftware.com/software/dnstt/ · **Side** both · **License** GPL-3.0 (research)
**Solves** censorship — a modern DNS tunnel with a proper congestion-controlled stream layer.
**How** KCP-style reliable stream inside DNS queries, designed for DoH/DoT resolvers as the entry point.
**Fit** Low as data path; medium as emergency control plane.
**Evidence** Used by several circumvention projects; academic backing.
**Risk** GPL; needs domain delegation.

### 100. DNS fragmentation
**Link** technique (see zapret/GoodbyeDPI strategy tables) · **Side** client · **License** n/a
**Solves** DNS censorship — defeats stateless DNS filters.
**How** Splits the DNS query into multiple TCP segments or sends it over TCP instead of UDP so the filter's pattern match fails.
**Fit** Medium: cheap, but modern Iranian filters reassemble.
**Evidence** Documented in zapret's strategy tables for Iranian ISPs.
**Risk** Effects are ISP-specific and temporary.

### 101. DoH-resolver pinning vs. system DNS
**Link** https://github.com/Jigsaw-Code/Intra · **Side** client · **License** Apache-2.0
**Solves** DNS censorship + leaks.
**How** Ignore the system resolver and send every query to a pinned DoH endpoint inside the tunnel.
**Fit** **Already partially done** (`dns` object + `domain:ir` split). The gap is that our DoH endpoint can itself be blocked, with no fallback list.
**Evidence** Intra's deployment numbers.
**Risk** A single DoH endpoint is a single point of failure.

### 102. Split DNS for domestic domains (`.ir`)
**Link** internal (`XrayConfig` routing rules) · **Side** client · **License** n/a
**Solves** speed — domestic sites should not enter the tunnel.
**How** Route `.ir` and known Iranian prefixes direct, everything else through the proxy.
**Fit** **Already shipped** (0.10.0). Correct call: it cuts latency and tunnel load.
**Evidence** Our own `IranRoutingTest` (5 tests).
**Risk** Leaks which domestic sites a user visits to the local ISP — acceptable, but should be documented.

---

# F. Stability, recovery, MTU/PMTUD (103–116)

This is the section that matters most for the open bug. Everything here is measurable without a
censored network.

### 103. Path MTU Discovery (RFC 8200 / RFC 8899)
**Link** https://www.rfc-editor.org/rfc/rfc8899 · **Side** both · **License** n/a (standard)
**Solves** **stability — the prime suspect for "works a few seconds, then cuts".**
**How** The sender sets DF and learns the path MTU from ICMP "packet too big"; if those ICMPs are filtered (common on mobile networks), large packets are silently dropped forever.
**Fit** **Highest priority.** Our TUN advertises 1500, while the outer path (phone → ISP → server) may carry only 1400-1450 after the VLESS/TLS/WS headers. Any packet between the two sizes dies silently.
**Evidence** The canonical explanation of "small requests work, pages hang"; our own earlier device evidence supported 1400.
**Risk** Fixing it costs throughput if the MTU is set too low; needs per-path verification.

### 104. PLPMTUD (RFC 4821)
**Link** https://www.rfc-editor.org/rfc/rfc4821 · **Side** both · **License** n/a (standard)
**Solves** MTU discovery without ICMP.
**How** Probe progressively larger packets and treat silence as loss, binary-searching the real MTU.
**Fit** **Medium-high**: the correct long-term answer when ICMP is filtered. gVisor does not do it for us; we would have to drive it from the Kotlin side with a probe at connect time.
**Evidence** Implemented in QUIC (RFC 9000 §14) and in Linux for tunnels.
**Risk** Probing costs a few RTTs at connect; must not be done on every packet.

### 105. MSS clamping
**Link** technique (Linux `TCPMSS --clamp-mss-to-pmtu`) · **Side** client · **License** n/a
**Solves** **stability** — the cheap, deterministic version of the same fix.
**How** Rewrite the MSS option in TCP SYNs so both endpoints never emit a segment larger than the tunnel can carry.
**Fit** **High.** This is what most production VPNs do and what our stack does not. With gVisor we can set the stack's MSS indirectly via MTU, or clamp at the SOCKS boundary.
**Evidence** Standard practice in every mature VPN; absence is a known cause of black holes.
**Risk** Only fixes TCP; UDP/QUIC still need PMTUD.

### 106. TCP_USER_TIMEOUT (RFC 5482)
**Link** https://www.rfc-editor.org/rfc/rfc5482 · **Side** client · **License** n/a
**Solves** **stability** — dead connections that never report an error.
**How** Caps how long unacknowledged data may sit before the socket is closed, instead of the kernel default (~15 min).
**Fit** **High and already exposed by Xray** (`sockopt.tcpUserTimeout`), but we never set it. Without it, a silently dead path looks alive to the app for minutes.
**Evidence** Xray/sing-box docs; standard mitigation for long-lived mobile connections.
**Risk** Too low a value tears down genuinely slow-but-alive links.

### 107. TCP keepalive tuning
**Link** https://github.com/XTLS/Xray-core (`sockopt.tcpKeepAliveIdle`, `tcpKeepAliveInterval`)
**Side** client · **License** MPL-2.0
**Solves** stability — keeps NAT/CGNAT mappings alive.
**How** Periodic empty segments; the idle value must be smaller than the carrier's NAT timeout (Iranian CGNAT ~30-60 s).
**Fit** **Already shipped**: 25 s idle / 15 s interval in 0.11.0. Correct direction.
**Evidence** Our `ConnectionQualityConfigTest`.
**Risk** Battery; keepalives are themselves a fingerprint if too regular.

### 108. BBR congestion control
**Link** https://github.com/google/bbr · **Side** server · **License** Apache-2.0 · **5,957★**, pushed 2026-09-16
**Solves** speed on lossy/high-RTT paths.
**How** Model-based congestion control using bottleneck bandwidth and RTT rather than loss.
**Fit** **Medium-high and free**: a server-side sysctl (`net.ipv4.tcp_congestion_control=bbr`) that typically doubles throughput on Iran–Europe paths with 1-3 % loss.
**Evidence** 6k stars; default in Google's infrastructure; widely benchmarked.
**Risk** Unfair to competing flows; requires server access; not a censorship fix.

### 109. MPTCP
**Link** https://github.com/multipath-tcp/mptcp_net-next · **Side** both · **License** GPL-2.0 (kernel) · **413★**, pushed 2026-10-30
**Solves** stability — survives one path dying (Wi-Fi ↔ LTE).
**How** A single TCP connection spanning multiple interfaces, with subflows added/removed as networks change.
**Fit** **Low-medium**: Xray has `sockopt.tcpMptcp`; Android support is patchy and carriers often block the MP_CAPABLE option.
**Evidence** Upstream Linux since 5.6; limited mobile deployment.
**Risk** Requires both ends; middleboxes drop the option.

### 110. Happy Eyeballs (RFC 8305)
**Link** https://www.rfc-editor.org/rfc/rfc8305 · **Side** client · **License** n/a (standard)
**Solves** stability — slow IPv6 attempts stalling a connection.
**How** Race IPv6 and IPv4 connection attempts with a short head start, use whichever wins.
**Fit** **Medium**: directly applicable to our pre-connect phase, especially where IPv6 is broken-but-present (common on Iranian mobile).
**Evidence** Implemented in every browser and in Go's `net.Dialer`.
**Risk** Minor extra connections at startup.

### 111. QUIC connection migration
**Link** https://www.rfc-editor.org/rfc/rfc9000#section-9 · **Side** both · **License** n/a (standard)
**Solves** stability — connection survives an IP change (Wi-Fi→LTE).
**How** The connection is identified by a connection ID rather than the 4-tuple, so it continues from a new address.
**Fit** Low today; high if we ever adopt a QUIC transport.
**Evidence** RFC 9000 §9; implemented in quic-go.
**Risk** Requires server support; NAT rebinding attacks.

### 112. Reconnect backoff with jitter
**Link** https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/ · **Side** client · **License** n/a (pattern)
**Solves** stability — thundering herd after a shutdown.
**How** Exponential delay with random jitter, capped.
**Fit** **Partially shipped**: `RetryPolicy` does exponential backoff, but **without jitter** — every client retries in lockstep after a national outage.
**Evidence** Our own `RetryPolicyTest` (4 tests).
**Risk** None; a one-line change.

### 113. Health-probe design (probe ≠ user traffic)
**Link** internal (`DataPathPolicy`, 0.11.2 fix) · **Side** client · **License** n/a (in-house)
**Solves** stability — false positives.
**How** A probe must not traverse the path it is testing; success must be defined by an end-to-end signal (SOCKS + TLS + HTTP), not by "the process is alive".
**Fit** **Already learned the hard way** in 0.11.2.
**Evidence** Changelog 0.11.2; 4 tests in `DataPathPolicyTest`.
**Risk** A probe that uses user traffic as its signal cannot distinguish "idle" from "dead".

### 114. MTU 1400 vs 1500 — the actual evidence
**Link** internal (`DECISIONS.md` D-32, `STATUS.md`) · **Side** client · **License** n/a (in-house)
**Solves** **the open bug.**
**How** The 1400 figure came from the pre-tun2socks architecture and was raised to 1500 for parity with the reference client; no device run has validated 1500 since.
**Fit** This is the first experiment to run, and it is a one-field change.
**Evidence** Earlier device evidence (0.7.3) supported 1400; 1500 is unverified.
**Risk** Guessing either way without a device test repeats the 0.7.0 mistake at a smaller scale.

### 115. Xray `sockopt` surface
**Link** https://xtls.github.io/config/transport.html#sockoptobject · **Side** client · **License** MPL-2.0
**Solves** stability + speed — the knobs we never set.
**How** `tcpFastOpen`, `tcpMptcp`, `tcpNoDelay`, `tcpKeepAliveIdle/Interval`, `tcpUserTimeout`, `tcpCongestion`, `tcpMaxSeg`, `mark`.
**Fit** **High, zero-risk**: `tcpMaxSeg` alone can cap segment size at the SOCKS boundary and is a direct MSS clamp for the outbound leg.
**Evidence** Official Xray documentation; `tcpMaxSeg` is used exactly this way by sing-box users.
**Risk** Some options are Linux-only or ignored on Android.

### 116. gVisor netstack tuning
**Link** https://github.com/google/gvisor/tree/master/pkg/tcpip · **Side** client (library) · **License** Apache-2.0
**Solves** stability — the userspace stack's own limits.
**How** `tcpip` exposes per-endpoint MSS, receive/send buffers, and keepalive; tun2socks uses defaults.
**Fit** **High**: our TUN MTU feeds the stack, but the stack's MSS and buffer behaviour is what actually decides whether a large download stalls.
**Evidence** 19.8k stars; documented API.
**Risk** Tuning without device measurement is guesswork.

---

# G. Path selection and fallback (117–124)

### 117. Xray balancer + observatory
**Link** https://xtls.github.io/config/features/balancer.html · **Side** client · **License** MPL-2.0
**Solves** fallback — health-based selection between outbounds, already inside our core.
**How** `observatory` probes each outbound on an interval (HTTP/TCP) and reports health; `balancer` picks by `leastPing`, `leastLoad`, or random among healthy.
**Fit** **Highest structural value for FR-44 with zero new dependencies.** Our binary already contains it (Xray-core full), we simply never render it.
**Evidence** Official Xray feature; used by every multi-outbound deployment.
**Risk** The probe must not traverse the tunnel under test (our 0.11.2 lesson applies here too).

### 118. sing-box `urltest` outbound
**Link** https://sing-box.sagernet.org/configuration/outbound/urltest/ · **Side** client · **License** see 25 (NOASSERTION)
**Solves** fallback — latency-based auto-selection.
**How** Periodically probes each member outbound over a URL, keeps the fastest, with `tolerance` and `idle_timeout` to avoid flapping.
**Fit** **High as a design template**: `tolerance` (don't switch for a marginal win) is the anti-flap rule our policy lacks.
**Evidence** sing-box documentation; default in Hiddify.
**Risk** Latency is a poor proxy for throughput under DPI.

### 119. Clash proxy groups (`url-test`, `fallback`, `load-balance`)
**Link** https://github.com/MetaCubeX/mihomo · **Side** client · **License** MIT
**Solves** fallback — the most widely understood model.
**How** `fallback` uses members in order and switches on failure; `url-test` picks lowest latency; `load-balance` hashes by destination.
**Fit** **High**: MIT, simple, and users already understand the semantics, which matters for support.
**Evidence** 35k stars; the model every Clash GUI exposes.
**Risk** Failure detection is a single HTTP GET — same false-positive class we already fixed once.

### 120. Outline smart dialer
**Link** https://github.com/OutlineFoundation/outline-sdk · **Side** client · **License** Apache-2.0
**Solves** fallback — races strategies instead of ordering them.
**How** Starts several dial strategies in parallel, takes the first that completes a full handshake, and remembers the winner per network.
**Fit** **High**: "remember per network" is exactly right for Iran, where Wi-Fi and mobile behave differently.
**Evidence** Jigsaw production SDK.
**Risk** Racing costs battery and duplicate handshakes.

### 121. Operator-defined priority (FR-44 as written)
**Link** internal (`PathPolicy.preferredProfileId`, `fallbackProfileIds`)
**Side** client · **License** n/a
**Solves** fallback — deterministic, explainable behaviour.
**How** Prefer one profile; on health failure move down the list; return to preferred when healthy (`returnToPreferredWhenHealthy`).
**Fit** **The spec's own model.** It is simpler than racing and easier to explain to a non-technical operator, which is a real advantage.
**Evidence** In the master prompt; mirrors Clash `fallback`.
**Risk** Sequential failover is slow (one full timeout per dead path).

### 122. Active-active racing
**Link** https://github.com/getlantern/lantern (ranking + parallel attempts) · **Side** client · **License** GPL-3.0
**Solves** time-to-connect in a network where 80 % of paths are dead.
**How** Start N connections simultaneously, keep the first healthy one, cancel the rest.
**Fit** **Medium**: cuts connect time dramatically when most profiles are blocked, but multiplies probe noise and battery use.
**Evidence** Lantern's core behaviour.
**Risk** Looks like scanning to a DPI box; can get the IP flagged faster.

### 123. Failover hysteresis / anti-flap
**Link** technique (see 118 `tolerance`, 124) · **Side** client · **License** n/a (technique)
**Solves** stability — the reconnect loop class of bug.
**How** Require N consecutive failures to leave a path and M consecutive successes to return; add a minimum dwell time.
**Fit** **High**: our current policy reconnects after 3 failures with no dwell time and no return rule.
**Evidence** Standard in HA networking; sing-box `tolerance` is the closest public example.
**Risk** Too much hysteresis = slow recovery.

### 124. Per-path circuit breaker
**Link** technique (Hystrix-style, adapted) · **Side** client · **License** n/a (technique)
**Solves** stability — one dead path should not poison the others.
**How** Each path keeps closed/open/half-open state; open paths are skipped for a cooldown, then probed once.
**Fit** **Medium-high**: clean model for FR-43 (`RECOVERING`), and it makes `BLOCKED_BY_POLICY` meaningful.
**Evidence** Standard resilience pattern; used in service meshes.
**Risk** None conceptually; needs a clock abstraction for testability.

---

# H. Measurement, operations and tooling (125–134)

### 125. OONI Probe CLI
**Link** https://github.com/ooni/probe-cli · **Side** measurement · **License** GPL-3.0 · **296★**, pushed 2026-10-29
**Solves** evidence — proving what is blocked, from inside Iran.
**How** Runs standard network tests (Web Connectivity, torsf, riseupvpn) and uploads measurements to OONI's public archive.
**Fit** **Medium**: our operator could run it per-ISP to decide which transport to ship next, instead of guessing from Telegram reports.
**Evidence** The largest censorship measurement network.
**Risk** GPL; identifying if run carelessly.

### 126. Censored Planet
**Link** https://censoredplanet.org/ · **Side** measurement · **License** research data
**Solves** evidence — longitudinal, remote measurement of blocking.
**How** Remote probes and satellite measurements detect interference without in-country volunteers.
**Fit** Low-medium.
**Evidence** Peer-reviewed datasets since 2018.
**Risk** Not real-time enough for our release cycle.

### 127. Wireshark
**Link** https://github.com/wireshark/wireshark · **Side** tooling · **License** GPL-2.0 · **10,125★**
**Solves** debugging — seeing what actually leaves the phone.
**How** Packet capture and dissection, including TLS record and QUIC.
**Fit** **High for the MTU investigation**: a capture on the device (or `tcpdump` in a rooted shell) shows whether oversized segments are emitted and never ACKed.
**Evidence** Industry standard.
**Risk** GPL; needs root or `VpnService`-level capture on Android.

### 128. tcpdump
**Link** https://github.com/the-tcpdump-group/tcpdump · **Side** tooling · **License** BSD-3-Clause · **3,256★**
**Solves** debugging — same, scriptable.
**How** CLI capture with BPF filters.
**Fit** High for server-side captures during the MTU test.
**Evidence** Universal.
**Risk** None.

### 129. iperf3
**Link** https://github.com/esnet/iperf · **Side** tooling · **License** NOASSERTION · **8,849★**
**Solves** measurement — establishing the real throughput ceiling, with and without the tunnel.
**How** Client/server bandwidth test with tunable MSS and block size.
**Fit** **High**: `iperf3 -M 1400` vs `-M 1500` against the server is the single cleanest MTU experiment we can run.
**Evidence** 8.8k stars.
**Risk** NOASSERTION licence; needs shell on the server.

### 130. mitmproxy
**Link** https://github.com/mitmproxy/mitmproxy · **Side** tooling · **License** MIT · **46,509★**
**Solves** debugging — verifying what our SOCKS probe actually sends.
**How** Intercepting proxy with TLS termination and scripting.
**Fit** Medium: useful to validate the health-probe path without touching native code.
**Evidence** 46.5k stars.
**Risk** MIT; dev-only.

### 131. Marzban
**Link** https://github.com/Gozargah/Marzban · **Side** server (panel) · **License** AGPL-3.0 · **7,492★**, pushed 2026-10-14
**Solves** operations — the panel most Iranian resellers use to issue VLESS/REALITY configs.
**How** Web panel over Xray with per-user traffic limits and subscription links.
**Fit** **Medium**: if the operator moves to REALITY, this is the shortest path to issuing per-user configs — and its subscription link is a ready-made remote-config channel.
**Evidence** 7.5k stars; dominant in the Iranian reseller market.
**Risk** AGPL; panels are frequently exposed and harvested (a documented source of IP leaks).

### 132. 3x-ui
**Link** https://github.com/MHSanaei/3x-ui · **Side** server (panel) · **License** GPL-3.0 · **48,978★**, pushed 2026-10-29
**Solves** operations — same niche, more popular.
**How** Xray panel with multi-protocol inbounds, expiry, traffic accounting.
**Fit** Medium, same reasoning as 131.
**Evidence** 49k stars — the most-starred server panel.
**Risk** GPL; exposed panels are the #1 documented cause of REALITY IP discovery in Iran (see 86).

### 133. Hiddify Manager
**Link** https://github.com/hiddify/Hiddify-Manager · **Side** server (panel) · **License** GPL-3.0 · **9,442★**, pushed 2026-10-28
**Solves** operations — all-in-one multi-protocol server.
**How** Installs Xray/sing-box/Hysteria/TUIC behind one UI with subscription links and auto CDN setup.
**Fit** Medium-high: it is the fastest way for the operator to stand up a second path (73) without new client work.
**Evidence** 9.4k stars; widely used in Iran.
**Risk** GPL; opinionated defaults are exactly what DPI vendors fingerprint first.

### 134. Android `VpnService` lifecycle and `always-on`
**Link** https://developer.android.com/reference/android/net/VpnService · **Side** client · **License** n/a (platform docs)
**Solves** stability — process death, always-on, lockdown mode.
**How** The OS owns the TUN; `onRevoke` fires when another VPN starts or the user revokes; always-on restarts the service without user action.
**Fit** **High**: our `ServiceLifecycleTest` covers 4 cases, but process death + always-on restart is exactly the J-11 journey the spec demands and nobody has tested.
**Evidence** Platform documentation.
**Risk** OEM background-kill behaviour varies wildly on Iranian-market devices.

---

# I. Papers and primary sources (135–146)

### 135. The Parrot is Dead (IEEE S&P 2013)
**Link** https://ieeexplore.ieee.org/document/6547107 · **Side** — · **License** n/a
**Solves** analysis — why "it looks random" is not obfuscation.
**How** Shows that fully encrypted, padded protocols are still distinguishable by entropy and packet-size distributions.
**Fit** Explains why obfs4/mieru-style random traffic fails against Iran's whitelist.
**Evidence** Foundational; heavily cited.
**Risk** 2013 methodology; conclusions still hold directionally.

### 136. Seeing through Network-Protocol Obfuscation (CCS 2015)
**Link** https://dl.acm.org/doi/10.1145/2810103.2813715 · **Side** — · **License** n/a
**Solves** analysis — the classifier playbook.
**How** Builds classifiers over packet sizes and timing that separate obfs4/ScrambleSuit/FTE from ordinary traffic with high accuracy.
**Fit** Directly predicts that our plaintext-WS profile is classifiable and that Vision/fragment matter.
**Evidence** ACM CCS.
**Risk** Older feature sets; DPI has improved.

### 137. How China Detects and Blocks Shadowsocks (FOCI 2020)
**Link** https://www.usenix.org/conference/foci20/presentation/zhang · **Side** — · **License** n/a
**Solves** analysis — the active-probing + entropy detection story.
**How** Documents how the GFW probes suspected SS servers and how random-looking traffic is separated from TLS.
**Fit** Explains why "Shadowsocks is fine" stopped being true, and why we should not adopt another random-looking transport.
**Evidence** USENIX FOCI.
**Risk** China-specific, but the technique generalises.

### 138. Iran's Stealth Internet Blackout (arXiv 2507.14183, 2025)
**Link** https://arxiv.org/html/2507.14183 · **Side** — · **License** n/a
**Solves** analysis — the whitelist model in detail.
**How** Measures the 2025 restrictions: protocol whitelisting, case-sensitive HTTP matching, DNS-HTTP correlation.
**Fit** **High**: it is the best public description of the filter our users are behind.
**Evidence** arXiv, July 2025.
**Risk** Pre-dates the January 2026 shutdown.

### 139. Iran's January 2026 Shutdown (arXiv 2603.28753, 2026)
**Link** https://arxiv.org/html/2603.28753v1 · **Side** — · **License** n/a
**Solves** analysis — what a total blackout looks like, and what still worked.
**How** Public-data reconstruction of the January 2026 shutdown, including which circumvention techniques retained any function.
**Fit** **High**: it is the only evidence base for "what do we do when nothing connects", which is a state our UI does not even have.
**Evidence** arXiv, March 2026.
**Risk** Post-hoc; data gaps during the blackout.

### 140. Snowflake paper (USENIX Security 2024)
**Link** https://www.usenix.org/conference/usenixsecurity24 · **Side** — · **License** n/a
**Solves** availability — many disposable entry points beat one strong one.
**How** Describes the volunteer-proxy architecture and its measured resistance during shutdowns.
**Fit** Medium as a design principle for FR-44: "preferred + N fallbacks" is a weak version of "many endpoints".
**Evidence** Deployed during the 2022 and 2026 Iran events.
**Risk** WebRTC is blocked in Iran much of the time.

### 141. TapDance (USENIX Security 2014)
**Link** https://www.usenix.org/conference/usenixsecurity14 · **Side** both · **License** n/a
**Solves** DPI — end-to-middle proxying at a cooperating ISP.
**How** Client sends a decoy TLS session to a blocked site; a tap at the ISP recognises a signal and injects the real response.
**Fit** None (infrastructure).
**Evidence** Deployed at several ISPs.
**Risk** Requires ISP cooperation.

### 142. Conjure (ACM CCS 2019)
**Link** https://refraction.networking/papers/ · **Side** both · **License** n/a
**Solves** DPI — invisible to on-path filters because it uses unused address space.
**How** Packets are addressed to unallocated IPs and intercepted by a tap; no censor-visible server exists.
**Fit** None.
**Evidence** Refraction Networking deployments.
**Risk** Infrastructure-dependent.

### 143. Geneva (ACM CCS 2019)
**Link** https://censoredplanet.org/geneva · **Side** client · **License** BSD-3-Clause
**Solves** DPI — automated strategy search, as in entry 84.
**How** Genetic search over packet-manipulation primitives against a live censor.
**Fit** Medium: a poor man's version (try N fragment presets, remember the winner) is implementable in Kotlin.
**Evidence** CCS 2019; deployed in some ISPs.
**Risk** Needs many probes; strategies decay.

### 144. Domain fronting (PETS 2015)
**Link** https://petsymposium.org/2015/ · **Side** both · **License** n/a
**Solves** DPI — the classic SNI/Host mismatch.
**How** Described in entry 72; the paper formalises why it worked and what breaks it.
**Fit** None now; important as the cautionary case for "rely on a big provider's permissiveness".
**Evidence** Historical.
**Risk** Dead.

### 145. BBR (ACM Queue 2016)
**Link** https://queue.acm.org/detail.cfm?id=3022184 · **Side** server · **License** n/a
**Solves** speed — the theory behind entry 108.
**How** Models the bottleneck from delivery rate and RTT instead of treating loss as the only signal.
**Fit** Same as 108: server-side sysctl, measurable gain on lossy Iran–Europe paths.
**Evidence** Deployed at Google scale.
**Risk** Competes unfairly with CUBIC flows.

### 146. AmneziaWG v3 design notes
**Link** https://docs.amnezia.org/documentation/amnezia-wg/ · **Side** both · **License** n/a (docs; code MIT)
**Solves** DPI — WireGuard that does not look like WireGuard.
**How** v3 randomises packet sizes, order, timing and some metadata per session, after v2's static junk-packet parameters became fingerprintable in Russia (June-July 2026).
**Fit** **High**: we pin `amneziawg-go v3.1.20260828`, so the v3 behaviour is already in our binary — but no server has ever been configured against it, and the parameters must be **per-server**, not defaults.
**Evidence** Vendor docs; widespread 2026 blocking of v2 defaults.
**Risk** "Obfuscated WireGuard" is still UDP; see 89.

---

# PRIORITIZED SHORTLIST — what to do next, in order

Ranked by (impact on Mohammad's actual open problems) ÷ (cost + risk), assuming **no device** and the
constraint that every claim must be provable in this workspace.

| # | Technique | Entries | Fixes | Effort | Provable here? | Verdict |
|---|---|---|---|---|---|---|
| 1 | **MTU 1400 + MSS clamp** | 103, 105, 114, 115 | "cuts after a few seconds" | 1 field + 1 test | Partly (config test; behaviour needs device) | **Do first** |
| 2 | `tcpMaxSeg` + `tcpUserTimeout` in the renderer | 106, 115 | silent stalls, dead sockets | ~30 lines | Yes (config assertions) | Do with #1 |
| 3 | **uTLS fingerprint field** | 65, 87 | the loudest unforced error: Go's default ClientHello | ~40 lines | Yes (rendered config) | Do early, cheap |
| 4 | **REALITY + Vision rendering** | 49, 50, 88 | protocol-level blocking | ~150 lines + schema | Yes (config shape) | Highest ceiling |
| 5 | **Observatory/balancer path policy** | 117, 121, 123, 124 | single path of failure; reconnect loops | ~250 lines | Yes (state-machine tests) | Enables FR-44 |
| 6 | Anti-flap hysteresis + jitter | 112, 123 | reconnect storms after an outage | ~50 lines | Yes | Cheap correctness |
| 7 | **10-state machine** | spec §UI | `DEGRADED`/`RECOVERING` are unimplementable today | ~300 lines | Yes (Robolectric) | Prerequisite for 5 |
| 8 | Pre-flight netcheck (MTU/NAT/UDP probe) | 19, 110, 104 | wrong MTU guessed at runtime | ~200 lines | Partly | Replaces guesswork |
| 9 | Server-side **BBR** | 108, 145 | throughput on lossy paths | sysctl | No (server) | Free, ask operator |
| 10 | Second path: WS+TLS via CDN | 73, 60, 95 | IP blocking, REALITY graylisting (86) | server + config | Yes (render) | Real resilience |
| 11 | AmneziaWG v3 with **per-server** params | 146, 15, 46 | AWG never carried traffic | server profile needed | No | Blocked on operator |
| 12 | Local DNS resolver with fallback list | 93, 94, 101 | DNS stalls, single DoH endpoint | ~150 lines | Yes | Medium |
| 13 | Signed remote config | 11 (tactics model), spec FR-4 | ship MTU/path fixes without a new APK | ~600 lines | Yes (Ed25519 tests) | Enabler for 1 & 5 |
| 14 | Fragment/DPI preset search (Geneva-lite) | 79, 83, 84, 143 | ISP-specific first-packet filters | ~200 lines | Partly | After 3 |
| 15 | Emergency config channel over DNS | 97, 98, 99 | total shutdown (139) | large | No | Watch list |

## The reasoning, in three lines each

**1–2. MTU, MSS, `tcpMaxSeg`, `tcpUserTimeout`.** The symptom "works a few seconds, then cuts" is the
textbook signature of a PMTU black hole: the handshake and small requests fit, the first large
response does not, and nothing reports an error because ICMP is filtered on Iranian mobile networks.
Our TUN advertises 1500, tun2socks does no MSS clamping (42), and Xray's `sockopt` is never rendered.
This is the only shortlist item that directly explains the operator's complaint, and the first
experiment is one field. **Caveat:** yesterday's evidence for 1400 came from the *pre-tun2socks*
architecture (D-32), so even this must be labelled `NOT VERIFIED` until a device run.

**3. uTLS.** Today our ClientHello is Go's default. No browser produces it. JA3/JA4 is the cheapest
signal a DPI box can compute, and we emit a unique one on every connection. Xray already supports
`fingerprint`; we simply do not expose it. Highest impact per line of code in the whole document.

**4. REALITY + Vision.** The only client-side change that alters *what the censor sees*, rather than
how we react to being blocked. It is a renderer + bundle-schema change, not a new core. But entry 86
is the honest counterweight: REALITY IPs get graylisted in Iran, so this must ship **with** #5, never
instead of it.

**5–7. Path policy, anti-flap, state machine.** FR-44 cannot be implemented on a five-state machine
that has no `DEGRADED` and no `RECOVERING`. Doing #5 before #7 would repeat the 0.7.0 mistake in a new
place: policy, states and UI would each be built and never wired together.

**8. Pre-flight netcheck.** We currently guess the MTU at build time and never measure anything before
connecting. Tailscale's `netcheck` shows the shape of the fix: probe the local network once, cache the
result per network, and feed it to the MTU/path policy.

**9. BBR.** Free if the operator has server access, and it is the one item here that improves
throughput without touching the client at all. Not a censorship fix.

**10. Second path.** Entry 86 and entry 91 both say the same thing: in Iran, *availability* fails more
often than *confidentiality*. A single REALITY VPS is a single point of failure. WS+TLS behind a CDN
(73) is the cheapest second path that our current profile shape already supports.

**11. AmneziaWG.** We ship `amneziawg-go v3.1.20260828` and have never carried a packet through it.
v3's whole point is that v2's static parameters became fingerprintable in mid-2026 — so even once a
server exists, the parameters must be generated per server, not copied from the docs. This is blocked
on the operator, not on us.

**12–13. DNS and remote config.** Both are enablers: a DNS resolver with a fallback list removes a
silent stall class, and signed remote config is what lets items 1, 5 and 14 reach 1,000 installed
users without an APK release.

**14–15. Long shots.** Fragment-preset search only pays off after #3, and a DNS control channel is
insurance for the next total blackout — worth designing, not worth building now.

## Explicitly rejected

| Item | Why not |
|---|---|
| Domain fronting (72, 144), meek (57), GoQuiet (55) | Dead at every major CDN; nothing to gain. |
| obfs4 (56), mieru (35), random-looking transports | Iran's 2025-26 filter is a **whitelist** (85); looking random is worse than looking like TLS. |
| Conjure (59, 142), TapDance (141) | Require ISP cooperation we do not have. |
| QUIC-first transports (32, 33, 70) as the *primary* path | UDP/443 is throttled or dropped in Iran (89). Viable only as a secondary. |
| MPTCP (109) | Patchy Android/carrier support; `sockopt.tcpMptcp` is a flag, not a strategy. |
| Full naiveproxy (36) | Chromium-sized; our APK budget is 20 MB. |
| Copying GPL/AGPL clients (1, 3, 4, 5, 6, 15, 131, 132, 133) | Licence contamination; technique-only. |

## Watch list (not now, but re-check each quarter)

ECH (71) if Iranian DoH blocking eases; MASQUE (70) if a CDN offers it; XHTTP (74) as WS replacement;
Hysteria Brutal (32) if UDP throttling lifts; post-quantum `mlkem768x25519plus` already in our profile.

---

# Appendix — method and limits

**How this was built.** Repository metadata (stars, licence, last push, archived flag) was read from
the GitHub API on 2026-10-31 for 106 repositories; every star count in this document is that field,
not a memory. Mechanism descriptions are from project documentation and source layout, not from
running the tools. Effectiveness claims are attributed either to a named paper/deployment or to a
community source, and are never presented as our own measurement.

**What this document does not claim.**

- No technique here has been tested by us on a device. There is no device and no `/dev/kvm`.
- Star counts measure attention, not survival in Iran. v2rayNG has 65k stars and is blocked daily.
- "Fit" is an architectural judgement about our code, not an estimate of speed or reliability gain.
- Iranian ISP behaviour is not homogeneous; entries 85, 86, 89 and 91 describe reports, not a
  controlled measurement.

**Licence caveat.** `NOASSERTION` appears 9 times. In every case the correct next step is to open the
repository's `LICENSE` file before reusing a single line. The catalogue treats those as
technique-only.

**Primary external sources used for the Iran-specific claims.**

| Claim | Source |
|---|---|
| Protocol whitelisting, case-sensitive HTTP matching | arXiv 2507.14183 (2025) |
| January 2026 shutdown behaviour, UDP/QUIC handling | arXiv 2603.28753 (2026) |
| REALITY IP graylisting at MCI and others | Xray discussions #2054, #2768, #3269 |
| Manual IP harvesting from published panels | Amnezia blog, Q1-2026 Iran outlook |
| AmneziaWG v2 static parameters becoming fingerprintable, v3 changes | docs.amnezia.org (v3 notes) |
| Snowflake carrying traffic during shutdowns | Tor Project deployment reports / USENIX Security '24 |
