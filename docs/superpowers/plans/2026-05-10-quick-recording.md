# Quick Recording Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a quick recording feature: select a node, start recording audio (recording start = clock-in, recording stop = clock-out), append recording file link to node body.

**Architecture:** New `RecordingService` (foreground Service, singleton) manages MediaRecorder and clock-in/clock-out independently. OutlineActivity receives broadcast updates to show an inline `RecordingBarView` status bar. Menu entries added to both ActionMode toolbar and options menu.

**Tech Stack:** Android MediaRecorder API, NotificationCompat, PendingIntent, BroadcastReceiver, ContentProvider

---

## File Structure

**New files:**
- `MobileOrg/src/main/java/com/matburt/mobileorg/Services/RecordingService.java` — Foreground service managing recording lifecycle + clock-in/out
- `MobileOrg/src/main/res/layout/recording_bar.xml` — Layout for inline recording status bar

**Modified files:**
- `MobileOrg/src/main/AndroidManifest.xml` — Add RECORD_AUDIO permission, register RecordingService
- `MobileOrg/src/main/res/menu/outline_menu.xml` — Add menu_record item
- `MobileOrg/src/main/res/menu/outline_node.xml` — Add menu_record item
- `MobileOrg/src/main/res/menu/outline_file.xml` — Add menu_record item
- `MobileOrg/src/main/res/values/strings.xml` — Add recording-related strings
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java` — Broadcast receiver, recording bar, menu handler
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java` — Handle menu_record in ActionMode
- `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java` — Add `appendFileLink()` method

---

### Task 1: Add strings and menu resources

**Files:**
- Modify: `MobileOrg/src/main/res/values/strings.xml`
- Modify: `MobileOrg/src/main/res/menu/outline_menu.xml`
- Modify: `MobileOrg/src/main/res/menu/outline_node.xml`
- Modify: `MobileOrg/src/main/res/menu/outline_file.xml`

- [ ] **Step 1: Add recording strings to strings.xml**

Add after the existing `menu_clockin` string:

```xml
<string name="menu_record">Record</string>
<string name="recording_notification_channel">MobileOrg Recording</string>
<string name="recording_notification_title">Recording: %s</string>
<string name="recording_stop">Stop</string>
<string name="recording_pause">Pause</string>
<string name="recording_resume">Resume</string>
```

- [ ] **Step 2: Add menu_record to outline_menu.xml**

Add after `menu_capturechild` item (line 23) in `MobileOrg/src/main/res/menu/outline_menu.xml`:

```xml
<item
    android:id="@+id/menu_record"
    android:icon="@drawable/ic_menu_record"
    android:title="@string/menu_record"/>
```

- [ ] **Step 3: Add menu_record to outline_node.xml**

Add after `menu_clockin` item (line 30) in `MobileOrg/src/main/res/menu/outline_node.xml`:

```xml
<item
    android:id="@+id/menu_record"
    android:icon="@drawable/ic_menu_record"
    app:showAsAction="ifRoom"
    android:title="@string/menu_record"/>
```

- [ ] **Step 4: Add menu_record to outline_file.xml**

Add after `menu_clockin` item in `MobileOrg/src/main/res/menu/outline_file.xml`:

```xml
<item
    android:id="@+id/menu_record"
    android:icon="@drawable/ic_menu_record"
    app:showAsAction="ifRoom"
    android:title="@string/menu_record"/>
```

- [ ] **Step 5: Create recording icon drawable**

Create `MobileOrg/src/main/res/drawable/ic_menu_record.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.49 6,-3.31 6,-6.72H17.3z"/>
</vector>
```

- [ ] **Step 6: Commit**

```bash
git add MobileOrg/src/main/res/values/strings.xml \
        MobileOrg/src/main/res/menu/outline_menu.xml \
        MobileOrg/src/main/res/menu/outline_node.xml \
        MobileOrg/src/main/res/menu/outline_file.xml \
        MobileOrg/src/main/res/drawable/ic_menu_record.xml
git commit -m "feat: add recording menu items and string resources"
```

---

### Task 2: Add RECORD_AUDIO permission and RecordingService to AndroidManifest

