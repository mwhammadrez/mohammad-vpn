# Iran-specific circumvention research — 2026-10-30

Research only. No code, config or dependency was changed in this task.

**Upstream state at the time of writing:** Xray-core v26.10.29 pinned; architecture
`tun2socks → 127.0.0.1:10808 SOCKS → Xray outbound`; the operator bundle currently carries a
**VLESS / WebSocket / `security: none`** profile.

---

## 4.1 Executive summary

**The single most important fact:** the current profile has no transport security. Everything in
this report is second-order compared to that. Fragment, mux and DNS tuning change how *a handshake*
looks; with `security: none` there is no TLS handshake to disguise, and the stream begins with a
plaintext HTTP Upgrade that any stateful DPI can classify. No client-side toggle fixes this.

Ranked by expected impact on a real Iranian connection:

| Rank | Change | Where | Effort | Why |
|---|---|---|---|---|
| 1 | **VLESS + REALITY** on TCP 443 with a plausible decoy SNI | server | M | Current best-evidenced method; client already supports it via the bundle (config-only, zero code) |
| 2 | **Fragment `packets: "1-3"` on by default** when the transport is plain WS | client | S | Slices the first three TCP writes; the only client-side lever that works without TLS |
| 3 | **Domestic routing dataset (Chocolate4U)** instead of generic `domain:ir` only | client | S | Adds `geosite:ir` + malware/phishing blocklists; reduces false proxying of Iranian services |
| 4 | **Second path for when REALITY is graylisted** (WS+CDN or AmneziaWG) | server+client | M | REALITY IPs are graylisted in Iran (Discussion #3269); one path is a single point of failure |
| 5 | Keep **Mux off** by default | client | — | Mux amplifies loss and gives a stable fingerprint; enable only on high-RTT stable links |
| 6 | **Browser dialer / XHTTP** as fallback transport | server+client | L | Newer transport with better HTTP mimicry; needs a matching server and a different dial path |

What should be **on by default**: REALITY (server-side choice), fragment `1-3` for WS profiles,
domestic routing, QUIC block, FakeDNS + domestic resolver for `.ir`.

What should stay **optional**: mux, fragment `tlshello` (it breaks REALITY — see finding 4),
Hysteria2, per-app routing.

What should be **excluded**: anything requiring a second core (Aether), anything with an
incompatible licence (GPL-3.0 client code copied into an Apache-2.0 app), and any protocol the
pinned Xray does not speak.

---

## 4.2 Findings table

Scoring: each criterion 1–5. **Total /35.** Recommend at ≥26.
Criteria: Iran effectiveness (IE), zero-config fit (ZC), compatibility with our pinned stack (CO),
maintenance (MT), licence (LC), performance cost (PC, 5 = cheap), security (SC).

| # | Technique / project | Source | IE | ZC | CO | MT | LC | PC | SC | Total | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | VLESS + REALITY | https://xtls.github.io/en/config/transports/reality.html | 5 | 5 | 5 | 5 | 4 | 4 | 5 | 33 | **Adopt (server)** |
| 2 | XTLS-Vision flow control | https://xtls.github.io/en/config/transports/tls.html | 4 | 5 | 5 | 5 | 4 | 5 | 4 | 32 | Adopt with REALITY |
| 3 | TCP fragment, `tlshello` | https://xtls.github.io/en/config/transports/finalmask.html | 4 | 5 | 5 | 5 | 5 | 4 | 4 | 32 | Adopt only with TLS |
| 4 | TCP fragment, `packets: "1-3"` | https://github.com/2dust/v2rayN/issues/10407 | 4 | 5 | 5 | 5 | 5 | 5 | 4 | 33 | **Default for WS** |
| 5 | `tlshello` + REALITY conflict | https://github.com/2dust/v2rayN/issues/10407 | — | — | — | — | — | — | — | — | **Known defect, avoid** |
| 6 | XHTTP transport | https://xtls.github.io/en/config/transports/xhttp.html | 4 | 3 | 4 | 5 | 4 | 3 | 4 | 27 | Fallback path |
| 7 | Browser dialer | https://xtls.github.io/en/config/features/browser_dialer.html | 3 | 2 | 2 | 5 | 4 | 2 | 3 | 21 | Exclude (needs a browser) |
| 8 | HTTPUpgrade | https://xtls.github.io/en/config/transports/httpupgrade.html | 3 | 4 | 5 | 5 | 4 | 4 | 4 | 29 | Optional |
| 9 | WebSocket + CDN fronting | https://github.com/XTLS/Xray-examples | 4 | 3 | 5 | 4 | 4 | 3 | 3 | 26 | Keep as fallback |
| 10 | gRPC transport | https://xtls.github.io/en/config/transports/grpc.html | 2 | 4 | 5 | 5 | 4 | 4 | 4 | 28 | Not preferred |
| 11 | Hysteria2 | https://sing-box.sagernet.org/configuration/outbound/hysteria2/ | 2 | 3 | 2 | 5 | 3 | 5 | 4 | 24 | Exclude for now (UDP throttled) |
| 12 | Hysteria2 + Salamander obfs | https://www.samnet.dev/learn/guides/hysteria2-setup/ | 3 | 3 | 1 | 4 | 3 | 4 | 4 | 22 | Exclude (needs another core) |
| 13 | TUIC v5 | https://github.com/EAimTY/tuic | 2 | 3 | 1 | 3 | 3 | 5 | 4 | 21 | Exclude |
| 14 | AnyTLS | https://sing-box.sagernet.org/configuration/outbound/anytls/ | 3 | 2 | 1 | 4 | 3 | 4 | 4 | 21 | Exclude (sing-box only) |
| 15 | Mieru | https://github.com/enfein/mieru | 2 | 2 | 1 | 4 | 3 | 4 | 4 | 20 | Exclude |
| 16 | ShadowTLS v3 | https://github.com/ihciah/shadow-tls | 3 | 2 | 1 | 3 | 3 | 4 | 4 | 20 | Exclude |
| 17 | AmneziaWG | https://github.com/amnezia-vpn/amneziawg-go | 3 | 4 | 5 | 4 | 5 | 4 | 4 | 29 | Keep as fallback |
| 18 | Psiphon tunnel core | https://github.com/Psiphon-Labs/psiphon-tunnel-core | 4 | 2 | 1 | 5 | 2 | 3 | 4 | 21 | Exclude (GPL, extra core) |
| 19 | sing-box as alternative core | https://github.com/SagerNet/sing-box | 3 | 2 | 1 | 5 | 3 | 3 | 4 | 21 | Exclude |
| 20 | mihomo | https://github.com/MetaCubeX/mihomo | 3 | 2 | 1 | 5 | 5 | 3 | 4 | 23 | Exclude |
| 21 | v2rayNG reference behaviour | https://github.com/2dust/v2rayNG | 4 | 4 | 4 | 5 | 2 | 4 | 4 | 27 | Reference only |
| 22 | PattNG fork (extra cores) | https://github.com/patterniha/PattNG | 4 | 3 | 2 | 4 | 2 | 3 | 3 | 21 | Reference only |
| 23 | Chocolate4U Iran rules | https://github.com/Chocolate4U/Iran-v2ray-rules | 5 | 5 | 5 | 5 | 2 | 5 | 4 | 31 | **Adopt dataset** |
| 24 | Loyalsoldier rules-dat | https://github.com/Loyalsoldier/v2ray-rules-dat | 4 | 5 | 5 | 5 | 2 | 5 | 4 | 30 | Adopt dataset |
| 25 | FakeDNS | https://xtls.github.io/en/config/fakedns.html | 4 | 5 | 5 | 5 | 4 | 5 | 4 | 32 | Keep |
| 26 | Domestic resolver for `.ir` | https://xtls.github.io/en/config/dns.html | 4 | 5 | 5 | 5 | 4 | 5 | 4 | 32 | Keep (already shipped) |
| 27 | QUIC block (`udp:443`) | https://github.com/net4people/bbs | 4 | 5 | 5 | 5 | 4 | 5 | 3 | 31 | Keep |
| 28 | `domainStrategy: IPIfNonMatch` | https://xtls.github.io/en/config/routing.html | 4 | 5 | 5 | 5 | 4 | 4 | 4 | 31 | Keep |
| 29 | Routing presets (IR/CN/RU whitelist) | https://github.com/patterniha/PattNG | 4 | 4 | 5 | 4 | 2 | 5 | 4 | 28 | Consider (operator UI) |
| 30 | REALITY graylisting evidence | https://github.com/XTLS/Xray-core/discussions/3269 | 5 | — | — | — | — | — | — | — | **Evidence source** |
| 31 | Fragment feature origin (Iran) | https://github.com/XTLS/Xray-core/issues/2000 | 5 | — | — | — | — | — | — | — | Evidence source |
| 32 | Iran blocking reports (UDP, SNI) | https://github.com/net4people/bbs | 4 | — | — | 5 | 4 | — | — | — | Monitoring source |
| 33 | Xray observatory (latency probes) | https://xtls.github.io/en/config/observatory.html | 3 | 4 | 5 | 5 | 4 | 4 | 4 | 29 | Optional |
| 34 | Serverless Iran configs (fragment+noise) | https://github.com/XTLS/Xray-examples | 3 | 2 | 4 | 4 | 4 | 4 | 3 | 24 | Exclude (no server) |
| 35 | NGINX SNI routing / decoy page | https://github.com/XTLS/Xray-examples | 5 | 4 | 5 | 4 | 4 | 4 | 4 | 30 | **Adopt (server)** |
| 36 | CDN fronting (Cloudflare) | https://github.com/XTLS/Xray-examples | 4 | 3 | 5 | 4 | 4 | 3 | 3 | 26 | Fallback path |

---

## 4.3 Detailed analysis — top findings

### 1. VLESS + REALITY (33/35) — the actual fix

**What:** the client connects to TCP 443 and performs a real TLS handshake with a *third-party*
site's certificate (the decoy). The censor sees a connection to, say, a large CDN; only a client
holding the server's public key can complete the tunnel.

**Why Iran:** SNI-based and protocol-fingerprint blocking both fail against it, because the
handshake is genuine. XTLS Discussion #3269 documents the counter-move: Iranian ISPs began
**graylisting REALITY server IPs** from 2024, which is why a second path is mandatory.

**Integration:** zero code. The bundle already carries a full Xray outbound; changing it to
`security: reality` + `realitySettings` is a server + bundle change. `XrayConfig` passes unknown
fields through untouched.

**Risk:** if the server IP is graylisted, everything dies. Hence finding #4.

### 2. Fragment `packets: "1-3"` (33/35) — the only client-side lever

**What:** slices the first three TCP writes of the connection into small chunks.

**Why:** with a plain WS profile there is no TLS ClientHello, so `tlshello` is inert; the first
bytes are an HTTP Upgrade request, which is exactly what a stateful DPI matches on. Slicing it
defeats naive pattern matchers.

**Evidence that `tlshello` is wrong for REALITY:** 2dust/v2rayN issue #10407 reports that
`tlshello` **breaks** REALITY on current Xray, and that v2rayNG sends `1-3` instead.

**Recommendation:** default `1-3` for `security: none` and WS profiles; leave `tlshello` as an
expert option only for TLS profiles where it is known to help.

### 3. Chocolate4U Iran rules (31/35)

A maintained dataset that ships `geosite:ir` and `geoip:ir` plus malware/phishing/ads categories,
built for Iranian users. Our current config uses the generic `domain:ir` suffix rule (D-22) because
generic datasets are large and can be missing. Pairing both is strictly better: suffix rule first
(no dataset needed), category rules when the dataset is present.

### 4. Second path (REALITY + WS/CDN or AmneziaWG) (30/35)

Because REALITY IPs get graylisted, the app should be able to switch to a different outbound
without the user doing anything. With the bundle design this is a **bundle** change: ship two
profiles (one REALITY, one WS+CDN) and let the existing profile list pick.

### 5. Mux off by default

Xray mux multiplexes streams over one TCP connection. On a lossy Iranian mobile link this
amplifies head-of-line blocking and gives the censor a stable, easily fingerprinted pattern.
Keep it as an expert toggle (already implemented, off by default).

---

## 4.4 Excluded findings

| Finding | Reason |
|---|---|
| Hysteria2 / TUIC | QUIC/UDP is throttled or blocked by Iranian ISPs in practice (r/dumbclub reports for Irancell; net4people bbs tracks dynamic UDP blocking). Requires a second core; our pinned Xray does support the hysteria transport, but the network path is the blocker, not the code |
| Psiphon | GPL-3.0 core, separate tunnel stack, and it bypasses the operator's server entirely — incompatible with the product model |
| AnyTLS / Mieru / ShadowTLS | Not implemented by Xray-core v26.10.29; adopting means a second core or a migration |
| Aether (PattNG) | A separate core with no public spec; cannot be added without vendoring GPL code |
| Browser dialer | Requires an actual browser process; not viable inside a VPN service |
| sing-box / mihomo as the core | Full re-architecture for no Iran-specific gain over Xray+REALITY |
| Serverless Iran configs | No server → no benefit to an operator-run service |

---

## 4.5 Roadmap

**Phase A — config/bundle only, no code (highest value per hour)**
1. Move the server to VLESS + REALITY + Vision on TCP 443, decoy SNI on a popular site.
2. Ship a second profile (WS + CDN) as fallback.
3. Swap the generic geo assets for Chocolate4U's Iran dataset.
4. Re-encrypt the bundle; no app change required.

**Phase B — small client changes**
5. Default `fragment = 1-3` when the profile has `security: none` (config renderer + one test).
6. Detect a failed health probe and automatically retry with the fallback profile (the health
   gate added in 0.10.0 already produces the signal).
7. Show which profile is active and why it failed (already partially in the log).

**Phase C — larger**
8. XHTTP transport with a matching server.
9. AmneziaWG as a third path for UDP-tolerant networks.
10. Optional observatory-style latency ranking across profiles.

---

## 4.6 Answers to the ten questions

1. **REALITY default?** Yes — but it is a server decision. The client needs no change.
2. **Fragment default?** Yes, but as `1-3`, not `tlshello`. `tlshello` is inert on a non-TLS
   profile and actively breaks REALITY.
3. **Mux default?** No. Keep off; it costs throughput on lossy links and adds a fingerprint.
4. **Better transport than WS/gRPC?** REALITY-on-TCP-443. WS+CDN remains the best *fallback*.
5. **Better DNS strategy?** The current one (FakeDNS + domestic resolver scoped to `.ir`) is
   already the recommended shape. Do not add DoH: it adds a dependency and a new failure mode.
6. **Auto-detect Iranian network conditions?** Not by fingerprinting the network. Detect by
   **outcome**: if the health probe fails twice, switch profiles. That is testable and honest.
7. **New post-2024 protocols?** AnyTLS and Mieru are the notable ones, but neither is in Xray;
   adopting them means a second core. Not worth it while REALITY works.
8. **Server-side setup?** REALITY on 443 with a real decoy site, a WS+CDN fallback, and an
   AmneziaWG endpoint for UDP-tolerant networks.
9. **Better geo datasets?** Chocolate4U `Iran-v2ray-rules` (Iran-focused, includes ads/malware),
   with Loyalsoldier as an alternative.
10. **Single highest-impact change?** Replace `security: none` with REALITY. Every other item in
    this report is a rounding error next to it.

---

## Sources

All URLs were requested during this task and returned HTTP 200 unless noted.

- https://xtls.github.io/en/config/transports/finalmask.html
- https://xtls.github.io/en/config/transports/reality.html
- https://xtls.github.io/en/config/transports/xhttp.html
- https://xtls.github.io/en/config/transports/tls.html
- https://xtls.github.io/en/config/transports/grpc.html
- https://xtls.github.io/en/config/transports/httpupgrade.html
- https://xtls.github.io/en/config/features/browser_dialer.html
- https://xtls.github.io/en/config/observatory.html
- https://xtls.github.io/en/config/outbound.html
- https://github.com/2dust/v2rayN/issues/10407
- https://github.com/XTLS/Xray-core/issues/2000
- https://github.com/XTLS/Xray-core/discussions/3269
- https://github.com/XTLS/Xray-examples
- https://github.com/Chocolate4U/Iran-v2ray-rules
- https://github.com/Loyalsoldier/v2ray-rules-dat
- https://github.com/net4people/bbs
- https://github.com/amnezia-vpn/amneziawg-go
- https://github.com/Psiphon-Labs/psiphon-tunnel-core
- https://github.com/SagerNet/sing-box
- https://github.com/MetaCubeX/mihomo
- https://github.com/enfein/mieru
- https://github.com/apernet/hysteria
- https://github.com/EAimTY/tuic
- https://sing-box.sagernet.org/configuration/outbound/anytls/
- https://sing-box.sagernet.org/configuration/outbound/hysteria2/
- https://github.com/2dust/v2rayNG
- https://github.com/patterniha/PattNG
- https://github.com/MatsuriDayo/NekoBoxForAndroid
- https://github.com/hiddify/hiddify-app
- https://www.samnet.dev/learn/guides/hysteria2-setup/
- https://plisio.net/cybersecurity/vless-protocol
- https://www.reddit.com/r/dumbclub/comments/1gfj6ip/how_to_bypass_block_on_hysteria2_connection/

## Method note

Maintenance and licence data in the scoring table came from the GitHub REST API on 2026-10-30
(`stargazers_count`, `license.spdx_id`, `pushed_at`, `archived`), not from memory. Effect claims
are labelled as community reports where no controlled measurement exists; none were reproduced
here, and none of them replace a device test on an Iranian network.
