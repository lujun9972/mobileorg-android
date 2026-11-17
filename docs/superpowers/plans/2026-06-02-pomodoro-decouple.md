# 番茄钟与 Clock 解耦 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 TimeclockService 内部的番茄钟倒计时与 clock in/out 解耦为两个正交状态，允许一个番茄钟内多次手动 clock in/out。

**架构：** 单 Service 内双状态机。番茄钟（IDLE/RUNNING/TIMED_OUT）和 Clock（IDLE/CLOCKED_IN）独立运行，共享一个前台通知。通知内容根据状态组合动态构建。统一 Activity 替代原 TimeclockDialog，根据活跃状态显示对应区域。

**技术栈：** Android Java，现有 TimeclockService + NotificationCompat + AlarmManager

---

### 任务 1：TimeclockService 状态模型重构

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`
**导出/变更接口：** `TimeclockService.java::onStartCommand`, `TimeclockService.java::pomodoroStartTime`, `TimeclockService.java::clockStartTime`, `TimeclockService.java::doClockOut`, `TimeclockService.java::stopPomodoro`, `TimeclockService.java::isPomodoroRunning`, `TimeclockService.java::isClockedIn`, `TimeclockService.java::getClockElapsedString`, `TimeclockService.java::getPomodoroRemainingString`, `TimeclockService.java::getClockNode`, `TimeclockService.java::CLOCK_IN`, `TimeclockService.java::CLOCK_OUT`, `TimeclockService.java::CLOCK_CANCEL`, `TimeclockService.java::POMODORO_START`, `TimeclockService.java::POMODORO_STOP`
**消费接口：** `OrgNodeRepository.java::addLogbook`
**复杂度：** deep

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`

- [ ] **步骤 1：添加 action 常量和状态字段**

在类顶部添加：
```java
// Action constants
public static final String ACTION_CLOCK_IN = "clock_in";
public static final String ACTION_CLOCK_OUT = "clock_out";
public static final String ACTION_CLOCK_CANCEL = "clock_cancel";
public static final String ACTION_POMODORO_START = "pomodoro_start";
public static final String ACTION_POMODORO_STOP = "pomodoro_stop";

// Extra for edited duration (minutes) from dialog
public static final String CLOCK_DURATION = "clock_duration";
```

替换原 `startTime` 字段为：
```java
private long pomodoroStartTime;   // 番茄钟启动时间
private long clockStartTime;      // 当前 clock in 启动时间
private boolean pomodoroRunning = false;
private boolean pomodoroTimedOut = false;
private boolean clockedIn = false;
```

删除 `hasTimedOut`、`pomodoroMode`、`pomodoroDurationMinutes` 旧字段（pomodoroDurationMinutes 重命名为 pomodoroDurationMins 保留）。

- [ ] **步骤 2：重写 `onStartCommand` 路由**

```java
public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) { stopSelf(); return START_NOT_STICKY; }
    String action = intent.getAction();
    if (action == null) action = inferAction(intent);
    
    switch (action) {
        case ACTION_POMODORO_START:
            handlePomodoroStart(intent);
            break;
        case ACTION_CLOCK_IN:
            handleClockIn(intent);
            break;
        case ACTION_POMODORO_STOP:
            handlePomodoroStop();
            break;
        case ACTION_CLOCK_OUT:
            doClockOut(true, intent.getIntExtra(CLOCK_DURATION, -1));
            break;
        case ACTION_CLOCK_CANCEL:
            doClockOut(false, -1);
            break;
        case TIMECLOCK_UPDATE:
            updateTime();
            break;
        case TIMECLOCK_TIMEOUT:
            handlePomodoroTimeout();
            break;
    }
    return START_NOT_STICKY;
}
```

`inferAction(Intent)` 兼容无 action 字符串的旧入口：有 `POMODORO_DURATION` extra → `ACTION_POMODORO_START`，有 `NODE_ID` → `ACTION_CLOCK_IN`。

- [ ] **步骤 3：实现 `handlePomodoroStart`**

```java
private void handlePomodoroStart(Intent intent) {
    int duration = intent.getIntExtra(POMODORO_DURATION, 25);
    this.pomodoroDurationMins = duration;
    this.pomodoroStartTime = System.currentTimeMillis();
    this.pomodoroRunning = true;
    this.pomodoroTimedOut = false;
    unsetPomodoroAlarms();
    setTimeoutAlarm(duration / 60, duration % 60);
    showOrRefreshNotification();
}
```

