# Implementation Plan: Consecutive Pomodoro Mode

Spec: `docs/superpowers/specs/2026-06-10-pomodoro-consecutive-design.md`

## Step 1: PomodoroTimer state machine

**Goal**: Upgrade PomodoroTimer from single-shot to state machine supporting consecutive mode.

**Files**: `MobileOrg/src/main/java/com/matburt/mobileorg/Services/PomodoroTimer.java`

**Changes**:
1. Add `PomodoroState` enum: `IDLE, WORK, REST, WAITING_NEXT`
2. Add fields: `totalCount`, `currentRound`, `state` (PomodoroState), `restStartTime`, `restDurationMinutes`
3. Modify `start(int durationMinutes)` → `start(int durationMinutes, int totalCount)`. When `totalCount <= 0`, treat as 1. Set `state = WORK`, `currentRound = 1`.
4. Keep `timedOut` as boolean flag within WORK state (not a separate state).
5. Add `startRest(int durationMinutes)`: set `state = REST`, `restStartTime = now`, `restDurationMinutes = durationMinutes`.
6. Add `setWaitingNext()`: set `state = WAITING_NEXT`.
7. Add `advanceToNextWork(int durationMinutes)`: increment `currentRound`, set `state = WORK`, reset startTime and duration.
8. Modify `stop()`: reset all state to IDLE (including totalCount, currentRound, state).
9. Keep `markTimeout()`: set `timedOut = true` (still within WORK state).
10. Add `isActive()`: return `state != PomodoroState.IDLE`.
11. Add `isResting()`: return `state == REST`.
12. Add `isWaitingNext()`: return `state == WAITING_NEXT`.
13. Add `getRoundProgress()`: return "2/4" string.
14. Add `getRestRemainingString()`: return "3:21" format for rest countdown, or "" if not resting.
15. Add `getRestRemainingMillis()`: for scheduling rest timeout.
16. Add `getTotalCount()`, `getCurrentRound()`.
17. Ensure `getRemainingString()` only returns meaningful values when `state == WORK`.

**Test**: `MobileOrg/src/test/java/com/matburt/mobileorg/Services/PomodoroTimerTest.java` — plain JUnit.

**Test cases**:
- `start(25, 1)` → state=WORK, totalCount=1, currentRound=1
- `start(25, 4)` → state=WORK, totalCount=4, currentRound=1
- `markTimeout()` → timedOut=true, state still WORK
- `startRest(5)` → state=REST, getRestRemainingString() returns valid countdown
- `startRest(0)` → state=REST, getRestRemainingMillis() returns 0
- `setWaitingNext()` → state=WAITING_NEXT
- `advanceToNextWork(25)` → currentRound increments, state=WORK, timedOut=false
- `stop()` from any state → state=IDLE, all fields reset
- `getRoundProgress()` returns "2/4"
- `getRemainingString()` returns "" when state=REST or WAITING_NEXT
- `isActive()` true for WORK/REST/WAITING_NEXT, false for IDLE

## Step 2: New preference keys and getters

**Goal**: Add preference definitions for consecutive mode settings.

**Files**:
- `MobileOrg/src/main/java/com/matburt/mobileorg/util/PreferenceUtils.java`
- `MobileOrg/src/main/res/xml/preferences.xml`
- `MobileOrg/src/main/res/values/strings.xml`
- `MobileOrg/src/main/res/values/arrays.xml`

**Changes**:
1. Add to `PreferenceUtils`:
   - `getPomodoroShortBreak()`: int, default 5, from `pomodoro_short_break`
   - `getPomodoroLongBreak()`: int, default 15, from `pomodoro_long_break`
   - `getPomodoroLongBreakInterval()`: int, default 4, from `pomodoro_long_break_interval`
   - `getPomodoroCountDefault()`: int, default 1, from `pomodoro_count_default`
2. Add 4 new `ListPreference` entries in `preferences.xml` under the existing Pomodoro `PreferenceScreen`.
3. Add entry/value arrays in `arrays.xml` (0-30 for break durations, 1-12 for interval, 1-10 for count).
4. Add string resources in `strings.xml`.

**Verification**: Build only. No runtime test needed for preferences.

## Step 3: Read POMODORO_COUNT from node

**Goal**: When starting pomodoro from a node context, read the node's POMODORO_COUNT property as default count.

**Files**:
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java`
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTimeclockController.java`

