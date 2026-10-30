# Behavioural analysis notes (no third-party code)

These notes describe *what a mature Android Xray client does*, distilled into decisions we
re-implemented independently. They contain no code, strings, or assets from any third party.

## Data path

```
VpnService TUN fd
   → userspace TCP/IP stack (tun2socks)
   → 127.0.0.1:10808 socks inbound
   → core outbound
   → remote server
```

Evidence that this is the prevailing pattern: such clients ship a SOCKS/HTTP inbound pair in
their config template and hand the descriptor into a native bridge. We verified the presence
of a userspace stack (gVisor) in a reference native library's dependency block, which cannot
come from the core alone.

## Configuration shape adopted

| Area | Choice | Rationale |
|---|---|---|
| Inbounds | `socks` 10808 + `http` 10809 on loopback | loopback-only, no LAN exposure |
| Sniffing | `http`, `tls`, `fakedns` | domain routing needs the recovered name |
| DNS | `fakedns` first, then operator servers | keeps resolution inside the tunnel |
| Policy | handshake 4 / connIdle 300 / uplinkOnly 1 / downlinkOnly 1 | matches observed defaults |
| Routing order | block udp:443 → block ads → direct private → direct home country → proxy | first-match semantics |
| MTU | 1400 (bundle), 1500 fallback | 1400 measured as stable on the test device |

## Deliberate deviations

| Deviation | Why |
|---|---|
| `domain:ir` instead of a country geosite category | the category is absent from several datasets; a missing category is a startup hazard. A suffix rule needs no dataset. |
| IPv6 opt-in, default off | the test device has a local IPv6 address but the server provides no IPv6 egress; enabling `::/0` caused stalls |
| No per-app list, no proxy-only UI | out of scope for v1 |

## What is *not* claimed

Matching configuration is not proof of matching runtime behaviour. Behavioural parity is
`UNKNOWN` until measured on a device; see `docs/ARCH_PARITY_AUDIT.md`.