- [ ] **步骤 4：实现 `handleClockIn`**

```java
private void handleClockIn(Intent intent) {
    long newNodeId = intent.getLongExtra(NODE_ID, -1);
    if (newNodeId < 0) return;
    // 若已 clock in，先保存旧任务
    if (clockedIn) {
        doClockOut(true, -1); // 用实际时间
    }
    try {
        this.node_id = newNodeId;
        this.node = new OrgNodeRepository(getContentResolver()).getById(node_id);
    } catch (OrgNodeNotFoundException e) {
        Log.e("MobileOrg", "[ClockIn] Node not found: " + newNodeId, e);
        return;
    }
    this.clockStartTime = System.currentTimeMillis();
    this.clockedIn = true;
    showOrRefreshNotification();
}
```

- [ ] **步骤 5：实现 `doClockOut(boolean save, int editedDurationMinutes)`**

从 `TimeclockDialog.saveClock()` 迁移核心逻辑：
```java
public void doClockOut(boolean save, int editedDurationMinutes) {
    if (!clockedIn) return;
    if (save && node != null) {
        long endTime = System.currentTimeMillis();
        long startTime;
        String elapsedTime;
        if (editedDurationMinutes > 0) {
            long durationMillis = editedDurationMinutes * 60L * 1000L;
            startTime = endTime - durationMillis;
            int h = editedDurationMinutes / 60;
            int m = editedDurationMinutes % 60;
            elapsedTime = String.format("%d:%02d", h, m);
        } else {
            startTime = clockStartTime;
            long diff = endTime - clockStartTime;
            elapsedTime = formatMillisAsTime(diff);
        }
        new OrgNodeRepository(getContentResolver()).addLogbook(node, startTime, endTime, elapsedTime);
    }
    // 清 clock 状态
    this.node_id = -1;
    this.node = null;
    this.clockStartTime = 0;
    this.clockedIn = false;
    checkStopSelf();
    showOrRefreshNotification();
}
```

- [ ] **步骤 6：实现 `handlePomodoroStop` 和 `handlePomodoroTimeout`**

```java
private void handlePomodoroStop() {
    unsetPomodoroAlarms();
    this.pomodoroRunning = false;
    this.pomodoroTimedOut = false;
    checkStopSelf();
    showOrRefreshNotification();
}

private void handlePomodoroTimeout() {
    if (!pomodoroRunning) return;
    this.pomodoroTimedOut = true;
    // 震动/铃声提醒（保持现有逻辑）
    if (notification != null) {
        notification.defaults = Notification.DEFAULT_ALL;
        mNM.notify(notificationID, notification);
        notification.defaults = 0;
    }
    updateTime();
}
```

- [ ] **步骤 7：实现 `checkStopSelf` 和辅助方法**

```java
private void checkStopSelf() {
    if (!pomodoroRunning && !clockedIn) {
        cancelNotification(); // 停前台 + stopSelf
    }
}
```

公开查询方法：
```java
public boolean isPomodoroRunning() { return pomodoroRunning; }
public boolean isClockedIn() { return clockedIn; }
public OrgNode getClockNode() { return node; }
public String getClockElapsedString() {
    if (!clockedIn) return "0:00";
    return formatMillisAsTime(System.currentTimeMillis() - clockStartTime);
}
public String getPomodoroRemainingString() {
    if (!pomodoroRunning) return "";
    if (pomodoroTimedOut) {
        long overtime = System.currentTimeMillis() - (pomodoroStartTime + pomodoroDurationMins * 60L * 1000L);
        return "+" + formatMillisAsTime(overtime);
    }
    long remaining = (pomodoroDurationMins * 60L * 1000L) - (System.currentTimeMillis() - pomodoroStartTime);
    return formatMillisAsTime(Math.max(0, remaining));
}
public boolean isPomodoroTimedOut() { return pomodoroTimedOut; }
```

- [ ] **步骤 8：重写 `showOrRefreshNotification` 和 `updateTime`**

统一通知构建逻辑，替代原 `showNotification`。根据 `pomodoroRunning` + `clockedIn` 组合填充 RemoteViews 文本和添加 action button。

