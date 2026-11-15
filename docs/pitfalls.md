# Pitfalls — 已知坑与规则

本仓库历史事故与 Android 兼容性规则的完整集合。**改代码前按场景查对应节；review 时用速查表逐项审计。** 每条规则都来自真实事故，条目格式：规则 → 一句根因 → 指针。

## 速查表（review 审计清单）

| 改动场景 | 必查项 |
|---|---|
| 通知 / PendingIntent | FLAG_IMMUTABLE、先建 Channel、Builder 传 CHANNEL_ID、前台服务类型声明、API 33+ POST_NOTIFICATIONS、超时/提醒类通知用独立 HIGH importance 渠道、周期更新的 RemoteViews 刷新**所有**文字字段 |
| Service / 权限 | 提前 return 后 onDestroy 判空、API 26+ startForeground/startForegroundService、每个 UI 入口独立做危险权限检查并给用户反馈、构造函数不做网络 I/O |
| 广播 / Intent | sendBroadcast 前 setPackage、`CONSTANT.equals(getAction())`、preference intent 用 targetPackage/targetClass、AlarmManager 的 receiver exported=true |
| 菜单 / ActionMode | `app:showAsAction`（非 android:）、长按菜单审计 **4 套** XML（node/node_uneditable/file/file_uneditable）、invalidateOptionsMenu 只在 SYNC_DONE 后 |
| UI / 主题 | MaterialComponents widget 显式 layout_width/height、双层主题（XML + Java）必须同步改、ListPreference 每个 entry 有代码分支、setTheme 只在 onCreate 生效（变更后 recreate）、Fragment 子类 public static、vector 用 xmlns:android |
| 数据层 / SQL | LIKE 模式经 `likePattern()` 转义+ESCAPE、计算值拼 LIKE 前守卫空串（`'%%'` 匹配全表）、写节点显式设 parentId/fileId、复制构造复制全部字段、setPayload 后需要本地可见就 write(resolver)、cursor 在 throw/finally 路径关闭 |
| 定时器 / 闹钟 | 一次性 Handler 定时必须有周期兜底、API 31+ 精确闹钟需 SCHEDULE_EXACT_ALARM（无则回退 setWindow）、闹钟声音用 USAGE_ALARM 流（渠道 setSound(null,null)）、NumberPicker getValue 前 clearFocus |
| 同步 / 文件 | pull 后清理远端已删文件、导出导入走 SAF（打开类 intent `setType("*/*")`）、配置不完整（如 calendarName 为空）在 Service 入口守卫跳过 |
| 测试 | ProviderTestCase2 先 `setContext(ApplicationProvider...)`、setUp 清空所有表、废弃 android.test.* 需 useLibrary 三件套、绝不 `pm revoke` 已授予权限（MIUI 杀进程=杀 instrumentation）、grant 用例按字母序排最后 |
| 重构 / 提交 | 批量正则替换逐 commit 等 CI 验证、删常量 grep 全仓库**含 androidTest**、绝不 `git add -A`、版本守卫统一 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` |

## 1. 通知与前台服务

- **PendingIntent 一律 FLAG_IMMUTABLE**：API 31+ 未指定会崩溃。
- **notify() 前先创建 NotificationChannel**：API 26+ 必须，否则通知静默丢失。
- **NotificationCompat.Builder 构造函数必须传 CHANNEL_ID**：`new NotificationCompat.Builder(context, CHANNEL_ID)`。不传则 API 26+ 前台服务抛 `CannotPostForegroundServiceNotificationException`。
- **前台服务**：API 26+ 调 `startForeground()`；启动用 `startForegroundService()`；manifest 声明前台服务类型（SyncService=`dataSync`、TimeclockService=`specialUse`，API 34+ 必须）。Alarm PendingIntent 在 API 26+ 用 `getForegroundService()`。
- **API 33+ 同步前运行时申请 POST_NOTIFICATIONS**。
- **提醒类通知（超时/REST 结束）必须用独立 IMPORTANCE_HIGH 渠道**：渠道 importance 优先于 `notification.defaults`，LOW 渠道静默屏蔽声音/振动/heads-up。不复用前台通知做提醒（很多设备不会为 ongoing 通知再次发声）。渠道 ID：`mobileorg_timeclock_timeout`（声音来自 MediaPlayer，渠道 `setSound(null,null)` 只振动）、`mobileorg_timeclock_rest`（渠道自带声音+振动）。开始下一轮时取消 REST 通知防陈旧。
- **闹钟声音用 USAGE_ALARM 流**：通知渠道声音走通知流，静音模式被屏蔽；`MediaPlayer` + `AudioAttributes.USAGE_ALARM` 在闹钟流播放。MediaPlayer 生命周期作为 Service 字段管理，所有 stop/cancel/destroy 路径显式 release（曾因 OnCompletionListener 后双重 release 崩溃）。
- **超时通知的"关闭闹铃"action**：`ACTION_ALARM_DISMISS` + `setAutoCancel(false)` + `deleteIntent`——解除按钮始终可用，滑掉通知也能停闹钟。只停声音不停会话。
- **周期更新的通知必须刷新所有文字字段**：`updateTime()` 每 60s 刷新大号时间，但标题行只在创建时设置 → 标题陈旧。任何多字段 RemoteViews 周期更新时刷新全部时间字段。
- **通知 action 图标各不相同**：pause=⏸、stop=⏹、play=▶，内联按钮同理。
- **Scoped Storage**：`WRITE_EXTERNAL_STORAGE` 限 maxSdkVersion 28；`requestLegacyExternalStorage=true` 保留 SDCard 同步兼容。

## 2. Service 与权限

- **Service 提前 return 后 onDestroy 必须 NPE 防护**：`onCreate()`/`onStartCommand()` 中提前 return（如权限检查）后 Android 仍调 `onDestroy()`，被跳过代码中本应初始化的字段使用前必须判空。
- **主线程禁止网络操作**：Synchronizer 构造函数在主线程调用，构造函数绝不做网络 I/O（SSH/HTTP），全部放后台同步线程。
- **每个入口点独立做危险权限检查**：同一功能可从多个 UI 路径（ActionMode 菜单 AND options 菜单）进入时，每条路径都要检查后才启动 Service；绝不假设用户来自已检查的路径。
- **Service 内防御性编码**：硬件 API（如 `MediaRecorder.setAudioSource()`）用 try-catch 包裹——权限可能在 UI 检查与 Service 执行之间被撤销，绝不让权限拒绝导致崩溃。
- **权限检查必须给用户反馈**：绝不用 `checkCallingOrSelfPermission()`（被拒时静默返回）。用 `ContextCompat.checkSelfPermission()` + `ActivityCompat.requestPermissions()` 弹系统对话框；多路径共用检查时委托单一方法（如 `tryStartRecording()`）。设置页查询类场景（`SettingsActivity.populateCalendarNames()`）未授权时申请，在 `onRequestPermissionsResult()` 回调中重试查询——曾静默抛 SecurityException 显示"日历不存在"。
- **权限申请不得随 Activity 重建重复弹出**：申请前检查当前状态，已在请求流程中则不重复发起（`.scratch/calendar-permission-prompt` 有 4 用例守护）。
- **开关型 preference 与其依赖是独立配置，Service 入口守卫半配置状态**（2026-09-05 日历崩溃）：`calendarEnabled` 勾选后 `calendarName` 可以永远为空且 UI 无提示——入口统一 `CalendarWrapper.isCalendarSelected()` 守卫，不完整则跳过并 `Log.w` 留可观测信号。throws 声明的 API 必须有调用方处理链。
- **TimeclockDialog 必须随服务停止关闭**：服务更新状态后（`checkStopSelf()`/`stopSelf()` 之前）广播 `BROADCAST_STATE_CHANGED`，对话框 `onStart()` 注册 receiver 调 `maybeFinish()`。对话框内停止按钮用本地 `clockSection.getVisibility()` 判断，不用异步的 `service.isPomodoroRunning()`（startService 是异步的，有竞态）。

## 3. 广播与 Intent

- **sendBroadcast 必须 setPackage()**：targetSdk 34 下隐式广播可能无法投递到 `RECEIVER_NOT_EXPORTED` 的 receiver。适用于所有 `OrgUtils.announceSync*()`。
- **Intent.getAction() 可能为 null**：SINGLE_TOP/CLEAR_TOP 导航走 `onNewIntent()`，intent 可能无 action。用 `CONSTANT.equals(intent.getAction())`（常量在左）。
- **Preference intent 用 targetPackage/targetClass**：不用隐式 `android:action`，现代 Android 上解析可能失败。
- **AlarmManager 触发的 BroadcastReceiver 必须 exported="true"**：系统进程（uid 2000）投递广播，非 exported 报 `Permission Denial`。即使 action 是 app 私有的。
- **非 exported Activity 无法 `am start`**：shell uid 被拒。测试中用 `targetContext.startActivity(intent + FLAG_ACTIVITY_NEW_TASK)`。

## 4. UI 与主题

- **菜单 XML 用 `app:showAsAction`**：AppCompat Toolbar 静默忽略 `android:showAsAction`，图标不显示且无报错。
- **长按 ActionMode 菜单有 4 套**（2026-08-25）：`onCreateActionMode()` 按节点类型选 `outline_node` / `outline_node_uneditable` / `outline_file` / `outline_file_uneditable`。加菜单项必须审计 4 套——agenda 类节点走 `outline_file_uneditable` 分支，漏加无任何报错。
- **主题系统两层独立体系**：(1) XML 主题（`themes.xml` + `OrgUtils.setTheme()`）控制 ActionBar/preference/对话框；(2) Java 主题（`Gui/Theme/`）控制 outline 渲染。新增主题选项必须同时更新两处 + `themes.xml` 定义匹配 style（parent 选对 dark/light 基底）。只改一层导致页面外观不一致。
- **Preference 选项必须有实现分支**：`arrays.xml` 的每个 entry 都要有对应代码，否则静默落入默认（Monochrome 曾落 Light）。
- **主题变更 resume 不生效**：`setTheme()` 必须在 `setContentView()` 前调用。从设置改主题返回只触发 `onResume()`——用字段跟踪主题名，变化则 `recreate()`。只有 OutlineActivity 受影响（其他 Activity 每次新建）。
- **MaterialComponents 迁移 parent 映射**：`Theme.AppCompat`→`Theme.MaterialComponents`、`Widget.AppCompat.ActionBar.Solid`→`Widget.MaterialComponents.ActionBar.Solid`。`.Inverse` 变体无等价物，用 `.Solid`（`Light.DarkActionBar` 已提供深色 ActionBar）。
- **MaterialComponents widget 必须显式 layout_width/layout_height**：缺任何维度 inflate 时抛 `UnsupportedOperationException`（AppCompat widget 可继承默认，MaterialComponents 不行）。
- **Material Components 约 21000 方法，minSdk<21 需 Multidex**：`multiDexEnabled true` + `androidx.multidex:multidex:2.0.1` + Application `attachBaseContext()` 调 `MultiDex.install()`。
- **vector drawable 用 xmlns:android**：`res-auto` 是 layout 的 `app:` 命名空间，用在 `<vector>` 上 AAPT 构建失败。
- **Fragment 子类必须 public static**：AndroidX Fragment 1.2+ 要求可无参重建。非静态内部类改为 static + Bundle arguments + `getActivity()` 强转。
- **绝不用宽泛 try-catch 包 Activity 生命周期**：`onCreate()` 吞异常后 Activity 无 view，`onResume()` 在 null view 上崩溃且原始错误不可见（INFO 级日志）。让 RuntimeException 传播；防御性判空放在 `onResume()`/broadcast 回调。
- **用户主动点击按钮必须有确定性结果**：保存/取消/确定后动作无条件完成，绝不依赖正交状态（如 pomodoro 是否运行）。唯一例外：通知按钮的"停止"动作用 `maybeFinish()`（无其他活动才关闭）是对的。
- **DurationPicker 是预览不是提交**：picker 确定按钮只更新显示值；真正保存在用户点"保存"时发生。混淆两者让 UI 心智模型失灵。
- **时长输入不用 TimePickerDialog**：API 34 + MaterialComponents 下 `onTimeSet()` 参数和 `view.getHour()/getMinute()` 都返回初始值，用户选择被完全忽略。用 `AlertDialog` + 双 NumberPicker（`DurationPickerFragment`）。
- **NumberPicker.getValue() 前 clearFocus()**：焦点未清除时 getValue 返回初始值，忽略用户输入。
- **View.startAnimation() 需 view 已 attach**：`MenuItem.setActionView()` 配动画时先 setActionView 再 `View.post()` 启动；对未 attach 的 view 启动动画被静默忽略。
- **同步按钮动画必须在 onPrepareOptionsMenu() 自愈**：`onResume()` 的 `invalidateOptionsMenu()` 重建菜单会丢 action view；`SYNC_DONE` 时 `getActionView()` 可能返回 null。修复三件套：`SyncService.isSyncRunning` 静态标志 + onPrepare 检查恢复/清除动画 + SYNC_DONE handler 对 menuItem 判空。
- **isSyncRunning 必须在 SYNC_DONE 广播之前清除**：广播后、finally 清标志之间，任何 `invalidateOptionsMenu()` 会看到 `isSyncRunning=true` 且 actionView=null → 恢复旋转动画，之后无人能停。通用规则：UI 回调读取的状态标志必须在宣布变化的广播**之前**更新。
- **绝不在同步进度回调中 invalidateOptionsMenu()**：SYNC_PROGRESS 每 tick 触发刷新，菜单重建丢弃 action view 代际，旧 spinner 永远旋转。只在 SYNC_DONE 分支 invalidate（停止动画之后）。
- **OutlineAdapter 有 2 个固定 header**（TODO、Agenda）：position↔index 换算必须减 `numExtraItems=2`。
- **`OrgNodeListActivity` 无 onSaveInstanceState**：旋转可能出问题。
- **AppCompat per-app language 持久化需 AppLocalesMetadataHolderService**（2026-08-27 手测）：API<33 上 `setApplicationLocales()` 默认只存内存。manifest 声明该 service（`enabled="false"` + `exported="false"` + `autoStoreLocales=true` meta-data），AppCompat 反射读取才持久化。非 AppCompat Activity 需 `attachBaseContext` 里 `createConfigurationContext` 手动 wrap（`OrgUtils.wrapForAppLocales`）。
- **manifest label 的 ActionBar 标题不跟随语言切换**（2026-08-27 手测）：框架在 locale 应用前解析 `labelRes`。修复：`onCreate()` 显式 `setTitle(R.string.xxx)`（HelpActivity/HelpDetailActivity 受影响）。硬编码品牌名无需处理。

## 5. 数据层与 SQL

- **LIKE 模式中的用户输入必须转义 %/_ 并配 ESCAPE**（2026-08-29）：`OrgFileRepository.likePattern()` 统一 trim + 转义（`%`→`\%`、`_`→`\_`、`\`→`\\`），selection 加 `ESCAPE '\'`。任何把外部值放进 LIKE 模式的地方同样处理。
- **计算值拼 LIKE 前守卫空字符串**（事故 2026-08-22）：`nodeId` 为空时 `PAYLOAD LIKE '%%'` 匹配全表并覆写。capture 型节点 `getNodeId()` 返回 `""` 是常态。守卫 `nodeId == null || nodeId.isEmpty()`；`generateApplyWriteEdits` 跳过 `fileId=-1` 的孤儿节点。通用规则：审计危险 API 对调用者能传入的*每一个*状态。
- **`OrgNode(OrgNode)` 复制构造必须复制身份字段**（2026-08-23）：只复制展示字段会让"复制→变更→按 id 持久化"静默写空（`updateNode(buildIdUri(-1))` 匹配 0 行）。复制**所有**字段；需要独立身份的调用方（`cloneChildren`、`switchToView`）显式覆盖。当守卫把响亮失败变成静默无操作时，检查调用者原来依赖什么。
- **`new OrgNode()` 默认 parentId=-1/fileId=-1 会产生孤儿行**：不设这两个字段就 write 的路径（如 `EditActivityControllerCreate.saveEdits`）创建脱离文件树的节点。写节点时显式设置两者。
- **setPayload 只改内存**：变更需本地可见（非仅同步）时，`setPayload()` 后必须 `write(resolver)`（`addLogbook` 教训）。
- **not-found 异常路径上的 cursor 泄漏**（2026-06-21）：`getByFilename(CAPTURE_FILE)` 的 not-found 是*正常*路径，每次 resume 都跑。throw 分支关闭 cursor（或 try-finally）。StrictMode（`detectLeakedClosableObjects()`+`penaltyLog()`）定位分配栈。
- **仅为匹配而加载数据时只加载 equals() 用到的字段**：`CalendarSyncService.getCalendarEntries()` 曾把全文件日历条目（含 245KB payload）载入 MultiMap 导致 OOM——`CalendarEntriesParser.includePayload=false` 跳过 description/location；cursor 在 finally 关闭。
- **OrgRenderer XSS 防护顺序**：先 `htmlEncode()` 再 `applyInlineMarkup()` 再链接。顺序既防注入又保留 org 格式。
- **OrgRenderer 用原始 payload**：调 `node.getPayload()`（原始），内部 `preClean()` 自清理。不要 `getCleanedPayload()`——会剥掉渲染需要的 `#+BEGIN_SRC`。
- **OrgRenderer 递归深度上限 50**：`nodeToHTMLRecursive()` 的 `MAX_RECURSION_DEPTH`，超深嵌套静默截断。
- **PayloadFragment 渲染前同步编辑内容**：`node.setPayload(payload.get())`，否则预览显示陈旧数据。
- **`OrgData` 是 `OrgContract.OrgData` 内部类**：包外 import 用全名 `com.matburt.mobileorg.OrgData.OrgContract.OrgData`。
- **跨包静态方法必须 public**：包私有对其他包不可见。
- **单例非线程安全**：DB、Parser、Synchronizer 是单例；同步线程写、UI 读存在竞态。

