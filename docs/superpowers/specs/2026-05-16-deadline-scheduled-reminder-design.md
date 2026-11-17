# DEADLINE/SCHEDULED 提醒服务设计规格

## 目标

为 MobileOrg Android 添加基于 DEADLINE 和 SCHEDULED 日期的系统通知提醒。每次同步后自动扫描即将到期的任务，通过 AlarmManager 注册精确提醒，到期时发送 Android 通知。同时提供每日概览通知，汇总当日待办。

## 现状

- `OrgNodeTimeDate` 已解析 DEADLINE/SCHEDULED 日期（类型枚举：Scheduled, Deadline, Timestamp, InactiveTimestamp）
- `OrgProviderUtils.getActiveTodos()` 能区分「活跃态」和「完成态」TODO
- 通知渠道已有 `mobileorg_sync` 和 `mobileorg_timeclock`，需新增 `mobileorg_reminder`
- `SyncService` 同步完成后发送 `SYNC_DONE` 广播，可在此触发提醒注册
- `CalendarSyncService` 已实现日历双向同步（含提醒），但是通过系统日历间接实现，需要 CALENDAR 权限，且不区分 DEADLINE/SCHEDULED
- 现有设置中「日历提醒」是日历同步的附属功能，与本次设计的直接提醒互相独立、可共存

## 架构

```
同步完成 → ReminderScheduler.scheduleAll(resolver, context)
                ↓
    查询所有含 DEADLINE/SCHEDULED 的 OrgNode
                ↓
    过滤：已完成态 / 已过期 / 全局开关关闭
                ↓
    对每个日期：计算提醒时间 = 节点日期 - 对应提前量配置
                ↓
    AlarmManager.setWindow() 注册 PendingIntent（Uri 含 nodeId + dateType）
                ↓
    ReminderReceiver.onReceive()
                ↓
    查询节点信息 → 构建 Notification → notify()
                ↓
    点击通知 → ViewActivity 打开对应节点

--- 每日概览 ---

    ReminderScheduler.scheduleDailyOverview(context)
                ↓
    AlarmManager 注册每日定时闹钟
                ↓
    DailyOverviewReceiver.onReceive()
                ↓
    查询今日 SCHEDULED + 即将到期 DEADLINE → 构建摘要通知
```

## 核心组件

### 1. ReminderScheduler（`util/ReminderScheduler.java`）

职责：扫描日期、过滤、注册 Alarm。

**公开接口：**
```java
public class ReminderScheduler {
    // 同步后调用，注册所有未来提醒 + 每日概览闹钟
    public static void scheduleAll(ContentResolver resolver, Context context);

    // 取消所有已注册的提醒
    public static void cancelAll(Context context);

    // 设备重启后重新注册
    public static void rescheduleAll(ContentResolver resolver, Context context);

    // 注册每日概览闹钟
    public static void scheduleDailyOverview(Context context);
}
```

**实现要点：**

1. 从 SharedPreferences 读取全局开关和提前量配置
2. 查询 OrgData 中所有含 DEADLINE 或 SCHEDULED 日期的节点：
   - **包含**：有活跃态 TODO 的节点 + 无 TODO 状态但有日期的节点
   - **排除**：完成态 TODO（DONE、CANCELLED 等），用 `getActiveTodos()` 判断
3. 对每个节点的每个日期类型（DEADLINE / SCHEDULED）分别处理：
   - DEADLINE：提醒时间 = `节点日期 - deadlineAdvance`
   - SCHEDULED：提醒时间 = `节点日期 - scheduledAdvance`
   - Timestamp / InactiveTimestamp：不注册提醒
   - 若提醒时间已过（< 当前时间），跳过
   - 忽略 repeater（`+1w` 等），只按原始日期计算
4. 用 `AlarmManager.setWindow(type, triggerAt, windowLength, pendingIntent)` 注册
5. PendingIntent 唯一性：`Uri.parse("mobileorg://reminder/" + nodeId + "/" + dateType)`
   - 每个节点的 DEADLINE 和 SCHEDULED 各自独立的 PendingIntent
6. 调用前先 `cancelAll()` 清除旧 alarm，再重新注册（避免重复/过期）
   - `cancelAll()` 实现方式：查询 DB 中所有含 DEADLINE/SCHEDULED 的节点，为每个构建 PendingIntent 并调用 `AlarmManager.cancel()`。不需要额外的 SharedPreferences 跟踪——cancel 一个不存在的 alarm 是安全的 no-op
7. 只注册未来 7 天内的提醒（依赖用户定期同步来注册更远的提醒）
8. 设置变更（提前量等）不立即生效，下次同步时 `scheduleAll()` 用新配置重新注册

**查询逻辑：**

