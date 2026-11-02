#!/usr/bin/env bash
# Builds the panel assets and runs both JS suites: pure helpers and the worker against SQLite.
set -euo pipefail
cd "$(dirname "$0")/.."
node panel/build.mjs
node --test panel/test/lib.test.mjs panel/test/worker.test.mjs