`updateTime()` 保持 alarm 驱动，但用新字段（pomodoroStartTime / clockStartTime）计算。

- [ ] **步骤 9：拆分 alarm 管理**

将 `unsetAlarms()` 拆分为 `unsetPomodoroAlarms()`（取消 timeout alarm）和 `unsetUpdateAlarm()`（取消分钟更新 alarm）。update alarm 只要 service 活着就一直跑。

- [ ] **步骤 10：修改 `getStartTime` / `getEndTime` / `getNodeID`**

```java
public long getClockStartTime() { return clockStartTime; }
public long getNodeID() { return node_id; }
```

删除旧的 `getStartTime()` / `getEndTime()` / `getElapsedTimeString()`。

- [ ] **步骤 11：修改 `cancelNotification`**

```java
public void cancelNotification() {
    unsetPomodoroAlarms();
    unsetUpdateAlarm();
    mNM.cancel(notificationID);
    if (Compat.isAtLeastO()) stopForeground(true);
    notification = null;
    stopSelf();
}
```

- [ ] **步骤 12：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java
git commit -m "refactor(pomodoro): decouple pomodoro timer from clock-in state in TimeclockService

Two orthogonal state machines: pomodoro (IDLE/RUNNING/TIMED_OUT) and
clock (IDLE/CLOCKED_IN). Shared foreground notification adapts to state
combination. doClockOut() migrated from TimeclockDialog."
```

---

### 任务 2：重构 TimeclockDialog 为统一通知界面

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockDialog.java`, `MobileOrg/src/main/res/layout/timeclock_dialog.xml`
**导出/变更接口：** `TimeclockDialog.java::onStart`, `TimeclockDialog.java::saveClock`
**消费接口：** `TimeclockService.java::isPomodoroRunning`, `TimeclockService.java::isClockedIn`, `TimeclockService.java::getClockElapsedString`, `TimeclockService.java::getPomodoroRemainingString`, `TimeclockService.java::isPomodoroTimedOut`, `TimeclockService.java::getClockNode`, `TimeclockService.java::ACTION_POMODORO_STOP`, `TimeclockService.java::ACTION_CLOCK_OUT`, `TimeclockService.java::ACTION_CLOCK_CANCEL`, `TimeclockService.java::CLOCK_DURATION`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockDialog.java`
- 修改：`MobileOrg/src/main/res/layout/timeclock_dialog.xml`

- [ ] **步骤 1：修改布局 XML，增加番茄钟区域**

在 `timeclock_dialog.xml` 的 RelativeLayout 中，在 `timeclock_text` 上方添加番茄钟区域：

```xml
<LinearLayout
    android:id="@+id/pomodoro_section"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_alignParentTop="true"
    android:gravity="center"
    android:paddingBottom="12dp" >
    <TextView
        android:id="@+id/pomodoro_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textAppearance="?android:attr/textAppearanceLarge"
        android:text="" />
    <Button
        android:id="@+id/pomodoro_stop_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Stop Pomodoro" />
</LinearLayout>
```

将原 `timeclock_text` 的 `layout_alignParentTop` 改为 `layout_below="@id/pomodoro_section"`。
将原按钮 LinearLayout 的 `layout_below` 改为 `layout_below="@id/timeclock_text"`。
给 clock 区域整体包一个 LinearLayout id=`clock_section`，方便整体 GONE。

- [ ] **步骤 2：重构 TimeclockDialog Java 代码**

`onStart()` 改为从 service 读取两个状态，动态显示/隐藏区域：

```java
TimeclockService service = TimeclockService.getInstance();

// 番茄钟区域
LinearLayout pomoSection = findViewById(R.id.pomodoro_section);
if (service != null && service.isPomodoroRunning()) {
    pomoSection.setVisibility(View.VISIBLE);
    TextView pomoTime = findViewById(R.id.pomodoro_time);
    String remaining = service.getPomodoroRemainingString();
    pomoTime.setText("\uD83C\uDF45 " + remaining);
    if (service.isPomodoroTimedOut()) {
        pomoTime.setTextColor(Color.RED);
    }
    Button stopBtn = findViewById(R.id.pomodoro_stop_button);
    stopBtn.setOnClickListener(v -> {
        sendServiceAction(TimeclockService.ACTION_POMODORO_STOP);
        pomoSection.setVisibility(View.GONE);
        maybeFinish();
    });
} else {
    pomoSection.setVisibility(View.GONE);
}