**Files:**
- Modify: `MobileOrg/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add RECORD_AUDIO permission**

Add after the existing `FOREGROUND_SERVICE_SPECIAL_USE` permission (around line 12) in `MobileOrg/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
```

- [ ] **Step 2: Register RecordingService**

Add after the TimeclockService declaration (around line 183) in `MobileOrg/src/main/AndroidManifest.xml`:

```xml
<service android:name=".Services.RecordingService"
    android:foregroundServiceType="specialUse" >
</service>
```

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/src/main/AndroidManifest.xml
git commit -m "feat: add RECORD_AUDIO permission and register RecordingService"
```

---

### Task 3: Add `appendFileLink` method to OrgNode

**Files:**
- Modify: `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java`

- [ ] **Step 1: Add the `appendFileLink` method**

Add after the existing `addLogbook` method (around line 614) in `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java`:

```java
/**
 * Append a file link (e.g. [[file:xxx.aac]]) to the node body.
 */
public void appendFileLink(String filePath, ContentResolver resolver) {
    String link = "[[file:" + filePath + "]]";
    StringBuilder rawPayload = new StringBuilder(getPayload());
    rawPayload.append("\n").append(link);

    boolean generateEdits = !getFilename(resolver).equals(FileUtils.CAPTURE_FILE);
    if (generateEdits) {
        OrgEdit edit = new OrgEdit(this, OrgEdit.TYPE.BODY, rawPayload.toString(), resolver);
        edit.write(resolver);
    }
    setPayload(rawPayload.toString());
}
```

This method follows the same pattern as `addLogbook` — builds a new payload string, optionally creates an OrgEdit for version control, then sets the payload.

