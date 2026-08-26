# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指引。

## 项目概述

MobileOrg Android — Org-mode 的 Android 客户端。Fork 自不再维护的 [matburt/mobileorg-android](https://github.com/matburt/mobileorg-android)。这是 `main` 分支，从原始代码库（commit 39ffb4e）起步，已应用 Android 8+ / 12+ 兼容性修复。

## 构建

**环境要求**：JDK 17、Android SDK platform 34 + build-tools 34。

```bash
./gradlew assembleDebug
```

APK 输出：`MobileOrg/build/outputs/apk/debug/`

**构建工具链**：Gradle 8.5 + AGP 8.2.2 + JDK 17 + compileSdk 34 + targetSdk 34。

**注意**：不在本地构建。推送到远端由 CI 进行构建，用 `gh run list` / `gh run view` 检查 CI 结果即可。

**测试设备**: 无线调试已开启，`adb connect 192.168.31.198:<port>` 可连接。若需本地运行 instrumentation 测试，先连接设备再执行 `./gradlew connectedDebugAndroidTest`。

**Remote/CI**: Git remote `git.zhlh6.cn` 是一个 Gitea 代理，自动同步到 GitHub。推送会触发 GitHub Actions CI。用 `gh` CLI 查询 GitHub 仓库获取 CI 状态（如 `gh run list`）。

**测试**: `MobileOrg/src/androidTest/` 中有 94 个 instrumentation 测试，使用 `ProviderTestCase2` + `AndroidJUnit4`。通过 `./gradlew connectedDebugAndroidTest` 运行（需要模拟器）。CI 通过 GitHub Actions 在 API 30 模拟器上运行。

## 架构

### 构建系统

- 单模块 `:MobileOrg` + 库模块 `:libraries:locale`
- 3 个本地 JAR 位于 `MobileOrg/libs/`（CWAC adapters、json_simple）——不在 Maven 上
- 签名：release keystore 及硬编码密码在 `build.gradle` 中
- 版本名来自 `git describe --tags`

### 关键包（`com.matburt.mobileorg`）

- **`OrgData/`** — 核心数据层。`OrgDatabase`（SQLite）、`OrgFileParser`（解析 org 文件入库）、`OrgProvider`/`OrgProviderUtils`（ContentProvider）、`MobileOrgApplication`（应用初始化）。单例通过静态 `getInstance()` / `startXxx()` 获取。
- **`Synchronizers/`** — 抽象基类 `Synchronizer` 及实现：`WebDAVSynchronizer`、`SSHSynchronizer`（JSch）、`SDCardSynchronizer`。各自实现 `isConfigured()`、`isConnectable()`、`synchronize()`、`postSynchronize()`。
- **`Gui/Outline/`** — 主 UI。`OutlineAdapter` 在文件列表前固定插入 2 个 header 项（TODO、Agenda）（`numExtraItems = 2`），所有 position 到 index 的换算必须减 2。
- **`Services/`** — `SyncService`（通过 `AlarmManager` + 后台线程同步，API 26+ 为前台服务）、`TimeclockService`（前台通知计时器）、`CalendarSyncService`、`ReminderReceiver`（单条 deadline/scheduled 通知）、`DailyOverviewReceiver`（每日汇总通知）。
- **`Gui/`** — 通知（`SynchronizerNotification`/`Compat` 支持 NotificationChannel）、向导 Activity、widget、搜索。
- **`util/ReminderScheduler`** — 扫描 OrgData 中的 DEADLINE/SCHEDULED 日期，注册 AlarmManager 提醒。在同步后和开机时调用。

### 数据流

1. `MobileOrgApplication.onCreate()` → 初始化 DB、Synchronizer、OrgFileParser、SyncService alarm
2. `SyncService` → `Synchronizer.runSynchronizer()` 拉取远端文件 → `OrgFileParser.parseFile()` → SQLite
3. UI 通过 `OrgProvider` ContentProvider / `OrgProviderUtils` 读取
4. `OutlineAdapter.refresh()` 从 ContentProvider 重新加载文件列表

## Android 兼容性（已应用的修复）

原始代码面向 API 17，在现代 Android 上会崩溃。已应用以下修复：

- **PendingIntent FLAG_IMMUTABLE**：所有 PendingIntent 调用使用 `FLAG_IMMUTABLE`（Android 12+ / API 31 必须）
- **NotificationChannel**：任何 `notify()` 调用前先创建 Channel（Android 8+ / API 26 必须）。Channel ID：`mobileorg_sync`、`mobileorg_timeclock`
- **前台服务**：`SyncService` 和 `TimeclockService` 在 API 26+ 调用 `startForeground()`。Alarm PendingIntent 在 API 26+ 使用 `getForegroundService()`
- **服务启动**：`SyncService.startAlarm()`/`stopAlarm()` 和 `OutlineActivity.runSynchronize()` 在 API 26+ 使用 `startForegroundService()`
- **前台服务类型**：SyncService 声明 `dataSync`，TimeclockService 声明 `specialUse`（API 34+ 必须）
- **POST_NOTIFICATIONS**：API 33+ 在同步前运行时申请
- **Scoped Storage**：`WRITE_EXTERNAL_STORAGE` 限定 maxSdkVersion 28；`requestLegacyExternalStorage=true` 用于 SDCard 同步兼容
- **NotificationCompat.Builder**：所有 Builder 构造函数必须传 CHANNEL_ID（如 `new NotificationCompat.Builder(context, CHANNEL_ID)`）。否则通知没有 channel 引用，在 API 26+ 以 `CannotPostForegroundServiceNotificationException` 崩溃。
- **运行时权限**：日历权限（`READ_CALENDAR`、`WRITE_CALENDAR`）是危险权限，访问 CalendarProvider 前必须检查。`CalendarSyncService` 在 `onCreate()`/`onStartCommand()` 中检查，未授权则 stop self。新增任何危险权限使用时，务必添加运行时检查——API 23+ 上仅 manifest 声明是不够的。
- **菜单 XML showAsAction**：项目使用 AppCompat（`AppCompatActivity`），所有菜单 XML 必须用 `app:showAsAction`（来自 `xmlns:app="http://schemas.android.com/apk/res-auto"`）而非 `android:showAsAction`。后者会被 AppCompat Toolbar/ActionBar 静默忽略，导致菜单图标不显示。
- **OutlineActionMode 长按菜单有 4 套** *(2026-08-25 分享功能手测发现)*：`onCreateActionMode()` 按节点类型在 4 套菜单 XML 中选择——`outline_node`（可编辑节点）、`outline_node_uneditable`（不可编辑节点）、`outline_file`（文件节点）、`outline_file_uneditable`（agenda 文件节点）。给长按菜单加项时必须审计 4 套是否都该加——只加 node 两套时，agenda 类节点（实际走 `outline_file_uneditable` 分支）没有菜单项，且无任何报错。
- **Service 提前 return → onDestroy NPE**：在 `onCreate()`/`onStartCommand()` 中添加提前 return（如权限检查）后，Android 仍会调用 `onDestroy()`。被跳过的代码中本应初始化的字段，在 `onDestroy()` 中使用前必须判空。
- **主线程禁止网络操作**：Synchronizer 构造函数在 `SyncService.getSynchronizer()` 中于主线程调用。构造函数中绝不做网络 I/O（SSH 连接、HTTP 请求）。所有网络操作必须在后台同步线程。
- **sendBroadcast 必须用 setPackage()**：targetSdk 34 下，隐式广播可能无法投递到 `RECEIVER_NOT_EXPORTED` 的 receiver。`sendBroadcast()` 前务必 `intent.setPackage(context.getPackageName())` 使其显式化。适用于所有 `OrgUtils.announceSync*()` 方法。
- **Preference intent 必须用 targetPackage/targetClass**：XML preference 中用 `android:targetPackage` + `android:targetClass`，不用隐式 `android:action`。隐式 action intent 在现代 Android 上可能解析错误或失败。
- **View.startAnimation() 需要 view 已 attach**：`MenuItem.setActionView()` 配合动画时，先 `setActionView()`，再用 `View.post()` 启动动画。对未 attach 的 view 启动动画会被静默忽略。
- **Intent.getAction() 可能为 null**：使用 `FLAG_ACTIVITY_SINGLE_TOP` 或 `FLAG_ACTIVITY_CLEAR_TOP` 导航时，已存在的 Activity 收到 `onNewIntent()` 回调。传入的 intent 可能没有 action（`getAction()` 返回 null）。务必用 `CONSTANT.equals(intent.getAction())`（常量在左）而非 `intent.getAction().equals(CONSTANT)`，避免 NPE。
- **API 30+ 上的 ProviderTestCase2**：`RenamingDelegatingContext` 的 delegate 为 null，导致 `getDatabasePath()` NPE。修复：在 `super.setUp()` 之前调用 `setContext(ApplicationProvider.getApplicationContext())`。另外：(1) DB 数据在测试间持久化——setUp() 必须清空所有表（Edits、OrgData、Files）；(2) `getMockContext()` 可能暴露抛 `UnsupportedOperationException` 的 `MockContext` 方法（如 `getPackageName()`）。向调用 `getPackageName()`/`sendBroadcast()` 的代码传 context 时，用 `ContextWrapper` 包装 `ApplicationProvider.getApplicationContext()` 并 override `getContentResolver()` 返回测试的 `MockContentResolver`。
- **Instrumentation 测试 `useLibrary`**：使用已废弃 `android.test.*` 类（ProviderTestCase2、MockContentResolver）的测试，需要在 build.gradle 的 `android {}` 块中加 `useLibrary 'android.test.base'`、`useLibrary 'android.test.runner'`、`useLibrary 'android.test.mock'`。
- **Fragment 内部类必须是 `public static`**：AndroidX Fragment 1.2+ 要求所有 Fragment 子类为 `public static`（不能是非静态内部类）。非静态内部类持有外部实例的隐式引用，无法通过必需的无参构造函数重建。受影响：`TimeclockDialog.EditTimePickerFragment`、`DateTableRow.{StartTimePickerDialogFragment, EndTimePickerDialogFragment, DatePickerDialogFragment}`。修复：类声明为 `public static`，数据通过 `Bundle` arguments 传递，通过 `getActivity()` 强转访问外部 Activity/View。
- **每个入口点都要做危险权限检查 + Service 内防御性编码**：(1) 当一个功能可从多个 UI 路径访问（如 ActionMode 菜单 AND options 菜单）时，每个路径都必须独立检查危险权限后才能启动 Service。不要假设用户来自已检查权限的路径。(2) Service 本身也要防御性编码——硬件 API 调用（如 `MediaRecorder.setAudioSource()`）用 try-catch 包裹，因为权限可能在 UI 检查和 Service 执行之间被撤销。绝不让权限拒绝导致崩溃。
- **权限检查必须给用户反馈**：UI 路径中绝不用 `checkCallingOrSelfPermission()`——权限被拒时它静默返回，用户看不到任何反馈。改用 `ContextCompat.checkSelfPermission()` + `ActivityCompat.requestPermissions()` 弹出系统权限对话框。多个 UI 路径需要同一权限检查时，委托给单一方法（如 `tryStartRecording()`），不要复制逻辑。
- **通知 action 图标必须各不相同**：每个 `NotificationCompat.Action` 应有各自语义明确的图标（pause=`⏸`、stop=`⏹`、play=`▶`）。所有 action 用同一图标会让用户困惑。内联 UI 按钮同理。
- **Vector drawable 命名空间**：`<vector>` XML 必须用 `xmlns:android="http://schemas.android.com/apk/res/android"`。用 `res-auto`（那是 layout 中 `app:` 属性用的）会导致 AAPT 构建失败，报 "attribute not found" 错误。
- **MaterialComponents 主题迁移**：从 AppCompat 迁移到 MaterialComponents（Chip、ChipGroup 等需要）时，style parent 变化：`Theme.AppCompat` → `Theme.MaterialComponents`、`Widget.AppCompat.ActionBar.Solid` → `Widget.MaterialComponents.ActionBar.Solid`。`.Inverse` 变体（如 `Widget.AppCompat.Light.ActionBar.Solid.Inverse`）没有 MaterialComponents 等价物——改用 `.Solid` 变体，因为 `Theme.MaterialComponents.Light.DarkActionBar` 已提供深色 ActionBar。
- **大依赖需要 Multidex**：引入 Material Components 库（约 21000 方法）可能使 DEX 方法总数超过 65536 单 dex 上限。minSdk < 21（无原生 multidex）时必须：(1) `defaultConfig` 加 `multiDexEnabled true`；(2) 加 `implementation 'androidx.multidex:multidex:2.0.1'`；(3) Application 类 override `attachBaseContext()` 调用 `MultiDex.install(this)`。
- **MaterialComponents widget 必须显式声明 layout_width/layout_height**：使用 MaterialComponents 主题（`Theme.MaterialComponents`）时，包括 `ChipGroup`、`Chip` 在内的所有 widget 必须在 XML 中显式声明 `android:layout_width` 和 `android:layout_height`。与某些可继承默认值的 AppCompat widget 不同，MaterialComponents view 缺少这些属性会在 inflate 时以 `UnsupportedOperationException: You must supply a layout_width attribute` 崩溃。务必检查 layout XML 中每个 view 元素都声明了两个维度。
- **绝不用宽泛 try-catch 包裹 Activity 生命周期**：`onCreate()` 包在 `try { ... } catch (Exception e) { log(e); }` 中会静默吞掉初始化失败（包括 `setContentView()` 错误）。Activity 继续运行但没有 view，后续生命周期方法（`onResume()`）在 null view 上崩溃，抛出误导性的 NPE。如果日志级别是 INFO 而用户按 error 过滤 logcat，原始错误完全不可见。正确做法：让 RuntimeException 自然传播；在 `onResume()` 或 broadcast receiver 调用的方法中加判空，防御配置变更。
- **OrgNode.addLogbook 必须持久化到 DB**：`addLogbook()` 曾调用 `setPayload()`，但那只更新内存字段，不更新数据库。clock out 后，CLOCK 条目只存在于 OrgEdit 表，在节点编辑器中不可见。修复：`setPayload()` 之后调用 `write(resolver)` 持久化 payload。适用于任何通过 `setPayload()` 修改 `payload` 的方法——若变更需在本地可见（而不只是同步），务必跟 `write(resolver)`。
- **saveClock：endTime=now，startTime=now-duration**：clock out 时 `endTime` 应为 `System.currentTimeMillis()`（clock out 时刻），`startTime = endTime - duration`。这样编辑时长会向过去调整开始时间，而不是把结束时间推向未来。之前保留原始 clock-in 时间并计算 `endTime = startTime + duration` 的做法，在 clock-in 后不久编辑时长时会把结束时间推到未来。
- **时长输入不要用 TimePickerDialog**：`android.app.TimePickerDialog` 在某些设备上（确认于 API 34 + MaterialComponents 主题）在 `onTimeSet()` 回调参数 AND `view.getHour()/getMinute()` 中返回初始值——用户的选择被完全忽略。这使得编辑计时时长不可行。修复：`DurationPickerFragment` 中改用 `AlertDialog` + 两个 `NumberPicker`（小时/分钟）。
- **NumberPicker.getValue() 前必须 clearFocus()**：某些设备上 `NumberPicker` 在焦点清除前不把滚动位置提交到内部值。不 `clearFocus()` 时 `getValue()` 返回初始 `setValue()` 的值，忽略用户输入。`picker.getValue()` 前务必 `picker.clearFocus()`。
- **同步不删除远端已删除的文件**：`Synchronizer.pull()` 只比较远端校验和找需要下载的文件，从不检查本地有而远端 index 没有的文件。从服务器 `index.org` 移除的文件在本地 DB 中无限残留。修复：添加 `removeRemoteDeletedFiles()`，将本地 DB 文件与远端 `filenameMap` 对比并移除孤儿（排除 `CAPTURE_FILE` 和 `AGENDA_FILE`）。同时把 index.org 解析移到 `changedFiles.size() == 0` 提前 return 之前，因为仅文件删除不产生 changed files 但仍需清理。
- **AlarmManager BroadcastReceiver 必须 exported="true"**：AlarmManager 通过系统进程（uid 2000）投递 PendingIntent 广播，与 app 的 UID 不同。`android:exported="false"` 时 Android 拒绝广播，报 `Permission Denial: not exported from uid`。所有由 AlarmManager 触发的静态 BroadcastReceiver 必须用 `exported="true"`，即使广播 action 是 app 私有的。
- **设置中的日历权限需要运行时检查**：`CalendarWrapper.getCalendars()` 查询 CalendarProvider，需要 `READ_CALENDAR` 运行时权限。`SettingsActivity.populateCalendarNames()` 的调用曾静默抛 `SecurityException`，显示"日历不存在"。修复：查询前检查权限，未授权则 `ActivityCompat.requestPermissions()` 申请，在 `onRequestPermissionsResult()` 回调中重试。
- **主题变更在 resume 时不生效**：`setTheme()` 必须在 `setContentView()` 之前调用（Android 框架要求），所以只能在 `onCreate()` 中生效。从设置改完主题返回时只触发 `onResume()`——Activity 保持旧主题。修复：用字段跟踪当前主题名，`onResume()` 中检查是否变化，变化则 `recreate()` 重建 Activity。适用于任何主题变更后可能被 resume 的 Activity——本 app 只有 `OutlineActivity` 受影响，其他 Activity 每次都是新建。
- **主题系统有两层独立体系**：(1) **XML 主题**（`themes.xml` + `OrgUtils.setTheme()`）控制系统 UI——ActionBar、preference 页面、对话框、标准 widget 的文字颜色。(2) **Java 主题**（`Gui/Theme/` 的 `DefaultTheme`/`MonoTheme`/`WhiteTheme`）控制自定义 outline 渲染——列表项颜色、TODO 状态、层级缩进、tags。两层必须对齐。新增主题选项必须同时更新 `OrgUtils.setTheme()`（XML 主题选择）和 `DefaultTheme.getTheme()`（Java 主题选择），并在 `themes.xml` 中定义 parent 正确（dark 还是 light 基底）的匹配 style。只更新一层会导致各页面外观不一致。
- **Preference 选项必须有实现分支**：`arrays.xml` 定义了 "Monochrome" 主题选项，但 `OrgUtils.setTheme()` 没有对应分支——静默落入 Light 主题。添加 `ListPreference` 选项时，务必验证对应代码处理了 entries 数组中的每个值。
- **AppCompat per-app language 持久化需要 AppLocalesMetadataHolderService** *(2026-08-27 手测发现)*：API <33 上 `AppCompatDelegate.setApplicationLocales()` 的选择默认只存内存——进程重启后丢失、回退跟随系统。必须在 manifest 声明 `androidx.appcompat.app.AppLocalesMetadataHolderService`（`enabled="false"` + `exported="false"` + `autoStoreLocales=true` meta-data），AppCompat 反射读取该 meta-data 才会持久化；service 本身不会被真正启动，`enabled=false` 是官方要求的写法。此外非 AppCompatActivity（framework Activity）不吃 AppCompat 的自动 recreate/locale 应用——需 `attachBaseContext` 里 `createConfigurationContext` 手动 wrap（见 `OrgUtils.wrapForAppLocales`）。
- **manifest label 的 ActionBar 标题不跟随 per-app 语言切换** *(2026-08-27 手测发现)*：框架在 locale 应用之前就解析了 `ActivityInfo.labelRes`，AppCompat 的 locale 包装覆盖不到它——API <33 切换语言 recreate 后 ActionBar 标题仍是旧语言的文本（无任何报错）。修复：`onCreate()` 中显式 `setTitle(R.string.xxx)` 覆盖（HelpActivity/HelpDetailActivity 受影响）。硬编码品牌名 label（"MobileOrg" 等）无需处理。

所有版本守卫使用 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` 模式。

- **用户主动点击按钮必须有确定性结果**：用户点击保存/取消/确定后，动作必须无条件完成。绝不让 `finish()` 依赖于正交状态（如 `maybeFinish()` 检查 pomodoro 是否还在运行）。用户明确选择了行动——照做。唯一例外是通知按钮的"停止"动作，`maybeFinish()` 是正确的（停掉一个功能，仅当无其他活动时才关闭）。
- **DurationPicker 是预览不是提交**：picker 的确定按钮只应更新父 Activity 中显示的值。真正的保存/clock out 只在用户点击明确的"保存"按钮时发生。把预览和提交混在一步会破坏用户心智模型，让 UI 感觉失灵（对话框过早关闭，或显示陈旧数据）。
- **Pomodoro 超时提醒必须用 HIGH importance 通知渠道**：Android 8+ 上通知渠道 importance 优先于 `notification.defaults`。`IMPORTANCE_LOW` 渠道会静默屏蔽声音、振动和 heads-up 弹窗。旧代码在前台通知上设 `notification.defaults = Notification.DEFAULT_ALL`——被 LOW 渠道完全忽略。修复：创建独立 `IMPORTANCE_HIGH` 渠道（`mobileorg_timeclock_timeout`）并发送独立通知（独立 ID、非 ongoing、`CATEGORY_ALARM`、`autoCancel`）。绝不能复用前台通知做提醒——很多设备不会为已显示的 ongoing 通知再次触发声音/振动。停止/取消/销毁时也要清理超时通知。
- **同步按钮动画必须在 onPrepareOptionsMenu() 自愈**：`onResume()` 调用 `invalidateOptionsMenu()` 重建菜单。若同步正在进行，旋转动画的 `ImageView`（通过 `MenuItem.setActionView()` 设置）会丢失。`SYNC_DONE` 到达时 `synchronizerMenuItem.getActionView()` 返回 null——动画停不下来，更糟的是 MenuItem 引用可能已过期。修复：(1) 添加 `SyncService.isSyncRunning` 静态标志；(2) `onPrepareOptionsMenu()` 中检查该标志——同步中但 actionView 缺失则恢复旋转动画，同步完成但 actionView 卡住则清除动画。`SYNC_DONE` handler 中也要对 `synchronizerMenuItem` 判空。
- **Pomodoro 闹钟解除**：超时通知有"关闭闹铃"action 按钮，只停闹钟声音不停 pomodoro 会话。用 `ACTION_ALARM_DISMISS` 配 `setAutoCancel(false)` 和 `deleteIntent`，使解除按钮始终可用（点通知主体不会解除；滑掉通知通过 deleteIntent 停闹钟）。同时修复 `stopAndReleaseAlarmSound()` 的双重 release bug——`release()` 在 try-catch 外，若 `OnCompletionListener` 已释放 MediaPlayer 则崩溃。
- **REST 结束通知必须用独立渠道**：`handleRestTimeout()` 曾调用 `showOrRefreshNotification()`，那只静默更新前台通知——无声音无振动。注释承诺"notification sound + vibration, not alarm"但代码什么都没做。修复：创建独立 `IMPORTANCE_HIGH` 渠道（`mobileorg_timeclock_rest`），默认声音+振动（不 `setSound(null)`）。工作超时渠道（`mobileorg_timeclock_alarm`）用 `setSound(null, null)`，因为它的闹钟来自 MediaPlayer，不能复用于 REST。还要在 `handlePomodoroNext()` 取消 REST 通知，避免开始下一轮后用户看到陈旧的"休息结束"通知。
- **isSyncRunning 必须在 SYNC_DONE 广播之前清除**：`announceSyncDone()` 曾在 `isSyncRunning` 仍为 true 时发广播；标志直到 `finally` 块才清除。这产生竞态：`SynchServiceReceiver.onReceive()` → `stopSyncAnimation()` 清除了动画，随后 `onRefreshDisplay.run()` 或其他回调触发 `invalidateOptionsMenu()` → `onPrepareOptionsMenu()` 看到 `isSyncRunning=true` 且 actionView=null → 恢复旋转动画。动画一旦恢复就没有任何东西能停掉它（不会再有广播来）。修复：在 `Synchronizer.announceSyncDone()` 中、`sendBroadcast()` 之前设置 `SyncService.isSyncRunning = false`。通用规则：UI 回调读取的任何状态标志，必须在宣布状态变化的广播之前更新，而不是之后。
- **绝不在同步进度回调中 invalidateOptionsMenu()**：`SYNC_PROGRESS` 广播在每个进度 tick 触发 `onRefreshDisplay`。若任何刷新路径也调用 `invalidateOptionsMenu()`，菜单会在同步中重建，丢弃 `SYNC_START` 时创建的 action view 代际。`synchronizerMenuItem` 字段只跟踪最新代际，因此 `SYNC_DONE` 时的 `stopSyncAnimation()` 清除的是新的、不在动画的 view，而旧代际的 spinner 永远旋转。修复：只在 `SYNC_DONE` 分支 invalidate（在停止动画之后——同步在那里消费 edit 批次，undo 菜单状态必须刷新），绝不在进度事件中。

## Bug 修复工作流

每次修复一个 bug 后，必须完成以下步骤：

1. **总结经验写入 CLAUDE.md** — 将根因和修复方法记录到 "Android 兼容性（已应用的修复）" 部分
2. **检查同类问题** — 全局搜索相同模式（如 `grep -r "android:showAsAction"`），避免只修一处遗漏其他
3. **补充单元测试** — 为修复的场景编写测试，防止回归
4. **更新博文** — 将新坑补充到 `~/github/lujun9972.github.com/编程之旅/MobileOrg-Android-从API-17迁移到API-34的实战记录.org`

## 运维注意事项

- **限流恢复**：遇到 API 429 限流错误（如 `"已达到 5 小时的使用上限"`）时，用 `CronCreate` 在重置时间调度一次性任务来恢复未完成的工作。不要轮询或立即重试。

## 已知坑

- **loadDataWithBaseURL 的 baseUrl 决定相对链接解析** *(2026-08-26 验证修复)*：`loadDataWithBaseURL(baseUrl, html, ...)` 加载字符串 HTML 时，页面内所有相对 URL（`<a>`/`<img>`/`<link>`）都相对 baseUrl 解析，与 readAsset() 实际读取的路径无关。baseUrl 写 `file:///android_asset/help/` 而多语言 HTML 在 `help/{zh,en}/` 子目录时：共享图片（真在 `help/images/`）正常显示，但 `xxx.html` 互链解析到不存在的 `help/xxx.html` → WebView **静默** ERR_FILE_NOT_FOUND、保留旧页面——现象是"图片正常、链接点了没反应"，且无任何日志。修复：baseUrl 从 assetPath 父目录推导（`"file:///android_asset/" + assetPath.substring(0, lastIndexOf('/')+1)`）；`shouldOverrideUrlLoading` 拦截 `file:///android_asset/help/` 前缀转回 `loadAsset()`（同时保留 dark class 注入——否则跳转后页面丢失暗色主题）；html 内共享资源用 `../` 上跳引用（`../help.css`、`../images/`）。诊断辅助坑：(1) WebView 虚拟节点在 uiautomator dump 中 **x 坐标可信、y 坐标不可信**（常全部堆在同一 y），定位点击目标用截图像素分析；(2) `historyUrl=null` 时 `getUrl()` 恒返回 `about:blank`，不能断言导航目标；(3) `evaluateJavascript` 的 JS 合成 click 不触发 `shouldOverrideUrlLoading`，测试须直接驱动回调本身。
- **not-found 异常路径上的 Cursor 泄漏** *(2026-06-21 验证修复)*：`OrgFileRepository.getById/getByFilename` 和 `OrgNodeRepository.getById` 打开 cursor 后抛 `Org*NotFoundException` 却不关闭。对 `getByFilename(CAPTURE_FILE)` 而言"not found" 分支是*正常*路径——没有 capture 时 `OutlineActivity.onResume() → refreshTitle() → getChangesCount() → getByFilename()` 每次 resume 都跑，每次泄漏一个 cursor。Finalizer 警告 `A resource failed to call AbstractCursor.close / CursorWrapperInner.close`。StrictMode（`detectLeakedClosableObjects()` + `penaltyLog()`）定位了分配栈。修复：throw 分支中关闭 cursor。**验证**：修复版 APK 上重新启用 StrictMode + CloseGuard，15 次 `onResume()` 循环 + 3 次强制 GC 产生 **0** 条 StrictMode 警告——泄漏确认消除。临时 StrictMode 诊断代码已移除。通用规则：方法打开 Closeable 后若能在正常 close() 前抛出，就在 throw 分支关闭（或用 try-finally）。
- **单例状态**：DB、Parser、Synchronizer 是单例，非线程安全。同步线程写 DB 时 UI 在读——存在竞态条件。
- **RecyclerView + 额外项**：`OutlineAdapter` 加 2 个 header 项。position 到 index 换算必须减 `numExtraItems`。
- **`OrgNodeListActivity`**：没有 `onSaveInstanceState`——旋转可能出问题。
- **AndroidX**：项目使用 AndroidX（`androidx.*` import）。本地 JAR 启用 Jetifier。
- **OrgRenderer XSS 防护**：渲染用户内容到 HTML 时，务必先 `htmlEncode()` 再 `applyInlineMarkup()`。编码→标记→链接的顺序既防注入又保留 org 格式。
- **OrgRenderer 用原始 payload**：`OrgRenderer` 调用 `node.getPayload()`（原始），内部通过 `preClean()` 自行清理。不要用 `getCleanedPayload()`，它会剥掉源码渲染需要的 `#+BEGIN_SRC` 块。
- **OrgRenderer 递归深度**：`nodeToHTMLRecursive()` 有 `MAX_RECURSION_DEPTH = 50`。超过 50 层嵌套的 org 文件会被静默截断。
- **PayloadFragment 预览同步**：渲染前必须调用 `node.setPayload(payload.get())` 同步已编辑内容。否则预览显示陈旧数据。
- **OrgData 是内部类**：`OrgData` 是 `OrgContract.OrgData`，不是独立类。从 `OrgData` 包外 import 时用 `import com.matburt.mobileorg.OrgData.OrgContract.OrgData`。短形式 `import ...OrgData.OrgData` 只在 `OrgData` 包内可用。
- **跨包静态方法必须 public**：包私有（`static` 无 `public`）方法对其他包的类不可见。`util/` 中的工具方法若被 `Services/` 调用，必须 `public static`。
- **同步配置导出/导入必须用 SAF**：直接 `FileWriter` 到 `getExternalFilesDir()` 会在卸载后丢失；`getExternalStoragePublicDirectory()` 在 API 29+（Scoped Storage）报 EACCES。用 `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`（Storage Access Framework）让用户选位置，配置才能在重装后保留。
- **ACTION_OPEN_DOCUMENT 按扩展名 MIME 过滤会置灰老设备上的文件** *(2026-08-23 验证，MI PAD 4 / Android 9)*：导入配置的 intent 设 `setType("application/json")` 后，`.json` 文件在 SAF 选择器中灰色不可选——老版 Android 的 `MimeTypeMap` 不映射 `.json` 扩展名，文件 MIME 未知，被过滤器排除。修复：打开类 intent 用 `setType("*/*")`（选错文件由解析层报错 toast 兜底）；`ACTION_CREATE_DOCUMENT` 创建新文件不受影响，可保留精确 MIME。
- **卸载 app 前必须确认无未同步数据**：本地 capture/edit 只存在于 app 私有 SQLite 数据库。`adb uninstall` 永久删除，无法恢复。务必先提醒用户同步或导出数据。
- **批量正则重构必须经 CI 验证才算完成**：批量查找替换可能过于激进。`== false` 正则 `(\b\S+) == false\b` 捕获了 `if(` 前缀产生 `!if(expr)` 和 `!while(expr)`。`return true/false` 模式的非贪婪 `(.+?)` 匹配了意外目标，如 `if (entry.get(name))` → `return entry.get(name))`。每次只推一个 commit 并等 CI——不要在未验证时堆积多个机械变更。
- **删除常量/字段必须 grep 整个仓库包括测试**：从主源码移除符号时，同时搜索 `src/main/` 和 `src/androidTest/`。测试常引用相同常量（`Synchronizer.CAPTURE_FILE`），测试 import 损坏会导致构建步骤发现不了的 CI 失败。
- **构造函数参数用基类型以利测试**：`Synchronizer` 构造函数曾接收 `SynchronizerNotification`（具体子类），但测试 stub 继承 `SynchronizerNotificationCompat`（基类）。用基类做参数类型才能无类型错误地传入测试 stub。
- **绝不用 `git add -A`**：它会暂存不该提交的未跟踪文件（`debug.sh`、`.superpowers/`、docs）。总是 `git add <file>` 暂存具体文件，然后 `git status` / `git diff --cached` 审查后再提交。
- **服务停止时 TimeclockDialog 必须关闭**：`TimeclockDialog` 是通知 contentIntent 打开的 `Theme.Dialog` Activity。通过通知按钮或 OutlineActivity 菜单停止 pomodoro/计时完全绕过对话框。服务必须在更新状态后（但在 `checkStopSelf()`/`stopSelf()` 之前）广播 `BROADCAST_STATE_CHANGED`，对话框必须在 `onStart()` 注册 receiver 调用 `maybeFinish()`。否则对话框作为残留小窗口持续可见。另外：对话框内的停止按钮必须用本地 `clockSection.getVisibility()` 检查而非异步的 `service.isPomodoroRunning()`，避免竞态（`startService()` 是异步的，`maybeFinish()` 运行时状态还没更新）。
- **Pomodoro 闹钟声音必须用 USAGE_ALARM 流**：通知渠道声音受通知音频流控制，静音模式下被屏蔽。需要在静音模式下也能听到的计时提醒，用 `MediaPlayer` 配 `AudioAttributes.USAGE_ALARM` 在闹钟音频流播放。通知渠道应 `setSound(null, null)`（静音，仅振动）。迁移时用新渠道 ID（旧渠道创建后不能改声音）。`MediaPlayer` 生命周期必须作为 Service 字段管理，所有 stop/cancel/destroy 路径显式 release。
- **通知标题必须在 updateTime() 中更新，而不只是 showOrRefreshNotification()**：`updateTime()` 每 60 秒经 Handler 刷新大号时间显示（`timeclock_notification_time`），但标题行（`timeclock_notification_text`，如 "🍅 0:14 | 学日语"）只在 `showOrRefreshNotification()` 设置一次。结果：标题显示通知创建时的陈旧时间而大号时间是新的。修复：`updateTime()` 也必须用当前剩余/超时时间 + 节点名重建并更新标题文字。适用于任何自定义 RemoteViews 有多个文字字段的通知——周期性更新时必须刷新**所有**显示的时间字段，而不只是一个。
- **AlarmManager 在现代 Android 上做精确定时不可靠**：(1) `setRepeating()` 自 API 19 起不精确——闹钟被批处理，可能晚几分钟。(2) `setWindow()` 窗口小于 10 分钟时在 API 31+（Android 12+）被强制拉长到 10 分钟，所以 `setWindow(RTC_WAKEUP, trigger, 60000)` 在 10 分钟窗口内任意时刻触发。(3) `setExact()` 和 `setExactAndAllowWhileIdle()` 在 API 31+ 需要 `SCHEDULE_EXACT_ALARM` 权限；没有则抛 SecurityException 或被静默降级。(4) Doze 和 App Standby 进一步延迟所有非精确闹钟。**前台服务计时器的修复（TimeclockService）**：用 `Handler.postDelayed()` 替代 AlarmManager（更简单，无特殊权限）。但 `Handler` 在 Doze 下并非完全可靠——在 MIUI/xaga 上确认，一次性 `postDelayed(timeoutRunnable, 20min)` 晚了 25 分钟才触发（前台服务进程活着，但 CPU 睡眠，消息队列停滞）。修复：自重新调度的 `updateTick`（60s）兼做兜底——检查 `getRemainingMillis() <= 0 && !isTimedOut()` 并调用 `handlePomodoroTimeout()`；同一模式通过 `getRestRemainingMillis()` 覆盖 REST 阶段。`handlePomodoroTimeout` 必须幂等——用 `isTimedOut()` 也做守卫（不只是 `isRunning()`，因为 `markTimeout` 保持 `running=true`），兜底触发后迟到的一次性 `timeoutRunnable` 不会重播闹钟。通用规则：前台服务上的任何一次性 `Handler` 定时器必须有周期性自重新调度兜底。**后台提醒的修复（ReminderScheduler）**：`SCHEDULE_EXACT_ALARM` 在 API 31+ 是**特殊**权限——仅 manifest 声明不够，用户必须在系统设置中授予；全新安装默认拒绝。策略选择集中在 `ReminderScheduler.chooseAlarmStrategy(apiLevel, canScheduleExactAlarms)`：API 31+ 无权限必须回退 `setWindow()`（**不是** `setExactAndAllowWhileIdle()`——那仍会抛 `SecurityException`）；API 23-30 无需特殊权限用 `setExactAndAllowWhileIdle()`；每日总览用一次性 `setExact()`（非 `setRepeating()`）在 `DailyOverviewReceiver` 中重新调度。
- **`updateAllNodes` 空 nodeId LIKE '%%' 全表覆写** *(事故 2026-08-22，当日修复)*：`OrgNodeRepository.updateAllNodes()` 调用 `getNodeId()`，对 capture 型节点结果是 `""` —— `EditActivityControllerCreate.saveEdits()` 以默认 `parentId=-1`/`fileId=-1` 且无 `:ID:` 属性保存 capture，`getOlpId()` 走到 `getOrgFile(fileId=-1)` → `OrgFileNotFoundException` → 返回 `""`。旧守卫 `!nodeId.startsWith("olp:")` 抓不住空字符串，产生 `PAYLOAD LIKE '%%'` 匹配每一行并全表覆写 NAME/TODO/PAYLOAD/PRIORITY/TAGS（NULL payload 不匹配 LIKE，这就是 `Captures` 文件根存活的原因）。由一次对 capture 节点的 checkbox 点击在真实环境触发（所有本地数据视觉上"丢失"；因服务器未受影响，通过重新同步恢复）。修复：`updateAllNodes` 守卫 `nodeId == null || nodeId.isEmpty()`（`findOriginalNode` 中同样的 LIKE 模式同理）；`generateApplyWriteEdits` 跳过 `fileId=-1` 的孤儿节点，使其不会向同步上传发出空 nodeId 的 edit。通用规则：(1) 任何由计算值构建的 `LIKE '%' + value + '%'` SQL 必须守卫空字符串——它是匹配所有的模式；(2) 向危险 API 添加新调用者时，审计 API 对调用者能传入的*每一个*状态的行为，而不只是 happy path。
- **`OrgNode(OrgNode)` 复制构造必须复制身份字段** *(2026-08-23 修复)*：复制构造原本只复制展示字段（level/priority/todo/tags/name/payload），留下 `id=-1`、`parentId=-1`、`fileId=-1`。任何"复制 → 变更 → 按 id 持久化"的序列随后静默写空：`updateNode(buildIdUri(-1))` 匹配 0 行，孤儿副本的 `getNodeId()` 返回 `""`（无 `:ID:` 属性；`parentId=-1` 使 `getOlpId()` 构建空路径，`getOrgFile(fileId=-1)` 抛异常），空 nodeId 守卫又跳过 LIKE 兜底。值得注意的是，在空 nodeId 守卫存在*之前*，同样的 `""` 产生 `LIKE '%%'` 全表覆写——守卫把损坏变成了静默数据丢失，掩盖了更深的 bug。修复：构造函数复制 `id`/`parentId`/`fileId`。需要独立身份的两个调用者（`OrgFileParser.cloneChildren`、`PayloadFragment.switchToView` 预览）都已显式覆盖这些字段。通用规则：(1) 复制构造必须复制**所有**字段，除非有文档化的理由不这样做——审计任何复制→持久化序列的身份丢失；(2) 当守卫把响亮失败变成静默无操作时，检查调用者原来依赖什么。
- **`OrgNode` 默认值可能静默产生孤儿行**：`new OrgNode()` 的 `parentId=-1`、`fileId=-1`。不设置这两个字段就 `repo.write(node)` 的代码路径（如 `EditActivityControllerCreate.saveEdits` 只设 `level=1`）创建脱离文件树的行：它们显示为顶层 outline 条目，`getFilename()` 返回 `""`，`getOlpId()` 返回 `""`。已知未解决案例：2026-08-22 修复之前创建的 capture 节点。写节点时，务必显式设置 `parentId` 和 `fileId`。

## Agent skills

### Issue tracker

Issue 和 PRD 以本地 markdown 文件形式存放在 `.scratch/` 下。见 `docs/agents/issue-tracker.md`。

### Triage labels

五个规范角色使用默认名称：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文布局：仓库根一个 `CONTEXT.md` + `docs/adr/`。见 `docs/agents/domain.md`。

- **CalendarSyncService.getCalendarEntries 全量 payload 载入 MultiMap → OOM**：同步解析大 org 文件（929KB home.org）后，堆已接近 256MB 上限。`getCalendarEntries()` 随后把一个文件的**所有**日历条目载入 `MultiMap<CalendarEntry>`，每条含完整 org `description` payload（可达 245KB+）。`description` 和 `location` 字段从不用于匹配（equals() 只比较 dtStart/dtEnd/title）也不用于移除（只需 entry.id）。修复：给 `CalendarEntriesParser` 加 `includePayload` 标志，为 `false` 时跳过 `description`/`location`。同方法还修复了 cursor 泄漏——cursor 打开后从不关闭。通用规则：仅为去重/匹配而加载数据时，只加载 equals() 用到的字段。cursor 务必在 finally 块中关闭。