**Changes**:
1. `OutlineActionMode`: before showing picker, read `node`'s POMODORO_COUNT property via `node.getPayload().getProperty("POMODORO_COUNT")`. If non-empty, parse as int; otherwise use `PreferenceUtils.getPomodoroCountDefault()`. Pass as default count to picker.
2. `OutlineTimeclockController.showPomodoroDurationPicker()`: change signature to accept `int defaultCount`. Show dual NumberPicker (duration + count).
3. Toolbar menu call (no node): pass `PreferenceUtils.getPomodoroCountDefault()` as default count.

**Key**: The Intent `ACTION_POMODORO_START` now carries extra `POMODORO_COUNT` (int).

## Step 4: Dual NumberPicker dialog

**Goal**: Replace single duration picker with dual (duration + count) picker.

**Files**: `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTimeclockController.java`

**Changes**:
1. `showPomodoroDurationPicker()` accepts `int defaultCount` parameter.
2. Build AlertDialog with two NumberPickers side by side:
   - Left: duration (1-120 min, default from `getPomodoroDuration()`)
   - Right: count (1-99, default from `defaultCount`)
3. On OK: send `ACTION_POMODORO_START` with both `POMODORO_DURATION` and new `POMODORO_COUNT` extras.
4. Update `OutlineActionMode` to pass node's POMODORO_COUNT (or preference default) to this method.

## Step 5: TimeclockService — consecutive mode logic

**Goal**: Wire up the state machine in the service.

**Files**: `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`

**Changes**:

### 5a. New constants and state
1. Add `ACTION_POMODORO_FINISH = "pomodoro_finish"` — user taps Finish during WORK(timedOut)
2. Add `ACTION_POMODORO_NEXT = "pomodoro_next"` — user confirms "start next" during WAITING_NEXT
3. Add `ACTION_POMODORO_SKIP_REST = "pomodoro_skip_rest"` — user skips rest during REST
4. Add `POMODORO_COUNT = "pomodoro_count"` extra key
5. Add `restTimeoutRunnable` field

### 5b. Modify handlePomodoroStart
1. Read `POMODORO_COUNT` from intent (default 1).
2. Call `pomodoroTimer.start(duration, count)` with both params.
3. Rest of existing logic unchanged (schedule updateRunnable and timeoutRunnable).

### 5c. Modify handlePomodoroTimeout
1. After `pomodoroTimer.markTimeout()` and `repo.recordPomodoroSession()`, do NOT transition to REST.
2. Keep pomodoro in WORK(timedOut) state — alarm sounds, user must Finish.
3. Show timeout alert notification (existing behavior, with "关闭闹铃" button).
4. Update foreground notification to show "🍅 2/4 完成" + "Finish" button.
5. Play alarm sound (existing behavior).

### 5d. New handlePomodoroFinish
1. Stop alarm sound.
2. Cancel timeout alert notification.
3. If `currentRound == totalCount` (last round):
   - Show "all done" notification (`🎉 N/N 完成！`)
   - Stop foreground service
4. Else:
   - Calculate rest duration (short/long based on round).
   - If rest duration > 0: call `pomodoroTimer.startRest(restDuration)`, schedule `restTimeoutRunnable`.
   - If rest duration == 0: call `pomodoroTimer.setWaitingNext()`, send confirmation notification immediately.
   - Update foreground notification.

### 5e. New handleRestTimeout
1. `pomodoroTimer.setWaitingNext()`
2. Send confirmation notification with "开始下一个" button.
3. Play notification sound + vibration (NOT alarm sound).
4. Update foreground notification.

### 5f. New handlePomodoroNext
1. Stop any alarm/notification sound.
2. `pomodoroTimer.advanceToNextWork(durationMinutes)` (use same duration as before).
3. Schedule new `timeoutRunnable` for this work period.
4. Ensure `updateRunnable` is running.
5. Update notification to WORK state.
6. Cancel confirmation notification if any.

### 5g. New handlePomodoroSkipRest
1. Cancel `restTimeoutRunnable`.
2. Call `handlePomodoroNext()` directly (skip rest, start next immediately).

### 5h. Modify handlePomodoroStop → Cancel semantics
1. Stop alarm sound (if playing).
2. Cancel all timers (updateRunnable, timeoutRunnable, restTimeoutRunnable).
3. Cancel all notifications (foreground, timeout alert, rest confirmation).
4. `pomodoroTimer.stop()` → reset to IDLE.
5. `notifyStateChanged()`.
6. `checkStopSelf()` → stops service since nothing else is running.
7. Completed sessions already recorded in DB — no cleanup needed.

