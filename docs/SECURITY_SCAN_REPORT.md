# Tool-assisted scan — 2026-10-30

Tools were run against this working tree. Every result below is copied from real output.

## Tools

| Tool | Version | Source | Scope |
|---|---|---|---|
| `go vet` | Go 1.27 | golang.org | `core-go` |
| `staticcheck` | latest | honnef.co/go/tools | `core-go` |
| `govulncheck` | latest | golang.org/x/vuln | `core-go` reachable symbols |
| `detekt` | 1.23.8 | github.com/detekt/detekt | `app/src/main/java` |
| `mobsfscan` | 1.1.0 | github.com/MobSF/mobsfscan | `app/src/main/java` |
| Android Lint | AGP 8.x | Android SDK | whole module |

## Results

| Tool | Findings | Verdict |
|---|---|---|
| `go vet` | 0 | clean |
| `staticcheck` | 0 | clean |
| `govulncheck` | 0 reachable; 3 in required modules, not called | clean |
| `mobsfscan` | 6 × INFO (certificate transparency, screenshot prevention, root detection, SafetyNet, SSL pinning, tapjacking) | informational; the app is not a banking client |
| Android Lint | 0 errors | clean |
| `detekt` | 249 weighted (mostly `MagicNumber`, `MaxLineLength`) | 3 real defects, listed below |

## Real defects found

### 1. `forceIpv6` was ignored (behavioural)

`SelfTest.fetchEgress(..., forceIpv6 = true)` never read the parameter. The IPv6 probe could therefore
be answered by an IPv4 path and its address reported as the IPv6 egress, corrupting `ipv6.possibleLeak`.

**Fix:** the answer's family is validated; an IPv4 answer is reported as `answered over IPv4` instead
of an address. Covered by `SelfTestFamilyTest` (3 tests).

### 2. The self-test could not see its own tunnel (behavioural)

The app calls `addDisallowedApplication(packageName)` so the core can dial the server without looping.
That exclusion also applied to the self-test sockets, so egress was always measured from the underlying
network — a working tunnel could report `no-tunnel`.

**Fix:** the probe resolves the VPN `Network` and uses `Network.openConnection(...)`, which routes the
probe through the tunnel while leaving the core's own dial excluded.

### 3. Dead UI code after the redesign (housekeeping)

`humanBytes`, `statusText`, `statusDetail`, `buttonText` were unreferenced. Removed.

## False positives recorded

| Finding | Why not a defect |
|---|---|
| `XrayConfig.render(mtu)` unused | MTU belongs to the TUN interface, not the core JSON; the parameter is API lineage, not a bug |
| `MagicNumber` (≈200 hits) | protocol constants and byte sizes; naming them adds no safety |
| `mobsfscan` certificate-transparency / SafetyNet | not applicable to a personal VPN client |

## Not run

| Tool | Why |
|---|---|
| MobSF (full) | requires Docker, unavailable here |
| LeakCanary / StrictMode runs | require a device |
| OWASP dependency-check | needs an NVD API key; `govulncheck` covered the Go side |
