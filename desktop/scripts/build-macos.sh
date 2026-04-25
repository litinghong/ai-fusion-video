#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

TARGET="${1:-universal-apple-darwin}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "macOS packages must be built on macOS."
  exit 1
fi

case "$TARGET" in
  universal-apple-darwin)
    rustup target add x86_64-apple-darwin aarch64-apple-darwin
    ;;
  x86_64-apple-darwin|aarch64-apple-darwin)
    rustup target add "$TARGET"
    ;;
  *)
    echo "Unsupported macOS target: $TARGET"
    exit 1
    ;;
esac

if [[ ! -d node_modules ]]; then
  pnpm install
fi

node scripts/generate-icons.mjs
pnpm tauri icon src-tauri/icons/app-icon.png
pnpm tauri build --target "$TARGET" --bundles app,dmg
