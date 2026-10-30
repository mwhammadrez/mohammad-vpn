# Performance and quality audit — 2026-10-30

Baseline: 0.10.1 (versionCode 25). Result: 0.10.2 (versionCode 26).

## What could and could not be measured

| Metric | How | Status |
|---|---|---|
| Idle recomposition count | Compose test harness (`RecompositionTest`) | **measured** |
| Recomposition while connecting | same | **measured** |
| APK size, DEX size, method count | `aapt2` / `unzip` / `dexdump` | **measured** |
| Lint findings | `lintDebug` XML | **measured** |
| Dead code / unused branches | detekt + Lint | **measured** |
| Frame timing (p50/p95/p99), jank rate | JankStats | **NOT VERIFIED — needs a device** |
| Cold start `am start -W` | adb | **NOT VERIFIED — needs a device** |
| Memory after 5 cycles | `dumpsys meminfo` | **NOT VERIFIED — needs a device** |

No number in the last three rows exists in this report. Inventing them would be worse than
leaving them out.

## Measurements

| Metric | 0.10.1 | 0.10.2 | Change |
|---|---|---|---|
| Idle recompositions over ~60 frames | 0 | 0 | already optimal (D-40) |
| Recomposition during Connecting animation | 0 | 0 | animation lives in the draw phase |
| Lint findings | 19 | 15 | −4 |
| — `ObsoleteSdkInt` | 3 | 1 | dead `SDK_INT` branches removed |
| — `AutoboxingStateCreation` | 1 | 0 | `mutableLongStateOf` |
| — `MissingApplicationIcon` | 1 | 0 | adaptive icon added |
| — `UseKtx` | 4 | 1 | `prefs().edit { }` |
| APK | 15.0 MB | 14.97 MB | −0.03 MB |
| DEX | 1.39 MB | 1.39 MB | flat |
| Methods | 10 618 | 10 635 | +17 (Compose test deps are test-only; the +17 is the adaptive icon + KTX helper) |
| Tests | 85 | 87 | +2 |

The remaining `ObsoleteSdkInt` is `mipmap-anydpi-v26`: adaptive icons are only valid in a
`v26` folder — moving them to `mipmap-anydpi` makes AAPT skip the file and the build fails with
"resource mipmap/ic_launcher not found". The warning is accepted deliberately.

The remaining 13 findings are `GradleDependency` / `NewerVersionAvailable` (dependency refresh)
and `ChromeOsAbiSupport` (arm64-only is intentional). None is a defect.

## Issues found and fixed

| # | Finding | Class | Fix |
|---|---|---|---|
| 1 | Three `Build.VERSION.SDK_INT` branches that can never be false at minSdk 26 | Minor (dead code) | removed; `Notification.Builder(this, CHANNEL_ID)` and `stopForeground(STOP_FOREGROUND_REMOVE)` are unconditional |
| 2 | `mutableStateOf(0L)` boxing a Long every tick | Minor | `mutableLongStateOf` |
| 3 | No application icon (`android:icon` unset) | Major (user-visible) | adaptive icon: original mint power-glyph on `#050807` |
| 4 | Four `SharedPreferences.edit().apply()` sites | Minor | KTX `edit { }` |
| 5 | 12-row console rebuilt per log line | Minor | already bounded in 0.10.1; unchanged |

## Verified "no wasted frames" claim

`RecompositionTest` drives the real screen with `mainClock.autoAdvance = false`, counts body
compositions through a `SideEffect`, then advances 60 frames plus 10 s of virtual time:

```
idle screen does not recompose while frames advance   PASS
connecting state animates without recomposing the body PASS
```

This is the measurable half of "no wasted recomposition". Frame *duration* is a different
question and needs a device.

## Dead code and dependencies

- `detekt` reports no unused private members after the 0.9.x cleanup.
- No unused resources flagged by Lint.
- `ui-test-manifest` remains a `debugImplementation`: required by the Compose test rule.
  It adds one unexported, never-started `ComponentActivity` to debug builds only.

## Remaining known limitations

1. Jank, startup and memory are unmeasured — no device attached.
2. Baseline profile cannot be generated without a device; it is the largest remaining
   startup win.
3. `XrayConfig.render(mtu)` still takes an unused parameter (API lineage, not a defect).
4. Dependency refresh (13 advisories) was skipped on purpose: each bump needs its own
   regression run and this task is not a dependency-upgrade task.

## Verdict

**OPTIMIZED WITH KNOWN LIMITATIONS**

Host-measurable work is done and evidenced: zero idle recomposition, no dead SDK branches, an
icon, tighter lint. Runtime smoothness (fps, jank, startup, memory) is **NOT VERIFIED** and
must be measured on the device before anyone claims the app is "buttery smooth".
