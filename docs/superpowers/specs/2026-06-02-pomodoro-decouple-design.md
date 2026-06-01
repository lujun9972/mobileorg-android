# 番茄钟与 Clock 解耦设计

日期: 2026-06-02

## 问题

当前番茄钟与 clock in 1:1 绑定：启动番茄钟 = clock in 一个任务，clock out = 番茄钟结束。实际使用中，一个番茄钟内可能完成多个任务，中间还可能有不需要记录的琐事。强制绑定导致用户无法在番茄钟内自由切换任务。

## 方案

方案 A：单服务内解耦。`TimeclockService` 内部拆分为两个独立状态（番茄钟 + Clock），共享一个前台 Service 和通知。

不选方案 B（拆成两个 Service）：多前台服务资源浪费，通知合并复杂。
不选方案 C（允许 node_id=-1）：hack 性质，状态管理混乱。

## 状态模型

`TimeclockService` 维护两个正交状态：

```
番茄钟: IDLE → RUNNING → TIMED_OUT → IDLE
Clock:  IDLE → CLOCKED_IN → IDLE
```

状态字段拆分（替代原来的单个 `startTime`）：
- `pomodoroStartTime` — 番茄钟启动时间，用于倒计时和超时计算
- `clockStartTime` — 当前 clock in 启动时间，用于已用时间计算和 CLOCK 记录
- 删除原来的 `startTime` 字段

Service 存活条件：至少一个状态非 IDLE。都回 IDLE 时 `stopSelf()`。

### 生命周期事件

| 操作 | 番茄钟状态 | Clock 状态 | Service |
|------|-----------|-----------|---------|
| 启动番茄钟 | IDLE→RUNNING | 不变 | 保活 |
| Clock in | 不变 | IDLE→CLOCKED_IN | 保活 |
| Clock in（已 clock in） | 不变 | 自动 CLOCKED_IN→IDLE→CLOCKED_IN | 保活 |
| Clock out（保存） | 不变 | CLOCKED_IN→IDLE | 若番茄钟也 IDLE 则停 |
| Clock out（丢弃） | 不变 | CLOCKED_IN→IDLE | 若番茄钟也 IDLE 则停 |
| 番茄钟超时 | RUNNING→TIMED_OUT | 不变 | 保活 |
| 停止番茄钟 | RUNNING/TIMED_OUT→IDLE | 不变 | 若 clock 也 IDLE 则停 |

## 入口与菜单

### 主菜单 `outline_menu.xml`（新增）

| 番茄钟状态 | 菜单项 | 行为 |
|-----------|--------|------|
| 未运行 | "Pomodoro" | 弹 DurationPicker，启动番茄钟 |
| 运行中 | "Stop 🍅" | 直接停止番茄钟 |

通过 `onPrepareOptionsMenu` 动态切换菜单项标题和图标。

### ActionMode `outline_node.xml`（修改）

| 菜单项 | 番茄钟未运行 | 番茄钟运行中 |
|--------|------------|------------|
| Pomodoro | 显示，弹 DurationPicker | 隐藏 |
| Clock In | 始终显示 | 始终显示 |

## Clock in 行为

通过 `onStartCommand` 检测当前 clock 状态：

- **未 clock in** → 直接 clock in 到新任务，设 clockStartTime
- **已 clock in** → 调用 `doClockOut(true)` 保存当前任务的 CLOCK 记录 → 清 clock 状态 → clock in 新任务

两种情况都不影响番茄钟状态。

### `doClockOut(boolean save)` 方法

Clock out 核心逻辑归属 `TimeclockService`（从 `TimeclockDialog.saveClock()` 迁移）：
- `save=true`：用 `clockStartTime` 和当前时间计算时长，调用 `OrgNodeRepository.addLogbook()` 写入 CLOCK 记录。若 intent 携带 `CLOCK_DURATION` extra（分钟数），则用 `startTime = endTime - duration` 覆盖 clockStartTime
- `save=false`：丢弃记录，不做任何写入
- 两种情况都清空 clock 状态字段（node_id, node, clockStartTime）
- 若番茄钟也 IDLE，则 `stopSelf()`

`TimeclockDialog` 不再直接写 CLOCK 记录，改为发送 `CLOCK_OUT` / `CLOCK_CANCEL` intent 给 service。

