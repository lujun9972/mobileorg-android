# 番茄钟闹铃独立关闭 实现计划

> **面向 AI 代理的工作者：** 必需子技能：`superpowers:serial-executing-plans`。计划极小（单文件 ~20 行改动），串行执行即可。

**目标：** 在番茄钟超时通知上添加「关闭闹铃」按钮，允许用户关闭闹铃而不停止番茄钟。

**架构：** 在 `TimeclockService` 中新增 `ACTION_ALARM_DISMISS` action，超时通知添加 Dismiss 按钮 + `deleteIntent` + `setAutoCancel(false)`，顺带修复 `stopAndReleaseAlarmSound()` 的 double-release bug。

**技术栈：** Android Service, NotificationCompat, MediaPlayer

---

### 任务 1：实现所有代码变更

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`
**导出/变更接口：** `TimeclockService.java::ACTION_ALARM_DISMISS`, `TimeclockService.java::handleAlarmDismiss()`
**消费接口：** `Compat.java::FLAG_IMMUTABLE`, `TimeclockService.java::stopAndReleaseAlarmSound()`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`

- [ ] **步骤 1：添加常量和 switch case**

在第 42 行 `TIMECLOCK_TIMEOUT` 后添加：

```java
public static final String ACTION_ALARM_DISMISS = "alarm_dismiss";
```

在 `onStartCommand()` 的 switch 中，`TIMECLOCK_TIMEOUT` case 之后添加：

```java
case ACTION_ALARM_DISMISS:
    handleAlarmDismiss();
    break;
```

- [ ] **步骤 2：添加 `handleAlarmDismiss()` 方法**

在 `handlePomodoroTimeout()` 方法之后添加：

```java
private void handleAlarmDismiss() {
    stopAndReleaseAlarmSound();
    mNM.cancel(TIMEOUT_NOTIFICATION_ID);
}
```

- [ ] **步骤 3：修改超时通知构建器**

在 `handlePomodoroTimeout()` 方法中，找到 `timeoutBuilder` 构建块（约第 225-234 行），做以下变更：

替换 `.setAutoCancel(true)` 为 `.setAutoCancel(false)`。

在 `mNM.notify(...)` 调用之前，添加 Dismiss 按钮 + deleteIntent：

```java
Intent dismissIntent = new Intent(this, TimeclockService.class);
dismissIntent.setAction(ACTION_ALARM_DISMISS);
PendingIntent dismissPI = PendingIntent.getService(this, 4, dismissIntent, Compat.FLAG_IMMUTABLE);
timeoutBuilder.setDeleteIntent(dismissPI);
timeoutBuilder.addAction(new NotificationCompat.Action.Builder(
        R.drawable.ic_media_stop, "关闭闹铃", dismissPI).build());
```

- [ ] **步骤 4：修复 `stopAndReleaseAlarmSound()` double-release bug**

将 `stopAndReleaseAlarmSound()` 方法（约第 428-437 行）替换为：

```java
private void stopAndReleaseAlarmSound() {
    if (alarmMediaPlayer != null) {
        try {
            if (alarmMediaPlayer.isPlaying()) alarmMediaPlayer.stop();
            alarmMediaPlayer.release();
        } catch (IllegalStateException e) {
            // MediaPlayer already released by OnCompletionListener
        }
        alarmMediaPlayer = null;
    }
}
```

- [ ] **步骤 5：构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

---

### 任务 2：Commit 并推送 CI 验证

**依赖：** 任务 1
**文件集：** 无（git 操作）
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java docs/superpowers/specs/2026-06-06-pomodoro-alarm-dismiss-design.md
git commit -m "feat: add alarm dismiss button to pomodoro timeout notification

- Add ACTION_ALARM_DISMISS action to stop alarm without stopping pomodoro
- Add dismiss button + deleteIntent on timeout notification
- Change setAutoCancel(false) to preserve dismiss button
- Fix double-release bug in stopAndReleaseAlarmSound()"
```

- [ ] **步骤 2：推送并等待 CI**

```bash
git push
gh run list --limit 1
```

等待 CI 完成后用 `gh run view` 检查结果。预期：绿色通过。

- [ ] **步骤 3：更新 CLAUDE.md**

在 CLAUDE.md 的 "Android Compatibility (Applied Fixes)" 部分追加：

```
- **Pomodoro alarm dismiss**: Timeout notification has a "关闭闹铃" action button that stops the alarm sound without stopping the pomodoro session. Uses `ACTION_ALARM_DISMISS` with `setAutoCancel(false)` and `deleteIntent` so the dismiss button is always available. Also fixes a double-release bug in `stopAndReleaseAlarmSound()` where `release()` was outside the try-catch and could crash if `OnCompletionListener` had already released the MediaPlayer.
```

```bash
git add CLAUDE.md
git commit -m "docs: record pomodoro alarm dismiss pattern in CLAUDE.md"
git push
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量
