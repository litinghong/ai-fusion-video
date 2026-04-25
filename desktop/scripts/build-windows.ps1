param(
  [ValidateSet("x64", "arm64", "all")]
  [string]$Arch = "all"
)

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

$targets = @()
if ($Arch -eq "x64" -or $Arch -eq "all") {
  $targets += "x86_64-pc-windows-msvc"
}
if ($Arch -eq "arm64" -or $Arch -eq "all") {
  $targets += "aarch64-pc-windows-msvc"
}

if (-not (Test-Path "node_modules")) {
  pnpm install
}

node scripts/generate-icons.mjs
pnpm tauri icon src-tauri/icons/app-icon.png

foreach ($target in $targets) {
  rustup target add $target
  pnpm tauri build --target $target --bundles nsis
}
