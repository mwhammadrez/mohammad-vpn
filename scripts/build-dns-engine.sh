#!/usr/bin/env bash
# Builds the DNS-tunnel client engine and puts it where Android can exec it.
#
# Why an executable and not a library: the engine is a Go program with its own CLI and config file.
# Android allows executing files from `nativeLibraryDir`, so a binary shipped as `lib*.so` inside
# jniLibs is extracted there at install time (the app already sets useLegacyPackaging = true).
#
# Licence: MasterDnsVPN is MIT. WhiteDNS, which motivated this work, is proprietary and none of its
# code is used here (D-112).
set -euo pipefail
cd "$(dirname "$0")/.."

SRC="${MDVPN_SRC:-/tmp/masterdns}"
PIN=4144e544d220681a8e8fe808855ac09dcd07c31c
NDK="${ANDROID_NDK_HOME:-$HOME/tools/android-sdk/ndk/28.2.13676358}"
ABI="${ABI:-arm64-v8a}"
OUT="app/src/main/jniLibs/$ABI/libmdvpn_client.so"

case "$ABI" in
  arm64-v8a) GOARCH=arm64;  CC_TARGET=aarch64-linux-android26-clang ;;
  x86_64)    GOARCH=amd64;  CC_TARGET=x86_64-linux-android26-clang ;;
  *) echo "unsupported ABI: $ABI" >&2; exit 2 ;;
esac

if [ ! -d "$SRC/.git" ]; then
  echo "cloning MasterDnsVPN at $PIN into $SRC"
  git clone -q https://github.com/masterking32/MasterDnsVPN "$SRC"
fi
git -C "$SRC" fetch -q origin "$PIN" 2>/dev/null || true
git -C "$SRC" checkout -q "$PIN" || { echo "pin $PIN not available" >&2; exit 3; }

TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$TC/$CC_TARGET"
[ -x "$CC" ] || { echo "NDK compiler not found: $CC" >&2; exit 4; }

echo "building engine for android/$ABI"
(cd "$SRC" && CGO_ENABLED=1 CC="$CC" GOOS=android GOARCH="$GOARCH" \
  go build -trimpath -buildvcs=false -ldflags="-s -w" -o /tmp/mdvpn_client_$ABI ./cmd/client)

mkdir -p "$(dirname "$OUT")"
cp "/tmp/mdvpn_client_$ABI" "$OUT"
chmod 755 "$OUT"
"$TC/llvm-strip" --strip-all "$OUT" 2>/dev/null || true

echo "engine: $(ls -lh "$OUT" | awk '{print $5}')  sha256: $(sha256sum "$OUT" | cut -c1-16)"
