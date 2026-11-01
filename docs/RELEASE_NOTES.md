# Release notes — Mohammad (`ir.mohammad.vpn`)

Only user-visible and operator-visible changes are listed. Anything that could not be proven in this
workspace is marked `NOT VERIFIED`.

---

## 0.15.4 — real DNS profiles import, and a key rule that was wrong

### Fixed
- **The key-length rule rejected every real profile.** The client hashes whatever key it is given
  (SHA-256 for AES-256/ChaCha20, MD5 for AES-128); only the *server's* key file has a fixed size.
  Requiring 16/24/32 characters here refused the 64-character hex keys that public servers hand out.
  Now only an empty key or one containing whitespace is refused.

### Added
- `masterdns://<base64>` links (the WhiteDNS export format) import into a DNS profile.
- `cottendns://` and `soar://` are recognised and refused **by name**: they need engines this build
  does not ship, and a profile that cannot connect is worse than a clear message.

### Checked against public servers
Three public WhiteDNS profiles were tried from this host with the matching engine — ours for
`masterdns`, and CottenDns built from its own source for `cottendns`. Both were rejected at MTU
discovery with no answer from the authoritative server, while that server answers SOA queries in
0.13 s. The same client and the same generated config against a server on this machine establish a
session and carry traffic (`204` in 0.30 s). So the code path is sound; those public endpoints did not
accept this host.

---

## 0.15.3 — a DNS profile can no longer hijack the Mode 1 dial

### Fixed
- Pressing the big dial in Mode 1 while a DNS profile was selected started a DNS engine, which then
  failed with no server — the symptom looked identical to "the app is broken". Mode 1 now picks an
  Xray profile (or refuses and says why), and the selection rule lives in one tested place.
- Mode 2's profile list and selection use the same rule, so the two screens can no longer disagree.

---

## 0.15.2 — Mode 2, reviewed (M6-UI review)

Eight defects found by reading the mode code against its own rules, all fixed:

- The version under the title was a literal, so it stayed `v0.15.0` after a build.
- Switching Mode 1 ⇄ Mode 2 left the previous session running — two cores, which is exactly what
  D-105 forbids. Switching now stops it.
- The tile and the widget could start a tunnel beside a live DNS proxy. All three entry points now
  stop the proxy first.
- Starting the proxy while a tunnel was up had the same problem from the other side.
- A running proxy was ignored by the delay test, which started a second engine instead of measuring
  through the live port.
- Mode 1 listed DNS profiles. The two environments now hold their own kinds.
- The DNS screen had no way to add a profile; it now has PASTE, and importing a `dns://` link in
  Mode 1 switches to Mode 2 automatically.
- Stopping the proxy used `startService`, which Android 8+ refuses from the background. It uses
  `stopService`, and a second START while an engine is alive is ignored instead of racing for the port.

---

## 0.15.1 — Mode 2 (M6-UI)

### Added
- **A chevron in the header.** Tap it: Mode 1 (Xray) and Mode 2 (DNS tunnel). The active mode is named
  under the title, so the environment on screen is never ambiguous.
- **Mode 2 screen.** Its own palette, its own connect orb, its own profile list (DNS only), its own log
  tail.
- **PROXY / FULL TUNNEL.** Proxy mode starts the engine and publishes `127.0.0.1:10809` — no VPN
  permission, no routes, nothing else on the phone touched. Full tunnel uses the same path Mode 1
  already had.

### Notes
- Switching mode, or switching proxy/full-tunnel, stops what the other one started.
- The proxy is a foreground service of type `specialUse`: the app still asks for five permissions.

---

## 0.15.0 — DNS tunnel as a second path (M6-DNS)

### Added
- **A DNS-tunnel profile type.** Where a network permits nothing but DNS on port 53, this path carries
  the traffic inside DNS queries to a domain whose nameserver is your own server.
- Engine: MasterDnsVPN (MIT), built from a pinned commit and shipped inside the APK. It runs as a child
  process behind the same bridge as Xray, so nothing else in the app changed.
- `dns://domain?key=…&method=…&resolvers=a,b` imports into a profile, validated before use.

### Notes
- It is slow by design — tens to hundreds of KB/s. Use it when nothing else connects, not for video.
- The key length must match the cipher (16/24/32); a mismatch is now caught at bundle load instead of
  turning into "connecting…" forever.
- The key is never written to the log.

