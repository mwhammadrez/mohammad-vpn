# Release notes — Mohammad (`ir.mohammad.vpn`)

Only user-visible and operator-visible changes are listed. Anything that could not be proven in this
workspace is marked `NOT VERIFIED`.

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
