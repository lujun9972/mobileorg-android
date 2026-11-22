# CLAUDE.md

Claude Code (claude.ai/code) 在此仓库工作时的指引。**本文件只做导航——规则与坑在 docs/pitfalls.md，改代码前按场景查。**

## 项目概述

MobileOrg Android — Org-mode 的 Android 客户端。Fork 自不再维护的 [matburt/mobileorg-android](https://github.com/matburt/mobileorg-android)，`main` 分支从原始代码库（commit 39ffb4e）起步，已应用 Android 8+ / 12+ 兼容性修复。

## 构建

- 环境要求：JDK 17、Android SDK platform 34 + build-tools 34；工具链 Gradle 8.5 + AGP 8.2.2 + compileSdk/targetSdk 34。
- `./gradlew assembleDebug`，APK 在 `MobileOrg/build/outputs/apk/debug/`。
- **不在本地构建**：推送触发 GitHub Actions CI，用 `./ci-watch.sh` 等两个 workflow 的结论（Build APK 不编译 test 源集，测试结论以 Instrumentation Tests 为准）。安装到设备用 `./deploy.sh`（下载 CI 最新 APK → `adb install -r`）。**绝不 `adb uninstall`**——本地未同步数据会永久丢失。
- 版本名来自 `git describe --tags`；release keystore 及密码在 `build.gradle`。

## Remote / CI

Remote `origin` 直连 GitHub（`git@github.com:lujun9972/mobileorg-android.git`，2026-11 前曾走 git.zhlh6.cn Gitea 代理），push 触发 CI。`gh` CLI 查 run：`gh run list -R lujun9972/mobileorg-android`。云端协议目录（手机 SSH 同步目标，`~/mobileorg/`）：`ssh -p 8022 lujun9972@tencent_cloud.lujun9972.win`（本机免密，或 `ssh tencent_cloud`）。

## 测试

- `MobileOrg/src/androidTest/`：94 个 instrumentation 测试（ProviderTestCase2 + AndroidJUnit4），`./gradlew connectedDebugAndroidTest` 需真机/模拟器，CI 在 API 30 模拟器跑。**绝不连用户真机跑**——androidTest 的 setUp 清空三张表，等于清掉未同步数据（与"绝不 adb uninstall"同级）。测试坑见 pitfalls.md §8。
- `MobileOrg/src/test/`：纯逻辑 JVM 测试，无需设备——纯逻辑修复优先落这里。
- 本地设备：无线调试 `adb connect <ip>:<port>`（IP/端口易变，以设备显示为准）；拿不到就请用户插 USB。logcat 跟踪用 `./debug.sh`。

## 架构

- 单模块 `:MobileOrg` + 库模块 `:libraries:locale`；3 个本地 JAR 在 `MobileOrg/libs/`（CWAC adapters、json_simple，不在 Maven）。
- 核心包（`com.matburt.mobileorg`）：
  - `OrgData/` — 数据层：`OrgDatabase`（SQLite）、`OrgFileParser`（解析入库）、`OrgProvider`（ContentProvider）、`MobileOrgApplication`（初始化）。单例经静态 `getInstance()`/`startXxx()` 获取。
  - `Synchronizers/` — `Synchronizer` 基类 + WebDAV/SSH（JSch）/SDCard 实现，各自 `isConfigured()`/`isConnectable()`/`synchronize()`/`postSynchronize()`。
  - `Gui/Outline/` — 主 UI。`OutlineAdapter` 固定插入 2 个 header（TODO、Agenda），position↔index 换算减 `numExtraItems=2`。
  - `Services/` — `SyncService`（AlarmManager 后台同步）、`TimeclockService`（前台计时/番茄钟）、`CalendarSyncService`、`ReminderReceiver`、`DailyOverviewReceiver`。
  - `util/ReminderScheduler` — 扫描 DEADLINE/SCHEDULED 注册提醒，同步后与开机时调用。
- 数据流：app 启动初始化 DB/Synchronizer/SyncService alarm → `SyncService` 拉远端文件 → `OrgFileParser.parseFile()` 入库 → UI 经 `OrgProvider` 读取。
- 领域词汇与分层定义见 **[CONTEXT.md](CONTEXT.md)**；架构决策记录见 **docs/adr/**。

## 改代码前查坑

**[docs/pitfalls.md](docs/pitfalls.md)** — 速查表（review 审计清单）+ 分类规则。触发场景：通知/PendingIntent、Service/权限、广播/Intent、菜单/ActionMode、UI/主题、SQL/数据层、定时器/闹钟、同步/文件、测试、重构提交。

## Bug 修复后

**[docs/review-checklist.md](docs/review-checklist.md)** — 四步验收（坑记录 → 同类检查 → 回归测试 → 博文更新），每个 `fix(...)` commit 完成后逐项过，博文路径也在其中。

## Agent 工作流

- Issue 与 PRD 在 `.scratch/`（[docs/agents/issue-tracker.md](docs/agents/issue-tracker.md)）；triage labels 见 [docs/agents/triage-labels.md](docs/agents/triage-labels.md)；单上下文布局见 [docs/agents/domain.md](docs/agents/domain.md)。
- 限流恢复：遇 API 429 用 `CronCreate` 在重置时间调度一次性任务恢复未完成工作，不轮询、不立即重试。
