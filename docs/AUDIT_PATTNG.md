# AUDIT — PattNG (patterniha/PattNG)

**Date:** 2026-10-31 · **Method:** full source read of the app, its three submodules, and a
commit-level diff of the forked Xray core against the upstream commit it is based on
**Verdict in one line:** the "configs that ping here but not elsewhere" effect is real, and its main
cause is a **removed upstream safety check**, reinforced by two genuinely novel TCP tricks.

---

## 1. What the project is

| Item | Value |
|---|---|
| Repo | https://github.com/patterniha/PattNG (fork of `2dust/v2rayNG`) |
| Licence | **GPL-3.0** (root `LICENSE`) |
| Size | 529 tracked files · 313 Kotlin · 112 XML · 82 test files |
| Submodules | `AndroidLibXrayLite` → **patterniha/AndroidLibXrayLite** (itself replacing `xtls/xray-core` with `patterniha/xray-core`) · `aether` → patterniha/Aether · `hev-socks5-tunnel` (upstream heiher) |
| CI | `.github/workflows/build.yml`, `fastlane.yml` |
| Build helper scripts | `compile-aether.sh`, `compile-hevtun.sh`, `compile-psiphon.sh`, `compile-pt.sh`, `fetch-psiphon-servers.sh` |

The README advertises three changes: `cipherSuites` + `unsafe` fingerprint fields, the **Aether** core,
and "the ability to connect to unencrypted configs for public addresses in VLESS and Trojan". The first
two are conveniences. **The third is the feature the operator is asking about.**

---

## 2. Why configs "ping" here and return −1 everywhere else

Three independent mechanisms, in order of how much they explain the symptom.

### 2.1 The upstream safety check is commented out — this is the main cause

Upstream Xray (since 26.x) refuses to build a VLESS/Trojan outbound that has **no transport security**
to a **public** address:

```go
// xray-core infra/conf/xray.go:250
func validateOutboundTransportSecurity(rawConfig interface{}, senderSettings *proxyman.SenderConfig) error {
    if senderSettings.StreamSettings != nil && senderSettings.StreamSettings.GetSecurityType() != "" { return nil }
    if vlessCfg, ok := rawConfig.(*VLessOutboundConfig); ok {
        if vlessCfg.Encryption != "" && vlessCfg.Encryption != "none" { return nil }
        if requiresTransportSecurity(vlessCfg.Address) {
            return errors.New("vless without TLS or other encryption is prohibited unless the server address is a private IP or domain")
        }
    }
    ...
}
```

PattNG's commit `f8d54a0` ("Xray-core: Allow unencrypted outbounds on public Internet for VLESS and
Trojan") **comments the call out**:

```go
-	if err := validateOutboundTransportSecurity(rawConfig, senderSettings); err != nil {
-		return nil, err
-	}
+	//if err := validateOutboundTransportSecurity(rawConfig, senderSettings); err != nil {
+	//	return nil, err
+	//}
```

Consequence: a config whose `serverHost` is a **public IP and has no TLS** cannot even be built by a
stock Xray, so "real ping" (which measures a request *through the proxy*) returns `-1`. In PattNG the
same config builds, connects, and measures. **That is the whole "it pings where others don't" story for
the majority of free Iranian configs** — they are plaintext VLESS/Trojan to a public IP.

Two details worth knowing:

* `requiresTransportSecurity` (`infra/conf/xray.go:238`) treats a **domain** the same as a public IP
  (only private/loopback addresses are exempt). So switching a config from `1.2.3.4` to a domain in
  stock Xray does not help either; only the patched core accepts it.
* The patch is a blanket comment-out of a build-time guard. It removes the only thing that told the
  user "this configuration sends your traffic in clear text".

### 2.2 `oob.<byte>,...` dialMode — TCP urgent-data desynchronisation

Commit `1a42987` (files `transport/internet/dialmode_oob*.go`, plus wiring in `system_dialer.go`).

Mechanism: for the first *N* writes on the dialed TCP connection, the client appends one extra byte and
sends the whole buffer with **`MSG_OOB`** (`unix.Send(fd, buf, MSG_OOB)` on unix, `WSASend(..., MSG_OOB)`
on Windows). The urgent pointer marks the **last byte** as out-of-band:

