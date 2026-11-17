# Pomodoro Timeout Alarm Sound in Silent Mode

## Problem

When the device is in silent mode (ringer muted or vibrate-only), the pomodoro timer completion notification makes no sound. The current implementation relies on the notification channel's default sound, which is controlled by the notification audio stream — silenced when the ringer is muted.

## Decision

Use `MediaPlayer` with `AudioAttributes.USAGE_ALARM` to play the system default alarm sound on the alarm audio stream. The alarm stream is independent of the notification stream and is not silenced in normal silent mode or "Alarms only" DND mode.

## Design

### Sound playback: `Compat.playAlarmSound(Context)`

A new static method in `Compat.java` that creates and starts a `MediaPlayer`:

- Audio routing: `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION` (API 21+), fallback `setAudioStreamType(STREAM_ALARM)` (API 17-20)
- Sound source: `Settings.System.DEFAULT_ALARM_ALERT_URI`
- Null guard: returns immediately if URI is null (some OEM ROMs)
- Lifecycle: `OnCompletionListener` and `OnErrorListener` both call `release()`
- Synchronous `prepare()` + `start()` on the calling thread (acceptable for local alarm files, <10ms)

Returns the `MediaPlayer` instance so the caller can stop it early if needed.

### Notification channel: `mobileorg_timeclock_alarm`

New channel ID replacing `mobileorg_timeclock_timeout`. Created with:
- `IMPORTANCE_HIGH` (heads-up notification, vibration)
- `setSound(null, null)` (no notification sound — MediaPlayer handles audio)
- `setCategory(Notification.CATEGORY_ALARM)` (semantics for DND policy)

The old channel `mobileorg_timeclock_timeout` is abandoned in place. It appears as "unused" in system notification settings. This avoids the Android limitation where existing channel sound settings cannot be modified programmatically.

### `TimeclockService` changes

**New field**: `private MediaPlayer alarmMediaPlayer;`

**`handlePomodoroTimeout()`** — after posting the notification:
1. Call `stopAndReleaseAlarmSound()` to release any previous instance
2. Call `Compat.playAlarmSound(this)` and store returned `MediaPlayer` in field

**`handlePomodoroStop()`** — before cancelling the notification:
1. Call `stopAndReleaseAlarmSound()` to stop sound immediately when user taps Stop (covers case where pomodoro stops but clock-in continues)

**`cancelNotification()`** — add `stopAndReleaseAlarmSound()` before `mNM.cancel()` calls. This is the shared cleanup path called from `onDestroy()` and other stop/cancel flows, ensuring alarm sound stops regardless of how the service is shut down.

**`stopAndReleaseAlarmSound()`** — new private helper:
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

## Files changed

| File | Change |
|------|--------|
| `MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java` | Add `playAlarmSound(Context)` method |
| `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java` | Add `alarmMediaPlayer` field, `stopAndReleaseAlarmSound()`, update `handlePomodoroTimeout()`, `handlePomodoroStop()`, `onDestroy()` |

No new permissions required. No XML or resource changes.

## DND mode behavior

| Device state | MediaPlayer alarm stream | Notification vibration |
|-------------|------------------------|----------------------|
| Normal | Plays | Yes |
| Silent / Vibrate | Plays | Yes |
| DND — Alarms only | Plays | No (DND suppresses) |
| DND — Priority only | Plays (alarm stream unaffected) | Depends on priority settings |
| DND — Total silence | No (Android hard limit) | No |

## Out of scope

- User-selectable alarm sound (YAGNI)
- Full-screen intent / wake screen
- `ACCESS_NOTIFICATION_POLICY` permission or `setBypassDnd(true)`
- Applying alarm sound to ReminderReceiver or DailyOverviewReceiver