## 6. 定时器与闹钟

- **AlarmManager 精确定时在现代 Android 不可靠**：`setRepeating()` 自 API 19 不精确；`setWindow()`<10min 在 API 31+ 被强制拉长到 10min；`setExact*()` 在 API 31+ 需 `SCHEDULE_EXACT_ALARM`（特殊权限，用户须在系统设置授予，全新安装默认拒绝）；Doze/App Standby 延迟一切非精确闹钟。
- **策略集中在 `ReminderScheduler.chooseAlarmStrategy()`**：API 31+ 无权限回退 `setWindow()`（**不是** `setExactAndAllowWhileIdle()`——仍抛 SecurityException）；API 23-30 用 `setExactAndAllowWhileIdle()`；每日总览用一次性 `setExact()` 在 receiver 中重新调度。
- **前台服务一次性 Handler 定时必须有周期兜底**（MIUI/xaga 实测 20min 定时晚 25min）：`updateTick`（60s 自重新调度）兼做兜底——`getRemainingMillis() <= 0 && !isTimedOut()` 时调 `handlePomodoroTimeout()`。`handlePomodoroTimeout` 幂等（`isTimedOut()` 守卫——`markTimeout` 保持 running=true，所以不能只查 `isRunning()`）。
- **saveClock 时间语义**：clock out 时 `endTime=System.currentTimeMillis()`、`startTime=endTime-duration`（向过去调整），不是 `endTime=startTime+duration`（会把结束时间推到未来）。

