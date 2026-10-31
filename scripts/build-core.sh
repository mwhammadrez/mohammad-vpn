#!/usr/bin/env bash
# Rebuilds app/libs/mohammad-core.aar from the pinned Xray + AmneziaWG sources, applying the
# patches in core-go/patches in order.
#
# Why patches exist: the pinned Xray refuses to build an unencrypted VLESS/Trojan outbound to a
# public address (#6303). Operator bundles still contain such profiles, so the client must accept
# them while saying so in the log. See DECISIONS.md D-96.
set -euo pipefail
cd "$(dirname "$0")/.."

XRAY_SRC="${XRAY_SRC:-/tmp/xray-src}"
XRAY_PIN=0afec66085a85191e9dd2a4a6646c7c9a94688d1
GOBIN="${GOBIN:-$HOME/go/bin}"
export PATH="/usr/local/go/bin:${GOBIN}:${PATH}"

if [ ! -d "$XRAY_SRC/.git" ]; then
  echo "cloning xray-core at $XRAY_PIN into $XRAY_SRC"
  git clone -q https://github.com/XTLS/Xray-core "$XRAY_SRC"
  git -C "$XRAY_SRC" checkout -q "$XRAY_PIN" || {
    echo "pin $XRAY_PIN not found; fetch it explicitly" >&2; exit 2; }
fi

for patch in core-go/patches/*.patch; do
  [ -e "$patch" ] || continue
  if git -C "$XRAY_SRC" apply --check "$OLDPWD/$patch" 2>/dev/null; then
    git -C "$XRAY_SRC" apply "$OLDPWD/$patch"
    echo "applied $(basename "$patch")"
  elif git -C "$XRAY_SRC" apply --check --reverse "$OLDPWD/$patch" 2>/dev/null; then
    echo "already applied $(basename "$patch")"
  else
    echo "patch $(basename "$patch") does not apply cleanly" >&2
    exit 3
  fi
done

cd core-go
# The module replaces xray-core with this path; keep them in sync.
GOFLAGS="-mod=mod" GOMAXPROCS="${GOMAXPROCS:-2}" GOGC="${GOGC:-50}" \
  gomobile bind \
  -target=android/arm64,android/amd64 -androidapi 26 \
  -o artifacts/mohammad-core.aar ./libmohammad
cd ..
./scripts/strip-aar.sh core-go/artifacts/mohammad-core.aar
cp core-go/artifacts/mohammad-core.aar app/libs/mohammad-core.aar
echo "core rebuilt: $(ls -lh app/libs/mohammad-core.aar | awk '{print $5}')"