## 番茄钟行为

- 启动不绑定 node_id，不自动 clock in
- 超时后提醒（震动/铃声），继续显示红色超时时间（保持现有逻辑）
- 手动停止或超时后停止 → 不影响 clock 状态

## 通知

复用现有 `RemoteViews` 布局（icon + text + time），根据状态组合动态填充：

| 状态 | text | time | Action Button |
|------|------|------|--------------|
| 只有番茄钟 | `🍅 12:30` | 空 | [Stop] |
| 只有 clock | `任务名` | `0:15` | 无 |
| 两者都有 | `🍅 12:30 \| 任务名` | `0:15` | [Stop 🍅] |

通知主体点击 → 打开统一 Activity。Action Button 点击 → 发送 `POMODORO_STOP` intent，直接停止番茄钟不打开 UI。

### 通知 action button 实现

使用 `NotificationCompat.Action` + PendingIntent 发送 `POMODORO_STOP` action 到 `TimeclockService`。图标用 `⏹`。

## 统一通知界面（替代 TimeclockDialog）

点击通知打开一个 Activity，根据当前状态动态展示内容：

### 布局

```
┌─────────────────────────────┐
│ 番茄钟区域（仅番茄钟活跃时显示）    │
│ 🍅 12:30 / 🍅 +5:00 (红色)   │
│ [停止番茄钟]                   │
├─────────────────────────────┤
│ Clock 区域（仅 clock 活跃时显示）  │
│ 任务名 @ 0:15                  │
│ [编辑时长] [保存] [取消]        │
└─────────────────────────────┘
```

- 只有一个区域时，另一个区域隐藏（GONE）
- Clock 区域的按钮行为与当前 TimeclockDialog 一致
- 停止番茄钟 = 发送 `POMODORO_STOP` + 更新 UI
- 保存 Clock = 发送 `CLOCK_OUT` + 更新 UI
- 如果两个都停了，Activity 自动 finish()

## Intent 协议

`onStartCommand` 根据 action 参数路由：

| Intent 内容 | Action 常量 | 行为 |
|------------|------------|------|
| `POMODORO_DURATION` 有值 | `POMODORO_START` | 启动番茄钟，设 pomodoroStartTime |
| NODE_ID 有值 | `CLOCK_IN` | clock in（若已 clock in 先 clock out） |
| — | `POMODORO_STOP` | 停止番茄钟 |
| — | `CLOCK_OUT` | 保存 CLOCK 记录，清 clock 状态 |
| — | `CLOCK_CANCEL` | 丢弃 CLOCK 记录，清 clock 状态 |
| — | `TIMECLOCK_UPDATE` | 更新通知时间（不变） |
| — | `TIMECLOCK_TIMEOUT` | 番茄钟超时（不变） |

## 不变的部分

- 普通 clock in（非番茄钟）的 CLOCK 记录格式不变
- `OrgNodeRepository.addLogbook()` 写入 LOGBOOK 不变
- 通知每分钟更新 alarm 机制不变
- `TimeclockService` 作为唯一前台 Service 不变
- foreground service type `SPECIAL_USE` 不变
- NotificationChannel `mobileorg_timeclock` 不变

## 涉及的文件

| 文件 | 改动 |
|------|------|
| `TimeclockService.java` | 拆分状态，重写 `onStartCommand` 路由，拆分 `cancelNotification` 为 `stopPomodoro` + `clockOut` |
| `TimeclockDialog.java` | 重构为统一 Activity，增加番茄钟区域 |
| `OutlineActionMode.java` | 修改 `runPomodoroService` 不传 node_id；修改 `runTimeClockingService` 不杀 service；番茄钟运行时隐藏 Pomodoro 菜单项 |
| `OutlineActivity.java` | 主菜单增加 Pomodoro/Stop 菜单项处理；`onPrepareOptionsMenu` 动态切换 |
| `outline_menu.xml` | 增加 `menu_pomodoro` 项 |
| `outline_node.xml` | 无变化（Pomodoro 菜单项保留，运行时通过代码隐藏） |
| `outline_file.xml` | 同 `outline_node.xml`，Pomodoro 菜单项保留，运行时通过代码隐藏 |
| `timeclock_notification.xml` | 可能不需要改（复用现有布局），如有需要调整间距 |
