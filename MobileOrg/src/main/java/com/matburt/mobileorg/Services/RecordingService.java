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
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
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

    public boolean isPaused() {
        return paused;
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
        }

        return START_NOT_STICKY;
    }

    private void startRecording(long nodeId) {
        if (mediaRecorder != null) {
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

        mediaRecorder = new MediaRecorder();
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(recordingFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            Log.e(TAG, "MediaRecorder setup failed", e);
            releaseRecorder();
            stopSelf();
            return;
        }

        showNotification();
        startUpdateLoop();
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

        long endTime = System.currentTimeMillis();
        long elapsedMillis = endTime - startTime - totalPausedDuration;
        String elapsedTime = formatElapsedTime(elapsedMillis);

        ContentResolver resolver = getContentResolver();
        new OrgNodeRepository(resolver).addLogbook(node, startTime, endTime, elapsedTime);
        new OrgNodeRepository(resolver).appendFileLink(node, recordingFilePath);

        Intent stoppedIntent = new Intent(BROADCAST_STOPPED);
        stoppedIntent.setPackage(getPackageName());
        sendBroadcast(stoppedIntent);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
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

        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.putExtra(ACTION_NAME, ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stopIntent,
                Compat.FLAG_IMMUTABLE);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(
                R.drawable.ic_media_stop, getString(R.string.recording_stop), stopPending);

        String pauseLabel = paused
                ? getString(R.string.recording_resume)
                : getString(R.string.recording_pause);
        String pauseActionName = paused ? ACTION_RESUME : ACTION_PAUSE;
        Intent pauseIntent = new Intent(this, RecordingService.class);
        pauseIntent.putExtra(ACTION_NAME, pauseActionName);
        PendingIntent pausePending = PendingIntent.getService(this, 2, pauseIntent,
                Compat.FLAG_IMMUTABLE);
        NotificationCompat.Action pauseAction = new NotificationCompat.Action(
                R.drawable.ic_media_pause, pauseLabel, pausePending);

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
            Compat.startForeground(this, NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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
