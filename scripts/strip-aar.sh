#!/usr/bin/env bash
# Strip DWARF/symbol tables from the gomobile-produced shared libraries inside an
# AAR. Keeps dynamic symbols, removes debug sections: ~40% smaller .so.
set -euo pipefail
AAR="${1:?usage: strip-aar.sh <aar>}"
NDK_BIN="${ANDROID_HOME:?ANDROID_HOME not set}/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin"
STRIP="$NDK_BIN/llvm-strip"
WORK="$(mktemp -d)"
cp "$AAR" "$WORK/in.aar"
mkdir -p "$WORK/x"
unzip -q "$WORK/in.aar" -d "$WORK/x"
find "$WORK/x/jni" -name '*.so' -print0 | while IFS= read -r -d '' so; do
  before=$(stat -c%s "$so")
  "$STRIP" --strip-debug "$so"
  after=$(stat -c%s "$so")
  echo "stripped $(basename "$so"): $before -> $after bytes"
done
( cd "$WORK/x" && zip -qr "$WORK/out.aar" . )
cp "$WORK/out.aar" "$AAR"
rm -rf "$WORK"
sha256sum "$AAR"
