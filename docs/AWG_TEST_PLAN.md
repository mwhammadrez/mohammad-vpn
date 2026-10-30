# AWG (AmneziaWG) real-server test plan — Mohammad 0.4.0

Status: **plan ready, execution blocked** on an AWG server profile.
Engine: `amneziawg-go v3.1.20260828` (`b5928ef`) compiled into `libgojni.so` next to Xray.
Bridge: `libmohammad.AwgController.start(configJson, tunFd, mtu)`.

---

## 1. Where an AWG profile lives

Profiles come from the encrypted operator bundle (`assets/operator/bundle.enc`), built from
`operator/bundle.source.json`. There is no in-app editor in v1.

Bundle envelope:

```json
{
  "schemaVersion": 1,
  "defaultProfileId": "awg1",
  "defaultDnsServers": ["1.1.1.1", "8.8.8.8"],
  "defaultMtu": 1420,
  "profiles": [ <profile>, <profile> ]
}
```

## 2. AWG profile schema

```json
{
  "id": "awg1",
  "name": "awg-test",
  "protocol": "AMNEZIAWG",
  "core": "AMNEZIAWG",
  "serverHost": "vpn.example.net",
  "serverPort": 51820,
  "awg": {
    "privateKey": "<base64, 32 bytes>",
    "listenPort": 0,
    "mtu": 1420,
    "peers": [
      {
        "publicKey": "<base64, 32 bytes>",
        "presharedKey": "<base64, 32 bytes, optional>",
        "endpoint": "vpn.example.net:51820",
        "allowedIPs": ["0.0.0.0/0", "::/0"],
        "persistentKeepalive": 25
      }
    ],
    "obf": {
      "jc": 4, "jmin": 40, "jmax": 70,
      "s1": 0, "s2": 0, "s3": 0, "s4": 0,
      "h1": 1, "h2": 2, "h3": 3, "h4": 4
    }
  }
}
```

### Field reference

| Field | Required | Type | Rules / notes |
|---|---|---|---|
| `id`, `name` | yes | string | `name` ≤ 64 chars, `id` unique |
| `protocol` | yes | enum | must be `AMNEZIAWG` or `WIREGUARD` |
| `core` | yes | enum | **must be `AMNEZIAWG`** for both of the above (validated at load) |
| `serverHost`, `serverPort` | yes | string / int | shown in the profile list; `1..65535` |
| `awg.privateKey` | yes | base64 | 32 bytes after decode; unique per device |
| `awg.listenPort` | no | int | `0` = ephemeral |
| `awg.mtu` | no | int | defaults to the bundle `defaultMtu`, then 1500; 1200–1500 |
| `awg.peers[].publicKey` | yes | base64 | 32 bytes |
| `awg.peers[].presharedKey` | no | base64 | 32 bytes; omit the key entirely if unused |
| `awg.peers[].endpoint` | yes | `host:port` | parsed with `net.SplitHostPort`; control characters rejected |
| `awg.peers[].allowedIPs` | no | string[] | defaults to `["0.0.0.0/0"]`; **add `::/0` or IPv6 stays outside** |
| `awg.peers[].persistentKeepalive` | no | int | seconds; 25 recommended behind carrier NAT |
| `awg.obf.*` | no | int | `jc/jmin/jmax` junk packets; `s1..s4` junk sizes; `h1..h4` header types. Zero/absent values are skipped |

Keys stay **base64** in the bundle (the canonical WireGuard form); the Go bridge converts them
to hex and rejects anything that does not decode to exactly 32 bytes.

## 3. Redacted example (all values fake)

```json
{
  "schemaVersion": 1,
  "defaultProfileId": "awg1",
  "defaultDnsServers": ["1.1.1.1"],
  "defaultMtu": 1420,
  "profiles": [
    {
      "id": "awg1",
      "name": "awg-test",
      "protocol": "AMNEZIAWG",
      "core": "AMNEZIAWG",
      "serverHost": "vpn.example.net",
      "serverPort": 51820,
      "awg": {
        "privateKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "listenPort": 0,
        "mtu": 1420,
        "peers": [
          {
            "publicKey": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            "endpoint": "vpn.example.net:51820",
            "allowedIPs": ["0.0.0.0/0", "::/0"],
            "persistentKeepalive": 25
          }
        ],
        "obf": { "jc": 4, "jmin": 40, "jmax": 70, "s1": 0, "s2": 0, "s3": 0, "s4": 0,
                 "h1": 1, "h2": 2, "h3": 3, "h4": 4 }
      }
    }
  ]
}
```

The `A…=`/`B…=` strings above are placeholders and will be rejected as invalid keys — they exist
only to show shape. Real keys must be generated with `wg genkey` / `wg pubkey`.

## 4. Build and install

```bash
# 1. put the real profile at operator/bundle.source.json (mode 0600, git-ignored)
export MOHAMMAD_OPERATOR_BUNDLE_PASSWORD='<password>'
./scripts/encrypt-operator-bundle
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 5. Execution checklist

| Step | Expected log line | Pass criteria |
|---|---|---|
| 1. Connect with the AWG profile | `I/VPN profile=… core=AMNEZIAWG` | state leaves `DISCONNECTED` |
| 2. Tunnel created | `I/VPN tunnel up: fd=… mtu=1420 addr=10.0.0.1/30` | fd > 0 |
| 3. Core start | `I/VPN awg started in <n> ms` | no `E/AWG` |
| 4. State | `I/VPN tunnel connected` | UI shows `Connected` |
| 5. Handshake (server side) | — | `wg show` lists the peer with a recent handshake |
| 6. Traffic | `↓/↑` counters climb | bytes increase within 5 s |
| 7. Egress | `https://ifconfig.co` | IP equals the AWG server egress |
| 8. Disconnect | `I/VPN awg.stop ok in <n> ms`, `tun fd closed` | back to `Disconnected` |

Failure signatures to report verbatim:

- `E/VPN awg config render failed: …` → bundle shape problem (client-side)
- `E/VPN awg start failed … awg config rejected: …` → UAPI rejected (key/endpoint)
- `E/VPN awg start failed … tun adopt failed: …` → fd/ioctl issue on the device
- `E/AWG awg panic: …` → native crash; include the stack line
- `W/VPN engine stopped unexpectedly (watchdog)` → core died after start

## 6. What to send back

1. The `Copy`d log from the app (redacted automatically).
2. `wg show` output from the server for the same peer.
3. `ifconfig.co` result while connected.
4. Server-side AWG settings (`jc/jmin/jmax/s1..s4/h1..h4`) so we can confirm the obfuscation
   parameters match; **do not** send the private key.

## 7. Known risks

| Risk | Why | Mitigation |
|---|---|---|
| `tun.CreateTUNFromFile` on Android | the library has no Android-specific TUN file; it relies on the Linux build tag and `TUNGETIFF` | if `tun adopt failed` appears, we add an Android-specific `Tun` implementation that skips the ioctl |
| IPv6 inside the tunnel | `allowedIPs` must include `::/0`, and the TUN already carries `fc00::1/126` + `::/0` | set both in the profile |
| Obfuscation mismatch | server and client must agree on `h1..h4` and `s1..s4` exactly | copy the server values verbatim |
| MTU | AWG overhead differs from Xray | start at 1420, drop to 1280 if large packets stall |
