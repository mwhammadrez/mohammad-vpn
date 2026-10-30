# Parity closure — configuration deltas vs the reference client

Source: operator-supplied black-box comparison (`COMPARISON_REPORT.md`, `FINAL_BEHAVIOR_REPORT.md`),
realme RMX3709 / Android 16. That report is also the first device evidence that the 0.7.3 data path
works: egress `95.135.125.40` matches the reference client, `tun2socks up` appears, and traffic
returns 3.83 s after airplane mode is switched off.

## Deltas and how each was closed

| # | Reference | Before | Now | Note |
|---|---|---|---|---|
| 1 | MTU 1500 | 1400 | **1500** | supersedes D-23; see risk note |
| 2 | `10.10.10.1/30` | pooled address (`10.0.0.1` in the run) | **10.10.10.1/30** | fixed, matches |
| 3 | 32 split routes, no default route, private/reserved excluded | `0.0.0.0/0` full tunnel | **45 split routes, no default** | see route-count note |
| 4 | single resolver `8.8.8.8` | `1.1.1.1` + `8.8.8.8` | **`8.8.8.8` only** | matches |
| 5 | one loopback listener `:10808` | `:10808` + `:10809` | **`:10808` only** | http inbound removed |
| 6 | agent underlying networks → `null` on loss, re-parented on restore | stale reference | **`setUnderlyingNetworks(null)` on loss, rebind on restore** | matches |

## Route-count note

The reference client exposes 32 routes; the minimal exact set that covers every public IPv4 prefix
while excluding `10/8`, `127/8`, `169.254/16`, `172.16/12`, `192.168/16` and `224/4` is 45 entries.
Matching the reference's *count* would mean leaving public space unrouted; the behaviour that matters
(no default route, LAN untouched, all public destinations tunnelled) is identical. `TunPlanTest`
asserts all three properties.

## Risk note — MTU 1500

D-23 recorded that 1500 failed on this device under the previous architecture (Xray owning a tun
inbound). That path no longer exists: the descriptor now belongs to the gVisor-based bridge, which is
the same arrangement the reference client uses at 1500. If large packets stall on a constrained path,
the bundle's `defaultMtu` is the single knob to revert to 1400.
