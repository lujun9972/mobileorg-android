package com.matburt.mobileorg.Gui.Outline;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Services.RecordingService;
import com.matburt.mobileorg.Services.TimeclockService;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.PreferenceUtils;

import java.util.Locale;

/**
 * Manages timeclock/recording UI concerns in OutlineActivity:
 * the recording bar, recording broadcast receiver, pomodoro duration picker,
 * and recording permission handling.
 */
public class OutlineTimeclockController {
    private final AppCompatActivity activity;
    private View recordingBar;
    private BroadcastReceiver recordingReceiver;
    private long pendingRecordNodeId = -1;

    public OutlineTimeclockController(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void onCreate() {
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
            activity.registerReceiver(recordingReceiver, recordingFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(recordingReceiver, recordingFilter);
        }
    }

    public void onDestroy() {
        if (recordingReceiver != null) {
            activity.unregisterReceiver(recordingReceiver);
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingRecordNodeId != -1) {
                startRecordingService(pendingRecordNodeId);
                pendingRecordNodeId = -1;
            }
        }
    }

    public void tryStartRecording(long nodeId) {
        if (RecordingService.isRecording()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startRecordingService(nodeId);
        } else {
            pendingRecordNodeId = nodeId;
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    public void showPomodoroDurationPicker() {
        // Prevent starting while consecutive mode is active
        TimeclockService service = TimeclockService.getInstance();
        if (service != null && service.isPomodoroActive()) {
            android.widget.Toast.makeText(activity, R.string.pomodoro_active_toast,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        int defaultDuration = PreferenceUtils.getPomodoroDuration();
        int defaultCount = PreferenceUtils.getPomodoroCountDefault();

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        // Duration picker (minutes)
        final NumberPicker minutePicker = new NumberPicker(activity);
        minutePicker.setMinValue(1);
        minutePicker.setMaxValue(120);
        minutePicker.setValue(defaultDuration);
        minutePicker.setWrapSelectorWheel(true);
        layout.addView(minutePicker);

        TextView minLabel = new TextView(activity);
        minLabel.setText(activity.getString(R.string.pomodoro_minute_picker_label));
        minLabel.setTextSize(18);
        minLabel.setGravity(Gravity.CENTER);
        layout.addView(minLabel);

        // Count picker
        final NumberPicker countPicker = new NumberPicker(activity);
        countPicker.setMinValue(1);
        countPicker.setMaxValue(99);
        countPicker.setValue(defaultCount);
        countPicker.setWrapSelectorWheel(true);
        layout.addView(countPicker);

        TextView countLabel = new TextView(activity);
        countLabel.setText(activity.getString(R.string.pomodoro_count_picker_suffix));
        countLabel.setTextSize(18);
        countLabel.setGravity(Gravity.CENTER);
        layout.addView(countLabel);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.pomodoro_picker_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    minutePicker.clearFocus();
                    countPicker.clearFocus();
                    int duration = minutePicker.getValue();
                    int count = countPicker.getValue();
                    Intent intent = new Intent(activity, TimeclockService.class);
                    intent.setAction(TimeclockService.ACTION_POMODORO_START);
                    intent.putExtra(TimeclockService.POMODORO_DURATION, duration);
                    intent.putExtra(TimeclockService.POMODORO_COUNT, count);
                    Compat.startService(activity, intent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showOrUpdateRecordingBar(long elapsedSeconds) {
        if (recordingBar == null) {
            recordingBar = activity.getLayoutInflater().inflate(R.layout.recording_bar, null);
            LinearLayout rootLayout = activity.findViewById(R.id.outline_root);
            rootLayout.addView(recordingBar, 0);

            ImageButton pauseBtn = recordingBar.findViewById(R.id.recording_pause_btn);
            pauseBtn.setOnClickListener(v -> {
                RecordingService instance = RecordingService.getInstance();
                if (instance != null) {
                    Intent intent = new Intent(activity, RecordingService.class);
                    intent.putExtra(RecordingService.ACTION_NAME,
                            instance.isPaused() ? RecordingService.ACTION_RESUME : RecordingService.ACTION_PAUSE);
                    activity.startService(intent);
                }
            });

            ImageButton stopBtn = recordingBar.findViewById(R.id.recording_stop_btn);
            stopBtn.setOnClickListener(v -> {
                Intent intent = new Intent(activity, RecordingService.class);
                intent.putExtra(RecordingService.ACTION_NAME, RecordingService.ACTION_STOP);
                activity.startService(intent);
            });
        }

        TextView elapsedView = recordingBar.findViewById(R.id.recording_elapsed);
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;
        elapsedView.setText(String.format(Locale.getDefault(), "%d:%02d", minutes, seconds));

        ImageButton pauseBtn = recordingBar.findViewById(R.id.recording_pause_btn);
        RecordingService instance = RecordingService.getInstance();
        if (instance != null && instance.isPaused()) {
            pauseBtn.setImageResource(R.drawable.ic_media_play);
        } else {
            pauseBtn.setImageResource(R.drawable.ic_media_pause);
        }
    }

    private void removeRecordingBar() {
        if (recordingBar != null) {
            LinearLayout rootLayout = activity.findViewById(R.id.outline_root);
            rootLayout.removeView(recordingBar);
            recordingBar = null;
        }
    }

    private void startRecordingService(long nodeId) {
        Intent intent = new Intent(activity, RecordingService.class);
        intent.putExtra(RecordingService.ACTION_NAME, RecordingService.ACTION_START);
        intent.putExtra(RecordingService.NODE_ID, nodeId);
        Compat.startService(activity, intent);
    }
}
