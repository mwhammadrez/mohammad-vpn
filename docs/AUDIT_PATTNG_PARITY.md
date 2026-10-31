# PARITY AUDIT — PattNG (2026-10-31, second pass)

**Method:** their fork's own commits, listed through the GitHub API, not the README; each commit's
files read against our pinned core. Their non-upstream commits on `patterniha/xray-core` are exactly
five features plus a version bump:

| Commit | Feature | Ours |
|---|---|---|
| `1a42987b` | `oob.<b1>,<b2>` dialMode | **ported** (`0002`) |
| `57b1c94a` | dialMode placement around the default dialer | **equivalent** |
| `e37118b5` | `merge` TCP finalmask | **ported** (`0003`) |
| `4bb7e4f2` | DNS `https+one` DoH schemes | **not ported** — see below |
| `a49faaf8` | ECH config fetch over `+one` DoH | **not ported** — see below |
| `ef8aee03` | TUN inbound, Darwin IPv6 | not applicable (we do not use the tun inbound) |

## The two that are not ported, and why

Both change **how a DoH server is queried**: `+one` opens a fresh TCP+TLS connection per query and
trims the request instead of reusing a connection with padding.

They change nothing for this client, because:
* the DNS entries we render are IP literals (`1.1.1.1`, `8.8.8.8`, `fakedns`), never `https://…`;
* we never set `echConfig`, so the ECH fetch path never runs.

Porting them would mean ~140 lines inside the DNS resolver with no test that can run here and no
behaviour change for any config this app can produce. If a DoH server is ever added to the bundle,
this decision has to be revisited — that is the trigger.

## What is ported, in detail

| Capability | Where | Difference from theirs |
|---|---|---|
| Unencrypted VLESS/Trojan to a public host | `0001` | warning instead of a silent allowance |
| `oob` urgent-byte desync | `0002` | syntax validated at bundle load; `Write` returns `len(p)`, not `len(p)+1` |
| `merge` coalescing | `0003` | `times: 0` means one flush, not unlimited; defaults applied in one place; ships Go tests |
| Per-profile `dialMode` / `finalMask` / `cipherSuites` | `Profile`, `XrayConfig` | validated against the core's own loaders before a core ever sees them |
| `unsafe` fingerprint | `TlsFingerprint` | allowed for TLS only, refused for REALITY, flagged degraded |
| Real delay | `RealDelay` | measures through a temporary core; never two cores at once |
| Paste import | `ConfigUri`, `ImportBatch` | carries `encryption`, `httpupgrade`, `xhttp`, `fm` |
| Test-all | `RealDelay.measureAll` | sequential; skips profiles that cannot be measured |

## Where we are deliberately stricter

* A broken share link is named, never silently dropped.
* REALITY + fragment is refused instead of producing a handshake that cannot complete.
* Every patch ships tests that run in `scripts/build-core.sh`; theirs ship none.
* The allow-list of fingerprints is read from the pinned core's source, not from documentation.

## Where we are deliberately weaker

* No Aether (MASQUE/WARP): AGPL-3.0, and M6 covers a second path differently.
* No subscription/QR UI: this client is fed by an operator bundle and by pasted links.
* No DoH `+one` (above).
