# Consecutive Pomodoro Mode Design

Date: 2026-06-10

## Problem

Current pomodoro timer runs one session at a time. After timeout, the user must manually stop and restart to begin another. No rest breaks, no chaining, no progress tracking across multiple pomodoros.

## Decision

Add a **consecutive mode** that chains N pomodoros with rest breaks between them. After each pomodoro timeout, the alarm sounds and the user must tap "Finish" to record the session and proceed. Between pomodoros, a rest countdown runs. When rest ends, a notification prompts the user to confirm starting the next one. After all N pomodoros complete, the service stops.

## State Machine

```
IDLE ──(start N)──→ WORK ──(timeout, round<N)──→ WORK(timedOut) ──(Finish)──→ REST ──(rest ends)──→ WAITING_NEXT ──(confirm)──→ WORK
  ↑                    │                              │                                              │
  │  (timeout, round=N)│  (Cancel)                    │(Cancel)                                      │(Cancel)
  │                    ↓                              ↓                                              ↓
  └────────────────────┴──────────────────────────────┴──────────────────────────────────────────────┘
```

States:
- **IDLE** — No timer running.
- **WORK** — Pomodoro countdown. Includes the `timedOut` sub-state (alarm sounding, waiting for user to Finish).
- **REST** — Rest countdown. Notification shows rest remaining and completed count.
- **WAITING_NEXT** — Rest ended, waiting for user to confirm "start next".

### Key state transitions

| From | Trigger | To | Notes |
|------|---------|----|-------|
| IDLE | start(N) | WORK | round=1, totalCount=N |
| WORK | Cancel | IDLE | Discard current pomodoro, keep completed sessions in DB |
| WORK | timeout (round<N) | WORK(timedOut) | Record session, sound alarm, wait for Finish |
| WORK | timeout (round=N) | WORK(timedOut) | Record session, sound alarm, wait for Finish |
| WORK(timedOut) | Finish (round<N) | REST | Stop alarm, start rest countdown |
| WORK(timedOut) | Finish (round=N) | IDLE | Stop alarm, show "all done" notification |
| REST | rest ends | WAITING_NEXT | Send confirmation notification (sound+vibrate) |
| REST | Skip Rest | WORK | Immediately start next round |
| REST | Cancel | IDLE | Terminate consecutive mode, keep completed sessions |
| WAITING_NEXT | confirm | WORK | Start next round |
| WAITING_NEXT | Cancel | IDLE | Terminate consecutive mode, keep completed sessions |

### User actions always stop alarm

Finish, Cancel, Skip Rest, Start Next — any user action implies stopping the alarm sound. The alarm is a notification mechanism only.

`count=1` is a special case: WORK → timeout → round==total → Finish → end. No rest, no confirmation. Behaves identically to current single-pomodoro mode (user taps Stop to dismiss after timeout, but now the button says "Finish").

## Startup Flow

Existing duration picker becomes a single AlertDialog with two NumberPickers:
1. Duration (minutes, 1-120, default from `pomodoro_duration` preference)
2. Count (1-99, default from node `POMODORO_COUNT` > `pomodoro_count_default` preference > 1)

### Default count priority

1. If started from a node context (long-press menu), read the node's `POMODORO_COUNT` property.
2. Otherwise, use `pomodoro_count_default` preference.
3. If neither is set, default to 1.

Node association ends after reading `POMODORO_COUNT`. The pomodoro does not display the node name or write CLOCK entries.

## Alarm and Finish Behavior

### Every pomodoro timeout sounds the alarm

Regardless of consecutive or single mode, every timeout triggers the full alarm (`MediaPlayer + USAGE_ALARM`, same as current behavior). The user must interact to proceed.

### WORK(timedOut) — waiting for Finish

After timeout, the pomodoro stays in WORK state with `timedOut=true`. Two notifications coexist:
1. **Foreground notification** (ongoing): shows `🍅 2/4 完成` + "Finish" button
2. **Timeout alert notification** (high priority): alarm alert + "关闭闹铃" button + deleteIntent

User can:
- Tap "关闭闹铃" → stop alarm sound, foreground notification remains with Finish button
- Swipe timeout notification → also stops alarm (via deleteIntent)
- Tap "Finish" → stop alarm + record + proceed (to REST or all-done)

The service waits indefinitely for user action. No auto-timeout.

### Cancel is unavailable during WORK(timedOut)

The outline menu "Stop 🍅" is not shown during timedOut. User must use notification or TimeclockDialog to Finish.

## Rest Behavior

### Rest duration calculation

- If `currentRound % longBreakInterval == 0` AND `currentRound < totalCount`: long rest.
- Otherwise: short rest.
- After the last pomodoro (`round == totalCount`): no rest, just end (via Finish).

### Rest duration = 0

Allowed. When rest duration is 0, skip the REST countdown phase but still enter WAITING_NEXT — user must confirm "start next" via notification.

### Rest end notification

- Sound: default notification sound + vibration.
- In silent mode: vibration only (notification stream is muted, only alarm stream is audible).
- NOT the full alarm sound used for pomodoro timeout.

## Notification Content

### Foreground notification (ongoing)

