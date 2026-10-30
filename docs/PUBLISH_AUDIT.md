# Publish audit — 2026-10-30

Read-only audit run before cleanup.

## Repository state

| Item | Value |
|---|---|
| Branch | `main` |
| Commits before cleanup | 15 |
| Working tree | clean |
| Remote | `https://github.com/mwhammadrez/mohammad-vpn.git` (private) |

## Secret scan

| Pattern | Hits | Verdict |
|---|---|---|
| Operator UUID `0722d93a-…` in `ConfigRenderTest.kt`, `RedactionTest.kt` | 2 | **REAL** — replaced with a random fixture |
| `password =` in SPEC/README/docs | 4 | False positive — placeholder text describing the KDF |
| `BuildConfig.OPERATOR_PASSWORD` in code | 2 | False positive — variable name, value injected at build time |
| Private keys, keystores, tokens | 0 | Clean |

`bundle.enc`, `bundle.source.json` and `local.properties` are not tracked and were removed from history earlier.

## Tracked binaries

| File | Size | Decision |
|---|---|---|
| `app/libs/mohammad-core.aar` | 28 MB | keep — required to build without the Go toolchain |
| `Mohammad-0.7.1-debug-arm64.apk` | 50 MB | keep — operator-requested backup |
| 4 × `*-sources.jar` (gomobile output) | 8–12 KB | removed from tracking |

## .gitignore gaps

`*.aab`, `*.jks`, `*.keystore`, `.DS_Store`, `.cxx/`, `.env`, `captures/`, `core-*/artifacts/*.jar`, `third_party/anet/mobile/libs/` — all added.

## Missing documentation

- `LICENSE` — added (Apache-2.0).
- `docs/PUBLISH_REPORT.md` — added with this cleanup.

## Follow-ups

- The operator UUID still exists in earlier GitHub history (private repository). Rotating the UUID at the server is the only complete remedy.