## 7. 同步与文件

- **同步必须清理远端已删除的文件**：`pull()` 只按校验和找下载项，从不检查"本地有、远端 index 没有"。`removeRemoteDeletedFiles()` 对比 filenameMap 移除孤儿（排除 CAPTURE_FILE/AGENDA_FILE）；index.org 解析移到 `changedFiles.size()==0` 提前 return 之前——仅文件删除不产生 changed files 但仍需清理。
- **同步配置导出/导入必须用 SAF**：`FileWriter` 到 `getExternalFilesDir()` 卸载即失；公共目录 API 29+ 报 EACCES。用 `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`。
- **ACTION_OPEN_DOCUMENT 用 `setType("*/*")`**（2026-08-23，MI PAD 4/Android 9）：老版 `MimeTypeMap` 不映射 `.json`，精确 MIME 使文件在 SAF 选择器中置灰不可选。创建类 intent 可保留精确 MIME。
- **卸载/清除 app 前必须确认无未同步数据**：本地 capture/edit 只在 app 私有 SQLite。`adb uninstall`/`pm clear` 前确认同步或导出（曾丢 7 条记录）。deploy.sh 只用 `adb install -r`，永不 uninstall。

## 8. 测试

- **API 30+ ProviderTestCase2**：`RenamingDelegatingContext` delegate 为 null → `getDatabasePath()` NPE。`super.setUp()` 前 `setContext(ApplicationProvider.getApplicationContext())`。DB 数据跨测试持久化——setUp 清空所有表（Edits/OrgData/Files）。`getMockContext()` 的 `MockContext` 方法（如 `getPackageName()`）会抛异常——用 `ContextWrapper` 包装真实 context 并 override `getContentResolver()`。
- **废弃 android.test.* 需 useLibrary 三件套**：`android.test.base`、`android.test.runner`、`android.test.mock` 加进 build.gradle 的 `android {}`。
- **instrumentation 中 `pm revoke` 已授予权限会杀 app 进程（=instrumentation 自己）**（2026-08-28，MIUI/API 27）：MIUI PowerKeeper 在权限撤销时 force-stop。grant 用例按字母序排最后（`@FixMethodOrder(NAME_ASCENDING)`+方法名编号）；@After 绝不 revoke——connectedAndroidTest 每次卸载重装，天然回到默认 denied 态。
- **MIUI 权限弹窗的焦点检测不可靠**：`dumpsys window` 的 mCurrentFocus 未必移出 app 包名。可靠信号：对话框必然 pause 被覆盖的 activity——正向轮询 `scenario.getState() == Lifecycle.State.STARTED`（无 PAUSED 粒度），负向断言轮询期间恒 RESUMED。
- **WebView 诊断三坑**（2026-08-26）：虚拟节点 uiautomator dump 中 x 可信 y 不可信（定位用截图像素分析）；`historyUrl=null` 时 `getUrl()` 恒 `about:blank`；`evaluateJavascript` 的合成 click 不触发 `shouldOverrideUrlLoading`，测试须直接驱动回调。
- **loadDataWithBaseURL 的 baseUrl 决定相对链接解析**：页面内所有相对 URL 相对 baseUrl 解析，与实际读取路径无关。baseUrl 与资源目录不一致 → WebView 静默 ERR_FILE_NOT_FOUND、保留旧页面（"图片正常、链接没反应"）。baseUrl 从实际路径父目录推导；`shouldOverrideUrlLoading` 拦截 asset 前缀转回 `loadAsset()`（保留 dark class 注入）；共享资源用 `../` 上跳。
- **测试 payload 构造坑**：`stripProperties()` 提取 `:ID:` 值要求行尾换行——写 `":ID: xxx\n"`，否则 getId() 返回 null 走 olp: 分支，测试伪通过。

## 9. 工程流程

- **绝不用 `git add -A`**：会暂存不该提交的文件。`git add <file>` + `git status`/`git diff --cached` 审查。
- **批量正则重构逐 commit 等 CI 验证**：正则可能捕获取意外的目标（`== false` 曾捕获 `if(` 前缀；`return (.+?)` 曾吞调用）。不在未验证时堆积机械变更。
- **删除常量/字段必须 grep 整个仓库包括 androidTest**：测试引用相同常量，主源码构建发现不了测试 import 损坏。
- **构造函数参数用基类型以利测试**：接收具体子类则测试 stub（继承基类）传不进来。
- **版本守卫统一 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` 模式**。
- **AndroidX + Jetifier**：本地 JAR 启用 Jetifier。
- **限流恢复**：API 429 时用 `CronCreate` 在重置时间调度一次性任务恢复工作，不轮询不立即重试。

## 10. 事故记录（根因分析详版）

需要完整事故经过与验证过程的条目，见博文 `~/github/lujun9972.github.com/编程之旅/MobileOrg-Android-从API-17迁移到API-34的实战记录.org` 与 `docs/adr/`。
