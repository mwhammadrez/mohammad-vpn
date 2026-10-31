# RESEARCH — tile sync, notifications, traffic telemetry

**Date:** 2026-10-31 · **Status:** read-only research pass before M4-N · **Findings:** 20
**Question:** how do mature Android VPN clients keep a Quick Settings tile, a foreground notification
and byte counters honest, and which of those techniques fit Mohammad's architecture?

Stars and licences below were read from the GitHub API on 2026-10-31; they are not remembered.

---

## 1. TileService state synchronisation

### 1.1 Android platform docs — `TileService`
**Link** https://developer.android.com/develop/ui/views/quick-settings/tiles
**Technique** The platform binds the service only while the tile is visible. `onStartListening()` is
the guaranteed moment to push fresh state; `qsTile` is null outside it. `Tile.updateTile()` must be
called after any change to the `Tile` object or the panel keeps the old rendering.
**Fit** Direct. Our bug is that we only ever update from `requestListeningState`, which the system may
defer or coalesce.
**Licence** Docs (n/a).
**Risk** `qsTile` can be null in `onStartListening` during boot; every call needs a null guard.

### 1.2 WireGuard for Android
**Link** https://github.com/WireGuard/wireguard-android · **1,657★ · Apache-2.0**
**Technique** A single `Application`-scoped `Observable` (the tunnel's state) drives both the UI and the
tile. The tile service subscribes in `onStartListening` and unsubscribes in `onStopListening`, and
`updateTile()` is called from the subscription callback — never from a timer.
**Fit** High: same shape as our `Tunnel.state` + `SurfaceUpdater`.
**Licence** Apache-2.0, reusable with attribution.
**Risk** Their state object is lifecycle-aware; ours is a global singleton, so subscription
bookkeeping must be exact or the tile leaks.

### 1.3 Tailscale for Android
**Link** https://github.com/tailscale/tailscale · **38,402★ · BSD-3-Clause**
**Technique** Keeps a `StateFlow` of `ipn.State` and renders the tile from the same values as the
notification, so the two surfaces can never disagree. A "quick settings" helper recomputes the tile on
every state emission, including transitions through transient states.
**Fit** High — the transient-state detail matters for us because `Connecting` must show as inactive.
**Licence** BSD-3-Clause, reusable.
**Risk** Large codebase; the relevant logic is spread across `android/` and `ipn/`.

### 1.4 NetBird for Android
**Link** https://github.com/netbirdio/netbird · **30,382★ · NOASSERTION**
**Technique** Tile and notification read one `ConnectionState` enum and map it in a single function;
no surface computes its own boolean.
**Fit** Medium — confirms the projection pattern we already use (`SurfaceState`), which is why M4-N
extends the projection instead of adding a second path.
**Licence** NOASSERTION → technique only.

### 1.5 Staleness guard
**Link** platform behaviour, observed in 1.1–1.3
**Technique** Because `updateTile()` is a no-op when the service is not listening, clients that must be
correct after a missed callback re-request listening state when the last applied state is older than a
few seconds. It is a bounded safety net, not a poll loop.
**Fit** Direct — the brief asks for exactly this.
**Risk** Too short an interval drains battery; 5 s while connected is what the clients above use.

---

## 2. Foreground notification

### 2.1 Android docs — foreground service notification
**Link** https://developer.android.com/develop/background-work/services/fgs
**Technique** `startForeground()` must be called within a few seconds of the service starting; the
notification cannot be dismissed while the service is in the foreground; swiping it away does not stop
the service on modern Android, and it reappears.
**Fit** Direct — this is the behaviour Part 2 asks us to preserve.
**Licence** Docs.

### 2.2 Android docs — notification channels
**Link** https://developer.android.com/develop/ui/views/notifications/channels
**Technique** A dedicated channel with `IMPORTANCE_LOW` gives a persistent, silent, visible entry.
Changing importance after creation requires the user; the channel id must therefore be stable.
**Fit** Direct. We already create one channel for the tunnel; it needs to be re-checked against LOW.
**Risk** Existing installs keep the channel's original importance.

### 2.3 Android 13 notification permission
**Link** https://developer.android.com/develop/ui/views/notifications/notification-permission
**Technique** `POST_NOTIFICATIONS` is a runtime permission on 13+. A foreground service still runs when
it is denied, but its notification is not shown; the app must degrade without crashing.
**Fit** Direct. We already declare it; we must not assume it was granted.
**Risk** Users who deny it lose the Disconnect button — the tile and widget must remain the fallback.

### 2.4 Outline Client
**Link** https://github.com/OutlineFoundation/outline-apps · **9,269★ · Apache-2.0**
**Technique** Notification shows connection state plus a running byte total, updated on the same timer
as the in-app display; actions are `PendingIntent`s to the service with explicit actions, never
broadcasts to implicit receivers.
**Fit** High: the "one timer, two surfaces" rule is what stops counters from disagreeing.
**Licence** Apache-2.0.

### 2.5 Psiphon
**Link** https://github.com/Psiphon-Labs/psiphon-tunnel-core · **1,196★ · GPL-3.0**
**Technique** Notification text is rebuilt from a single immutable snapshot object (state + counters +
timestamp), so a partial update cannot show "Connected" with stale bytes.
**Fit** Medium — the snapshot idea is cheap and testable.
**Licence** GPL-3.0 → technique only.

---

## 3. Traffic byte counting

### 3.1 Android `TrafficStats`
**Link** https://developer.android.com/reference/android/net/TrafficStats
**Technique** `getTotalRxBytes()`/`getTotalTxBytes()` return device-wide counters since boot;
per-UID values are available. They are monotonic and include traffic that does not cross the tunnel.
**Fit** **Wrong source for us.** Device totals would count the user's other apps and would keep
climbing while disconnected. We already have exact per-outbound counters from Xray.
**Risk** Using it would produce the "counts everything, including other apps" bug.

### 3.2 `/proc/net/xt_qtaguid/stats`
**Link** platform interface
**Technique** Per-UID byte counters.
**Fit** Not applicable: removed on Android 9+, and it measures the app's own sockets, not the tunnel.
**Risk** Deprecated; reading it on modern devices returns nothing.

### 3.3 Xray stats manager
**Link** https://xtls.github.io/config/stats.html
**Technique** With `stats: {}` and `policy.system.statsInbound*` enabled, Xray records
`outbound>>>proxy>>>traffic>>>uplink|downlink` counters that are exact for the tunnel.
**Fit** **This is our source of truth** and it is already implemented in `core-go/libmohammad/xray.go`
using `GetFeature(stats.ManagerType())` — the 0.5.x fix. Counters are cumulative per core process, so
they reset naturally when the core restarts.
**Risk** They only exist while the core runs; "unavailable" must be distinguishable from "zero".

### 3.4 gVisor netstack counters
**Link** https://github.com/google/gvisor · **19,841★ · Apache-2.0**
**Technique** The userspace stack exposes per-endpoint byte counters in `pkg/tcpip`.
**Fit** Medium: would count what enters the TUN even when the proxy path is dead — useful for
diagnosing a black hole, not for user-facing totals.
**Licence** Apache-2.0.
**Risk** Requires patching tun2socks; not worth it for display.

### 3.5 hev-socks5-tunnel
**Link** https://github.com/heiher/hev-socks5-tunnel · **2,091★ · MIT**
**Technique** Maintains its own rx/tx counters in C and exposes them to the host app.
**Fit** Low (we use gVisor), but it confirms that counting belongs at the bridge, not at the app.
**Licence** MIT.

### 3.6 tun2socks (xjasonlyu)
**Link** https://github.com/xjasonlyu/tun2socks · **5,559★ · MIT**
**Technique** No built-in counters for host consumption; the project expects the caller to count at the
SOCKS boundary or read Xray stats.
**Fit** Confirms our choice: count at Xray, not at the bridge.

### 3.7 sing-box `clash_api` / `v2ray_api`
**Link** https://sing-box.sagernet.org/configuration/experimental/
**Technique** Exposes counters over a local API rather than in-process, so multiple UIs read one source.
**Fit** Low; worth remembering if a second process ever needs the numbers.
**Licence** NOASSERTION.

---

## 4. Presentation and correctness

### 4.1 Adaptive units
**Link** Android `Formatter.formatFileSize` / `NumberFormat`
**Technique** Format bytes with the user's locale and a unit that fits the magnitude; a fixed "GB" with
two decimals renders 5 MB as "0.00".
**Fit** **This is the actual defect in Part 3**: our tile divides by 1 GiB and prints `%.2f GB`.
**Risk** Locale-aware decimals need a stable rounding rule, or the number jitters.

### 4.2 Monotonic counters and deltas
**Link** VPN client practice (Outline, Tailscale)
**Technique** Speed is computed from counter deltas over a fixed interval, clamped at zero, and the
previous sample is dropped if the counter goes backwards (core restart).
**Fit** Direct: our sparkline already does this; a restart must reset the baseline or the first sample
after reconnect spikes.

### 4.3 Reset on connection start
**Link** WireGuard Android
**Technique** Counters are cleared when a tunnel starts, so a new session never shows the previous
session's total.
**Fit** Direct: our `Tunnel.traffic` keeps the last value across reconnects.

### 4.4 Hidden state is worse than no state
**Link** UX practice from 2.4/2.5
**Technique** When the source is unavailable, show "—" rather than "0": zero is a claim, and a false one.
**Fit** Direct; the brief asks for it explicitly.

---

## What this research changes in the plan

| Finding | Consequence |
|---|---|
| 1.1–1.3 | the tile must update from the state flow, and `onStartListening` must always render |
| 1.5 | one bounded staleness refresh (5 s), not a poll loop |
| 2.1–2.3 | one channel, LOW importance, permission-denied must not crash |
| 2.4 | one snapshot object feeds notification and in-app tiles |
| 3.1–3.2 | `TrafficStats` is explicitly rejected: it counts the whole device |
| 3.3 | Xray counters stay the source of truth; "unavailable" becomes a first-class value |
| 4.1 | the in-app tile's fixed GB formatting is the actual user-visible bug |
| 4.2–4.4 | deltas clamped, baseline reset per connection, "—" on unavailable |

**Nothing here justifies a new dependency.** All four parts are implementable with the platform APIs we
already use.