// Clock 区域
LinearLayout clockSection = findViewById(R.id.clock_section);
if (service != null && service.isClockedIn()) {
    clockSection.setVisibility(View.VISIBLE);
    OrgNode clockNode = service.getClockNode();
    String name = (clockNode != null) ? clockNode.name : "(unknown)";
    String elapsed = service.getClockElapsedString();
    parseElapsedTime(elapsed);
    TextView textView = findViewById(R.id.timeclock_text);
    textView.setText(name + " @ " + elapsed);
} else {
    clockSection.setVisibility(View.GONE);
}
```

- [ ] **步骤 3：修改按钮行为，发 intent 而非直接调用**

Save 按钮：
```java
Intent intent = new Intent(TimeclockDialog.this, TimeclockService.class);
intent.setAction(TimeclockService.ACTION_CLOCK_OUT);
intent.putExtra(TimeclockService.CLOCK_DURATION, hour * 60 + minute);
startService(intent);
maybeFinish();
```

Cancel 按钮：
```java
Intent intent = new Intent(TimeclockDialog.this, TimeclockService.class);
intent.setAction(TimeclockService.ACTION_CLOCK_CANCEL);
startService(intent);
maybeFinish();
```

Edit 按钮保持不变（弹 DurationPickerFragment）。

- [ ] **步骤 4：添加 `maybeFinish` 和 `sendServiceAction` 辅助方法**

```java
private void maybeFinish() {
    TimeclockService service = TimeclockService.getInstance();
    if (service == null || (!service.isPomodoroRunning() && !service.isClockedIn())) {
        finish();
    }
}

private void sendServiceAction(String action) {
    Intent intent = new Intent(this, TimeclockService.class);
    intent.setAction(action);
    startService(intent);
}
```

- [ ] **步骤 5：删除旧的 `saveClock` 和 `endTimeclock` 方法**

删除 `saveClock(int, int)`（逻辑已迁入 service 的 `doClockOut`）。
删除 `endTimeclock()`（用 `maybeFinish()` 替代）。

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockDialog.java \
       MobileOrg/src/main/res/layout/timeclock_dialog.xml
git commit -m "refactor(pomodoro): unify TimeclockDialog for dual-state display

Show pomodoro countdown section and clock section independently.
Clock out sends intent to service instead of direct DB write."
```

---

### 任务 3：修改 OutlineActionMode — 番茄钟不绑定 node_id

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java`
**导出/变更接口：** 无
**消费接口：** `TimeclockService.java::isPomodoroRunning`, `TimeclockService.java::ACTION_POMODORO_START`, `TimeclockService.java::ACTION_CLOCK_IN`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java`

- [ ] **步骤 1：修改 `runPomodoroService` — 不传 node_id，不杀已有 service**

```java
private void runPomodoroService(int durationMinutes) {
    Intent intent = new Intent(context, TimeclockService.class);
    intent.setAction(TimeclockService.ACTION_POMODORO_START);
    intent.putExtra(TimeclockService.POMODORO_DURATION, durationMinutes);
    // 不传 NODE_ID，不杀已有 service
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent);
    } else {
        context.startService(intent);
    }
}
```

- [ ] **步骤 2：修改 `runTimeClockingService` — 不杀已有 service**

```java
private void runTimeClockingService() {
    Intent intent = new Intent(context, TimeclockService.class);
    intent.setAction(TimeclockService.ACTION_CLOCK_IN);
    intent.putExtra(TimeclockService.NODE_ID, node.id);
    // 不杀已有 service，由 service 内部处理已 clock in 的情况
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent);
    } else {
        context.startService(intent);
    }
}
```

- [ ] **步骤 3：在 `onPrepareActionMode` 中隐藏番茄钟菜单项**

```java
@Override
public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    MenuItem pomoItem = menu.findItem(R.id.menu_pomodoro);
    if (pomoItem != null) {
        TimeclockService service = TimeclockService.getInstance();
        pomoItem.setVisible(service == null || !service.isPomodoroRunning());
    }
    return true;
}
```

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java
git commit -m "refactor(pomodoro): ActionMode sends decoupled intents