```go
data := make([]byte, len(p)+1)
copy(data, p)
data[len(p)] = oobByte          // urgent byte
sendOOB(fd, data)               // MSG_OOB
```

* A conforming receiver's kernel strips the urgent byte from the stream, so the peer application sees
  the original bytes **unchanged** — no server patch is required.
* A middlebox that mishandles the urgent pointer (offsets its reassembly by one byte) fails to parse
  the TLS ClientHello / VLESS header and lets the flow pass.

`oob.0` affects only the first write; `oob.0,32,65` affects the first three. A zero-length write counts,
which lets the urgent byte be sent ahead of any real data.

### 2.3 `merge` finalmask — the opposite of fragmentation

Commit `e37118b` (`transport/internet/finalmask/merge/*`). It **coalesces** client→server writes: buffer
each write, flush when the connection is idle for `delayMs` (default **30 ms**) or the buffer reaches
`maxBytes` (default **16 KiB**), for `times` flushes, then pass through. Read side untouched.

Rationale: some DPI matches on "the first small packet" (the ClientHello) — merging makes the first
outbound write a single full-size segment instead of a handshake-shaped one. It is the mirror image of
`fragment`, which we already ship.

### 2.4 Aether (the `aether` submodule)

A MASQUE (HTTP/3, HTTP/2) + WireGuard client with nested modes (`wg inside masque`, `masque inside
masque`), automatic endpoint discovery, and — the part worth stealing — **end-to-end data-plane
validation**: "a gateway is only trusted once it actually passes traffic, not just once it answers the
handshake". That is exactly the discipline we adopted in 0.10.0 and again in M4-N.

---

## 3. Security and correctness analysis

### 3.1 Plaintext to a public IP (the headline feature) is a downgrade, not an upgrade

* **No confidentiality.** VLESS is designed to carry clear text inside TLS/REALITY. Without transport
  security, everything except the password frame is readable by the ISP — URLs, DNS, TLS SNI, message
  metadata.
* **Trivially blockable in principle.** The only reason such a config survives is the DPI desync from
  §2.2/§2.3; the moment the filter reassembles correctly, the flow is an obvious VLESS header.
