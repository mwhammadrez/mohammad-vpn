> Full project handbook: `docs/HANDBOOK.txt`

# Project status — 2026-10-30

Single source of truth for what is done, what is verified, and what is still open.
"Verified" always means: a command was run (or a device log was captured) and its output is
recorded in this repository.

## Done and verified

| Item | Evidence |
|---|---|
| Both cores build into one `libgojni.so` | `core-go/artifacts/CHECKSUMS.txt`, AAR in `app/libs/` |
| Xray + tun2socks data path | device log: `tun2socks up` → egress `95.135.125.40` (operator report, `docs/PARITY_CLOSURE.md`) |
| TUN fd ownership (no double close) | `core-go/libmohammad/tunbridge_test.go`, 3/3 pass |
| Handover: survives network loss, reconnects | `HandoverPolicyTest` 7/7, `SocksProbeTest` 5/5; device: recovery in 3.83 s |
| 55 JVM tests, lint, assemble, placeholder gate | `PROGRESS.md` per-release entries |
| Configuration parity with the reference client | `docs/PARITY_CLOSURE.md` |

## Done but NOT verified

| Item | Why |
|---|---|
| MTU 1500 | matched to the reference client; the 1400 figure came from the pre-tun2socks architecture (D-32). Needs one device run with large transfers |
| Split-route set | logic unit-tested; the actual route table on a device has not been dumped since the change |
| Single resolver `8.8.8.8` | same |
| 0.7.4 as a whole | no device attached to this workspace since the build |

## Not done

| Item | Status |
|---|---|
| AmneziaWG carrying real traffic | never tested — no AWG server profile was ever provided |
| Android security/tooling scan (MobSF, detekt, dependency audit) | not started |
| LeakCanary / StrictMode / long-session runs | not started |
| Release build and signing | out of scope so far; debug signing only |
| Process isolation (`:Daemon`-style service) | deliberately unchanged |
| Full UI (subscription import, per-app routing, settings) | only connect/disconnect, profile picker, ping, self-test |
| Route count exactly 32 | 45 is the minimal exact set; see `docs/PARITY_CLOSURE.md` |
| IPv6 egress | tunnel-side IPv6 is opt-in and off; server provides no IPv6 |
| Kill switch | none, by design (matches the reference client) |

## Known constraints of this workspace

- No device and no `/dev/kvm`: anything marked `NOT VERIFIED` cannot be closed from here.
- Host has 3.8 GiB RAM: builds are serial with `-Xmx1024m`.
- Debug builds are arm64-v8a only.
