# Pomodoro Alarm Sound 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 番茄钟超时时用 MediaPlayer + USAGE_ALARM 播放系统闹铃声，使静音模式下仍可听到提醒。

**架构：** 在 `Compat.java` 新增 `playAlarmSound()` 静态方法，创建 MediaPlayer 并设置闹钟音频流。`TimeclockService` 持有 MediaPlayer 字段，在超时时播放，在停止/取消/销毁时释放。通知渠道迁移到新 ID `mobileorg_timeclock_alarm`（静音，仅负责 UI + 振动）。

**技术栈：** Android MediaPlayer, AudioAttributes, Settings.System.DEFAULT_ALARM_ALERT_URI

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java` | 新增 `playAlarmSound(Context)` 静态方法，含 API 17-20 分支 |
| `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java` | MediaPlayer 字段、`stopAndReleaseAlarmSound()` 辅助方法、渠道 ID 迁移、集成到超时/停止/取消流程 |

---

### 任务 1：在 Compat.java 中添加 playAlarmSound 方法

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java`
**导出/变更接口：** `Compat.java::playAlarmSound`
**消费接口：** `android.media.MediaPlayer`, `android.media.AudioAttributes`, `android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java`（末尾新增方法 + import）

- [ ] **步骤 1：添加 import 语句**

在 `Compat.java` 文件顶部的 import 区域添加：
```java
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.Settings;
```

- [ ] **步骤 2：添加 SDK_LOLLIPOP 常量和 playAlarmSound 方法**

在类末尾 `}` 之前添加：
```java
public static final int SDK_LOLLIPOP = 21;

/**
 * Play the system default alarm sound on the alarm audio stream.
 * Returns the MediaPlayer instance for lifecycle management by the caller,
 * or null if the alarm URI is unavailable or playback fails.
 * OnCompletionListener and OnErrorListener auto-release the MediaPlayer.
 */
public static MediaPlayer playAlarmSound(Context context) {
    Uri alarmSound = Settings.System.DEFAULT_ALARM_ALERT_URI;
    if (alarmSound == null) {
        Log.w("MobileOrg", "[AlarmSound] No default alarm sound URI, skipping");
        return null;
    }
    try {
        MediaPlayer mp = new MediaPlayer();
        if (Build.VERSION.SDK_INT >= SDK_LOLLIPOP) {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } else {
            mp.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        mp.setDataSource(context, alarmSound);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                mp.release();
                return true;
            }
        });
        mp.prepare();
        mp.start();
        return mp;
    } catch (Exception e) {
        Log.e("MobileOrg", "[AlarmSound] Failed to play alarm sound", e);
        return null;
    }
}
```

注意：需要添加 `import android.util.Log;`（检查文件是否已有此 import，如果已有则跳过）。

- [ ] **步骤 3：验证编译**

