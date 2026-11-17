# ADR 0002: Use MediaPlayer with Alarm Stream for Pomodoro Timeout Alert

## Status

Accepted

## Context

The pomodoro timer completion alert uses a notification channel (`IMPORTANCE_HIGH`) to play a sound. When the device is in silent or vibrate-only mode, the notification audio stream is muted and the alert makes no sound. Users expect timer completion alerts to be audible even in silent mode, like a kitchen timer or alarm clock.

### Alternatives considered

1. **Notification channel sound only** (status quo) — Silent in silent mode. Unacceptable.
2. **`NotificationChannel.setBypassDnd(true)`** — Only works for system-signed apps. Non-system apps cannot set this flag; the system resets it.
3. **`AudioManager.setRingerMode()` to temporarily un-mute** — Invasive, unreliable across OEM ROMs, disrupts other audio.
4. **MediaPlayer + `USAGE_ALARM`** (chosen) — Plays on the alarm audio stream, which is independent of the notification/ringer stream. Not silenced in normal silent mode or "Alarms only" DND mode.

### Key constraint

Android's alarm audio stream (`STREAM_ALARM`) is the only audio stream reliably audible in silent mode for non-system apps. Total silence DND mode suppresses everything, including alarms — this is an Android hard limit.

## Decision

Play the system default alarm sound (`Settings.System.DEFAULT_ALARM_ALERT_URI`) via `MediaPlayer` configured with `AudioAttributes.USAGE_ALARM`. The notification channel is set to `setSound(null, null)` — it provides visual UI and vibration only.

A new notification channel ID (`mobileorg_timeclock_alarm`) is used instead of modifying the existing channel, because Android does not allow changing sound settings on already-created channels.

## Consequences

- Pomodoro timeout is audible in silent/vibrate mode.
- Sound is controlled by the system "alarm volume" slider (not notification volume).
- Total silence DND mode still suppresses the alert (Android hard limit, no workaround).
- Old notification channel (`mobileorg_timeclock_timeout`) becomes unused; users see it as inactive in system settings.
- `MediaPlayer` lifecycle must be managed within `TimeclockService` (field + explicit release in cleanup paths).
