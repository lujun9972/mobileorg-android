# Quick Recording Feature Design

## Goal

Add a quick recording feature to MobileOrg: select a node, start recording audio. Recording start = clock-in, recording stop = clock-out. The recording file link is appended to the node body as `[[file:xxx.aac]]`.

## Architecture

New `RecordingService` (foreground Service) manages MediaRecorder lifecycle and clock-in/clock-out independently. No dependency on existing `TimeclockService`. OutlineActivity receives broadcast updates to show an inline recording status bar at the top of the list.

## Components

### 1. RecordingService

Foreground Service, singleton pattern. Manages the full recording lifecycle.

**States**: `IDLE` → `RECORDING` ⇄ `PAUSED` → `IDLE`

**Intent Actions**:
- `ACTION_START` — Extra: `NODE_ID`. Starts recording + clock-in (records `startTime = System.currentTimeMillis()`).
- `ACTION_PAUSE` — Pauses MediaRecorder (`MediaRecorder.pause()`, API 24+). Records `pauseStartTime` to track paused duration.
- `ACTION_RESUME` — Resumes MediaRecorder. Adjusts total paused duration.
- `ACTION_STOP` — Stops recording + clock-out + writes to node.
- `ACTION_UPDATE` — Periodic alarm to refresh notification elapsed time.

**Clock-in/Clock-out**:
- START: Store `startTime`.
- STOP: Calculate `elapsedTime` (total minus paused duration), call `OrgNode.addLogbook(startTime, endTime, elapsedTime, resolver)`, then append `[[file:xxx.aac]]` to node body via `OrgNodePayload`.

**Recording File**:
- Format: AAC
- Path: `Environment.getExternalStoragePublicDirectory(DIRECTORY_MUSIC)/MobileOrg/<name>-<yyyyMMdd-HHmmss>.aac`
- `<name>` = node name with special characters replaced by underscores
- MediaRecorder config: `AudioSource.DEFAULT`, `OutputFormat.AAC_ADTS`, `AudioEncoder.AAC`, sample rate 44100

**Foreground Notification**:
- Channel ID: `mobileorg_recording`
- Content: node name + elapsed time (mm:ss)
- Action buttons: Pause/Resume + Stop (via PendingIntent sending ACTION_PAUSE/ACTION_STOP)

**Edge Cases**:
- Already recording when "Record" pressed again: ignore.
- Service killed by system: `onDestroy()` safely stops MediaRecorder, does NOT write clock entry (incomplete data).
- RECORD_AUDIO permission not granted: checked at menu click time, not in Service.

### 2. RecordingBarView

Custom View inserted above the ListView in OutlineActivity when recording is active.

**Layout**: Horizontal bar with:
- Recording icon (red dot)
- Node name (truncated)
- Elapsed time (mm:ss)
- Pause/Resume button
- Stop button (square icon)

**Behavior**:
- Added dynamically to OutlineActivity layout when `RECORDING_UPDATE` received and bar not yet shown.
- Updated in-place on each `RECORDING_UPDATE`.
- Removed on `RECORDING_STOPPED`.
- Button clicks send corresponding action Intents to RecordingService.

### 3. Broadcast Communication

**Broadcasts from RecordingService**:
- `RECORDING_UPDATE`: Extra `elapsed_time` (long, seconds). Sent periodically (every 1 second via Handler).
- `RECORDING_STOPPED`: No extras. Sent when recording ends.

**Registration in OutlineActivity**:
- Dynamic registration in `onCreate()` with `RECEIVER_NOT_EXPORTED` flag (API 33+).
- Unregister in `onDestroy()`.

### 4. Menu Entries

**ActionMode menus** (long-press context toolbar):
- Add `menu_record` to `outline_node.xml` and `outline_file.xml`, next to `menu_clockin`.
- Handled in `OutlineActionMode.onActionItemClicked()`.

**Options menu** (three-dot menu):
- Add `menu_record` to `outline_menu.xml`.
- Handled in `OutlineActivity.onOptionsItemSelected()`.
- Enabled only when a node is selected (same pattern as Clock in in ActionMode — the options menu version checks selected node state).

### 5. Permission Handling

- `RECORD_AUDIO` permission added to `AndroidManifest.xml`.
- Flow: menu click → `ContextCompat.checkSelfPermission()` → if denied, `ActivityCompat.requestPermissions()` → `onRequestPermissionsResult()` starts RecordingService on grant.

## Data Flow

```
User selects node → clicks "Record" menu
  → Permission check (RECORD_AUDIO)
  → startService(RecordingService, ACTION_START, nodeId)
  → RecordingService:
      1. clock-in (store startTime)
      2. MediaRecorder.start() → Music/MobileOrg/<name>-<timestamp>.aac
      3. startForeground(notification with pause/stop buttons)
      4. Handler posts periodic UPDATE broadcasts
  → OutlineActivity receives RECORDING_UPDATE
      → show/update RecordingBarView (elapsed time, node name)
  → User taps Stop (in notification or RecordingBarView):
      → RecordingService:
          1. MediaRecorder.stop() + release()
          2. clock-out → OrgNode.addLogbook()
          3. Append [[file:xxx.aac]] to node body
          4. stopForeground() + stopSelf()
          5. Broadcast RECORDING_STOPPED
      → OutlineActivity removes RecordingBarView
```

## Files to Create/Modify

**New files**:
- `MobileOrg/src/main/java/com/matburt/mobileorg/Services/RecordingService.java`
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/RecordingBarView.java`
- `MobileOrg/src/main/res/layout/recording_bar.xml`

**Modified files**:
- `MobileOrg/src/main/AndroidManifest.xml` — add RECORD_AUDIO permission, register RecordingService with foregroundServiceType
- `MobileOrg/src/main/res/menu/outline_menu.xml` — add menu_record
- `MobileOrg/src/main/res/menu/outline_node.xml` — add menu_record
- `MobileOrg/src/main/res/menu/outline_file.xml` — add menu_record
- `MobileOrg/src/main/res/values/strings.xml` — add recording-related strings
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java` — register broadcast receiver, handle recording menu, add/remove RecordingBarView
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java` — handle menu_record action
- `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java` — add method to append file link to node body