```java
// 查询所有非完成态且 payload 含 DEADLINE 或 SCHEDULED 的节点
Set<String> activeTodos = OrgProviderUtils.getActiveTodos(resolver);
// 构建排除条件：TODO 为空或 TODO 在 activeTodos 集合中
Cursor cursor = resolver.query(
    OrgData.CONTENT_URI,
    OrgData.DEFAULT_COLUMNS,
    OrgData.PAYLOAD + " LIKE ? OR " + OrgData.PAYLOAD + " LIKE ?",
    new String[]{"%DEADLINE:%", "%SCHEDULED:%"},
    null
);
// 遍历结果时过滤：TODO 为空 || activeTodos.contains(TODO)
```

然后从 payload 解析日期（复用 `OrgNodePayload.getScheduled()` / `getDeadline()`）。

### 2. ReminderReceiver（`Services/ReminderReceiver.java`）

职责：接收单个 Alarm 触发，发送通知。

**声明：**
- 静态 BroadcastReceiver，在 AndroidManifest.xml 注册
- IntentFilter: `com.matburt.mobileorg.REMINDER`
- 需要权限：无（内部广播）

**Intent Extra 约定：**
- `nodeId` (long)：节点 ID
- `dateType` (String)：`"DEADLINE"` 或 `"SCHEDULED"`
- `dateString` (String)：格式化的日期，如 `"2024-01-15"`

**实现要点：**

1. 从 Intent extra 提取 node ID、dateType、dateString
2. 用 ContentResolver 查询节点（标题、TODO 状态、payload）
3. 若节点已完成（TODO 不在 activeTodos 中），不发通知
4. 构建 Notification：
   - 标题：节点标题
   - 内容：`DEADLINE: 2024-01-15` 或 `SCHEDULED: 2024-01-15`
   - 点击 Action：Intent 打开 ViewActivity 定位到该节点
5. 使用 NotificationCompat.Builder + 渠道 `mobileorg_reminder`
6. Notification ID = `(nodeId + dateType).hashCode()`（确保同一节点的 DEADLINE 和 SCHEDULED 各自独立）

### 3. DailyOverviewReceiver（`Services/DailyOverviewReceiver.java`）

职责：每天定时触发，发送今日待办摘要通知。

**声明：**
- 静态 BroadcastReceiver，在 AndroidManifest.xml 注册
- IntentFilter: `com.matburt.mobileorg.DAILY_OVERVIEW`

**通知内容格式：**

有内容时：
```
📋 今日待办 (3)

今日 SCHEDULED (2):
· 完成周报
· 团队会议

即将到期 DEADLINE (1):
· 提交方案 (明天)
```
点击打开 OutlineActivity（主界面）。

无内容时：
```
📋 今日无待办
```
点击消掉通知。

**实现要点：**

1. 查询所有今日 SCHEDULED 的节点（TODO 筛选逻辑同 ReminderScheduler）
2. 查询所有在 DEADLINE 提前量窗口内的 DEADLINE 节点（复用 deadlineAdvance 配置）
3. 按格式构建分节摘要通知
4. 无内容时也发送「今日无待办」通知（确认系统在运行）
5. 使用同一通知渠道 `mobileorg_reminder`
6. 固定 Notification ID（如 `0`），每天覆盖前一天的概览

**每日概览闹钟注册：**
- 在 `scheduleAll()` 末尾调用 `scheduleDailyOverview()`
- 从 SharedPreferences 读取用户配置的概览时间（预设整点）
- 用 `AlarmManager.setRepeating()` 注册每日重复闹钟（API 19+ 不精确，Doze 下可能延迟几十分钟，可接受）
- `MobileOrgStartupIntentReceiver` 中也重新注册（见下）

### 4. 启动重注册（复用 `MobileOrgStartupIntentReceiver`）

职责：设备重启后重新注册所有提醒。

复用现有的 `MobileOrgStartupIntentReceiver`（已监听 `BOOT_COMPLETED`），在 `onReceive()` 末尾追加：

```java
// 在现有 SyncService.startAlarm() 和 CalendarSyncService 启动逻辑之后
ContentResolver resolver = context.getContentResolver();
ReminderScheduler.rescheduleAll(resolver, context);
ReminderScheduler.scheduleDailyOverview(context);
```

不新建独立的 BootReceiver。

### 5. 通知渠道

在 `MobileOrgApplication.onCreate()` 中创建：

```java
NotificationChannel channel = new NotificationChannel(
    "mobileorg_reminder",
    "任务提醒",
    NotificationManager.IMPORTANCE_HIGH
);
channel.setDescription("DEADLINE 和 SCHEDULED 任务到期提醒");
channel.enableVibration(true);
```

单条提醒和每日概览共用此渠道。

### 6. 设置界面

在现有 `preferences.xml` 中新增一个 PreferenceScreen：

