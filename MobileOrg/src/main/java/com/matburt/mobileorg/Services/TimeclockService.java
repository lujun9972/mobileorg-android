package com.matburt.mobileorg.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Builder;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.RemoteViews;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

public class TimeclockService extends Service {
	public static final String ACTION_CLOCK_IN = "clock_in";
	public static final String ACTION_CLOCK_OUT = "clock_out";
	public static final String ACTION_CLOCK_CANCEL = "clock_cancel";
	public static final String ACTION_POMODORO_START = "pomodoro_start";
	public static final String ACTION_POMODORO_STOP = "pomodoro_stop";

	public static final String NODE_ID = "node_id";
	public static final String POMODORO_DURATION = "pomodoro_duration";
	public static final String CLOCK_DURATION = "clock_duration";
	public static final String ACTION_ALARM_DISMISS = "alarm_dismiss";
	public static final String BROADCAST_STATE_CHANGED = "com.matburt.mobileorg.TIMECLOCK_STATE_CHANGED";
	private static final String CHANNEL_ID = "mobileorg_timeclock";
	private static final String TIMEOUT_CHANNEL_ID = "mobileorg_timeclock_alarm";
	private static final int TIMEOUT_NOTIFICATION_ID = 1338;
	private static final long UPDATE_INTERVAL_MS = 60L * 1000L;

	private final int notificationID = 1337;
	private NotificationManager mNM;
	private Notification notification;
	private MediaPlayer alarmMediaPlayer;

	private OrgNode clockNode;
	private MobileOrgApplication appInst;

	private static TimeclockService sInstance;

	private final PomodoroTimer pomodoroTimer = new PomodoroTimer();
	private final ClockTimer clockTimer = new ClockTimer();
	private OrgNodeRepository repo;

	private final Handler timerHandler = new Handler(Looper.getMainLooper());
	private Runnable updateRunnable;
	private Runnable timeoutRunnable;

	public static TimeclockService getInstance() {
		return sInstance;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		sInstance = this;
		this.mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		this.appInst = (MobileOrgApplication) getApplication();
		this.repo = new OrgNodeRepository(getContentResolver());
		Log.d("MobileOrg", "[ClockIn] TimeclockService.onCreate: sInstance set");
	}

	@Override
	public void onDestroy() {
		Log.d("MobileOrg", "[ClockIn] TimeclockService.onDestroy: sInstance clearing");
		cancelNotification();
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null) { stopSelf(); return START_NOT_STICKY; }
		String action = intent.getAction();
		if (action == null) action = inferAction(intent);

