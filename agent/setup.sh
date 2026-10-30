#!/bin/sh
# Restores the assistant-side memory and skills into the workspace.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
ws="${HOME}/.workspace"
mkdir -p "$ws/memory/wiki" "$ws/skills"
cp "$here/memory/MEMORY.md" "$ws/MEMORY.md"
cp "$here"/memory/wiki/*.md "$ws/memory/wiki/"
cp "$here"/memory/journal-*.md "$ws/memory/" 2>/dev/null || true
cp -r "$here"/skills/* "$ws/skills/" 2>/dev/null || true
echo "restored into $ws:"
ls "$ws/memory/wiki" "$ws/skills"