* **Upstream removed it deliberately** (`d7fa207`, "Forbid unencrypted outbounds on public Internet …
  (#6303)"). The fork's commit does not argue with that reasoning; it removes the check wholesale.
* **It also removes the warning**, so a user cannot tell a safe config from an unsafe one.

For Mohammad this is a **hard no**: we already require a fingerprint whenever TLS is on (D-48), and the
whole 0.14.x line exists because REALITY is the only path with a survivable story.

### 3.2 OOB is clever, and risky in ways the repo does not test

| Concern | Detail |
|---|---|
| Server compatibility | Needs the *receiver's* kernel to strip the urgent byte. Linux/macOS/Windows default to `SO_OOBINLINE` off, so a stock Xray server works — but a server behind a proxy, a container network with `net.ipv4.tcp_stdurg`, or an OS with different urgent semantics may deliver the byte into the stream and corrupt the handshake. |
| Path compatibility | Any middlebox that **drops** URG-bearing segments kills the connection outright. Some NAT/load-balancer vendors do. |
| REALITY interaction | REALITY authenticates on the ClientHello. If the urgent byte leaks into the stream, the handshake fails; if it is stripped, REALITY is unaffected. Untested. |
| Test coverage | **Zero tests.** `dialmode_oob*_test.go` does not exist; neither does a test for `merge`. The project has 82 Kotlin test files, so the omission is a choice, not a lack of habit. |
| Portability | It lives in a **forked core**. Configs using `dialMode` cannot run on stock Xray; the operator inherits a fork to maintain. |

### 3.3 merge's cost

Each flush delays client→server data by up to `delayMs` (30 ms default). For a request/response
workload that is a 30 ms penalty on the first writes of every connection burst, and it interacts badly
with small interactive traffic unless `times` is kept low.

---

## 4. Repo hygiene and other findings

| Finding | Detail |
|---|---|
| Licence conflict | Root is **GPL-3.0**, but the bundled `aether` submodule is **AGPL-3.0**. Combining GPL-3.0 and AGPL-3.0 code in one distributed work is not permitted by either licence as written; the combined work would have to be AGPL. Not legal advice — but it is a redistribution risk for anyone forking this. |
| Third-party call home | `AppConfig.IP_API_URL = "https://api.ip.sb/geoip"` — the app sends the user's public IP to a third party for geolocation. Common in v2rayNG lineage, still a privacy leak worth knowing. |
| Permissions | CAMERA (QR), RECEIVE_BOOT_COMPLETED, CHANGE_NETWORK_STATE, ACCESS_LOCAL_NETWORK, POST_NOTIFICATIONS — broader than Mohammad's five. |
| Hardcoded upstream URL | `PSIPHON_SERVERS_URL` points at an S3 path with a double slash (`//psiphon/...`). Fragile, and a single point of failure for that feature. |
| AI-authored scaffolding | `AGENTS.md`, `CLAUDE.md`, `GEMINI.md` at the root. Not a defect, but it means parts of the tree are generated; treat comments as claims, not evidence. |
| `unsafe` fingerprint | Their UI exposes it. In the fork, `unsafe` is accepted in the REALITY branch by skipping the `tls.GetFingerprint` lookup (`infra/conf/transport_security.go:356`). Meaning: "do not use uTLS at all". That is the loudest possible fingerprint, offered as a feature. |
| Fork currency | Their core is v26.10.27; upstream is v26.10.31. They merge upstream regularly (several `Merge branch 'XTLS:main'` commits), which is a good sign. |

---

## 5. Does it help Mohammad?

| Mechanism | Fit | Why |
|---|---|---|
| Allow plaintext VLESS/Trojan to public IPs | **Reject** | Downgrade in confidentiality and detectability; contradicts D-48 and the REALITY line of work. Our operator's configs are REALITY/TLS, so the guard never blocks us. |
| `oob.*` dialMode | **Prototype, don't adopt blind** | Genuinely novel against stateless Iranian DPI. But it needs a **patch to our pinned Xray** (D-08), has no upstream tests, and can break REALITY if the urgent byte leaks. It would have to ship behind a profile flag and be device-tested. |
| `merge` finalmask | **Prototype, don't adopt blind** | Same patch cost; 30 ms penalty; only useful for filters that key on the first small segment. Cheap to test once OOB is in. |
| Aether (MASQUE + nested tunnels) | **Idea only** | AGPL-3.0, and our M6 plan is already a second path. Its *data-plane validation* rule is the part to copy — and we already do. |
| Their ping/measure UI | **No** | Nothing novel; the difference is entirely "the config loads here". |

### The one sentence the operator should take away

The configs that "only PattNG can ping" are almost always **plaintext VLESS/Trojan pointing at a public
IP**. PattNG makes them work by deleting the check that was added to stop exactly that, and then helping
them survive DPI with urgent-data desync. It is a real effect, and it is also the least private
configuration class in existence.

### If we want the evasion without the downgrade

`oob` and `merge` are transport-level tricks and apply equally to REALITY. The experiment worth running,
in order:

1. Patch our pinned core with `oob` only (about 200 lines, mirrors their layout), behind
   `sockopt.dialMode`.
2. Device-test against REALITY: confirm the server's TLS stack is unaffected, and that a known-blocked
   ClientHello now passes.
3. Only then consider `merge`, and only for ISPs where fragmentation is already blocked.

None of that is free: it means carrying a core patch, which conflicts with the "pin upstream, no
patches" policy in D-08. That trade-off is the operator's call, and it should be a decision record
before any code.

---

## 6. What this audit actually verified

* Commit-level diff of `patterniha/xray-core` against `xtls/xray-core` at the fork's base, not against a
  later version — the first pass compared against v26.10.31 and produced 165 files of version noise;
  the real fork commits are the six listed in §2.
* The OOB and merge implementations were read in full (`dialmode_oob*.go`, `merge/conn.go`).
* The claim "configs others cannot ping" is traced to a specific commented-out call, not inferred.
* **Not verified:** any of this on a device, on an Iranian line, or against a REALITY server. Nothing in
  this document is a measurement; it is a source audit.
