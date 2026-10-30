# BASELINE REPORT — Mohammad (`ir.mohammad.vpn`)

**Phase:** 0 (explore, read-only)
**Date:** 2026-10-30
**Author:** build agent
**Status:** BASELINE COMPLETE — BUILD ENVIRONMENT NOT PROVISIONED

> This report contains no application code and no secrets. All version facts below were read from live registries in this session; every command that could not be executed is labelled `NOT VERIFIED` with the reason.

---

## 1. Host environment

| Item | Observed value | Evidence |
|---|---|---|
| OS | Ubuntu 22.04.5 LTS | `/etc/os-release` |
| Kernel | 6.1.158+ | `uname -a` |
| Architecture | x86_64 | `uname -a` |
| CPU cores | 4 | `nproc` |
| RAM | 3.8 GiB total, ~1.6 GiB free | `free -h` |
| Disk | 28 GiB total, **20 GiB free** | `df -h /home/user` |
| KVM | **absent** (`/dev/kvm` missing) | `ls -l /dev/kvm` |
| `git` | present | `command -v git` |
| `curl`, `unzip` | present | `command -v` |
| Network to `dl.google.com` / `repo1.maven.org` / `go.dev` | HTTP 200 | `curl -w %{http_code}` |

### 1.1 Toolchain status

| Tool | Required | Status |
|---|---|---|
| JDK 17+ | AGP 9.4 minimum is **JDK 17** | ❌ only a **JRE 11** (`java 11.0.32`), **no `javac`** |
| `JAVA_HOME` | required | ❌ unset |
| Gradle | 9.6.0 minimum for AGP 9.4 | ❌ missing (use wrapper) |
| Android SDK / `sdkmanager` / `adb` | required | ❌ missing, `ANDROID_HOME` unset |
| Android NDK | required by gomobile | ❌ missing |
| Go | Xray `go.mod` requires **go 1.27** | ❌ missing |
| `gomobile` / `gobind` | required | ❌ missing |
| `cmake` | optional (no C++ in this project) | ❌ missing |
| Device / emulator | instrumented tests, real-path verification | ❌ none, and **no `/dev/kvm`** |

**Conclusion:** the sandbox is a clean Ubuntu image with a JRE only. Nothing in the Android or Go toolchain exists yet. Phase 0 therefore verifies *upstream* facts and records a provisioning plan; the command table below stays `NOT VERIFIED` until provisioning runs.

---

## 2. Command table — verification status

| Purpose | Command | Status | Reason |
|---|---|---|---|
| Environment report | `./gradlew -version` | **NOT VERIFIED** | no Gradle wrapper/project yet (Phase 1) |
| Unit tests | `./gradlew testDebugUnitTest` | **NOT VERIFIED** | no project, no gradle, no JDK 17 |
| Lint | `./gradlew lintDebug` | **NOT VERIFIED** | same |
| Debug build | `./gradlew assembleDebug` | **NOT VERIFIED** | no SDK, no JDK 17 |
| Placeholder gate | `./scripts/check-no-placeholders` | **NOT VERIFIED** | script is created in M0 |
| Install | `./gradlew installDebug` | **NOT VERIFIED** | no `adb`, no device |
| Launch | `adb shell am start -n ir.mohammad.vpn/.MainActivity` | **NOT VERIFIED** | no `adb`, no device |
| Cold start | `adb shell am start -W …` | **NOT VERIFIED** | same |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | **BLOCKED** | no device and no KVM for an emulator |

No application-code change was made in this phase.

---

## 3. Upstream dependency verification

### 3.1 Xray-core — `https://github.com/XTLS/Xray-core`

| Field | Value |
|---|---|
| Latest release tag | **v26.10.29** (published 2026-10-29) |
| License | **MPL-2.0** (`spdx_id` from GitHub API) |
| Default branch | `main` |
| `go` directive in `go.mod` | **1.27** |
| Relevant deps | `apernet/quic-go v0.61.1-…`, `refraction-networking/utls`, `pion/*`, `sagernet/sing` |
| Release semantics | rolling date-tags (`vYY.MM.DD`), no LTS branch |

