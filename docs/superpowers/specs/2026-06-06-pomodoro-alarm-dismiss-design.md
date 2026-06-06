# 番茄钟闹铃独立关闭设计

## 背景

番茄钟完成后，`TimeclockService.handlePomodoroTimeout()` 通过 `MediaPlayer` 在闹铃音频流上播放系统默认闹铃。该声音可能持续 30-60 秒，且走 `USAGE_ALARM` 流绕过静音模式。

当前唯一的停止路径是 `ACTION_POMODORO_STOP`，它会停止闹铃 **并且** 终止整个番茄钟会话（包括超时计时）。用户无法只关掉闹铃而保持番茄钟继续运行。

## 目标

在超时通知上添加「关闭闹铃」按钮，点击后停止 `MediaPlayer` 播放并取消超时通知，番茄钟继续运行（`pomodoroRunning=true`、`pomodoroTimedOut=true` 不变），前台通知继续显示超时计时。

## 约束

- 不自动停止闹铃（用户必须手动点击）
- 关闭闹铃后超时通知自动消失
- 不改变前台通知的行为（Stop 按钮仍停止整个番茄钟）

## 设计

### 改动范围

仅 `TimeclockService.java`。

### 变更 1：新增常量

```java
public static final String ACTION_ALARM_DISMISS = "alarm_dismiss";
```

### 变更 2：新增 switch case

在 `onStartCommand()` 的 switch 中添加：

```java
case ACTION_ALARM_DISMISS:
    handleAlarmDismiss();
    break;
```

### 变更 3：新增方法 `handleAlarmDismiss()`

```java
private void handleAlarmDismiss() {
    stopAndReleaseAlarmSound();
    mNM.cancel(TIMEOUT_NOTIFICATION_ID);
}
```

- `stopAndReleaseAlarmSound()` 已有空指针保护和 `IllegalStateException` catch，重复调用安全。
- `mNM.cancel()` 对不存在的 notification id 无副作用。
- 不修改 `pomodoroRunning`、`pomodoroTimedOut`，番茄钟继续运行。

### 变更 4：超时通知添加 Dismiss 按钮 + deleteIntent + setAutoCancel(false)

在 `handlePomodoroTimeout()` 中构建 `timeoutBuilder` 时：

```java
Intent dismissIntent = new Intent(this, TimeclockService.class);
dismissIntent.setAction(ACTION_ALARM_DISMISS);
PendingIntent dismissPI = PendingIntent.getService(this, 4, dismissIntent, Compat.FLAG_IMMUTABLE);

timeoutBuilder
    .setAutoCancel(false)                    // 点击通知体不消失，保留 Dismiss 按钮
    .setDeleteIntent(dismissPI)              // 滑动删除 → 关闹铃
    .addAction(new NotificationCompat.Action.Builder(
        R.drawable.ic_media_stop, "关闭闹铃", dismissPI).build());
```

关键改动：
- **`setAutoCancel(false)`**：替换原有的 `setAutoCancel(true)`。用户点击通知体（打开 TimeclockDialog）时通知不消失，Dismiss 按钮始终可用。
- **`deleteIntent`**：用户滑动删除通知时触发 `ACTION_ALARM_DISMISS`，等同于点击 Dismiss 按钮，行为一致。
- requestCode=4（不与已有的 1、2、3 冲突）。

### 变更 5：修复 `stopAndReleaseAlarmSound()` double-release bug

`Compat.playAlarmSound()` 的 `OnCompletionListener` 会在声音播完后 `release()` MediaPlayer，但 `alarmMediaPlayer` 字段仍指向已释放的对象。后续调用 `stopAndReleaseAlarmSound()` 时，`release()` 在 try-catch **外面**，导致 `IllegalStateException` 崩溃。

修复——把 `release()` 移入 try-catch：

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

### 数据流

```
番茄钟超时 → handlePomodoroTimeout()
  → stopAndReleaseAlarmSound() (清理旧的)
  → alarmMediaPlayer = Compat.playAlarmSound(this) (播放闹铃)
  → 超时通知 (id=1338, autoCancel=false, deleteIntent=dismiss, + "关闭闹铃" 按钮)
  → 前台通知继续显示超时 (+XX:XX)

[路径 A] 用户点击「关闭闹铃」→ ACTION_ALARM_DISMISS → handleAlarmDismiss()
  → stopAndReleaseAlarmSound()  → cancel(1338)

[路径 B] 用户滑动删除超时通知 → deleteIntent → ACTION_ALARM_DISMISS → handleAlarmDismiss()
  → stopAndReleaseAlarmSound()  → cancel(1338) (通知已被系统删除)

[路径 C] 用户点击通知体 → TimeclockDialog 打开 → 通知不消失 → Dismiss 按钮仍可用

[路径 D] 闹铃自然播完 → OnCompletionListener release MediaPlayer → 字段仍非 null
  → 用户之后点 Dismiss → stopAndReleaseAlarmSound() → catch ISE → 设 null → 安全

[最终] 用户点击前台 Stop → ACTION_POMODORO_STOP → handlePomodoroStop()
  → stopAndReleaseAlarmSound() (防御性)
  → pomodoroRunning=false, pomodoroTimedOut=false
  → cancel(1338) (防御性)
```

### 不变的部分

- 前台通知（id 1337）：Stop 按钮仍停止整个番茄钟
- `handlePomodoroStop()`：仍调用 `stopAndReleaseAlarmSound()`（防御性）
- `cancelNotification()`：仍清理一切（闹铃 + 两个通知 + stopSelf）
- `onDestroy()` → `cancelNotification()` 仍兜底清理

### 错误处理

| 场景 | 行为 |
|------|------|
| 多次点击 Dismiss | 第二次 `alarmMediaPlayer` 为 null，no-op |
| 非超时状态收到 `ACTION_ALARM_DISMISS` | stop null MediaPlayer + cancel 不存在的通知，无副作用 |
| Dismiss 后再 Stop | `stopAndReleaseAlarmSound()` 已 null-safe |
| 闹铃自然播完后点 Dismiss | `OnCompletionListener` 已 release → `stopAndReleaseAlarmSound()` catch ISE → 设 null → 安全 |
| 点击通知体（TimeclockDialog） | 通知不消失（`autoCancel=false`），Dismiss 按钮保留 |
| 滑动删除通知 | `deleteIntent` 触发 Dismiss，闹铃停止 |

## 测试要点

- 番茄钟超时后点击「关闭闹铃」→ 闹铃停止，番茄钟继续显示超时
- 番茄钟超时后滑动删除超时通知 → 闹铃停止
- 番茄钟超时后点击通知体（打开 TimeclockDialog）→ 通知不消失，Dismiss 按钮仍在
- 番茄钟超时后点击「关闭闹铃」→ 再点击前台 Stop → 番茄钟完全停止
- 番茄钟超时后直接点击 Stop（不点 Dismiss）→ 闹铃停止 + 番茄钟停止
- 番茄钟超时后不操作 → 闹铃自然播完 → 再点 Dismiss → 不崩溃
- 番茄钟超时后不操作 → 闹铃自然播完 → 番茄钟继续运行