### Verified
- Engine and server, both built here, carried an HTTP request through a real session: **204 in 0.30 s**.
- **Not verified:** anything on a device, or through a real ISP resolver. That needs a delegated domain
  and a VPS that accepts UDP 53.

---

## 0.14.7 — test every profile at once (M4-U3)

### Added
- **TEST ALL n.** One tap measures every profile in turn and fills each row's chip as it finishes.
  Sequential by design; profiles that cannot be measured while another one is live are marked as
  skipped, never given a number that was not taken.
- `xhttp` / `splithttp` links import with their own settings block, and `fm` is now honoured on
  trojan links as well.

### Fixed
- A REALITY link carrying a fragment mask is imported without the mask (and with a log line) instead
  of producing a handshake that can never complete.
- The renderer writes `finalmask`, the core's exact key, so a profile mask and the global fragment
  toggle cannot disagree about spelling.

---

## 0.14.6 — imports are faithful now (M4-U2)

### Fixed
- **Post-quantum VLESS links were broken.** `encryption=mlkem768x25519plus…` was replaced with
  `none`, so the server rejected the handshake. It is carried through now.
- **`httpupgrade` was imported as WebSocket.** It has its own settings block and its host is a
  field, not a header. The upgrade never completed before; it does now.
- **`fm=` was dropped.** The finalMask encoded in the link (usually the ClientHello fragment) now
  becomes the profile's finalMask.
- **Links copied without newlines** are split correctly instead of importing only the first.

### Verified
- Three real links that failed in 0.14.5: rendered by the app's own config builder, accepted by the
  core, and answering **HTTP 204** through the server in 1.08–1.32 s. Measured from a host outside
  Iran; whether they pass Iranian filtering is still a device question.

---

## 0.14.5 — paste any config, measure what it really does (M4-U)

### Added
- **PASTE** in the profiles section. One link or a whole column of them: `vless://`, `vmess://`,
  `trojan://`, `ss://`. A broken line is reported and the rest still import.
- **Imported profiles persist**, are selectable like operator profiles, and pasting the same link
  twice replaces it instead of adding a duplicate.
- **A delay chip on every row.** The number is a real round trip through the proxy, not a TCP
  handshake to the server.

### Changed
- The old ping measured `connect()` to the server, which succeeds for a blocked account, an expired
  key, or a CDN edge that never reaches the origin. It reported 40 ms and carried nothing. It is
  gone, replaced by the measurement above.

### Not verified
- The delays themselves against a real server, and the clipboard flow on a device.

---

## 0.14.4 — `merge`, per-profile `finalMask`, `unsafe` (M4-C, second pass)

### Added
- **`merge` transport mask** — coalesces the first outbound writes into one full-size segment, so a
  filter that keys on "a small first packet" sees nothing to key on. Verified locally: three small
  writes arrived as one.
- **`finalMask` per profile** — set `merge`, `fragment` or any mask the core ships, without editing
  the core. Invalid types and out-of-range numbers are refused at bundle load with the field named.
- **`fingerprint: unsafe`** — accepted for TLS (the core uses it to mean "no uTLS"), refused for
  REALITY (the core refuses it there too).

### Changed
- `merge` is allowed with REALITY; `fragment` is still refused there, because splitting the
  ClientHello breaks REALITY's authentication.

### Not verified
- Whether `merge` or `oob` defeats a specific ISP, and whether either survives a real line end to end.
  That needs a device.

---

## 0.14.3 — the app can start with the operator's configuration (M4-C)

### Fixed
- **The core refused the shipped profile.** Upstream Xray rejects unencrypted VLESS/Trojan outbounds to
  public addresses. Every launch with the current bundle failed at config load. That guard is now a
  warning: the profile starts, and the log console prints `unencrypted outbound` each time so the
  trade-off is visible. Moving the server to REALITY remains the real fix.

### Added
- `dialMode: "oob.<b1>,<b2>,..."` per profile — appends a TCP urgent byte to the first writes so a
  middlebox that mishandles the urgent pointer desynchronises on the handshake. Verified locally that
  the peer receives the payload intact.
- `cipherSuites` per profile, applied only when TLS is on.

### Not verified
- Whether `oob` defeats any specific ISP. It needs a device on an Iranian line.

---

## 0.14.2 — tile reliability, live notification, working telemetry (M4-N)

Three reported defects, fixed at the cause.

### Fixed
- **Quick-settings tile sometimes stayed inactive after connecting.** The tile only refreshed when the
  shade opened, so a state change made while the shade was closed was never shown. It now updates from
  the same state stream as the app, immediately, with a five-second safety refresh.
