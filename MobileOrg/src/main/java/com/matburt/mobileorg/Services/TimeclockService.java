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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

/**
 * Foreground service for timeclock and pomodoro timer.
 *
 * Uses Handler-based timing instead of AlarmManager for reliable background operation.
 * AlarmManager is inexact from API 19+ and restricted by Doze/App Standby on modern Android,
 * causing missed timeouts and stale notification displays. Since this is a foreground service
 * (always alive while timer runs), Handler.postDelayed() is both simpler and more reliable.
 */
public class TimeclockService extends Service {
	// Action constants
	public static final String ACTION_CLOCK_IN = "clock_in";
	public static final String ACTION_CLOCK_OUT = "clock_out";
	public static final String ACTION_CLOCK_CANCEL = "clock_cancel";
	public static final String ACTION_POMODORO_START = "pomodoro_start";
	public static final String ACTION_POMODORO_STOP = "pomodoro_stop";

	// Extras
	public static final String NODE_ID = "node_id";
	public static final String POMODORO_DURATION = "pomodoro_duration";
	public static final String CLOCK_DURATION = "clock_duration";
	public static final String ACTION_ALARM_DISMISS = "alarm_dismiss";
	public static final String BROADCAST_STATE_CHANGED = "com.matburt.mobileorg.TIMECLOCK_STATE_CHANGED";
	private static final String CHANNEL_ID = "mobileorg_timeclock";
	private static final String TIMEOUT_CHANNEL_ID = "mobileorg_timeclock_alarm";
	private static final int TIMEOUT_NOTIFICATION_ID = 1338;
	private static final long UPDATE_INTERVAL_MS = 60L * 1000L; // 1 minute

	private final int notificationID = 1337;
	private NotificationManager mNM;
	private Notification notification;
	private MediaPlayer alarmMediaPlayer;

	private long node_id;
	private OrgNode node;
	private MobileOrgApplication appInst;

	private static TimeclockService sInstance;
	private long pomodoroStartTime;
	private long clockStartTime;
	private boolean pomodoroRunning = false;
	private boolean pomodoroTimedOut = false;
	private boolean clockedIn = false;
	private int pomodoroDurationMins = 25;

	// Handler-based timing (replaces AlarmManager)
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
		this.pomodoroDurationMins = duration;
		this.pomodoroStartTime = System.currentTimeMillis();
		this.pomodoroRunning = true;
		this.pomodoroTimedOut = false;

		// Cancel any previous timers
		cancelTimers();

