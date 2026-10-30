---
name: android-headless-debug
description: Debug, run and visually verify an Android/Kotlin/Compose app on a host with no device and no KVM, using Robolectric + Roborazzi + detekt + mobsfscan + Go analyzers.
---

# Headless Android debugging (no device, no KVM)

Proven on JDK 17.0.20.1, Gradle 8.14.6, AGP 8.13.2, Kotlin 2.0.21, x86_64 Linux 3.8 GiB RAM.

## Install (test-only, never shipped)

```kotlin
android {
    testOptions { unitTests {
        isIncludeAndroidResources = true
        all {
            it.systemProperty("robolectric.logging", "stdout")
            it.systemProperty("roborazzi.test.record", "true")   // no PNG without this
        }
    } }
}
dependencies {
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")  // ComponentActivity for tests
}
```

`app/src/test/resources/robolectric.properties`:
```
sdk=34
qualifiers=w411dp-h891dp-xxhdpi
```

## Static analysis

```bash
java -jar detekt.jar --input app/src/main/java --build-upon-default-config   # detekt 1.23.8
mobsfscan --no-fail app/src/main/java                                      # pip install mobsfscan
cd core-go && go vet ./... && staticcheck ./... && govulncheck ./libmohammad/...
```

## Traps that waste hours

1. `onRoot().captureToImage()` never works under Robolectric (`forceRedraw` waits for a frame that
   never arrives, 2 s timeout). Use `captureRoboImage(path)`.
2. Roborazzi >= 1.77 needs Kotlin 2.3 metadata; use 1.36.0 with Kotlin 2.0.x.
3. Infinite animations block `waitForIdle`. Set `mainClock.autoAdvance = false` and advance manually;
   better, don't create the transition while idle.
4. Without `ui-test-manifest` the rule fails: "Unable to resolve activity ... ComponentActivity".

## What it can and cannot prove

Can: activity launch, service lifecycle via `Robolectric.buildService`, real Compose taps, real
rendering to PNG (native Skia), asset decryption, crash handler round-trips.
Cannot: JNI/native libraries, real network, GPU-specific rendering. Those need a device.

Full write-up: `docs/TESTING_TOOLCHAIN.md` in the mohammad repo.