**Protocol support confirmed by source tree at v26.10.29** (`/proxy`, `/transport/internet`):

```
proxy:        blackhole dns dokodemo freedom http hysteria loopback masque
              shadowsocks shadowsocks_2022 socks trojan tun vless vmess wireguard
transport:    grpc httpupgrade hysteria kcp masque reality splithttp tcp tls
              websocket xdrive …
```

Two findings that change the spec:

1. **`proxy/hysteria` exists** in v26.10.29 (client.go, server.go, protocol.go). **Hysteria2 can therefore be served by Xray-core alone** — no extra GPL core is required. *Runtime behaviour still to be proven in M1 with a real endpoint.*
2. **`proxy/wireguard` also exists**, so plain WireGuard can be handled by Xray as well. The AmneziaWG bridge remains necessary only for AWG obfuscation parameters (Jc/Jmin/Jmax/S1–S4/H1–H4).

`proxy/hysteria/config.proto` at this tag declares only `ClientConfig{server}` / `ServerConfig{users}` — a **simplified** Hysteria2 surface (endpoint + auth), not the full hysteria config. Acceptance tests must therefore use the subset Xray accepts; features like `obfs` may be absent. *Marked: HIGH confidence from source, runtime unverified.*

### 3.2 amneziawg-go — `https://github.com/amnezia-vpn/amneziawg-go`

| Field | Value |
|---|---|
| GitHub "Releases" | **none** (`/releases/latest` → 404); tags only |
| Latest tag | **v3.1.20260828** (commit `b5928efb6c`) |
| Module path | `github.com/amnezia-vpn/amneziawg-go/v3` (note the `/v3`) |
| `go` directive | **1.25.0** |
| License | **MIT** |
| Default branch | `master` |

Pinning rule: pin **tag + commit** (`v3.1.20260828` @ `b5928efb6c`), because the project does not publish release artifacts.

### 3.3 Toolchain versions available upstream

| Component | Latest stable | Notes |
|---|---|---|
| Go | **1.27.3** | satisfies xray (`go 1.27`) and awg-go (`go 1.25.0`) |
| `gomobile` | `golang.org/x/mobile` @ `v0.0.0-20261011034528-197770444867` (2026-10-11) | pseudo-version; no tagged release |
| Android Gradle Plugin | **9.4.1** stable (9.5.0 is preview; 9.4.0 documented as Sept 2026) | supports **max API 37** |
| Gradle | **9.8.2** (min 9.6.0 for AGP 9.4) | wrapper version to pin |
| Android SDK Platform | `platform-36_r02`, `platform-36.1_r01`, `platform-36-ext19` (37 available) | `compileSdk 36` |
| Build Tools | `36.0.0` (min/default for AGP 9.4); `36.1`, `37.0.0` available | pin 36.0.0 |
| Android NDK | `r30` latest; AGP 9.4 default `28.2.13676358` | pin 28.2 for AGP compatibility |
| Command-line tools | `commandlinetools-linux-16111833_latest.zip` | provisioning entry point |

### 3.4 AGP 9.4 hard requirements (read from official release notes, 2026-10-27)

| Requirement | Minimum | Default |
|---|---|---|
| Gradle | 9.6.0 | 9.6.0 |
| SDK Build Tools | 36.0.0 | 36.0.0 |
| NDK | n/a | 28.2.13676358 |
| **JDK** | **17** | 17 |
| Max supported API | 37 | — |

**Conflict with the current sandbox:** only JRE 11 is installed and `javac` is absent. JDK 17 must be installed before any Gradle command can run.

---

## 4. Android platform facts verified

Verified against the official `VpnService` contract and cross-checked against a production client's manifest (read in a *separate, unrelated* analysis — used only as API evidence, no code reused):

