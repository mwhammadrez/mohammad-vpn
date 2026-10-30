# reference/ — analysis notes only

**This folder is not part of the application.** Nothing here is compiled, packaged, or
shipped in the APK. It exists so the reasoning behind architectural decisions is reviewable.

## What is here

| File | Content |
|---|---|
| `ANALYSIS.md` | Our own written analysis of a third-party client's *behaviour* (config shape, data path, defaults) |

## What is deliberately NOT here

No source code, decompiled output, resources, strings, icons, layouts, presets, rule
datasets, or signing material from any third-party application. The analysed app is
proprietary and carries no licence permitting redistribution. Publishing extracted code
would be a copyright violation regardless of repository visibility, and it is not needed:
everything the project uses is either written from scratch or comes from a permissively
licensed dependency (see `THIRD_PARTY.md`).

## How the analysis was produced (reproducible, no redistribution)

1. Obtain the official APK from its publisher.
2. Record its SHA-256.
3. Decompile locally with `jadx` and `apktool`, inspect the manifest, assets and native
   dependency block (`strings lib/*/libgojni.so | grep '^dep'`).
4. Write down *behavioural observations*, not code.

An example of a recorded identity (third-party, analysis only):

```
package      com.<redacted>.android
version      5.26.83 (83)
sha256       <recorded locally; not published here>
```

## Why this distinction matters

Architecture patterns (TUN → userspace stack → SOCKS → core) are not copyrightable and are
implemented by many independent clients. The implementation in this repository is original
and uses only components with compatible licences.
