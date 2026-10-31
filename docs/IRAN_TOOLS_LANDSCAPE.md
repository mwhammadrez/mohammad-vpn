# Iran-specific circumvention tools — landscape and verdict

**Date:** 2026-10-31 · **Status:** read-only research · **Entries:** 22
**Question:** which tools are actually built *for Iran*, how do they get around the filter, and is any
of it useful to Mohammad rather than merely interesting?

Stars and licences were read from the GitHub API on 2026-10-31. Iranian services that are not on
GitHub are described from their own sites and from the community lists that document their resolvers.

---

## 0. One distinction that changes every verdict

Iranian "filter breakers" are three different products that users lump together:

| Kind | What it actually does | Defeats DPI? | Protects traffic? |
|---|---|---|---|
| **DNS changer** (Shecan, 403, Electro, Radar, Begzar) | swaps the resolver so a domain returns a reachable IP | no | no |
| **WARP/Psiphon client** (Oblivion, warp-plus, MahsaNG's WARP modes) | encapsulates traffic to Cloudflare or Psiphon | partly | yes |
| **Proxy client** (MahsaNG, NikaNG, Hiddify, v2rayNG) | carries traffic to an operator's server over a camouflage transport | yes | yes |

Most of the famous Iranian names are in the first row and are about **sanctions**, not censorship:
they make `play.google.com` or a developer site resolve to an address that is not geo-blocked. They do
nothing for a user whose TLS is being fingerprinted. Confusing the two is the most common mistake in
this space, including in the brief that asked for this document.

---

## A. Iranian client apps

### A1. MahsaNG
**Link** https://mahsanet.com/app/mahsang · source https://github.com/MrAli-Code/MahsaNG
**265★ · GPL-3.0 · last push 2024-02-06 (the distributed build is newer)**
**What** The reference "Iranian" client: a v2rayNG fork running a customised Xray ("Mahsa-Core") with a
built-in Fragment panel, plus free configurations distributed by MahsaServer.
**How it bypasses** TLS ClientHello fragmentation tuned per ISP, plus a rotating pool of donated
servers so one blocked IP does not end the session.
**Fit for Mohammad** **High at the technique level, zero at the code level.** Fragment we already have;
what we lack is (a) *presets tuned per ISP* and (b) *rotation*. Both are on our roadmap as M4/M7.
**Licence** GPL-3.0 → technique only.
**Risk** Its free-config model means the operator trusts a third party's servers. Ours is the opposite
by design.

### A2. NikaNG
**Link** https://mahsanet.com/app/nikang
**What** The lean sibling: v2rayNG on Mahsa-Core with WARP, WireGuard-Noise and Fragment, no bundled
configurations.
**How** Same fragmentation, plus WARP as a transport, and WireGuard with noise padding.
**Fit** Medium: it is the closest published example of "a client that offers fragment + WARP + a normal
proxy in one binary" — exactly the shape M6 will have.
**Licence** Same project family, GPL-3.0.
**Risk** No configurations means the user must bring their own; not a product we can copy.

### A3. Segment (MahsaNet SDK)
**Link** https://mahsanet.com/app/segment
**What** An open-source codebase for building branded VPN apps (Android/iOS/desktop) on their stack.
**How** Modular client shell + config distribution + UI.
**Fit** Low. It is a competing application architecture, and adopting it would replace our state
machine, widget and telemetry with theirs.
**Licence** Not stated on the site; treat as no-reuse until read.
**Risk** Vendor lock-in to MahsaServer.

### A4. Hiddify
**Link** https://github.com/hiddify/hiddify-app · **33,547★ · NOASSERTION**
**What** Multi-protocol client (sing-box + Xray + TUIC + Hysteria2 + REALITY) with subscription import.
**How** Auto-selects a working profile by latency; ships REALITY defaults.
**Fit** Already covered in the main catalog (entry 3). Iranian-authored, widely used, and the best
public example of "subscription + auto-select".
**Licence** NOASSERTION → technique only.

### A5. v2rayNG
**Link** https://github.com/2dust/v2rayNG · **65,044★ · GPL-3.0**
**What** The baseline client every Iranian user has tried.
**How** Xray core behind a `VpnService`.
**Fit** Reference only; already catalogued (entry 1).

### A6. NekoBox
**Link** https://github.com/MatsuriDayo/NekoBoxForAndroid · **23,336★ · NOASSERTION**
**Fit** Reference only; catalogued as entry 2.

### A7. NiREvil/vless (subscription channel)
**Link** https://github.com/NiREvil/vless · **1,362★ · GPL-3.0 · pushed 2026-10-31**
**What** Not an app: a daily-updated repository of free VLESS/REALITY subscription links, widely
mirrored in Iranian Telegram channels.
**How** Distribution, not technology: thousands of users import the same links, which is why the
servers behind them die within hours and why IP harvesting works so well for the censor.
**Fit** **Negative lesson.** It is the clearest evidence that a public endpoint pool burns fast —
reinforces D-78 (never publish endpoints) and the encrypted-bundle model.
**Licence** GPL-3.0 (text/configs).

### A8. Everyday-VPN (config channel)
**Link** https://github.com/Everyday-VPN/Everyday-VPN · **154★ · MIT · pushed 2025-04-22**
**What** Daily free configurations, same economics as A7 at smaller scale.
**Fit** Same negative lesson.

---

## B. WARP and Psiphon hybrids

### B1. Oblivion
**Link** https://github.com/bepass-org/oblivion · **4,889★ · MIT**
**What** Unofficial Android WARP client, on the Play Store, explicitly built for Iran.
**How** It registers a free WARP identity, then lets the user place **Psiphon and Tor either before,
after, or around WARP** ("off / after WARP / before WARP / alone"), so a blocked WARP endpoint can be
reached through Psiphon and vice versa. Data path is WARP inside `hev-socks5-tunnel`.
**Fit** **High interest for M6.** It is the only widely used Iranian tool that treats *fallback* as a
first-class composition: transport A wrapped in transport B. That is a stronger version of our planned
"second path", and it costs no server IP of our own.
**Licence** MIT — reusable with attribution.
**Risk** WARP is throttled or blocked at times in Iran; Psiphon is slow and its licence (GPL) does not
mix with ours. The *composition* is the idea to steal, not the components.

### B2. warp-plus
**Link** https://github.com/bepass-org/warp-plus · **1,972★ · MIT**
**What** "Warp + Psiphon, an anti-censorship utility for Iran" — the CLI/desktop ancestor of Oblivion.
**How** Same idea without the Android shell: obtain a WARP config, scan for a reachable endpoint,
fall back to Psiphon if the scan fails.
**Fit** Medium: its endpoint *scanning* is the mechanism that makes WARP usable behind a filter.
**Licence** MIT.
**Risk** Scanning hundreds of endpoints is exactly the behaviour that gets a client fingerprinted.

### B3. bepass
**Link** https://github.com/bepass-org/bepass · **394★ · MIT**
**What** "A simple DPI bypass tool written in Go": a local proxy with fragmentation and TLS tricks.
**Fit** Low — our fragment support already covers it; this is the same idea in a different shell.
**Licence** MIT.

### B4. bepass-worker / bepass-relay
**Link** https://github.com/bepass-org/bepass-worker · **142★ · MIT**
**What** A Cloudflare Worker that acts as a relay for the bepass proxy.
**How** The client talks to a Worker hostname; the Worker forwards to the real endpoint, so the client
never sees the origin IP.
**Fit** Medium — same family as catalog entry 73 (CDN relay), which is planned for M6.
**Risk** Cloudflare's 2025 Worker limits (noted on MahsaNet's own blog) make this less reliable than it
was; terms-of-service risk remains.