| Fact | Detail |
|---|---|
| Service declaration | `<service android:permission="android.permission.BIND_VPN_SERVICE">` with `<intent-filter><action android:name="android.net.VpnService"/></intent-filter>` |
| Consent | `VpnService.prepare(context)` returns `null` when already authorised, else an `Intent` that must be launched for result |
| Interface | `VpnService.Builder`: `setSession`, `setMtu`, `addAddress`, `addRoute`, `addDnsServer`, `addAllowedApplication`, `addDisallowedApplication`, `setBlocking`, `establish()` → `ParcelFileDescriptor` or `null` |
| Revocation | `onRevoke()` is called when the user/OS revokes; resources must be released there |
| Always-on | `<meta-data android:name="android.net.VpnService.SUPPORTS_ALWAYS_ON" android:value="true"/>` |
| Foreground | API 34+ requires `android:foregroundServiceType="specialUse"` (plus `FOREGROUND_SERVICE_SPECIAL_USE`) for a long-running VPN service started outside a VPN consent flow |
| Loop prevention | the app's own package must be excluded via `addDisallowedApplication` unless the core needs it inside the tunnel |
| minSdk 26 | satisfies Compose (21+), `VpnService` (14+), and Keystore/EncryptedFile APIs used |

**Note on `setBlocking`:** required for a single-threaded TUN read loop; if the chosen core bridge spawns its own read loop the default non-blocking mode applies. Decision deferred to M1 with the actual gomobile bridge.

---

## 5. Environment vs. specification — discrepancies

| # | Spec assumption | Reality | Impact |
|---|---|---|---|
| D-1 | JDK 17 available | only JRE 11, no `javac` | **Blocker** for any Gradle task until fixed |
| D-2 | Android SDK/NDK present or installable | absent; 20 GiB free | provisioning required (~4–5 GiB) |
| D-3 | Go toolchain for gomobile | absent | provisioning required |
| D-4 | Device/emulator for real-path verification | none, **no KVM** | `connectedDebugAndroidTest` and S-2/S-3 real-device proof **cannot run here**; needs a physical device via `adb connect` or a KVM-capable host |
| D-5 | RAM adequate for Android builds | 3.8 GiB total | Gradle + Kotlin + R8 + gomobile together will thrash; mitigation: `org.gradle.jvmargs=-Xmx1536m`, no parallel workers, `kotlin.incremental=false`, build cores serially, and build the `.aar` cores **once** and cache them |
| D-6 | Hysteria2 needs an extra core | Xray v26.10.29 ships `proxy/hysteria` | simplifies v1; no GPL dependency needed |
| D-7 | amneziawg-go has releases | tags only, module path `/v3` | pin tag+commit; import path must include `/v3` |

---

## 6. Risks

| ID | Risk | Severity | Mitigation |
|---|---|---|---|
| R-1 | Build host cannot run an Android device/emulator (no KVM) | **High** | require a physical device over `adb` (USB/TCP) before M1 acceptance; otherwise instrumented criteria are `NOT VERIFIED` |
| R-2 | 3.8 GiB RAM vs Gradle + gomobile | **High** | serial builds, capped heap, prebuilt cached AARs, `--no-daemon` for gomobile steps |
| R-3 | gomobile + NDK build of Xray-core is the largest single task (Go 1.27, cgo, 4 ABI) | **High** | build only `arm64-v8a` + `x86_64` for debug; add `armeabi-v7a` only for release; consider `-androidapi 26` |
| R-4 | Xray rolling tags change daily; building `main` is unreproducible | Medium | pin tag `v26.10.29` + commit SHA in `core-xray/build.sh`, commit the resulting `.aar` checksum |
| R-5 | awg-go `/v3` module path + no releases | Medium | pin tag `v3.1.20260828`, verify `go get` resolves, vendor with `go mod vendor` |
| R-6 | Hysteria2 surface in Xray is minimal (server + user only) | Medium | validate against a real Hysteria2 endpoint in M1; if `obfs`/`bandwidth` are needed, add `apernet/hysteria` as a separate MIT bridge (deferred) |
| R-7 | Disk 20 GiB free | Low–Medium | SDK 36 + build-tools + NDK 28.2 + Go + Gradle caches ≈ 5 GiB; keep 8 GiB headroom |
| R-8 | Secrets handling in an APK with an operator bundle | Medium | Keystore-wrapped AES-GCM file, `allowBackup=false`, no plaintext in assets (already specified) |

---

## 7. Provisioning plan (defined, NOT executed in Phase 0)

Ordered, idempotent, no destructive steps:

