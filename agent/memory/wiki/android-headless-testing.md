---
name: android-headless-testing
description: How to run, render and audit an Android app without a device (Robolectric + Roborazzi + detekt + mobsfscan + Go analyzers)
type: reference
logged: 2026-10-30
updated: 2026-10-30
---
Toolchain and traps: `/home/user/.workspace/skills/android-headless-debug/SKILL.md`
Long write-up: `~/.workspace/mohammad/docs/TESTING_TOOLCHAIN.md`

Use when asked to test, screenshot or debug an Android app on a host with no device/emulator.
Key facts: Robolectric 4.14.1 + Roborazzi 1.36.0 (1.77 incompatible with Kotlin 2.0);
`captureToImage` fails under Robolectric, use `captureRoboImage`; needs
`ui-test-manifest`; pause the test clock for infinite animations.