Requires import: `com.matburt.mobileorg.util.FileUtils` (likely already imported).

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java
git commit -m "feat: add OrgNode.appendFileLink for recording file links"
```

---

### Task 4: Create RecordingService

**Files:**
- Create: `MobileOrg/src/main/java/com/matburt/mobileorg/Services/RecordingService.java`

- [ ] **Step 1: Write the RecordingService class**

Create `MobileOrg/src/main/java/com/matburt/mobileorg/Services/RecordingService.java` with the following content:

```java
package com.matburt.mobileorg.Services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import android.util.Log;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {

    private static final String TAG = "RecordingService";
    public static final String NODE_ID = "node_id";
    public static final String ACTION_NAME = "action";
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESUME = "resume";
    public static final String ACTION_UPDATE = "update";

    public static final String BROADCAST_UPDATE = "com.matburt.mobileorg.RECORDING_UPDATE";
    public static final String BROADCAST_STOPPED = "com.matburt.mobileorg.RECORDING_STOPPED";
    public static final String EXTRA_ELAPSED_SECONDS = "elapsed_seconds";

    private static final String CHANNEL_ID = "mobileorg_recording";
    private static final int NOTIFICATION_ID = 2;

    private static RecordingService sInstance;

    public static RecordingService getInstance() {
        return sInstance;
    }

    public static boolean isRecording() {
        return sInstance != null && sInstance.mediaRecorder != null;
    }

    private MediaRecorder mediaRecorder;
    private OrgNode node;
    private long startTime;
    private long pauseStartTime;
    private long totalPausedDuration;
    private String recordingFilePath;
    private Handler handler;
    private boolean paused;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getStringExtra(ACTION_NAME);
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_START:
                long nodeId = intent.getLongExtra(NODE_ID, -1);
                if (nodeId == -1) {
                    stopSelf();
                    return START_NOT_STICKY;
                }
                startRecording(nodeId);
                break;
            case ACTION_PAUSE:
                pauseRecording();
                break;
            case ACTION_RESUME:
                resumeRecording();
                break;
            case ACTION_STOP:
                stopRecording();
                break;
            case ACTION_UPDATE:
                updateNotification();
                break;
        }

        return START_NOT_STICKY;
    }

    private void startRecording(long nodeId) {
        if (mediaRecorder != null) {
            // Already recording, ignore
            return;
        }

        ContentResolver resolver = getContentResolver();
        try {
            node = new OrgNode(nodeId, resolver);
        } catch (OrgNodeNotFoundException e) {
            Log.e(TAG, "Node not found: " + nodeId);
            stopSelf();
            return;
        }

        startTime = System.currentTimeMillis();
        totalPausedDuration = 0;
        paused = false;

        // Create recording file
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "MobileOrg");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String sanitizedNodeName = node.name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff_.\\-]", "_");
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String filename = sanitizedNodeName + "-" + timestamp + ".aac";
        File recordingFile = new File(dir, filename);
        recordingFilePath = recordingFile.getAbsolutePath();

        // Setup MediaRecorder
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setOutputFile(recordingFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            Log.e(TAG, "MediaRecorder prepare/start failed", e);
            releaseRecorder();
            stopSelf();
            return;
        }

        showNotification();
        startUpdateLoop();

        Log.d(TAG, "Recording started: " + recordingFilePath);
    }

    private void pauseRecording() {
        if (mediaRecorder == null || paused) return;
        if (Build.VERSION.SDK_INT >= 24) {
            mediaRecorder.pause();
        }
        paused = true;
        pauseStartTime = System.currentTimeMillis();
        updateNotification();
    }

    private void resumeRecording() {
        if (mediaRecorder == null || !paused) return;
        if (Build.VERSION.SDK_INT >= 24) {
            mediaRecorder.resume();
        }
        totalPausedDuration += System.currentTimeMillis() - pauseStartTime;
        paused = false;
        updateNotification();
    }

    private void stopRecording() {
        if (mediaRecorder == null) {
            stopSelf();
            return;
        }

        handler.removeCallbacksAndMessages(null);

        try {
            mediaRecorder.stop();
        } catch (RuntimeException e) {
            Log.w(TAG, "MediaRecorder.stop() failed", e);
        }
        releaseRecorder();

        // Clock-out and write to node
        long endTime = System.currentTimeMillis();
        long elapsedMillis = endTime - startTime - totalPausedDuration;
        String elapsedTime = formatElapsedTime(elapsedMillis);

        ContentResolver resolver = getContentResolver();
        node.addLogbook(startTime, endTime, elapsedTime, resolver);
        node.appendFileLink(recordingFilePath, resolver);

        sendBroadcast(new Intent(BROADCAST_STOPPED));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();

        Log.d(TAG, "Recording stopped. File: " + recordingFilePath);
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                // ignore
            }
            mediaRecorder = null;
        }
    }

    private void showNotification() {
        Compat.createNotificationChannel(this, CHANNEL_ID,
                getString(R.string.recording_notification_channel));

        // Stop action
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.putExtra(ACTION_NAME, ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stopIntent,
                Compat.FLAG_IMMUTABLE);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(
                R.drawable.ic_menu_record, getString(R.string.recording_stop), stopPending);

        // Pause/Resume action
        String pauseLabel = paused
                ? getString(R.string.recording_resume)
                : getString(R.string.recording_pause);
        String pauseActionName = paused ? ACTION_RESUME : ACTION_PAUSE;
        Intent pauseIntent = new Intent(this, RecordingService.class);
        pauseIntent.putExtra(ACTION_NAME, pauseActionName);
        PendingIntent pausePending = PendingIntent.getService(this, 2, pauseIntent,
                Compat.FLAG_IMMUTABLE);
        NotificationCompat.Action pauseAction = new NotificationCompat.Action(
                R.drawable.ic_menu_record, pauseLabel, pausePending);

        String title = String.format(Locale.getDefault(),
                getString(R.string.recording_notification_title),
                node != null ? node.name : "");

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_record)
                .setContentTitle(title)
                .setOngoing(true)
                .addAction(pauseAction)
                .addAction(stopAction)
                .build();

        if (Compat.isAtLeastO()) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        }
    }

    private void updateNotification() {
        if (mediaRecorder == null) return;
        showNotification();

        long elapsedSeconds = getElapsedSeconds();
        Intent updateIntent = new Intent(BROADCAST_UPDATE);
        updateIntent.putExtra(EXTRA_ELAPSED_SECONDS, elapsedSeconds);
        updateIntent.setPackage(getPackageName());
        sendBroadcast(updateIntent);
    }

    private void startUpdateLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaRecorder != null) {
                    updateNotification();
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    private long getElapsedSeconds() {
        long elapsed = System.currentTimeMillis() - startTime - totalPausedDuration;
        if (paused) {
            elapsed -= (System.currentTimeMillis() - pauseStartTime);
        }
        return Math.max(0, elapsed / 1000);
    }

    private static String formatElapsedTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        // If still recording when destroyed, clean up without writing clock entry
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                // ignore
            }
            releaseRecorder();
        }
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/RecordingService.java
git commit -m "feat: add RecordingService with MediaRecorder and clock-in/out"
```

---

### Task 5: Create RecordingBarView layout

**Files:**
- Create: `MobileOrg/src/main/res/layout/recording_bar.xml`

- [ ] **Step 1: Write the recording bar layout**

Create `MobileOrg/src/main/res/layout/recording_bar.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/recording_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="8dp"
    android:paddingEnd="8dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp"
    android:background="#44FF0000">

    <View
        android:layout_width="10dp"
        android:layout_height="10dp"
        android:background="#FF0000" />

    <TextView
        android:id="@+id/recording_node_name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="8dp"
        android:ellipsize="end"
        android:maxLines="1"
        android:textSize="14sp"
        android:textColor="#FFFFFF" />

    <TextView
        android:id="@+id/recording_elapsed"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:textSize="14sp"
        android:textColor="#FFFFFF"
        android:text="0:00" />

    <ImageButton
        android:id="@+id/recording_pause_btn"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_marginStart="4dp"
        android:background="?android:attr/actionBarItemBackground"
        android:src="@drawable/ic_menu_record"
        android:contentDescription="@string/recording_pause"
        android:scaleType="centerInside" />

    <ImageButton
        android:id="@+id/recording_stop_btn"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_marginStart="4dp"
        android:background="?android:attr/actionBarItemBackground"
        android:src="@drawable/ic_menu_record"
        android:contentDescription="@string/recording_stop"
        android:scaleType="centerInside" />

