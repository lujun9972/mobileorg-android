# GitHub Actions CI for main branch

## 目标

为 main 分支添加 GitHub Actions 自动打包 APK，签名与 master 分支一致。

## 方案

照搬 master 分支已有的 CI 方案：将 main 分支的构建系统升级到与 master 相同（AGP 3.0.1 + Gradle 4.1 + compileSdk 26），复制 keystore 和 CI 配置。

## 需要做的事

### 1. 升级构建系统

将以下文件从 master 分支复制到 main 分支：

- `build.gradle` — AGP 3.0.1 + 仓库配置
- `settings.gradle` — include MobileOrg 模块
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 4.1
- `MobileOrg/build.gradle` — compileSdk 26, signingConfig, 新依赖格式（implementation）

### 2. 复制签名材料

- `mobileorg-release.keystore` — 从 master 复制到 main 分支根目录

签名配置已在 master 的 `MobileOrg/build.gradle` 中（signingConfigs.release），密码明文存储在代码中（与 master 一致）。

### 3. 添加 GitHub Actions

创建 `.github/workflows/build.yml`，与 master 分支内容一致：

- 触发条件：push 到 main 分支 + 手动触发
- 环境：ubuntu-latest + JDK 8
- 步骤：checkout → 安装 SDK 26 → 编译 release APK → 上传 artifact

### 4. 处理代码兼容性

升级到 compileSdk 26 后，确保之前修复的代码（NotificationChannel、FLAG_IMMUTABLE 等）的 `Build.VERSION_CODES.O` 引用可以正常编译（API 26 已包含这些常量）。

## 不做的事

- 不改动签名方案（保持明文密码 + keystore 在仓库，与 master 一致）
- 不添加新的构建变体或 flavor
- 不做 lint 配置以外的代码质量检查