		switch (action) {
			case ACTION_POMODORO_START:
				handlePomodoroStart(intent);
				break;
			case ACTION_CLOCK_IN:
				handleClockIn(intent);
				break;
			case ACTION_POMODORO_STOP:
				handlePomodoroStop();
				break;
			case ACTION_CLOCK_OUT:
				doClockOut(true, intent.getIntExtra(CLOCK_DURATION, -1));
				break;
			case ACTION_CLOCK_CANCEL:
				doClockOut(false, -1);
				break;
			case ACTION_ALARM_DISMISS:
				handleAlarmDismiss();
				break;
			default:
				Log.w("MobileOrg", "[ClockIn] Unknown action: " + action);
				break;
		}
		return START_NOT_STICKY;
	}

	private String inferAction(Intent intent) {
		if (intent.hasExtra(POMODORO_DURATION)) return ACTION_POMODORO_START;
		if (intent.hasExtra(NODE_ID)) return ACTION_CLOCK_IN;
		return "";
	}

	private void handlePomodoroStart(Intent intent) {
		int duration = intent.getIntExtra(POMODORO_DURATION, 25);
		pomodoroTimer.start(duration);
		cancelTimers();

		updateRunnable = new Runnable() {
			@Override
			public void run() {
				if (!pomodoroTimer.isRunning() && !clockTimer.isClockedIn()) return;
				updateTime();
				timerHandler.postDelayed(this, UPDATE_INTERVAL_MS);
			}
		};
		timerHandler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);

		final long timeoutDelay = pomodoroTimer.getRemainingMillis();
		timeoutRunnable = new Runnable() {
			@Override
			public void run() {
				timeoutRunnable = null;
				handlePomodoroTimeout();
			}
		};
		timerHandler.postDelayed(timeoutRunnable, timeoutDelay);

		Log.d("MobileOrg", "[Pomodoro] Started: " + duration + "min, timeout in " + timeoutDelay + "ms");
		showOrRefreshNotification();
	}

	private void handleClockIn(Intent intent) {
		long newNodeId = intent.getLongExtra(NODE_ID, -1);
		if (newNodeId < 0) return;
		if (clockTimer.isClockedIn()) {
			doClockOut(true, -1);
		}
		try {
			clockTimer.clockIn(newNodeId);
			this.clockNode = repo.getById(newNodeId);
		} catch (OrgNodeNotFoundException e) {
			Log.e("MobileOrg", "[ClockIn] Node not found: " + newNodeId, e);
			return;
		}
		startUpdateIfNeeded();
		showOrRefreshNotification();
	}

	public void doClockOut(boolean save, int editedDurationMinutes) {
		ClockTimer.ClockOutResult result = clockTimer.clockOut(editedDurationMinutes);
		if (result == null) return;
		if (save && clockNode != null) {
			repo.addLogbook(clockNode, result.startTime, result.endTime, result.elapsedTime);
		}
		this.clockNode = null;
		notifyStateChanged();
		checkStopSelf();
		showOrRefreshNotification();
	}

	private void handlePomodoroStop() {
		stopAndReleaseAlarmSound();
		Log.d("MobileOrg", "[Pomodoro] Stopping, cancelling timeout notification");
		cancelTimers();
		pomodoroTimer.stop();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
		notifyStateChanged();
		checkStopSelf();
		showOrRefreshNotification();
	}

	private void handlePomodoroTimeout() {
		if (!pomodoroTimer.isRunning()) {
			Log.w("MobileOrg", "[Pomodoro] Timeout received but pomodoro not running, ignoring");
			return;
		}
		pomodoroTimer.markTimeout();
		repo.recordPomodoroSession(pomodoroTimer.getStartTime(), pomodoroTimer.getDurationMinutes());
		Log.d("MobileOrg", "[Pomodoro] Timeout! duration=" + pomodoroTimer.getDurationMinutes() + "min, sending alert notification on channel " + TIMEOUT_CHANNEL_ID);

		if (Compat.isAtLeastO()) {
			NotificationChannel timeoutChannel = new NotificationChannel(
					TIMEOUT_CHANNEL_ID, "Pomodoro Timer Alert", NotificationManager.IMPORTANCE_HIGH);
			timeoutChannel.setDescription("Alerts when pomodoro timer completes");
			timeoutChannel.setSound(null, null);
			timeoutChannel.enableVibration(true);
			mNM.createNotificationChannel(timeoutChannel);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
				new Intent(this, TimeclockDialog.class), Compat.FLAG_IMMUTABLE);

		NotificationCompat.Builder timeoutBuilder = new NotificationCompat.Builder(this, TIMEOUT_CHANNEL_ID)
				.setSmallIcon(R.drawable.timeclock_icon)
				.setContentTitle("\uD83C\uDF45 番茄钟时间到！")
				.setContentText(pomodoroTimer.getDurationMinutes() + " 分钟番茄钟已完成")
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setCategory(NotificationCompat.CATEGORY_ALARM)
				.setAutoCancel(false)
				.setContentIntent(contentIntent);

		Intent dismissIntent = new Intent(this, TimeclockService.class);
		dismissIntent.setAction(ACTION_ALARM_DISMISS);
		PendingIntent dismissPI = PendingIntent.getService(this, 4, dismissIntent, Compat.FLAG_IMMUTABLE);
		timeoutBuilder.setDeleteIntent(dismissPI);
		timeoutBuilder.addAction(new NotificationCompat.Action.Builder(
				R.drawable.ic_media_stop, "关闭闹铃", dismissPI).build());

		mNM.notify(TIMEOUT_NOTIFICATION_ID, timeoutBuilder.build());
		Log.d("MobileOrg", "[Pomodoro] Alert notification posted, id=" + TIMEOUT_NOTIFICATION_ID);

		updateTime();

		stopAndReleaseAlarmSound();
		alarmMediaPlayer = Compat.playAlarmSound(this);
	}

	private void handleAlarmDismiss() {
		stopAndReleaseAlarmSound();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
	}

	private void startUpdateIfNeeded() {
		if (updateRunnable != null) return;
		updateRunnable = new Runnable() {
			@Override
			public void run() {
				if (!pomodoroTimer.isRunning() && !clockTimer.isClockedIn()) return;
				updateTime();
				timerHandler.postDelayed(this, UPDATE_INTERVAL_MS);
			}
		};
		timerHandler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);
	}

	private void cancelTimers() {
		if (updateRunnable != null) {
			timerHandler.removeCallbacks(updateRunnable);
			updateRunnable = null;
		}
		if (timeoutRunnable != null) {
			timerHandler.removeCallbacks(timeoutRunnable);
			timeoutRunnable = null;
		}
	}

	private void checkStopSelf() {
		if (!pomodoroTimer.isRunning() && !clockTimer.isClockedIn()) {
			cancelNotification();
		}
	}

	private void showOrRefreshNotification() {
		Compat.createNotificationChannel(this, CHANNEL_ID, "MobileOrg Timeclock");

		PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
				new Intent(this, TimeclockDialog.class), Compat.FLAG_IMMUTABLE);

		Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
		builder.setSmallIcon(R.drawable.timeclock_icon);
		builder.setOngoing(true);
		builder.setContentIntent(contentIntent);

		String title;
		if (pomodoroTimer.isRunning() && clockTimer.isClockedIn() && clockNode != null) {
			title = pomodoroTimer.getTitleString(clockNode.name);
		} else if (pomodoroTimer.isRunning()) {
			title = pomodoroTimer.getTitleString(null);
		} else if (clockTimer.isClockedIn() && clockNode != null) {
			title = clockNode.name;
		} else {
			title = "Timeclock";
		}
		builder.setContentTitle(title);

		this.notification = builder.getNotification();

		notification.contentView = new RemoteViews(this.getPackageName(),
				R.layout.timeclock_notification);

		notification.contentView.setImageViewResource(R.id.timeclock_notification_icon,
				R.drawable.timeclock_icon);
		notification.contentView.setTextViewText(R.id.timeclock_notification_text, title);

		if (pomodoroTimer.isRunning()) {
			Intent stopIntent = new Intent(this, TimeclockService.class);
			stopIntent.setAction(ACTION_POMODORO_STOP);
			PendingIntent stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, Compat.FLAG_IMMUTABLE);
			NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(
					R.drawable.ic_media_stop, "Stop", stopPendingIntent).build();
			builder.addAction(stopAction);
			this.notification = builder.build();
			notification.contentView = new RemoteViews(this.getPackageName(),
					R.layout.timeclock_notification);
			notification.contentView.setImageViewResource(R.id.timeclock_notification_icon,
					R.drawable.timeclock_icon);
			notification.contentView.setTextViewText(R.id.timeclock_notification_text, title);
		}

		updateTime();

		if (Compat.isAtLeastO()) {
			Compat.startForeground(this, notificationID, notification,
					android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		} else {
			mNM.notify(notificationID, notification);
		}
	}

	private void updateTime() {
		if (notification == null || notification.contentView == null) return;

		SpannableStringBuilder itemText;
		String titleText;
		boolean pomoRunning = pomodoroTimer.isRunning();
		boolean clockRunning = clockTimer.isClockedIn();

		if (pomoRunning && clockRunning) {
			titleText = pomodoroTimer.getTitleString(clockNode != null ? clockNode.name : null);
			itemText = new SpannableStringBuilder(clockTimer.getElapsedString());
		} else if (pomoRunning) {
			String remainingStr = pomodoroTimer.getRemainingString();
			itemText = new SpannableStringBuilder(remainingStr);
			titleText = pomodoroTimer.getTitleString(null);
			if (pomodoroTimer.isTimedOut()) {
				itemText.setSpan(new ForegroundColorSpan(Color.RED), 0,
						itemText.length(), 0);
			}
		} else if (clockRunning) {
			itemText = new SpannableStringBuilder(clockTimer.getElapsedString());
			titleText = (clockNode != null) ? clockNode.name : "Timeclock";
		} else {
			itemText = new SpannableStringBuilder("");
			titleText = "Timeclock";
		}

		notification.contentView.setTextViewText(
				R.id.timeclock_notification_text, titleText);
		notification.contentView.setTextViewText(
				R.id.timeclock_notification_time, itemText);
		mNM.notify(notificationID, notification);
	}

	public boolean isPomodoroRunning() { return pomodoroTimer.isRunning(); }
	public boolean isClockedIn() { return clockTimer.isClockedIn(); }
	public OrgNode getClockNode() { return clockNode; }
	public boolean isPomodoroTimedOut() { return pomodoroTimer.isTimedOut(); }

	public String getClockElapsedString() {
		return clockTimer.getElapsedString();
	}

	public String getPomodoroRemainingString() {
		return pomodoroTimer.getRemainingString();
	}

	public long getClockStartTime() { return clockTimer.isClockedIn() ? clockTimer.getStartTime() : 0; }
	public long getNodeID() { return clockTimer.isClockedIn() ? clockTimer.getNodeId() : -1; }

	private void notifyStateChanged() {
		Intent intent = new Intent(BROADCAST_STATE_CHANGED);
		intent.setPackage(getPackageName());
		sendBroadcast(intent);
	}

	public void cancelNotification() {
		stopAndReleaseAlarmSound();
		notifyStateChanged();
		cancelTimers();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
		mNM.cancel(notificationID);
		if (Compat.isAtLeastO()) stopForeground(true);
		notification = null;
		stopSelf();
	}

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

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