```xml
<PreferenceScreen android:title="任务提醒">
    <SwitchPreference
        android:key="key_reminderEnabled"
        android:title="启用提醒"
        android:defaultValue="true" />

    <ListPreference
        android:key="key_reminderDeadlineAdvance"
        android:title="DEADLINE 提前提醒"
        android:entries="@array/reminder_advance_labels"
        android:entryValues="@array/reminder_advance_values"
        android:defaultValue="259200000"
        android:dependency="key_reminderEnabled" />

    <ListPreference
        android:key="key_reminderScheduledAdvance"
        android:title="SCHEDULED 提前提醒"
        android:entries="@array/reminder_advance_labels"
        android:entryValues="@array/reminder_advance_values"
        android:defaultValue="0"
        android:dependency="key_reminderEnabled" />

    <ListPreference
        android:key="key_reminderDailyOverviewTime"
        android:title="每日概览时间"
        android:entries="@array/daily_overview_time_labels"
        android:entryValues="@array/daily_overview_time_values"
        android:defaultValue="28800000"
        android:dependency="key_reminderEnabled" />
</PreferenceScreen>
```

**提前量选项（`arrays.xml`）：**

| 标签 | 值（毫秒） |
|------|-----------|
| 当天（0:00） | 0 |
| 1 小时前 | 3600000 |
| 3 小时前 | 10800000 |
| 1 天前 | 86400000 |
| 3 天前 | 259200000 |

**每日概览时间选项（`arrays.xml`）：**

| 标签 | 值（当日零点后的毫秒偏移） |
|------|-----------|
| 6:00 | 21600000 |
| 7:00 | 25200000 |
| 8:00 | 28800000 |
| 9:00 | 32400000 |
| 10:00 | 36000000 |

设置变更不立即生效，下次同步时 `scheduleAll()` 用新配置重新注册。

### 7. 同步流程集成

在 `SyncService` 或 `Synchronizer.runSynchronizer()` 同步成功后调用：

```java
ReminderScheduler.scheduleAll(resolver, context);
```

## 提前量计算逻辑

```
提醒时间 = 节点日期 - 提前量配置

示例（DEADLINE: <2024-01-15 10:00>，提前量 = 1天）：
  提醒时间 = 2024-01-15 10:00 - 1天 = 2024-01-14 10:00

示例（SCHEDULED: <2024-01-15>，提前量 = 当天）：
  提醒时间 = 2024-01-15 00:00 - 0 = 2024-01-15 00:00
```

没有时间部分的日期视为当天 00:00。

## 边界情况处理

1. **完成态不提醒**：用 `getActiveTodos()` 判断活跃态，Receiver 触发时再次检查
2. **无 TODO 状态的条目**：包含在提醒范围（只要有 DEADLINE/SCHEDULED 日期）
3. **已过期不注册**：计算出的提醒时间 < 当前时间则跳过
4. **同节点两个日期**：DEADLINE 和 SCHEDULED 各自独立的 PendingIntent 和通知
5. **概览与单条重叠**：都正常发送，不特殊处理
6. **设备重启**：`MobileOrgStartupIntentReceiver` 调用 `rescheduleAll()` + `scheduleDailyOverview()` 重新注册
7. **大量提醒**：只注册未来 7 天内的，依赖用户定期同步注册更远的提醒
8. **节点日期被修改**：下次同步后 `scheduleAll()` 会 `cancelAll()` + 重新注册
9. **Repeater**：MVP 不支持，忽略 `+1w` 等后缀，只按原始日期提醒
10. **概览无待办**：发送「今日无待办」通知，点击消掉

## 文件清单

| 操作 | 文件 |
|------|------|
| 新建 | `util/ReminderScheduler.java` |
| 新建 | `Services/ReminderReceiver.java` |
| 新建 | `Services/DailyOverviewReceiver.java` |
| 修改 | `AndroidManifest.xml`（注册 Receiver） |
| 修改 | `MobileOrgApplication.java`（创建通知渠道） |
| 修改 | `SyncService.java` 或 `Synchronizer.java`（同步后触发） |
| 修改 | `Services/MobileOrgStartupIntentReceiver.java`（重启后重注册） |
| 修改 | `res/xml/preferences.xml`（设置界面） |
| 修改 | `res/values/arrays.xml`（提前量 + 概览时间选项） |
| 修改 | `res/values/strings.xml`（新增字符串资源） |

## 测试策略

1. **ReminderScheduler 单元测试**：验证日期解析、提前量计算、过滤逻辑（活跃态 / 完成态 / 无 TODO）
2. **ReminderReceiver 集成测试**：验证通知构建、Intent 数据正确性
3. **DailyOverviewReceiver 测试**：验证摘要格式、空内容处理
4. **端到端测试**：
   - 创建含 DEADLINE 的节点 → 同步 → 验证 alarm 已注册
   - 触发 alarm → 验证通知出现且内容正确
   - 标记 DONE → 再次同步 → 验证 alarm 已取消
   - 触发每日概览 → 验证摘要内容正确
   - 无待办日 → 验证「今日无待办」通知

## 不在范围内

- 通知上的快捷操作按钮（标记完成/推迟）
- 解析 org 语法中的 SCHEDULED 警告期（如 `-3d`）
- 支持 repeater（`+1w`、`++1m` 等）
- 提醒的 Snooze 功能
- 按文件或标签过滤提醒范围
- 每日概览单独的通知渠道
- 设置变更后立即生效