### 5i. Modify onStartCommand switch
1. Add case `ACTION_POMODORO_FINISH` → `handlePomodoroFinish()`
2. Add case `ACTION_POMODORO_NEXT` → `handlePomodoroNext()`
3. Add case `ACTION_POMODORO_SKIP_REST` → `handlePomodoroSkipRest()`

### 5j. Modify showOrRefreshNotification
1. Check `pomodoroTimer.getState()` and `pomodoroTimer.isTimedOut()` for all states.
2. Build appropriate title, content, and action buttons per the notification table in spec:
   - WORK (counting down): `🍅 2/4 | 23:45`, Cancel button
   - WORK (timedOut): `🍅 2/4 完成`, Finish button
   - REST: `☕ 休息中`, `3:21 | 2/4 完成`, 跳过休息 + Cancel buttons
   - WAITING_NEXT: `▶ 准备下一个`, `2/4 完成`, 开始下一个 + Cancel buttons

### 5k. Modify updateTime
1. WORK (timedOut): show overtime `+X:XX` in red (existing behavior).
2. REST: show rest countdown in big text area.
3. WAITING_NEXT: show static "准备下一个" text.
4. Include progress (round/total) in title for all states.

### 5l. Modify checkStopSelf
1. Also check `pomodoroTimer.isResting()` and `pomodoroTimer.isWaitingNext()` — don't stop if active.
2. Also check `pomodoroTimer.isTimedOut()` — don't stop if waiting for Finish.

### 5m. New public methods
1. `isPomodoroActive()`: return `pomodoroTimer.isActive()` — used by OutlineActivity for menu display.

### 5n. Cancel notification cleanup
1. `cancelNotification()` must also cancel `restTimeoutRunnable` and any rest/confirmation notifications.

## Step 6: TimeclockDialog — all states UI

**Goal**: Show consistent UI in the dialog for all consecutive mode states.

**Files**: `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockDialog.java`

**Changes**:
1. In `onStart()`, check `pomodoroTimer` state for all cases:
2. WORK (counting down): show progress `🍅 2/4 | 23:45` + Cancel button.
3. WORK (timedOut): show progress `🍅 2/4 完成` + Finish button.
4. REST: show rest countdown + Skip Rest button + Cancel button.
5. WAITING_NEXT: show "准备下一个" + Start Next button + Cancel button.
6. Wire buttons to send appropriate actions:
   - Cancel → `ACTION_POMODORO_STOP`
   - Finish → `ACTION_POMODORO_FINISH`
   - Skip Rest → `ACTION_POMODORO_SKIP_REST`
   - Start Next → `ACTION_POMODORO_NEXT`

## Step 7: OutlineActivity menu changes

**Goal**: Update menu to use isPomodoroActive() and handle timedOut state.

**Files**: `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`

**Changes**:
1. In `onPrepareOptionsMenu()`: use `service.isPomodoroActive()` instead of `service.isPomodoroRunning()`.
2. Show "Stop 🍅" when active AND not timedOut.
3. When timedOut: show "Pomodoro" (disabled, or show Toast "番茄钟正在等待确认" if tapped).
4. In `onOptionsItemSelected()`: only send `ACTION_POMODORO_STOP` if not timedOut.

## Step 8: Concurrent start prevention

**Goal**: Prevent starting a new pomodoro while consecutive mode is active.

**Files**: `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTimeclockController.java`

**Changes**:
1. In `showPomodoroDurationPicker()`, check `TimeclockService.getInstance()` — if service exists and `isPomodoroActive()`, show Toast and return.

## Step 9: All-done notification

**Goal**: Show completion summary when all pomodoros finish.

**Files**: `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`

**Changes**:
1. When last round finishes (in `handlePomodoroFinish()`):
   - Build a non-ongoing notification:
     - Title: `🎉 N/N 完成！`
     - Content: `总计 X 分钟`
     - AutoCancel, no action buttons
   - Use `TIMEOUT_CHANNEL_ID` (IMPORTANCE_HIGH) so it's visible.
   - Cancel the foreground service after posting.

## Step 10: Push and verify CI

**Goal**: Commit all changes, push, verify CI passes.

1. Stage and commit all modified/new files with descriptive message.
2. Push to remote.
3. `gh run list` / `gh run view` to check CI.
4. Fix any CI failures.
