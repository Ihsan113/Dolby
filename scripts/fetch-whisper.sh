#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="app/src/main/cpp/whisper.cpp"
if [[ -f "$OUT/CMakeLists.txt" ]]; then
  echo 'whisper.cpp already present'
  exit 0
fi
# Fixed upstream release; not master to prevent incompatible API changes.
git clone --depth 1 --branch v1.9.5 https://github.com/ggml-org/whisper.cpp.git "$OUT"
# Upstream ggml code ships inside whisper.cpp; do not clone any arbitrary binary.
echo "Pinned native dependency: $(git -C "$OUT" rev-parse HEAD)"
