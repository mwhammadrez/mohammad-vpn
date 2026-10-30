# TASK: Verify an Android app live on a host with no device and no /dev/kvm

This is a reproducible description of the toolchain that was actually installed and run on an
Ubuntu host with 3.8 GiB RAM, JDK 17, and no attached Android device. Everything below was executed;
the versions are the ones that worked.

## 1. Environment

| Item | Value |
|---|---|
| JDK | OpenJDK 17.0.20.1 |
| Gradle | 8.14.6 (wrapper) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.0.21 |
| compileSdk / minSdk | 36 / 26 |
| Host | x86_64 Linux, 3.8 GiB RAM, no KVM, no adb device |

## 2. Tools installed (all free, all open source)

| Tool | Version | Role | Installed as |
|---|---|---|---|
| Robolectric | 4.14.1 | Runs the Android framework inside the JVM: creates real `Service` and `Activity` instances, parses the real manifest and resources | `testImplementation` |
| Roborazzi | 1.36.0 | Captures a Compose tree to PNG under Robolectric | `testImplementation` |
| Compose UI Test | BOM 2024.12.01 | Drives the real UI: find node, click, assert | `testImplementation` |
| androidx.test core / ext-junit | 1.6.1 / 1.2.1 | `ApplicationProvider`, `AndroidJUnit4` | `testImplementation` |
| detekt CLI | 1.23.8 (`detekt-cli-1.23.8-all.jar`) | Kotlin static analysis (unused parameters, dead code, complexity) | standalone jar from GitHub releases |
| mobsfscan | 1.1.0 | Android security static scan (OWASP MASVS rules) | `pip install mobsfscan` |
| staticcheck | latest | Go static analysis | `go install honnef.co/go/tools/cmd/staticcheck@latest` |
| govulncheck | latest | Reachable-vulnerability scan for Go modules | `go install golang.org/x/vuln/cmd/govulncheck@latest` |
| go vet | Go 1.27 | Standard Go checks | toolchain |
| Android Lint | AGP 8.13.2 | `./gradlew lintDebug` | SDK |
| Headless Chrome | system | Renders an HTML design mock to PNG when a visual reference is needed | preinstalled |
| uguu.se | — | Hosts the resulting PNG for review (`curl -F "files[]=@x.png" https://uguu.se/upload`) | HTTP API |

Nothing above ships inside the APK: all test libraries are `testImplementation`, and the only
`debugImplementation` addition is `androidx.compose.ui:ui-test-manifest` (an empty, unexported
`ComponentActivity` needed to host a test composition).

## 3. Exact wiring that makes this work

`app/build.gradle.kts`

```kotlin
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.logging", "stdout")
                it.systemProperty("roborazzi.test.record", "true")   // without this no PNG is written
            }
        }
    }
}

dependencies {
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

`app/src/test/resources/robolectric.properties`

```properties
sdk=34
qualifiers=w411dp-h891dp-xxhdpi
```

## 4. What is tested, and what each suite proves

| Test class | Framework | What it actually executes |
|---|---|---|
| `LaunchTest` | Robolectric | Builds the real `MainActivity`, runs `onCreate` → `setContent`, reaches RESUMED, then destroys it |
| `ServiceLifecycleTest` | Robolectric | Builds the real VPN `Service`, calls `onStartCommand` with CONNECT/DISCONNECT intents, asserts state transitions and that no exception escapes |
| `UiInteractionTest` | Robolectric + Compose Test | Taps the power dial, both toggles and a profile row; asserts the callbacks receive the right values |
| `RenderTest` | Robolectric + Roborazzi | Renders the whole screen at 411×891 dp and at 320×640 dp into PNGs |
| `BundleLoadTest` | Robolectric | Decrypts the bundle that ships inside the APK (PBKDF2 + AES-GCM) and validates profiles, MTU, DNS |
| `CrashRecorderTest` | Robolectric | Installs the uncaught-exception handler, invokes it, asserts the report is persisted and consumed once |
| `HandoverPolicyTest`, `SocksProbeTest`, `TunPlanTest`, `TransportTuningTest`, `ConfigRenderTest`, … | plain JUnit | Pure decision logic, SOCKS5 handshake against a fake server, route set algebra, config rendering |

## 5. Commands and results

```bash
./gradlew testDebugUnitTest     # 83 tests, 0 failures
./gradlew lintDebug             # 0 errors
./gradlew assembleDebug         # BUILD SUCCESSFUL
./scripts/check-no-placeholders  # no banned markers
java -jar detekt.jar --input app/src/main/java --build-upon-default-config
mobsfscan --no-fail app/src/main/java     # 6 INFO, no secrets, no insecure comms
cd core-go && go vet ./... && staticcheck ./... && govulncheck ./libmohammad/...
```

## 6. Four pitfalls that cost real time (avoid them)

1. **`onRoot().captureToImage()` cannot work under Robolectric.** It calls `forceRedraw`, which waits
   for a frame callback that never fires → `ComposeTimeoutException` after 2 s. Use Roborazzi's
   `captureRoboImage(path)` instead; it draws the view directly.
2. **Roborazzi 1.77 requires Kotlin 2.3 metadata** and fails to compile against Kotlin 2.0.21.
   Use 1.36.0.
3. **An infinite animation prevents `waitForIdle` from ever returning.** Set
   `compose.mainClock.autoAdvance = false` and advance the clock by hand. Better: do not create the
   transition at all when it is idle (also a real performance fix).
4. **Compose test rule needs an activity in the manifest.** Without
   `debugImplementation("androidx.compose.ui:ui-test-manifest")` every test fails with
   "Unable to resolve activity for Intent ... ComponentActivity".

## 7. Coverage limits (state them, never hide them)

- Robolectric does **not** load native libraries, so the Go core (`libgojni.so`), the tun2socks
  bridge and any JNI-related crash are **out of reach**. A crash that happens inside JNI can only be
  observed on a device.
- There is no real network: DPI-evasion effects, DNS behaviour and actual throughput are unverified.
- Rendering is real (native Skia via Robolectric), but it is not a device GPU path; font metrics and
  hardware-layer differences remain device-only.
- No emulator was installed: without KVM an x86 AVD boot is impractical on 3.8 GiB of RAM.
