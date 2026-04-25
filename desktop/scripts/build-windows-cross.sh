#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

TARGET="${1:-x86_64-pc-windows-msvc}"

case "$TARGET" in
  x86_64-pc-windows-msvc|aarch64-pc-windows-msvc)
    ;;
  *)
    echo "Unsupported Windows target: $TARGET"
    exit 1
    ;;
esac

if ! command -v cargo-xwin >/dev/null 2>&1; then
  echo "cargo-xwin is required. Install it with: cargo install --locked cargo-xwin"
  exit 1
fi

rustup target add "$TARGET"

if [[ ! -d node_modules ]]; then
  pnpm install
fi

node scripts/generate-icons.mjs
pnpm tauri icon src-tauri/icons/app-icon.png
pnpm tauri build --runner cargo-xwin --target "$TARGET"