</LinearLayout>
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/main/res/layout/recording_bar.xml
git commit -m "feat: add recording bar layout for OutlineActivity"
```

---

### Task 6: Integrate recording into OutlineActivity

**Files:**
- Modify: `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`

- [ ] **Step 1: Add imports**

Add these imports to `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`:

```java
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.matburt.mobileorg.Services.RecordingService;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
```

- [ ] **Step 2: Add fields and permission launcher**

Add these fields to the `OutlineActivity` class (after the existing `syncReceiver` field):

```java
private View recordingBar;
private BroadcastReceiver recordingReceiver;
private ActivityResultLauncher<String> recordPermissionLauncher;
private long pendingRecordNodeId = -1;
```

In `onCreate()`, after the `syncReceiver` registration block (around line 80), add the permission launcher registration:

```java
recordPermissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        granted -> {
            if (granted && pendingRecordNodeId != -1) {
                startRecordingService(pendingRecordNodeId);
                pendingRecordNodeId = -1;
            }
        });
```

- [ ] **Step 3: Register recording broadcast receiver**

In `onCreate()`, after the syncReceiver registration (around line 80), add:

```java
recordingReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (RecordingService.BROADCAST_UPDATE.equals(action)) {
            long elapsed = intent.getLongExtra(RecordingService.EXTRA_ELAPSED_SECONDS, 0);
            showOrUpdateRecordingBar(elapsed);
        } else if (RecordingService.BROADCAST_STOPPED.equals(action)) {
            removeRecordingBar();
        }
    }
};

IntentFilter recordingFilter = new IntentFilter(RecordingService.BROADCAST_UPDATE);
recordingFilter.addAction(RecordingService.BROADCAST_STOPPED);
if (Build.VERSION.SDK_INT >= 33) {
    registerReceiver(recordingReceiver, recordingFilter, Context.RECEIVER_NOT_EXPORTED);
} else {
    registerReceiver(recordingReceiver, recordingFilter);
}
```

- [ ] **Step 4: Unregister recording receiver in onDestroy**

In `onDestroy()`, before `super.onDestroy()`, add:

```java
if (recordingReceiver != null) {
    unregisterReceiver(recordingReceiver);
}
```

- [ ] **Step 5: Add helper methods for recording bar and service**

Add these methods to `OutlineActivity`:

```java
private void showOrUpdateRecordingBar(long elapsedSeconds) {
    if (recordingBar == null) {
        recordingBar = getLayoutInflater().inflate(R.layout.recording_bar, null);
        LinearLayout rootLayout = findViewById(R.id.outline_root);
        rootLayout.addView(recordingBar, 0);

        ImageButton pauseBtn = recordingBar.findViewById(R.id.recording_pause_btn);
        pauseBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, RecordingService.class);
            RecordingService instance = RecordingService.getInstance();
            if (instance != null) {
                // Toggle pause/resume
                boolean isPaused = RecordingService.isRecording();
                intent.putExtra(RecordingService.ACTION_NAME,
                        RecordingService.ACTION_PAUSE);
                startService(intent);
            }
        });

        ImageButton stopBtn = recordingBar.findViewById(R.id.recording_stop_btn);
        stopBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, RecordingService.class);
            intent.putExtra(RecordingService.ACTION_NAME, RecordingService.ACTION_STOP);
            startService(intent);
        });
    }

    TextView elapsedView = recordingBar.findViewById(R.id.recording_elapsed);
    long minutes = elapsedSeconds / 60;
    long seconds = elapsedSeconds % 60;
    elapsedView.setText(String.format(Locale.getDefault(), "%d:%02d", minutes, seconds));
}

