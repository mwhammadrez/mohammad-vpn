# In-app self-test — how to produce device evidence without adb

Version 0.5.0 adds a **Run self-test** button. It performs the Phase C checks on the
device and produces a JSON report you can copy or share.

## Steps

1. Install `Mohammad-0.5.0-debug-arm64.apk` (allow install from unknown sources).
2. **Before connecting**, open the app once and leave it a few seconds. It records the
   carrier egress IP (`baseline egress captured` in the log). This is what makes the
   proof possible: the test compares the IP before and after.
3. Tap **Connect**, grant the VPN permission, wait for `Connected`.
4. Browse something for a few seconds (optional, makes the counters more interesting).
5. Tap **Run self-test**. It takes ~15 s and ends with `self-test finished in … ms`.
6. Tap **Copy report** (or **Share** → any app you can send to me from).

## What it collects

| Field | Meaning | Maps to Phase C row |
|---|---|---|
| `app.version`, `device.model/android/sdk/abis` | build + phone identity | Device |
| `tunnel.state/connected/core/protocol/mtu/ipv6Enabled/dnsServers` | live tunnel state | Connect |
| `dns[]` | three hostnames resolved with timings and address counts | DNS behaviour |
| `egress.baseline` | carrier IP captured before connecting | — |
| `egress.current` | public IP while connected | Real internet through tunnel |
| `egress.changed` / `conclusion` | `tunnel` if the IP changed | Real internet through tunnel |
| `ipv6.localAddressPresent/egress/possibleLeak` | IPv6 presence and whether it escapes | IPv6 leak |
| `traffic.deltaDownBytes` | bytes counted by the core during a 1 MiB download | Counters |
| `pingMs` | TCP handshake latency to the server | Ping test |
| `log[]` | last 200 redacted log lines | Disconnect/handover/revoke evidence |
| `durationMs` | total runtime | — |

**The report contains your public IP.** That is the point — it is the evidence. It does
**not** contain the profile UUID, the server host, keys, or the bundle password; those are
redacted before the report is written.

## Interpreting it

- `egress.changed = true` → traffic really left through the server.
- `egress.changed = false` with `tunnel.connected = true` → the tunnel is up but not
  carrying traffic: a real bug, send the report.
- `egress.conclusion = "inconclusive"` → the baseline was not captured (app started while
  already connected). Disconnect, force-stop, reopen, then repeat.
- `ipv6.possibleLeak = true` → IPv6 is escaping while IPv4 is tunnelled.

## After the report

Repeat once more for the remaining rows:

| Row | How |
|---|---|
| Disconnect | tap Disconnect, then Run self-test again while disconnected; `log[]` must show `tun fd closed` |
| Network change | while connected, toggle Wi-Fi/LTE, wait 20 s, Run self-test |
| Force-stop | connect, then `Settings → Apps → Mohammad → Force stop`, reopen, Run self-test |
| VPN revoke | connect, then `Settings → Network → VPN → Forget`, reopen, Run self-test |
| Redaction | Copy the log; search for the UUID prefix and the server host — neither may appear |