---

## C. DNS-only services (the ones users name most)

These resolve domains; they do not carry traffic. Their value to Mohammad is **zero for censorship**
and **non-trivial for sanctions** (Google Play, developer sites).

| Service | Resolvers (public, from the community lists) | What it is for | Fit for Mohammad |
|---|---|---|---|
| **Shecan** | 178.22.122.100 / 185.51.200.2 | sanctions workaround + faster local DNS | none — we already split `.ir` |
| **403.online** | 10.202.10.202 / 10.202.10.102 | sanctioned-service access | none |
| **Electro** | 78.157.42.100 / .101 | same | none |
| **Radar** | 10.202.10.10 / .11 | gaming latency + some unblocking | none |
| **Begzar** | 185.55.225.25 / 185.55.226.26 | unblocking popular sites | none |
| **Shatel / Shelter / Beshkan** | various | ISP-run resolvers | none |

**Why none of them help us:** a DNS changer only changes which address a name maps to. Once the
connection starts, the censor sees the same TLS handshake it always saw. Our problem is at the
transport layer, not the resolver. We already route domestic domains directly (`.ir`) and resolve the
rest inside the tunnel; switching to an Iranian resolver would *leak* more, not less.

**Exception worth noting:** 403/Shecan matter for *distribution* — an Iranian user cannot always reach
Google Play or GitHub without them, which is why our operator's installs may depend on them.