Pomodoro starts without node_id. Clock-in no longer kills existing
service. Pomodoro menu hidden when timer is running."
```

---

### 任务 4：主菜单添加番茄钟入口

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/res/menu/outline_menu.xml`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`
**导出/变更接口：** 无
**消费接口：** `TimeclockService.java::isPomodoroRunning`, `TimeclockService.java::ACTION_POMODORO_START`, `TimeclockService.java::ACTION_POMODORO_STOP`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/res/menu/outline_menu.xml`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`

- [ ] **步骤 1：在 `outline_menu.xml` 添加番茄钟菜单项**

在 `menu_record` 之后添加：
```xml
<item
    android:id="@+id/menu_pomodoro"
    android:icon="@drawable/ic_menu_pomodoro"
    app:showAsAction="ifRoom"
    android:title="@string/menu_pomodoro"/>
```

- [ ] **步骤 2：在 OutlineActivity 添加 `onPrepareOptionsMenu`**

```java
@Override
public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem pomoItem = menu.findItem(R.id.menu_pomodoro);
    if (pomoItem != null) {
        TimeclockService service = TimeclockService.getInstance();
        boolean running = service != null && service.isPomodoroRunning();
        pomoItem.setTitle(running ? R.string.menu_pomodoro_stop : R.string.menu_pomodoro);
        pomoItem.setIcon(running ? R.drawable.ic_media_stop : R.drawable.ic_menu_pomodoro);
    }
    return super.onPrepareOptionsMenu(menu);
}
```

在 `onResume()` 末尾调用 `invalidateOptionsMenu()` 确保菜单刷新。

- [ ] **步骤 3：在 `onOptionsItemSelected` 添加番茄钟处理**

```java
} else if (id == R.id.menu_pomodoro) {
    TimeclockService service = TimeclockService.getInstance();
    if (service != null && service.isPomodoroRunning()) {
        // 停止番茄钟
        Intent intent = new Intent(this, TimeclockService.class);
        intent.setAction(TimeclockService.ACTION_POMODORO_STOP);
        startService(intent);
    } else {
        // 弹 DurationPicker
        showPomodoroDurationPicker();
    }
    return true;
}
```

从 `OutlineActionMode` 中提取 `showPomodoroDurationPicker` 逻辑到 `OutlineActivity`（因为现在主菜单也需要它）。或者让 ActionMode 的方法保持不变，Activity 新增一个类似方法。

- [ ] **步骤 4：添加 string 资源**

在 `strings.xml` 中添加 `menu_pomodoro_stop` = "Stop 🍅"。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/res/menu/outline_menu.xml \
       MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java \
       MobileOrg/src/main/res/values/strings.xml
git commit -m "feat(pomodoro): add pomodoro entry to main options menu

Dynamic menu item: Pomodoro when idle, Stop 🍅 when running.
DurationPicker shown on start, direct stop on running."
```

---

### 任务 5：端到端验证

**依赖：** 任务 2, 任务 3, 任务 4
**文件集：** 无
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：** 无

- [ ] **步骤 1：推送到远端触发 CI**

```bash
git push origin main
```

- [ ] **步骤 2：检查 CI 结果**

```bash
gh run list --limit 3
```

等待 CI 完成，确认构建通过。

- [ ] **步骤 3：本地连接设备进行手测**

场景清单：
1. 主菜单点 Pomodoro → 弹 DurationPicker → 选 1 分钟 → 通知显示倒计时
2. 番茄钟运行中 → 主菜单变为 "Stop 🍅" → 点之 → 番茄钟停止，通知消失
3. 番茄钟运行中 → 长按任务 A → 点 Clock In → 通知显示 `🍅 | 任务A`
4. 番茄钟+clock 都在跑 → 点通知 → 看到两个区域 → Save clock → 番茄钟继续
5. 番茄钟运行中 → 长按任务 B → 点 Clock In → 任务 A 自动 clock out → 任务 B clock in
6. 番茄钟超时 → 通知变红 → 点通知 → 看到超时 + Stop 按钮
7. 番茄钟运行中 → ActionMode 里 Pomodoro 菜单项不可见
8. 纯 clock in（不启动番茄钟）→ 行为与原来一致

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 5

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2, 任务 3, 任务 4
- Wave 3（依赖 Wave 2）：任务 5
