# Resume in a fresh session (or a different account)

Everything needed to continue this project from zero context lives in this repository.

## Step 1 — get the code

```bash
git clone https://github.com/mwhammadrez/mohammad-vpn
cd mohammad-vpn
```

The repository is private: use a fine-grained token with `Contents: read` on that repo.

## Step 2 — read, in this order

| File | Why |
|---|---|
| `docs/HANDBOOK.txt` | the whole project end to end: architecture, design, build, history, decisions, open issues |
| `STATUS.md` | what is verified vs not verified |
| `DECISIONS.md` | why each choice was made (40 entries) |
| `PROGRESS.md` | per-release narrative |

## Step 3 — restore the agent-side memory (optional but recommended)

The assistant kept a small memory outside the repository. It is mirrored here:

```bash
bash agent/setup.sh          # copies memory and skills into ~/.workspace
```

Then load, in this order:

1. `agent/memory/MEMORY.md` — the resident index
2. `agent/memory/wiki/*.md` — one file per memory (user profile, testing references, lessons)
3. `agent/skills/android-headless-debug/SKILL.md` — how to run and render the app with no device

## Step 4 — environment

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:/usr/local/go/bin:$HOME/go/bin:$PATH
./gradlew testDebugUnitTest lintDebug assembleDebug   # 85 tests must stay green
```

Rebuilding the native core (only when Go changes) is described in `docs/HANDBOOK.txt` section 11.

## Step 5 — secrets you must obtain from the operator

These are deliberately absent from the repository:

| Secret | Where it must go | Why |
|---|---|---|
| operator bundle password | `local.properties` as `mohammad.operatorPassword=` | decrypts `bundle.enc` at build time |
| `operator/bundle.source.json` | same path, mode 0600 | the plaintext profile (UUID, server) |

Without them the app builds but ships no profile. **Never commit either.**

## Ground rules that must survive a handover

1. Never copy code, strings, icons or assets from any proprietary client. Behavioural reference only.
2. Every dependency pinned to an exact tag + commit, licence checked first.
3. Test first: failing test → implementation → real command output. Never weaken or skip a test.
4. Never log or commit secrets, UUIDs, passwords, private keys or full endpoints.
5. No device and no KVM: anything device-dependent is `NOT VERIFIED`, never assumed.
6. Structural, behavioural and feature-parity verdicts stay separate.
7. The APK embeds the bundle password — it must never be published.

## The one lesson worth reading twice

v0.7.0 shipped a new data path where both ends were built and tested but the middle was
never wired: the config had no tun inbound and the bridge was only ever stopped, never started.
Every unit test passed; the app carried no traffic. After any architectural change, prove the
**chain**, not the parts.