- **The in-app download/upload tiles looked empty.** The counters were correct; the display rounded
  5 MB to "0.00 GB". Values now pick their own unit ("5.0 MB", "1.2 GB"), and show "—" when the
  counters cannot be read instead of a misleading zero.
- **Totals carried over between sessions.** They reset when a new connection starts.

### Added
- A persistent notification with the real state, live download/upload totals updated every second, and
  two actions: Disconnect, and Pause/Resume.
- Pause disconnects the tunnel and keeps the notification with a Resume button. A true "stop forwarding
  but keep the tunnel" pause would need a data-path change that cannot be verified without a device, so
  it is deliberately not shipped (D-90).

### Unchanged
- No new permissions; the APK still requests the same five.
- No change to the data path, MTU, REALITY handling or validation.
- English only.

### Not verified
- Shade rendering, notification swipe behaviour and Doze: no device in this workspace.

---

## 0.14.1 — home-screen widget and quick-settings tile (M4-W)

Adds two ways to connect without opening the app. **No change to VPN behaviour**: the data path, MTU,
REALITY handling and validation are byte-identical to 0.14.0.

### Added
- Home-screen widget, resizable from 2x1 to 4x1. Shows the real connection state with an icon, a
  title and an action; tapping it connects or disconnects.
- Quick-settings tile with the same three states as the system expects: active, inactive, unavailable.
- Both surfaces show green only when the tunnel has answered a real SOCKS probe — the same rule as the
  in-app dial, because they read the same state.
- Both surfaces survive process death: a tap from a cold process starts the service, and a widget
  rendered before the app is opened reads the saved profile instead of asking the user to set up again.

### Not changed
- No new permissions. The APK still requests exactly the five it requested in 0.14.0.
- No new dependency. The widget uses RemoteViews rather than Glance.
- No endpoint, profile name, UUID or key is displayed on either surface.

### Known limitation
- Long-press on a quick-settings tile opens the app's system info page; Android gives apps no
  callback for that gesture. Tapping the tile opens the app when it cannot toggle (no profile, or no
  VPN consent), which is the closest sanctioned behaviour (D-87).
- AmneziaWG still reports connected on process start rather than after a probe (D-86). It remains
  `NOT VERIFIED` and is not recommended for users until its own milestone.

### Not verified
- Real launcher rendering, system tile binding, and Doze/reboot behaviour: no device in this
  workspace.

---

## 0.14.0 — REALITY + XTLS Vision (M3)

**This release must not be deployed alone. It ships together with the 0.15.0 fallback path (D-49).**

Why: REALITY is the strongest client-side camouflage available to us, but its server IPs are subject
to graylisting in Iran (Xray discussions #2054, #2768, #3269; `docs/RESEARCH_CATALOG.md` entry 86).
A build that offers REALITY as the only path replaces "one weak path" with "one strong path that
dies". The fallback policy in 0.15.0 is what makes REALITY survivable. Operators who cannot ship
0.15.0 in the same window should keep 0.14.0 on the existing WebSocket profile.

### Added
- `realitySettings` is now fully validated before any core starts: `serverName` must be a plain
  hostname, `publicKey` must be base64url without padding that decodes to 32 bytes, `shortId` must be
  hexadecimal of at most 16 characters, and an optional `spiderX` must start with `/`.
- `flow: xtls-rprx-vision` is rendered onto every VLESS user object in the outbound.
- A profile-level `flow` field, so the operator sets it once instead of editing transport JSON.

### Changed
- `xtls-rprx-vision` is accepted **only** with `security: reality` (SPEC TLS-4). The pinned core also
  accepts it with plain TLS; this build is deliberately narrower. See D-75.
- `security: reality` without a `realitySettings` object is rejected at load time instead of producing
  a config the core cannot use.
- `finalmask.fragment` is suppressed for REALITY profiles at render time, and the log records
  `fragment suppressed: it would break REALITY`. A bundle that declares both is rejected outright.

### Fixed
- `hellogolang` was accepted as a fingerprint in 0.13.0. The core's config layer rejects it
  (`infra/conf/transport_security.go`), so it is now rejected here too (D-73).

### Not verified
- That REALITY survives any specific Iranian ISP. Requires a device on a real line.
- That Vision improves throughput on a real path. Requires a device.
- `AmneziaWG` remains `NOT VERIFIED`.
