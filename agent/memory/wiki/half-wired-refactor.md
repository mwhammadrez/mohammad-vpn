---
name: half-wired-refactor
description: Refactors that change both ends of a seam but leave the call site — passes tests, fails on device
type: error
logged: 2026-10-30
updated: 2026-10-30
occurrences: 1
last_seen: 2026-10-30
status: mitigated
---

## Symptom
v0.7.0 shipped with a new architecture (TUN → tun2socks → SOCKS → core): the
config renderer dropped the tun inbound and a Go `TunBridge` was written and
unit-tested. On the device the core started fine (`xray started in 117 ms`) but
egress never changed — nothing read the TUN device, because the Kotlin call site
still passed the descriptor to the core. The bridge was only ever *stopped*,
never started.

## Root cause
I changed both ends of a seam (renderer + bridge) and verified each end
separately, but never verified the seam itself. Every test covered one component;
no test asserted "after startXray, a bridge exists and was started". The commit
message (`feat!: adopt tun2socks → SOCKS`) claimed completion that the code did
not deliver.

## Fix / avoidance
- When a refactor crosses a boundary (Kotlin ↔ Go, service ↔ core), grep for the
  call site *after* the change and confirm the old call is gone; do not trust the
  compile to catch it (Kotlin happily compiles a call that still type-checks).
- Prefer an explicit integration assertion (fake core, assert bridge.start called)
  over per-component tests for seam changes.
- Word commits by what is *verified*, not by what is intended.
