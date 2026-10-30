# Publish report — 2026-10-30

## Result

Repository: https://github.com/mwhammadrez/mohammad-vpn (private)
Branch: `main`

## Changes published

| Commit | Change |
|---|---|
| `chore: complete .gitignore for Android/Gradle/Go project` | added `*.aab`, `*.jks`, `*.keystore`, `.DS_Store`, `.cxx/`, `.env`, `captures/` |
| `fix: replace operator UUID with a random fixture in tests` | real VLESS UUID removed from `ConfigRenderTest` and `RedactionTest` |
| `chore: add Apache-2.0 license` | `LICENSE` at repository root |
| `chore: stop tracking generated gomobile jars` | 4 `-sources.jar` files untracked (kept on disk) |

## Verification

- `git ls-files | grep sources.jar` → 0
- `git grep 0722d93a` → no hits
- Gates: `clean`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `check-no-placeholders` — all pass.

## Known limitations

1. The operator UUID remains in earlier history of this private repository. Rotate the UUID server-side to retire it fully.
2. `assembleDebug` requires `app/libs/mohammad-core.aar`, which is committed; rebuilding it needs the Go toolchain and the `/tmp` source checkouts referenced by `core-go/go.mod`.
3. Behavioural verification on a device is still **NOT VERIFIED**.