```bash
# 1) JDK 17 (required by AGP 9.4)
sudo apt-get install -y openjdk-17-jdk-headless      # verify: java -version → 17, javac -version

# 2) Android command-line tools
mkdir -p $HOME/android-sdk/cmdline-tools
curl -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip -q /tmp/clt.zip -d $HOME/android-sdk/cmdline-tools && mv $HOME/android-sdk/cmdline-tools/cmdline-tools $HOME/android-sdk/cmdline-tools/latest

# 3) SDK packages (accept licenses non-interactively)
yes | sdkmanager --sdk_root=$HOME/android-sdk --licenses
sdkmanager --sdk_root=$HOME/android-sdk "platform-tools" "platforms;android-36" \
           "build-tools;36.0.0" "ndk;28.2.13676358"

# 4) Go + gomobile
curl -o /tmp/go.tgz https://go.dev/dl/go1.27.3.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf /tmp/go.tgz
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init

# 5) Gradle wrapper is generated by the project (pinned 9.8.2), not installed globally
```

Environment variables to export in the build shell: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`, `ANDROID_HOME=$HOME/android-sdk`, `ANDROID_SDK_ROOT=$HOME/android-sdk`, `PATH=$PATH:/usr/local/go/bin:$HOME/go/bin`.

**Estimated footprint:** JDK ~300 MiB, SDK ~1.4 GiB, NDK ~2.4 GiB, Go ~600 MiB, Gradle caches ~600 MiB → ~5.3 GiB of the 20 GiB free.

**Not provisionable here:** a device or KVM. R-1 stands until a real device is attached.

---

## 8. Decisions taken in Phase 0 (to be recorded in `DECISIONS.md`)

1. **Pin Xray-core `v26.10.29`** (commit recorded from the tag) — not `main`.
2. **Pin amneziawg-go `v3.1.20260828`** (commit `b5928efb6c`), import path `.../v3`.
3. **AGP 9.4.1 + Gradle 9.8.2 + JDK 17 + compileSdk/targetSdk 36 + build-tools 36.0.0 + NDK 28.2.13676358.**
4. **Hysteria2 is served by Xray-core** (no extra core, no GPL dependency); confirmed at source level, runtime check deferred to M1.
5. **`minSdk 26`** as specified.
6. **Debug builds ship `arm64-v8a` + `x86_64` only**; `armeabi-v7a` added for release.
7. **Cores are built once as `.aar` and committed by checksum**, not rebuilt on every Gradle run (R-2/R-4 mitigation).
8. Grep-able proof for every later claim: `docs/VERIFICATION_REPORT.md` starts empty and is only appended to with real command output.

## 9. Open questions requiring confirmation

| # | Question | Default if unanswered |
|---|---|---|
| Q-1 | Confirm app name **Mohammad** and package **`ir.mohammad.vpn`** (irreversible after first release) | proceed as stated |
| Q-2 | Confirm v1 scope excludes subscription/QR/editor/per-app (per §SCOPE) | proceed as stated |
| Q-3 | Confirm both cores ship in v1 (build time + APK size cost) | proceed as stated |
| Q-4 | Confirm operator-bundle delivery (encrypted file in `assets/operator/`) vs. remote fetch | local encrypted bundle |
| Q-5 | Which **device/emulator** will be used for real-path verification, given no KVM here? | none available → S-2/S-3 stay `NOT VERIFIED` |
| Q-6 | Is a **signing key** available for release builds, or debug-only for now? | debug-only |
| Q-7 | For Hysteria2, is the Xray subset (endpoint + auth) sufficient, or are `obfs`/`bandwidth` required? | Xray subset only |

---

## 10. Gate status

| Phase 0 exit criterion | Status |
|---|---|
| Environment inspected and recorded | ✅ |
| Commands verified as far as possible | ✅ (all `NOT VERIFIED`, reasons recorded) |
| Upstream repos, licenses, versions verified | ✅ |
| Android `VpnService` contract confirmed | ✅ |
| Discrepancies and risks recorded | ✅ |
| No application code changed | ✅ |
| Baseline Report produced | ✅ |

**STOP.**
