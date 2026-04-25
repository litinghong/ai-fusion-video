import { copyFileSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = resolve(desktopRoot, "..");
const sourceIcon = join(repoRoot, "ai-fusion-video-web", "public", "logo.png");
const iconDir = join(desktopRoot, "src-tauri", "icons");
const appIcon = join(iconDir, "app-icon.png");

if (!existsSync(sourceIcon)) {
  throw new Error(`Icon source not found: ${sourceIcon}`);
}

mkdirSync(iconDir, { recursive: true });
copyFileSync(sourceIcon, appIcon);

console.log(`Copied ${sourceIcon} to ${appIcon}`);
