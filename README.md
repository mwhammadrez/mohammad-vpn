# Mohammad — Android VPN client

Package `ir.mohammad.vpn` · v0.2.0 (debug) · minSdk 26 / targetSdk 36

A single-screen VPN client that connects an operator-provided profile, reports tunnel
state, streams a structured diagnostic log, and measures latency. Two engines ship in
one native library: **Xray-core** (VLESS/VMess/Trojan/Shadowsocks/SOCKS/HTTP/Hysteria2)
and **AmneziaWG** (WireGuard + obfuscation).

---

## Build

Prerequisites (versions actually used and verified):

| Component | Version |
|---|---|
| JDK | 17.0.20.1 |
| Gradle | 8.14.6 (wrapper) |
| AGP | 8.13.2 |
| Kotlin | 2.0.21 (Compose plugin bundled) |
| Android SDK | platform 36, build-tools 36.0.0 |
| NDK | 28.2.13676358 |
| Go | 1.27.3 + gomobile |

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/android-sdk

./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 16 unit tests
./gradlew lintDebug
```

The prebuilt native bridge lives in `app/libs/mohammad-core.aar`. To rebuild it from
source, see `core-go/` (needs `/tmp/xray-src` and `/tmp/awg-src` checkouts at the pinned
commits, or edit the `replace` directives in `core-go/go.mod`).

## Operator bundle

Profiles are not editable in-app at runtime. They ship encrypted:

```
assets/operator/bundle.enc
  "MHB1" | salt[16] | iv[12] | AES-256-GCM(ciphertext||tag)
  key = PBKDF2-HMAC-SHA256(password, salt, 210000, 32)
```

Regenerate after editing `operator/bundle.source.json` (git-ignored, mode 0600):

```bash
export MOHAMMAD_OPERATOR_BUNDLE_PASSWORD='…'
./scripts/encrypt-operator-bundle
```

The password reaches the app through `local.properties`
(`mohammad.operatorPassword=…`), which is git-ignored. This is obfuscation, not
cryptography against a determined attacker — see `DECISIONS.md` D-11.

### Bundle schema (v1)

```json
{
  "schemaVersion": 1,
  "defaultProfileId": "p1",
  "defaultDnsServers": ["1.1.1.1", "8.8.8.8"],
  "defaultMtu": 1400,
  "profiles": [
    { "id": "p1", "name": "…", "protocol": "VLESS", "core": "XRAY",
      "serverHost": "…", "serverPort": 443,
      "xray": { "protocol": "vless", "settings": { }, "streamSettings": { } } }
  ]
}
```

AMNEZIAWG profiles use `"awg": { privateKey, listenPort, mtu, peers[], obf{} }` instead
of `xray`; keys stay base64 and are converted to hex inside the Go bridge.

## Architecture

```
MainActivity (Compose) ──► Tunnel/LodStore (StateFlow singletons)
        │
        ▼
MohammadVpnService (VpnService, foreground, specialUse)
        │  Builder → establish() → ParcelFileDescriptor
        ▼
libmohammad.XrayController  |  libmohammad.AwgController
        │  start(json, fd[, mtu])
        ▼
libgojni.so  (Xray v26.10.29  +  amneziawg-go v3.1.20260828)
```

- The app excludes its own package from the tunnel, so the engine's sockets never loop
  back into the VPN.
- Xray receives the TUN descriptor through `XRAY_TUN_FD` and its `tun` inbound.
- The engine runs in the same process as the service; no `protect()` dance is needed.

## Diagnostics

Every line in the app log is `HH:mm:ss.SSS LEVEL/TAG message` with `TAG` ∈
{APP, VPN, XRAY, AWG}. Xray's own log stream is forwarded into the UI at `debug` level,
and every message passes through a redactor that masks UUIDs, `host:port` endpoints and
long key tokens. **Copy** puts the whole log on the clipboard.

Typical failure signatures:

| Line | Meaning |
|---|---|
| `E/VPN core start failed … is not registered` | protocol registry missing in the native build |
| `E/XRAY [Error] failed to …` | core-level failure (auth, stream, DNS) |
| `E/VPN establish() returned null` | VPN consent revoked |
| `E/VPN interface build threw …` | VpnService.Builder rejected a parameter |

## Repository map

```
app/                 Android module (Compose UI, VpnService, bundle loader)
core-go/             merged gomobile module → mohammad-core.aar
core-xray/, core-awg/ individual wrappers (kept for reference/standalone builds)
third_party/anet/    patched anet (Go 1.27 linkname fix)
operator/            plaintext bundle source (git-ignored)
scripts/             encrypt-operator-bundle
docs/                BASELINE_REPORT.md
SPEC.md PLAN.md PROGRESS.md DECISIONS.md
```

## Known limitations

- Debug signing only; no release keystore.
- `armeabi-v7a` not built (arm64 + x86_64 only).
- AmneziaWG is wired end-to-end but has never been exercised against a real AWG server.
- No unit tests for the service lifecycle (requires instrumentation).
- Hysteria2 works only through the subset Xray-core accepts.

## License

Application code: Apache-2.0 (see `LICENSE`).
Third-party components keep their own licences — Xray-core (MPL-2.0), amneziawg-go (MIT),
tun2socks (MIT), gVisor (Apache-2.0). See `reference/THIRD_PARTY.md`.
