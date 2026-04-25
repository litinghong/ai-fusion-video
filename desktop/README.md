# AI Fusion Video 桌面端打包说明

这个目录是基于 Tauri v2 的桌面外壳。桌面端不会内置后端或前端静态资源，而是直接加载线上地址：

```text
https://fusion.aigateways.cn
```

## 目录结构

```text
desktop/
  package.json
  scripts/
    build-macos.sh
    build-windows-cross.sh
    build-windows.ps1
    generate-icons.mjs
  src-tauri/
    Cargo.toml
    tauri.conf.json
    src/
```

## 环境要求

通用要求：

- Node.js 20+。
- pnpm。
- Rust stable。
- Tauri 系统依赖。

macOS 打包要求：

- macOS 机器。
- Xcode Command Line Tools。
- 如需发布给用户下载，建议配置 Apple Developer ID 签名和 notarization。

Windows 打包要求：

- 推荐在 Windows 机器上构建 Windows 包。
- Visual Studio Build Tools，包含 Desktop development with C++。
- Windows ARM64 包需要安装 VS2022 的 C++ ARM64 build tools。

macOS 交叉构建 Windows 包是可选方案，只建议在没有 Windows 构建机时使用。需要额外安装 NSIS、LLVM、`cargo-xwin`。

## 初始化依赖

```bash
cd desktop
pnpm install
```

## 本地运行

```bash
cd desktop
pnpm dev
```

开发模式会打开一个桌面窗口并加载 `https://fusion.aigateways.cn`。

## 生成图标

```bash
cd desktop
pnpm icons
```

`scripts/generate-icons.mjs` 会把 `../ai-fusion-video-web/public/logo.png` 同步为 Tauri 的图标源文件 `src-tauri/icons/app-icon.png`，随后 `tauri icon` 会补齐 Tauri 需要的 `.icns`、`.ico` 和其他平台图标。

## 打包 macOS

推荐生成同时支持 Intel 和 Apple Silicon 的 Universal 包：

```bash
cd desktop
pnpm build:mac
```

也可以分别构建：

```bash
pnpm build:mac:x64
pnpm build:mac:arm64
```

输出目录通常在：

```text
desktop/src-tauri/target/universal-apple-darwin/release/bundle/
desktop/src-tauri/target/x86_64-apple-darwin/release/bundle/
desktop/src-tauri/target/aarch64-apple-darwin/release/bundle/
```

常见产物：

- `.app`
- `.dmg`

## 打包 Windows

推荐在 Windows 机器上执行：

```powershell
cd desktop
pnpm build:win
```

分别构建：

```powershell
pnpm build:win:x64
pnpm build:win:arm64
```

输出目录通常在：

```text
desktop/src-tauri/target/x86_64-pc-windows-msvc/release/bundle/nsis/
desktop/src-tauri/target/aarch64-pc-windows-msvc/release/bundle/nsis/
```

常见产物：

- `*-setup.exe`

正式 release 包已配置为 Windows GUI 子系统，双击启动时不会额外弹出 DOS/命令行窗口。通过 `pnpm dev` 启动时仍会绑定当前开发终端，这是正常的开发模式行为。

## 在 macOS 上交叉构建 Windows

先安装额外工具：

```bash
brew install nsis llvm
cargo install --locked cargo-xwin
rustup target add x86_64-pc-windows-msvc
rustup target add aarch64-pc-windows-msvc
```

然后构建：

```bash
cd desktop
pnpm build:win:x64:cross
pnpm build:win:arm64:cross
```

交叉构建 Windows 包存在官方提示的限制，签名和部分安装器行为最好仍在 Windows 构建机或 CI 中验证。

注意：macOS 上交叉构建 Windows 时不要给 Tauri CLI 额外传 `--bundles nsis`。Tauri CLI 在 macOS 主机上会把 `--bundles` 的可选值限制为 macOS/iOS 类型，Windows 的 NSIS 目标由 `src-tauri/tauri.conf.json` 里的 `bundle.targets` 控制。

## 配置说明

核心配置在 `src-tauri/tauri.conf.json`：

- `build.frontendDist` 指向 `https://fusion.aigateways.cn`，表示生产包加载远程站点，不内置 web 资源。
- `app.windows[0].url` 指向同一个线上地址。
- `bundle.targets` 包含 `app`、`dmg`、`nsis`。
- Windows 安装器使用 NSIS，安装范围默认为当前用户。
- WebView2 安装模式使用 `downloadBootstrapper`，安装包较小，但首次安装时可能需要联网补齐 WebView2 Runtime。

## 发布注意事项

- macOS 正式分发建议做 Developer ID 签名和 notarization，否则用户下载后可能遇到 Gatekeeper 拦截。
- Windows 正式分发建议做代码签名，否则 SmartScreen 可能提示未知发布者。
- 因为桌面端加载的是远程页面，服务端应保持 HTTPS、登录态安全、CSP 和版本兼容策略。
- 目前桌面端没有向远程网页暴露本地 Tauri API，安全边界相对保守。

## 参考文档

- Tauri 配置：<https://v2.tauri.app/reference/config/>
- Tauri 图标：<https://v2.tauri.app/develop/icons/>
- Tauri macOS App Bundle：<https://v2.tauri.app/distribute/macos-application-bundle/>
- Tauri Windows Installer：<https://v2.tauri.app/distribute/windows-installer/>