运行：`./gradlew compileDebugJavaWithJavac`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java
git commit -m "feat: add Compat.playAlarmSound() with USAGE_ALARM stream"
```

---

### 任务 2：在 TimeclockService 中集成闹钟声音播放

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`
**导出/变更接口：** `TimeclockService.java::stopAndReleaseAlarmSound`, `TimeclockService.java::TIMEOUT_CHANNEL_ID`（值变更）
**消费接口：** `Compat.java::playAlarmSound`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`

- [ ] **步骤 1：添加 import 和 MediaPlayer 字段**

在文件顶部 import 区域添加：
```java
import android.media.MediaPlayer;
```

在 `private Notification notification;`（第 49 行附近）之后添加字段：
```java
private MediaPlayer alarmMediaPlayer;
```

- [ ] **步骤 2：迁移渠道 ID**

将第 43 行的渠道 ID 从：
```java
private static final String TIMEOUT_CHANNEL_ID = "mobileorg_timeclock_timeout";
```
改为：
```java
private static final String TIMEOUT_CHANNEL_ID = "mobileorg_timeclock_alarm";
```

- [ ] **步骤 3：修改渠道创建，设置静音 + CATEGORY_ALARM**

在 `handlePomodoroTimeout()` 中，将 `Compat.createNotificationChannelHigh(...)` 调用（第 208-209 行）替换为内联渠道创建（因为需要 `setSound(null, null)` 和 `setCategory()`，通用方法不支持）：

```java
// Create HIGH importance channel with no sound (MediaPlayer handles audio via alarm stream)
if (Compat.isAtLeastO()) {
    NotificationChannel timeoutChannel = new NotificationChannel(
            TIMEOUT_CHANNEL_ID, "Pomodoro Timer Alert", NotificationManager.IMPORTANCE_HIGH);
    timeoutChannel.setDescription("Alerts when pomodoro timer completes");
    timeoutChannel.setSound(null, null);
    timeoutChannel.enableVibration(true);
    timeoutChannel.setCategory(Notification.CATEGORY_ALARM);
    mNM.createNotificationChannel(timeoutChannel);
}
```

需要确认 `android.app.NotificationChannel` 和 `android.app.Notification` 已在 import 中（`NotificationChannel` 可能未 import——检查并添加）。

- [ ] **步骤 4：在 handlePomodoroTimeout() 末尾添加闹钟声音播放**

在 `updateTime();`（第 228 行）之后、方法结束之前添加：
```java
// Play alarm sound via alarm audio stream (audible in silent mode)
stopAndReleaseAlarmSound();
alarmMediaPlayer = Compat.playAlarmSound(this);
```

- [ ] **步骤 5：在 handlePomodoroStop() 中添加停止闹铃声**

在 `handlePomodoroStop()` 的开头（`Log.d(...)` 之前）添加：
```java
stopAndReleaseAlarmSound();
```

- [ ] **步骤 6：在 cancelNotification() 中添加停止闹铃声**

在 `cancelNotification()` 的 `notifyStateChanged()` 之后、`unsetPomodoroAlarms()` 之前添加：
```java
stopAndReleaseAlarmSound();
```

- [ ] **步骤 7：添加 stopAndReleaseAlarmSound() 辅助方法**

在 `cancelNotification()` 方法之后添加：
```java
private void stopAndReleaseAlarmSound() {
    if (alarmMediaPlayer != null) {
        try {
            if (alarmMediaPlayer.isPlaying()) alarmMediaPlayer.stop();
        } catch (IllegalStateException e) {
            // already released or in error state
        }
        alarmMediaPlayer.release();
        alarmMediaPlayer = null;
    }
}
```

- [ ] **步骤 8：验证编译**

运行：`./gradlew compileDebugJavaWithJavac`
预期：BUILD SUCCESSFUL

- [ ] **步骤 9：推送并等待 CI 通过**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java
git commit -m "feat: play alarm sound via USAGE_ALARM on pomodoro timeout

- Add MediaPlayer field with stopAndReleaseAlarmSound() helper
- Migrate notification channel to mobileorg_timeclock_alarm (silent, vibration only)
- Play system alarm sound on timeout, stop on user action or service shutdown"
git push
gh run list --limit 1
```

等待 CI 通过。如果 CI 失败，检查编译错误并修复。

---

### 任务 3：更新 CLAUDE.md 记录此坑

**依赖：** 任务 2
**文件集：** `CLAUDE.md`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`CLAUDE.md`

- [ ] **步骤 1：在 "Android Compatibility (Applied Fixes)" 末尾添加条目**

```markdown
- **Pomodoro alarm sound must use USAGE_ALARM stream**: Notification channel sound is controlled by the notification audio stream and silenced in silent mode. For timer alerts that must be audible in silent mode, use `MediaPlayer` with `AudioAttributes.USAGE_ALARM` to play on the alarm audio stream. The notification channel should be `setSound(null, null)` (silent, vibration only). Use a new channel ID when migrating (old channels can't have their sound changed after creation). `MediaPlayer` lifecycle must be managed as a Service field with explicit release in all stop/cancel/destroy paths.
```

- [ ] **步骤 2：Commit**

```bash
git add CLAUDE.md
git commit -m "docs: record pomodoro USAGE_ALARM stream pattern in CLAUDE.md"
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 3

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2
- Wave 3（依赖 Wave 2）：任务 3
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
