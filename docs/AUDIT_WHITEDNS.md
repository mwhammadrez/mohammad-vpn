# AUDIT — WhiteDNS (DNS tunnelling), for possible integration

**Date:** 2026-10-31 · **Repo:** `WhiteDNS/WhiteDNS-Android` (1,491★) · **Method:** full clone, source
read, engine inventory, and a real Android build of the underlying MIT engine with our NDK.

**Verdict in one line:** the *idea and the engine* are worth having; **the app is not copyable** (its
licence forbids it), and the technique is a last-resort path — tens to hundreds of KB/s, not a
replacement for REALITY.

---

## 1. What it actually is

A DNS tunnel: every packet of your TCP traffic is encoded into **DNS queries** to a domain you own,
whose authoritative nameserver is your own server. Anything that lets DNS through — captive portals,
whitelist firewalls, networks that block every port except 53 — will carry it.

WhiteDNS is the Android front end. The tunnelling itself is done by **separate Go executables** that
the app ships and runs as child processes. WhiteDNS implements none of the protocol itself.

### Licensing — read this before planning anything

| Component | Licence | Can we use it? |
|---|---|---|
| `WhiteDNS-Android` (the app) | **Source-available proprietary** | **No.** "You may not use this software … to create … another app, fork, clone, modified version … or derivative product." |
| `MasterDnsVPN` (engine) | **MIT** | yes |
| `StormDNS` (engine) | **MIT** | yes (derivative of MasterDnsVPN) |
| `CottenDns` (engine) | **MIT** | yes (derivative of StormDNS) |
| `Soar` (Lantern engine) | Apache-2.0 | yes |
| `tun2proxy` | MIT | yes |

So the legal route is: **use an MIT engine, write our own integration.** That is also the better
route technically, because WhiteDNS's app is a general-purpose client with subscriptions, QR, a
4-engine picker and a whole UI we do not need.

---

## 2. Architecture of the reference app

```
VpnService ──fd──> tun2proxy (native) ──SOCKS5──> engine process ──DNS queries──> our server
                                                      │
                                             config.toml + resolvers.txt
```

* The engines are **not** JNI/gomobile. They are plain executables placed in `jniLibs/` under names
  like `libstormdns_client.so`, so the package manager extracts them to `nativeLibraryDir`, where
  Android allows executing them. The app starts them with `ProcessBuilder` and pipes their stdout.
* `tun2proxy` is a second executable that bridges the VpnService descriptor to the engine's local
  SOCKS5 listener.
* The app excludes itself from the tunnel with `addDisallowedApplication(own package)`, so the
  engine's DNS sockets do not loop back into the tunnel. No fd protection is needed. (MasterDnsVPN
  additionally supports `FD_CONTROL_UNIX_SOCKET` for hosts that cannot exclude themselves; we do not
  need it either.)
* A separate service scans resolvers: it partitions them across processes, probes each, and keeps the
  working ones with their measured upload/download MTU.

---

## 3. The protocol, in detail

MasterDnsVPN is a **custom** protocol, not DNS-over-QUIC (SlipStream) and not KCP+Noise (DNSTT).

**Carrier.** Payload travels inside the **QNAME** of a query to `<data>.<tunnel-domain>`; the server
answers with **TXT** records. Labels are base32/base36/base64 encoded (`internal/basecodec`), which is
why a shorter domain directly buys throughput.

**Header.** 5–7 bytes: `SessionID(1) + PacketType(1) + Integrity(1–2)`, plus optional `StreamID(2)`,
`SequenceNum(2)`, `FragmentID/Total(2)`, `CompressionType(1)` — each present only when that packet
type needs it. DNSTT spends ~59 B and SlipStream ~24 B on the same job, which is the whole design
thesis: **header cost is what kills DNS tunnels.**

**Reliability.** Its own ARQ: window 1000, RTO 0.5 s → 3 s, NACK on near-miss gaps, data/control
retransmit caps, inactivity timers. Streams are multiplexed (SOCKS5 per-connection stream IDs).

**Multipath.** Many resolvers in parallel, a balancer with 8 strategies (random, round robin, least
loss, lowest latency, hybrid, …), optional packet **duplication** (1–10×) for lossy paths, per-stream
resolver failover, runtime disabling of dead resolvers and background re-activation.

**MTU.** It measures upload and download MTU **per resolver** at startup and drops "bottleneck"
resolvers that would drag the global MTU down. Upload MTU in DNS is small (38–150 B typical), download
can be 1–4 KB thanks to TXT fragmentation. This is what makes it work on networks where a fixed-size
probe would fail.