private void removeRecordingBar() {
    if (recordingBar != null) {
        LinearLayout rootLayout = findViewById(R.id.outline_root);
        rootLayout.removeView(recordingBar);
        recordingBar = null;
    }
}

private void tryStartRecording(long nodeId) {
    if (RecordingService.isRecording()) {
        Toast.makeText(this, "Already recording", Toast.LENGTH_SHORT).show();
        return;
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
        startRecordingService(nodeId);
    } else {
        pendingRecordNodeId = nodeId;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 0);
    }
}

private void startRecordingService(long nodeId) {
    Intent intent = new Intent(this, RecordingService.class);
    intent.putExtra(RecordingService.ACTION_NAME, RecordingService.ACTION_START);
    intent.putExtra(RecordingService.NODE_ID, nodeId);
    if (Build.VERSION.SDK_INT >= 26) {
        startForegroundService(intent);
    } else {
        startService(intent);
    }
}
```

Note: Add `import java.util.Locale;` and `import android.widget.Toast;` if not already imported.

- [ ] **Step 6: Handle menu_record in onOptionsItemSelected**

In `onOptionsItemSelected()`, add before `return false;` (around line 181):

```java
} else if (id == R.id.menu_record) {
    long checkedNodeId = listView.getCheckedNodeId();
    if (checkedNodeId >= 0) {
        tryStartRecording(checkedNodeId);
    } else {
        Toast.makeText(this, "Select a node first", Toast.LENGTH_SHORT).show();
    }
    return true;
```

- [ ] **Step 7: Add id to outline.xml root layout**

In `MobileOrg/src/main/res/layout/outline.xml`, add `android:id` to the root LinearLayout:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/outline_root"
    ...
```

- [ ] **Step 8: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java \
        MobileOrg/src/main/res/layout/outline.xml
git commit -m "feat: integrate recording bar and menu into OutlineActivity"
```

---

### Task 7: Handle menu_record in OutlineActionMode

**Files:**
- Modify: `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java`

- [ ] **Step 1: Add import**

Add to imports:

```java
import com.matburt.mobileorg.Services.RecordingService;
```

- [ ] **Step 2: Add handler in onActionItemClicked**

In `onActionItemClicked()`, add after the `menu_clockin` handler (around line 95):

```java
} else if (id == R.id.menu_record) {
    runRecordingService();
```

- [ ] **Step 3: Add the runRecordingService method**

Add after the existing `runTimeClockingService()` method (around line 228):

```java
private void runRecordingService() {
    Intent intent = new Intent(context, RecordingService.class);
    intent.putExtra(RecordingService.ACTION_NAME, RecordingService.ACTION_START);
    intent.putExtra(RecordingService.NODE_ID, node.id);
    if (Build.VERSION.SDK_INT >= 26) {
        context.startForegroundService(intent);
    } else {
        context.startService(intent);
    }
}
```

Add `import android.os.Build;` if not already imported.

- [ ] **Step 4: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java
git commit -m "feat: handle recording menu in OutlineActionMode"
```

---

### Task 8: Build and smoke test

**Files:** None (verification only)

- [ ] **Step 1: Build the APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run instrumentation tests**

Run: `./gradlew connectedDebugAndroidTest`
Expected: All 94 tests pass

- [ ] **Step 3: Commit any fixups if needed**

If build or test issues are found, fix and commit with appropriate messages.