---

## D. Iranian infrastructure and services

### D1. MahsaServer
**Link** https://mahsaserver.com/
**What** Free configuration distribution at scale, with per-user assignment.
**How** Users get a server from a pool; the pool rotates as IPs die.
**Fit** **Conceptually important for M7.** It is the Iranian ecosystem's answer to "how do I change
configs for thousands of users": a distribution service, not an app update.
**Risk** Central trust; a compromised distribution poisons every client.

### D2. Radar
**Link** https://mahsaserver.com/radar
**What** Live monitoring of Iranian network conditions.
**Fit** Medium: the operator's equivalent would be a diagnostics export from our app (the M1
`mtu resolved` line and M4's path logs are the beginning of one).
**Risk** None to us; it is a public data source.

### D3. Filternet
**Link** https://filternet.mahsanet.com/
**What** A web gateway for people who cannot install anything (during shutdowns).
**Fit** Low — different product shape, but it is the answer to "what do users do when the app cannot
connect at all", which is a state our UI does not have.

### D4. CompassVPN
**Link** https://compassvpn.org/
**What** Self-hosted VPN deployment (their answer to "run your own server").
**Fit** Low; the operator already has a server.

---

## E. What is actually transferable

| Technique | Seen in | Verdict | Where it would land |
|---|---|---|---|
| Fragment presets tuned per ISP | MahsaNG, NikaNG | **Adopt (later)** | after M4; needs ISP measurements first |
| Transport composition (A wrapped in B) | Oblivion | **Adopt as a design** | M6 second path |
| Endpoint rotation without a new APK | MahsaServer, Hiddify | **Adopt** | M7 remote config |
| Public config channels | NiREvil, Everyday-VPN | **Reject** | burns endpoints; D-78 exists because of this |
| DNS switching | Shecan/403/Electro/Radar | **Reject** | solves sanctions, not censorship |
| WARP as a free fallback endpoint | Oblivion, warp-plus | **Consider** | M6, if the operator accepts Cloudflare dependency |
| Self-hosted app SDK | Segment | **Reject** | would replace our architecture |
| ISP-level monitoring | Radar | **Adapt** | diagnostics export, M5/M7 |

### The three things this survey changes

1. **M6 gets a second shape.** "Second path" no longer means only "another VPS". Oblivion shows that
   wrapping one transport in another (WARP inside Psiphon, or a Worker in front of the origin) is what
   Iranian users actually run when a single hop dies. Worth evaluating before adding a server.
2. **Fragment presets are an ISP problem, not a code problem.** MahsaNG's advantage is not that it
   fragments; ours does too. It is that its presets were tuned against MCI/Irancell/Rightel
   measurements. We cannot tune anything without device data — which strengthens the case for the
   diagnostics export and for M-DEV.
3. **The famous Iranian "filter breakers" are mostly DNS changers.** They are not competitors and not
   references. Copying them would not help a single user whose handshake is being fingerprinted.

**Nothing here justifies a new dependency.** The two adoptable ideas (composition, rotation) are
architecture, not libraries, and both fit the milestones already planned.
