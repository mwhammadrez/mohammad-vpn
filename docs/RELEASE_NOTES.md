# Release notes — Mohammad (`ir.mohammad.vpn`)

Only user-visible and operator-visible changes are listed. Anything that could not be proven in this
workspace is marked `NOT VERIFIED`.

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