| State | Title | Body | Action Buttons |
|-------|-------|------|----------------|
| WORK (counting down) | `🍅 2/4` | `23:45` remaining | Cancel |
| WORK (timedOut) | `🍅 2/4 完成` | overtime `+0:30` | Finish |
| REST | `☕ 休息中` | `3:21 \| 2/4 完成` | 跳过休息, Cancel |
| WAITING_NEXT | `▶ 准备下一个` | `2/4 完成` | 开始下一个, Cancel |
| ALL DONE | `🎉 4/4 完成！` | `总计 100 分钟` | (autoCancel, no button) |

### Timeout alert notification (separate, high priority)

Only shown during WORK(timedOut):

| Title | Body | Buttons |
|-------|------|---------|
| `🍅 番茄钟时间到！` | `25 分钟番茄钟已完成` | 关闭闹铃 (also deleteIntent for swipe) |

Single foreground notification. Content and buttons change based on state.

## Outline Menu

Uses `isPomodoroActive()` (state != IDLE) to decide display:
- Active + not timedOut: show "Stop 🍅" → sends Cancel
- Active + timedOut: show "Pomodoro" (disabled/ignored, Toast "番茄钟正在等待确认")
- Not active: show "Pomodoro" → open duration picker

## TimeclockDialog

Shows content consistent with notification in all states:
- WORK (counting down): progress `🍅 2/4 | 23:45` + Cancel button
- WORK (timedOut): progress `🍅 2/4 完成` + Finish button
- REST: rest countdown + Skip Rest button + Cancel button
- WAITING_NEXT: "准备下一个" + Start Next button + Cancel button

## Concurrent Start Prevention

If user taps "Start Pomodoro" while consecutive mode is active (`isPomodoroActive()`): show Toast "番茄钟正在进行中", ignore.

## Cancel Behavior (replaces "Mid-Stop")

Cancel is available during WORK (countdown), REST, and WAITING_NEXT. Not available during WORK(timedOut).

When user Cancels:
- All already-completed pomodoro sessions remain in `pomodoro_sessions` table.
- The current incomplete pomodoro is discarded (not recorded).
- Service returns to IDLE.

## Device Reboot

State is lost on reboot. No persistence of consecutive mode progress. Same behavior as current single-pomodoro mode.

## Data Model Changes

### PomodoroTimer

New fields:
- `totalCount` (int) — total pomodoros in this run
- `currentRound` (int) — 1-based current round
- `PomodoroState state` — enum: IDLE, WORK, REST, WAITING_NEXT
- `restStartTime` (long) — when rest started
- `restDurationMinutes` (int) — current rest duration

`timedOut` remains a boolean flag within WORK state (not a separate state).

New methods:
- `start(int durationMinutes, int totalCount)` — start with count (replaces single-param start)
- `startRest(int durationMinutes)` — switch to REST state
- `setWaitingNext()` — switch to WAITING_NEXT state
- `advanceToNextWork(int durationMinutes)` — increment round, switch to WORK
- `isResting()`, `isWaitingNext()`, `isActive()` — state queries (`isActive()` = state != IDLE)
- `getRoundProgress()` — returns "2/4" string
- `getRestRemainingString()` — returns "3:21" or "" if not resting
- `getRestRemainingMillis()` — for scheduling rest timeout
- `getTotalCount()`, `getCurrentRound()`

### TimeclockService

New actions:
- `ACTION_POMODORO_FINISH` — user confirmed Finish during WORK(timedOut): record + proceed to REST or end
- `ACTION_POMODORO_NEXT` — user confirmed "start next" during WAITING_NEXT
- `ACTION_POMODORO_SKIP_REST` — user skipped rest during REST

Modified actions:
- `ACTION_POMODORO_STOP` → now semantically "Cancel": discard current, terminate consecutive mode, keep completed sessions

New handler:
- `restTimeoutRunnable` — fires when rest countdown ends, transitions to WAITING_NEXT, sends confirmation notification.

### Intent extras

- `POMODORO_COUNT` (int) — total count for consecutive mode (added to ACTION_POMODORO_START intent)

## New Preferences

All in existing Pomodoro preference screen:

| Key | Type | Default | Label |
|-----|------|---------|-------|
| `pomodoro_short_break` | ListPreference | 5 | 短休息时长（分钟） |
| `pomodoro_long_break` | ListPreference | 15 | 长休息时长（分钟） |
| `pomodoro_long_break_interval` | ListPreference | 4 | 长休息间隔（每N个番茄） |
| `pomodoro_count_default` | ListPreference | 1 | 默认连续番茄数 |

`pomodoro_short_break` allows value 0 (no rest countdown, still requires confirmation).

## Database

`pomodoro_sessions` table unchanged. Each completed pomodoro is still an independent record. Consecutive mode chains multiple single pomodoros — no new tables needed.

## PomodoroStatisticsRepository

No changes. Statistics already count individual sessions. Consecutive mode produces multiple sessions that are counted naturally.

## Files to Modify

1. `PomodoroTimer.java` — state machine, new fields/methods
2. `TimeclockService.java` — new actions (Finish/Next/SkipRest), rest handler, notification changes, Cancel semantics
3. `TimeclockDialog.java` — progress display, Finish/Cancel/SkipRest UI per state
4. `OutlineTimeclockController.java` — dual NumberPicker dialog, concurrent start prevention
5. `OutlineActivity.java` — isPomodoroActive() for menu, disabled during timedOut
6. `OutlineActionMode.java` — pass POMODORO_COUNT from node property
7. `PreferenceUtils.java` — new preference getters
8. `preferences.xml` — new preference entries
9. `arrays.xml` — new entry/value arrays for preferences
10. `strings.xml` — new string resources
