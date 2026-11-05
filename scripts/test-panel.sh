#!/usr/bin/env bash
# Builds the panel assets and runs the JS suites: pure helpers, the panel worker against SQLite,
# and the node worker with a stubbed sockets/WebSocketPair pair.
set -euo pipefail
cd "$(dirname "$0")/.."
node panel/build.mjs
node --test --test-force-exit --test-timeout=20000 panel/test/lib.test.mjs panel/test/worker.test.mjs panel/test/proxy.test.mjs