**Crypto.** `DATA_ENCRYPTION_METHOD`: none / XOR / ChaCha20 / AES-128/192/256-GCM, shared key. Note
the default in their sample is `1` (XOR) — fine for obfuscation, **not** for confidentiality. We would
default to AES-256-GCM.

**Compression.** Off / ZSTD / LZ4 / ZLIB, both directions, above a size threshold.

**Local conveniences.** Built-in SOCKS5 (with optional auth), a raw TCP mode, and a **local DNS
server with cache** so the device's resolver does not leak to the ISP.

---

## 4. What I measured here (not from the README)

| Check | Result |
|---|---|
| Build engine for `android/arm64` with our NDK 28 | **OK**, 5.3 MB (`-s -w`) |
| Build engine for `android/x86_64` | OK, 5.7 MB |
| Build for host (linux/amd64) | OK, 7.7 MB, `-version` runs |
| `zip -9` of the arm64 binary | 2.1 MB |
| APK impact, arm64 only, uncompressed `.so` | 14.3 MB → **~19.6 MB** (budget 20 MB) |
| Engine dependencies | `toml`, `lz4`, `klauspost/compress`, `x/crypto` — all permissive |

The APK budget is the first concrete constraint: adding the engine fits, but leaves almost nothing
else. Either we ship arm64 only (already our target) or we raise the 20 MB budget with the operator.

---

## 5. Server side (an operator action, like REALITY)

1. An `A` record for `ns.example.com` → server IP, **DNS-only** (no CDN proxy).
2. An `NS` record delegating `v.example.com` → `ns.example.com`.
3. Run the MIT **server** binary on UDP 53 with the same domain and key.
4. Generate a shared key; put it in the client profile.

Propagation can take minutes to hours. No certificate and no domain fronting involved.

---

## 6. How it would fit into Mohammad

The app already has the right shape: `VpnService → TunBridge → SOCKS5 → core`. A DNS profile is a
second core, not a second architecture.

| Piece | Change |
|---|---|
| `Profile` | `core = "DNS"`, plus `dns { domain, key, method, resolvers[] }` |
| Engine packaging | `scripts/build-dns-engine.sh` → `app/src/main/jniLibs/arm64-v8a/libmdvpn_client.so`, built from the MIT source at a pinned commit |
| Start | render TOML (same style as `XrayConfig`), exec the binary, wait for its SOCKS port to accept |
| Bridge | `TunBridge.start(fd, mtu, "socks5://127.0.0.1:10809")` — one parameter, the bridge does not care which core answers |
| Stop | kill the process in the same place the Xray controller is stopped today |
| Real delay | **works unchanged**: `RealDelay` speaks SOCKS5, so it measures the DNS path as easily as Xray |
| Scan | optional later: probe resolvers and keep the working ones, as the reference app does |
| Import | `dns://`/`stormdns://` links in `ConfigUri`, behind the same validation |

Sequencing: this is M6's alternative ("second path"). It does not disturb M1–M5.

---

## 7. Honest limits

* **Throughput.** Real-world DNS tunnelling is tens to hundreds of KB/s. Their "10 MB in 1.7 s" is a
  localhost LAN measurement. Browsing works; video does not.
* **Latency.** Every packet is a DNS round trip. Expect 150–600 ms on top of whatever the resolver adds.
* **Battery and heat.** Many resolvers × duplication × ARQ is a lot of wake-ups.
* **It depends on DNS.** If the ISP hijacks or drops UDP 53 to public resolvers, the tunnel dies. That
  is why these clients carry long resolver lists and scan them.
* **Blockability.** A tunnel domain is a single point of failure: block the delegation or the
  nameserver and every user is down until the profile changes. This is a *last resort* path, not the
  primary one.
* **Detection.** DNS tunnelling is a known category; heavily instrumented networks can fingerprint
  query volume and label entropy.

---

## 8. Score

| Dimension | Score | Note |
|---|---|---|
| Fit for Iran in a total block | 9/10 | Nothing else survives "DNS only" |
| Fit as the daily driver | 4/10 | Too slow for media, high latency |
| Engineering quality of the engine | 8/10 | Small header, ARQ, multipath, per-resolver MTU; MIT |
| Ease of integration into this app | 8/10 | Subprocess + one SOCKS URL; ~5 MB APK |
| Licensing of the reference app | 0/10 | Cannot be reused; engine route only |
| **Overall as a second path (M6)** | **8/10** | Worth building, not worth replacing REALITY |

---

## 9. Open questions for the operator

1. Is there a domain available for an `NS` delegation, and a VPS that can accept UDP 53?
2. Which engine: MasterDnsVPN (most active, 7.3k★) or Soar (Apache-2.0, Lantern-maintained)?
3. Is ~5 MB of APK growth acceptable, or does the 20 MB budget need raising?