		// Schedule periodic notification updates (every minute)
		updateRunnable = new Runnable() {
			@Override
			public void run() {
				if (!pomodoroRunning && !clockedIn) return;
				updateTime();
				timerHandler.postDelayed(this, UPDATE_INTERVAL_MS);
			}
		};
		timerHandler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);

		// Schedule exact timeout via Handler
		final long timeoutDelay = duration * 60L * 1000L;
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
		if (clockedIn) {
			doClockOut(true, -1);
		}
		try {
			this.node_id = newNodeId;
			this.node = new OrgNodeRepository(getContentResolver()).getById(node_id);
		} catch (OrgNodeNotFoundException e) {
			Log.e("MobileOrg", "[ClockIn] Node not found: " + newNodeId, e);
			return;
		}
		this.clockStartTime = System.currentTimeMillis();
		this.clockedIn = true;

		// Start periodic updates if not already running
		startUpdateIfNeeded();

		showOrRefreshNotification();
	}

	public void doClockOut(boolean save, int editedDurationMinutes) {
		if (!clockedIn) return;
		if (save && node != null) {
			long endTime = System.currentTimeMillis();
			long startTime;
			String elapsedTime;
			if (editedDurationMinutes > 0) {
				long durationMillis = editedDurationMinutes * 60L * 1000L;
				startTime = endTime - durationMillis;
				int h = editedDurationMinutes / 60;
				int m = editedDurationMinutes % 60;
				elapsedTime = String.format("%d:%02d", h, m);
			} else {
				startTime = clockStartTime;
				long diff = endTime - clockStartTime;
				elapsedTime = formatMillisAsTime(diff);
			}
			new OrgNodeRepository(getContentResolver()).addLogbook(node, startTime, endTime, elapsedTime);
		}
		this.node_id = -1;
		this.node = null;
		this.clockStartTime = 0;
		this.clockedIn = false;
		notifyStateChanged();
		checkStopSelf();
		showOrRefreshNotification();
	}

	private void handlePomodoroStop() {
		stopAndReleaseAlarmSound();
		Log.d("MobileOrg", "[Pomodoro] Stopping, cancelling timeout notification");
		cancelTimers();
		this.pomodoroRunning = false;
		this.pomodoroTimedOut = false;
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
		notifyStateChanged();
		checkStopSelf();
		showOrRefreshNotification();
	}

	private void handlePomodoroTimeout() {
		if (!pomodoroRunning) {
			Log.w("MobileOrg", "[Pomodoro] Timeout received but pomodoro not running, ignoring");
			return;
		}
		this.pomodoroTimedOut = true;
		writePomodoroSession();
		Log.d("MobileOrg", "[Pomodoro] Timeout! duration=" + pomodoroDurationMins + "min, sending alert notification on channel " + TIMEOUT_CHANNEL_ID);

		// Create HIGH importance channel with no sound (MediaPlayer handles audio via alarm stream)
		if (Compat.isAtLeastO()) {
			NotificationChannel timeoutChannel = new NotificationChannel(
					TIMEOUT_CHANNEL_ID, "Pomodoro Timer Alert", NotificationManager.IMPORTANCE_HIGH);
			timeoutChannel.setDescription("Alerts when pomodoro timer completes");
			timeoutChannel.setSound(null, null);
			timeoutChannel.enableVibration(true);
			mNM.createNotificationChannel(timeoutChannel);
		}

		// Build a separate timeout notification (not the foreground notification)
		PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
				new Intent(this, TimeclockDialog.class), Compat.FLAG_IMMUTABLE);

		NotificationCompat.Builder timeoutBuilder = new NotificationCompat.Builder(this, TIMEOUT_CHANNEL_ID)
				.setSmallIcon(R.drawable.timeclock_icon)
				.setContentTitle("\uD83C\uDF45 番茄钟时间到！")
				.setContentText(pomodoroDurationMins + " 分钟番茄钟已完成")
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

		// Update foreground notification to show overtime status
		updateTime();

		stopAndReleaseAlarmSound();
		alarmMediaPlayer = Compat.playAlarmSound(this);
	}

	private void handleAlarmDismiss() {
		stopAndReleaseAlarmSound();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
	}

	private void writePomodoroSession() {
		SQLiteDatabase db = new OrgDatabase(this).getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("started_at", pomodoroStartTime);
		values.put("duration_min", pomodoroDurationMins);
		values.put("completed_at", System.currentTimeMillis());
		db.insert(OrgDatabase.Tables.POMODORO_SESSIONS, null, values);
		db.close();
	}

	/**
	 * Start periodic notification updates if not already running.
	 * Called from handleClockIn when there's no pomodoro running the update loop.
	 */
	private void startUpdateIfNeeded() {
		if (updateRunnable != null) return; // Already running (pomodoro or previous clock-in)
		updateRunnable = new Runnable() {
			@Override
			public void run() {
				if (!pomodoroRunning && !clockedIn) return;
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
		if (!pomodoroRunning && !clockedIn) {
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

		// Build title based on state
		String title;
		if (pomodoroRunning && clockedIn && node != null) {
			title = "\uD83C\uDF45 " + formatMillisAsTime((pomodoroDurationMins * 60L * 1000L) - (System.currentTimeMillis() - pomodoroStartTime)) + " | " + node.name;
		} else if (pomodoroRunning) {
			title = "\uD83C\uDF45 " + formatMillisAsTime((pomodoroDurationMins * 60L * 1000L) - (System.currentTimeMillis() - pomodoroStartTime));
		} else if (clockedIn && node != null) {
			title = node.name;
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

		// Add Stop button if pomodoro is running
		if (pomodoroRunning) {
			Intent stopIntent = new Intent(this, TimeclockService.class);
			stopIntent.setAction(ACTION_POMODORO_STOP);
			PendingIntent stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, Compat.FLAG_IMMUTABLE);
			NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(
					R.drawable.ic_media_stop, "Stop", stopPendingIntent).build();
			builder.addAction(stopAction);
			this.notification = builder.build();
			// builder.build() creates a new notification without our custom contentView;
			// re-attach it so updateTime() can use notification.contentView
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
		long now = System.currentTimeMillis();

		if (pomodoroRunning) {
			if (!pomodoroTimedOut) {
				long remaining = (pomodoroDurationMins * 60L * 1000L) - (now - pomodoroStartTime);
				if (remaining < 0) remaining = 0;
				String remainingStr = formatMillisAsTime(remaining);
				itemText = new SpannableStringBuilder(remainingStr);
				titleText = buildPomodoroTitle(remainingStr);
			} else {
				long overtime = now - (pomodoroStartTime + pomodoroDurationMins * 60L * 1000L);
				String overtimeStr = "+" + formatMillisAsTime(overtime);
				itemText = new SpannableStringBuilder(overtimeStr);
				itemText.setSpan(new ForegroundColorSpan(Color.RED), 0,
						itemText.length(), 0);
				titleText = buildPomodoroTitle(overtimeStr);
			}
		} else if (clockedIn) {
			long elapsed = now - clockStartTime;
			itemText = new SpannableStringBuilder(formatMillisAsTime(elapsed));
			titleText = (node != null) ? node.name : "Timeclock";
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

	private String buildPomodoroTitle(String timeStr) {
		if (clockedIn && node != null) {
			return "\uD83C\uDF45 " + timeStr + " | " + node.name;
		}
		return "\uD83C\uDF45 " + timeStr;
	}

	private String formatMillisAsTime(long millis) {
		long totalMinutes = millis / (60 * 1000);
		long hours = totalMinutes / 60;
		long minutes = totalMinutes % 60;
		return String.format("%d:%02d", hours, minutes);
	}

	// Public query methods
	public boolean isPomodoroRunning() { return pomodoroRunning; }
	public boolean isClockedIn() { return clockedIn; }
	public OrgNode getClockNode() { return node; }
	public boolean isPomodoroTimedOut() { return pomodoroTimedOut; }

	public String getClockElapsedString() {
		if (!clockedIn) return "0:00";
		return formatMillisAsTime(System.currentTimeMillis() - clockStartTime);
	}

	public String getPomodoroRemainingString() {
		if (!pomodoroRunning) return "";
		if (pomodoroTimedOut) {
			long overtime = System.currentTimeMillis() - (pomodoroStartTime + pomodoroDurationMins * 60L * 1000L);
			return "+" + formatMillisAsTime(overtime);
		}
		long remaining = (pomodoroDurationMins * 60L * 1000L) - (System.currentTimeMillis() - pomodoroStartTime);
		return formatMillisAsTime(Math.max(0, remaining));
	}

	public long getClockStartTime() { return clockStartTime; }
	public long getNodeID() { return node_id; }

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
