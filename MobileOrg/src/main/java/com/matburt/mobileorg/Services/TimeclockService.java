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
import android.os.SystemClock;
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
import com.matburt.mobileorg.util.PreferenceUtils;

public class TimeclockService extends Service {
	public static final String ACTION_CLOCK_IN = "clock_in";
	public static final String ACTION_CLOCK_OUT = "clock_out";
	public static final String ACTION_CLOCK_CANCEL = "clock_cancel";
	public static final String ACTION_POMODORO_START = "pomodoro_start";
	public static final String ACTION_POMODORO_STOP = "pomodoro_stop";
	public static final String ACTION_POMODORO_FINISH = "pomodoro_finish";
	public static final String ACTION_POMODORO_NEXT = "pomodoro_next";
	public static final String ACTION_POMODORO_SKIP_REST = "pomodoro_skip_rest";

	public static final String NODE_ID = "node_id";
	public static final String POMODORO_DURATION = "pomodoro_duration";
	public static final String POMODORO_COUNT = "pomodoro_count";
	public static final String CLOCK_DURATION = "clock_duration";
	public static final String ACTION_ALARM_DISMISS = "alarm_dismiss";
	public static final String BROADCAST_STATE_CHANGED = "com.matburt.mobileorg.TIMECLOCK_STATE_CHANGED";
	private static final String CHANNEL_ID = "mobileorg_timeclock";
	private static final String TIMEOUT_CHANNEL_ID = "mobileorg_timeclock_alarm";
	private static final String REST_CHANNEL_ID = "mobileorg_timeclock_rest";
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
	private Runnable restTimeoutRunnable;
	// Diagnostic: records when timeout was scheduled, to measure actual fire delay.
	// Used to diagnose Handler delays on aggressive vendor ROMs (MIUI/EMUI/ColorOS).
	private long timeoutScheduledAtElapsed;
	private long timeoutTargetElapsed;

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
		diagLog("onCreate", null);
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
		diagLog("onStartCommand", "action=" + action + " flags=" + flags + " startId=" + startId);

		switch (action) {
			case ACTION_POMODORO_START:
				handlePomodoroStart(intent);
				break;
			case ACTION_CLOCK_IN:
				handleClockIn(intent);
				break;
			case ACTION_POMODORO_STOP:
				handlePomodoroCancel();
				break;
			case ACTION_POMODORO_FINISH:
				handlePomodoroFinish();
				break;
			case ACTION_POMODORO_NEXT:
				handlePomodoroNext();
				break;
			case ACTION_POMODORO_SKIP_REST:
				handlePomodoroSkipRest();
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

	/**
	 * Diagnostic log for pomodoro timing issues. Prints both elapsedRealtime
	 * (deep-sleep aware, accurate for measuring delays) and currentTimeMillis
	 * (wall clock, user-correlatable). Grep with: adb logcat -s MobileOrg:V | grep PomodoroDiag
	 */
	private void diagLog(String event, String detail) {
		Log.d("MobileOrg", "[PomodoroDiag] event=" + event
				+ " elapsed=" + SystemClock.elapsedRealtime()
				+ " now=" + System.currentTimeMillis()
				+ " state=" + pomodoroTimer.getState()
				+ " timedOut=" + pomodoroTimer.isTimedOut()
				+ " running=" + pomodoroTimer.isRunning()
				+ (detail != null && !detail.isEmpty() ? " " + detail : ""));
	}

	// ========== Pomodoro handlers ==========

	/**
	 * Schedule the one-shot work-phase timeout and record diagnostic timing.
	 * Shared by handlePomodoroStart (round 1) and handlePomodoroNext (round N+1).
	 */
	private void scheduleWorkTimeout() {
		final long timeoutDelay = pomodoroTimer.getRemainingMillis();
		timeoutScheduledAtElapsed = SystemClock.elapsedRealtime();
		timeoutTargetElapsed = timeoutScheduledAtElapsed + timeoutDelay;
		timeoutRunnable = new Runnable() {
			@Override
			public void run() {
				timeoutRunnable = null;
				long fireElapsed = SystemClock.elapsedRealtime();
				diagLog("timeoutRunnableFired", "fireElapsed=" + fireElapsed
						+ " delayMs=" + (fireElapsed - timeoutTargetElapsed));
				handlePomodoroTimeout();
			}
		};
		timerHandler.postDelayed(timeoutRunnable, timeoutDelay);
		diagLog("scheduleTimeout", "delay=" + timeoutDelay
				+ " targetElapsed=" + timeoutTargetElapsed
				+ " round=" + pomodoroTimer.getCurrentRound() + "/" + pomodoroTimer.getTotalCount());
	}

	private void handlePomodoroStart(Intent intent) {
		int duration = intent.getIntExtra(POMODORO_DURATION, 25);
		int count = intent.getIntExtra(POMODORO_COUNT, 1);
		pomodoroTimer.start(duration, count);
		cancelTimers();

		updateRunnable = new Runnable() {
			@Override
			public void run() {
				if (!pomodoroTimer.isActive() && !clockTimer.isClockedIn()) return;
				diagLog("updateTick", "remaining=" + pomodoroTimer.getRemainingMillis());
				checkTimeoutFallback();
				updateTime();
				timerHandler.postDelayed(this, UPDATE_INTERVAL_MS);
			}
		};
		timerHandler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);

		scheduleWorkTimeout();

		Log.d("MobileOrg", "[Pomodoro] Started: " + duration + "min × " + count);
		showOrRefreshNotification();
	}

	/**
	 * Defensive timeout fallback: if the one-shot timeoutRunnable was delayed
	 * by Doze/ROM power management (observed 25-min delay on MIUI/xaga), detect
	 * the timeout from the periodic updateTick instead. Idempotent — safe every
	 * tick; handlePomodoroTimeout guards on isRunning() && !isTimedOut().
	 */
	private void checkTimeoutFallback() {
		// WORK phase: one-shot timeoutRunnable may be delayed by Doze/ROM
		if (pomodoroTimer.isRunning() && !pomodoroTimer.isTimedOut()
				&& pomodoroTimer.getRemainingMillis() <= 0) {
			diagLog("timeoutFallbackFired",
					"remaining=" + pomodoroTimer.getRemainingMillis()
							+ " (timeoutRunnable delayed by Doze)");
			handlePomodoroTimeout();
			return;
		}
		// REST phase: one-shot restTimeoutRunnable has the same Doze exposure.
		// handleRestTimeout is idempotent via state transition (REST→WAITING_NEXT).
		if (pomodoroTimer.isResting()
				&& pomodoroTimer.getRestRemainingMillis() <= 0) {
			diagLog("restTimeoutFallbackFired",
					"remaining=" + pomodoroTimer.getRestRemainingMillis());
			handleRestTimeout();
		}
	}

	private void handlePomodoroTimeout() {
		diagLog("handlePomodoroTimeout_enter", null);
		// Idempotent guard: markTimeout sets timedOut=true but keeps running=true,
		// so a delayed timeoutRunnable firing after a fallback checkTimeoutFallback
		// would re-enter here without the isTimedOut() check. Block re-entry.
		if (!pomodoroTimer.isRunning() || pomodoroTimer.isTimedOut()) {
			Log.w("MobileOrg", "[Pomodoro] Timeout ignored: not running or already timed out");
			diagLog("handlePomodoroTimeout_ignored",
					"reason=" + (!pomodoroTimer.isRunning() ? "notRunning" : "alreadyTimedOut"));
			return;
		}
		pomodoroTimer.markTimeout();
		// NOTE: recordPomodoroSession moved to handlePomodoroFinish. Timeout only
		// marks completion of the countdown; the session is counted only after the
		// user explicitly confirms via Finish. This lets the user Cancel (discard)
		// an unsatisfying pomodoro even after timeout, without polluting stats.
		Log.d("MobileOrg", "[Pomodoro] Timeout! round=" + pomodoroTimer.getCurrentRound() + "/" + pomodoroTimer.getTotalCount() + ", duration=" + pomodoroTimer.getDurationMinutes() + "min");

		// Show timeout alert notification (same as before)
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

		updateTime();

		stopAndReleaseAlarmSound();
		alarmMediaPlayer = Compat.playAlarmSound(this);

		// Update foreground notification to show Finish button
		showOrRefreshNotification();
	}

	private void handlePomodoroFinish() {
		// Idempotent guard: only the WORK(timedOut) state can be finished.
		// Prevents duplicate recordPomodoroSession writes on rapid double-tap of
		// the Finish button or PendingIntent dispatch races. markTimeout sets
		// timedOut=true; startRest/stop reset it to false, so the second call returns.
		if (!pomodoroTimer.isTimedOut()) {
			diagLog("handlePomodoroFinish_blocked", "reason=notTimedOut");
			return;
		}
		// Record session BEFORE stop()/startRest() — stop() resets startTime,
		// so we must capture it while still in WORK(timedOut) state.
		repo.recordPomodoroSession(pomodoroTimer.getStartTime(), pomodoroTimer.getDurationMinutes());
		diagLog("recordPomodoroSession", "round=" + pomodoroTimer.getCurrentRound()
				+ "/" + pomodoroTimer.getTotalCount()
				+ " duration=" + pomodoroTimer.getDurationMinutes() + "min");

		stopAndReleaseAlarmSound();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);

		int currentRound = pomodoroTimer.getCurrentRound();
		int totalCount = pomodoroTimer.getTotalCount();

		if (currentRound >= totalCount) {
			// Last round — all done
			Log.d("MobileOrg", "[Pomodoro] All " + totalCount + " pomodoros completed!");
			pomodoroTimer.stop();
			showAllDoneNotification(totalCount);
			notifyStateChanged();
			checkStopSelf();
		} else {
			// Not last round — enter REST
			int restDuration = calculateRestDuration(currentRound);
			Log.d("MobileOrg", "[Pomodoro] Finish round " + currentRound + "/" + totalCount + ", rest=" + restDuration + "min");

			if (restDuration > 0) {
				pomodoroTimer.startRest(restDuration);
				scheduleRestTimeout(restDuration);
			} else {
				// rest duration = 0, skip countdown but still need confirmation
				pomodoroTimer.startRest(0);
				// Immediately transition to WAITING_NEXT since rest is 0
				pomodoroTimer.setWaitingNext();
			}

			notifyStateChanged();
			showOrRefreshNotification();
		}
	}

	private void handleRestTimeout() {
		restTimeoutRunnable = null;
		if (!pomodoroTimer.isResting()) return;

		pomodoroTimer.setWaitingNext();
		Log.d("MobileOrg", "[Pomodoro] Rest ended, waiting for user to start next");

		// Send notification with sound + vibration (separate channel from work timeout,
		// which uses setSound(null,null) for MediaPlayer-based alarm)
		if (Compat.isAtLeastO()) {
			NotificationChannel restChannel = new NotificationChannel(
					REST_CHANNEL_ID, "Pomodoro Rest Alert", NotificationManager.IMPORTANCE_HIGH);
			restChannel.setDescription("Alerts when pomodoro rest period ends");
			restChannel.enableVibration(true);
			mNM.createNotificationChannel(restChannel);
		}

		PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
				new Intent(this, TimeclockDialog.class), Compat.FLAG_IMMUTABLE);

		NotificationCompat.Builder restBuilder = new NotificationCompat.Builder(this, REST_CHANNEL_ID)
				.setSmallIcon(R.drawable.timeclock_icon)
				.setContentTitle("\u2615 休息结束")
				.setContentText("准备开始下一个番茄钟")
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setAutoCancel(true)
				.setContentIntent(contentIntent)
				.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

		mNM.notify(TIMEOUT_NOTIFICATION_ID, restBuilder.build());

		notifyStateChanged();
		showOrRefreshNotification();
	}

	private void handlePomodoroNext() {
		stopAndReleaseAlarmSound();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
		int duration = pomodoroTimer.getDurationMinutes();
		pomodoroTimer.advanceToNextWork(duration);

		cancelTimersExceptUpdate();

		// Schedule new work timeout
		scheduleWorkTimeout();

		startUpdateIfNeeded();
		Log.d("MobileOrg", "[Pomodoro] Next round: " + pomodoroTimer.getCurrentRound() + "/" + pomodoroTimer.getTotalCount());
		notifyStateChanged();
		showOrRefreshNotification();
	}

	private void handlePomodoroSkipRest() {
		if (restTimeoutRunnable != null) {
			timerHandler.removeCallbacks(restTimeoutRunnable);
			restTimeoutRunnable = null;
		}
		Log.d("MobileOrg", "[Pomodoro] Skipping rest, starting next round");
		handlePomodoroNext();
	}

	private void handlePomodoroCancel() {
		stopAndReleaseAlarmSound();
		cancelTimers();
		pomodoroTimer.stop();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
		notifyStateChanged();
		checkStopSelf();
		showOrRefreshNotification();
	}

	private int calculateRestDuration(int completedRound) {
		int interval = PreferenceUtils.getPomodoroLongBreakInterval();
		if (completedRound > 0 && completedRound % interval == 0) {
			return PreferenceUtils.getPomodoroLongBreak();
		}
		return PreferenceUtils.getPomodoroShortBreak();
	}

	private void scheduleRestTimeout(int restDurationMinutes) {
		final long delay = restDurationMinutes * 60L * 1000L;
		restTimeoutRunnable = new Runnable() {
			@Override
			public void run() {
				handleRestTimeout();
			}
		};
		timerHandler.postDelayed(restTimeoutRunnable, delay);
		Log.d("MobileOrg", "[Pomodoro] Rest timeout scheduled in " + delay + "ms");
	}

	private void showAllDoneNotification(int totalCount) {
		// Calculate total minutes from all recorded sessions (approximation: use duration × count)
		int totalMinutes = pomodoroTimer.getDurationMinutes() * totalCount;

		PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
				new Intent(this, TimeclockDialog.class), Compat.FLAG_IMMUTABLE);

		NotificationCompat.Builder doneBuilder = new NotificationCompat.Builder(this, TIMEOUT_CHANNEL_ID)
				.setSmallIcon(R.drawable.timeclock_icon)
				.setContentTitle("\uD83C\uDF89 " + totalCount + "/" + totalCount + " 完成！")
				.setContentText("总计 " + totalMinutes + " 分钟")
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setAutoCancel(true)
				.setContentIntent(contentIntent);

		mNM.notify(TIMEOUT_NOTIFICATION_ID, doneBuilder.build());
	}

	// ========== Clock handlers ==========

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

	// ========== Timer management ==========

	private void startUpdateIfNeeded() {
		if (updateRunnable != null) return;
		updateRunnable = new Runnable() {
			@Override
			public void run() {
				if (!pomodoroTimer.isActive() && !clockTimer.isClockedIn()) return;
				updateTime();
				timerHandler.postDelayed(this, UPDATE_INTERVAL_MS);
			}
		};
		timerHandler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS);
	}

	private void cancelTimers() {
		diagLog("cancelTimers", null);
		if (updateRunnable != null) {
			timerHandler.removeCallbacks(updateRunnable);
			updateRunnable = null;
		}
		if (timeoutRunnable != null) {
			timerHandler.removeCallbacks(timeoutRunnable);
			timeoutRunnable = null;
		}
		if (restTimeoutRunnable != null) {
			timerHandler.removeCallbacks(restTimeoutRunnable);
			restTimeoutRunnable = null;
		}
	}

	private void cancelTimersExceptUpdate() {
		diagLog("cancelTimersExceptUpdate", null);
		if (timeoutRunnable != null) {
			timerHandler.removeCallbacks(timeoutRunnable);
			timeoutRunnable = null;
		}
		if (restTimeoutRunnable != null) {
			timerHandler.removeCallbacks(restTimeoutRunnable);
			restTimeoutRunnable = null;
		}
	}

	private void checkStopSelf() {
		if (!pomodoroTimer.isActive() && !clockTimer.isClockedIn()) {
			cancelNotification();
		}
	}

	// ========== Notification ==========

	private void showOrRefreshNotification() {
		Compat.createNotificationChannel(this, CHANNEL_ID, "MobileOrg Timeclock");

		PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
				new Intent(this, TimeclockDialog.class), Compat.FLAG_IMMUTABLE);

		Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
		builder.setSmallIcon(R.drawable.timeclock_icon);
		builder.setOngoing(true);
		builder.setContentIntent(contentIntent);

		PomodoroTimer.PomodoroState pomoState = pomodoroTimer.getState();
		boolean pomoTimedOut = pomodoroTimer.isTimedOut();
		boolean clockRunning = clockTimer.isClockedIn();
		String progress = pomodoroTimer.getRoundProgress();

		String title;
		if (pomoState == PomodoroTimer.PomodoroState.WORK && clockRunning && clockNode != null) {
			// Pomodoro + clock combined (legacy — pomodoro doesn't show node name in consecutive mode, but keep compat)
			title = pomodoroTimer.getTitleString(clockNode.name);
		} else if (pomoState == PomodoroTimer.PomodoroState.WORK) {
			title = buildWorkTitle();
		} else if (pomoState == PomodoroTimer.PomodoroState.REST) {
			title = buildRestTitle();
		} else if (pomoState == PomodoroTimer.PomodoroState.WAITING_NEXT) {
			title = buildWaitingTitle();
		} else if (clockRunning && clockNode != null) {
			title = clockNode.name;
		} else {
			title = "Timeclock";
		}
		builder.setContentTitle(title);

		// Add action buttons based on state
		if (pomoState == PomodoroTimer.PomodoroState.WORK && !pomoTimedOut) {
			// WORK countdown: Cancel button
			addCancelAction(builder);
		} else if (pomoState == PomodoroTimer.PomodoroState.WORK && pomoTimedOut) {
			// WORK timed out: Finish (count this pomodoro) + Cancel (discard, don't count)
			addFinishAction(builder);
			addCancelAction(builder);
		} else if (pomoState == PomodoroTimer.PomodoroState.REST) {
			// REST: Skip rest + Cancel
			addSkipRestAction(builder);
			addCancelAction(builder);
		} else if (pomoState == PomodoroTimer.PomodoroState.WAITING_NEXT) {
			// WAITING_NEXT: Start next + Cancel
			addStartNextAction(builder);
			addCancelAction(builder);
		}

		this.notification = builder.build();

		notification.contentView = new RemoteViews(this.getPackageName(),
				R.layout.timeclock_notification);
		notification.contentView.setImageViewResource(R.id.timeclock_notification_icon,
				R.drawable.timeclock_icon);
		notification.contentView.setTextViewText(R.id.timeclock_notification_text, title);

		updateTime();

		if (Compat.isAtLeastO()) {
			Compat.startForeground(this, notificationID, notification,
					android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		} else {
			mNM.notify(notificationID, notification);
		}
	}

	private String buildWorkTitle() {
		String timeStr = pomodoroTimer.getRemainingString();
		String progress = pomodoroTimer.getRoundProgress();
		StringBuilder sb = new StringBuilder("\uD83C\uDF45 ");
		sb.append(timeStr);
		if (!progress.isEmpty()) {
			sb.append(" | ").append(progress);
		}
		if (pomodoroTimer.isTimedOut()) {
			sb.append(" 完成");
		}
		return sb.toString();
	}

	private String buildRestTitle() {
		String restTime = pomodoroTimer.getRestRemainingString();
		String progress = pomodoroTimer.getRoundProgress();
		return "\u2615 休息 " + restTime + " | " + progress + " 完成";
	}

	private String buildWaitingTitle() {
		String progress = pomodoroTimer.getRoundProgress();
		return "\u25B6 准备下一个 | " + progress + " 完成";
	}

	private void addCancelAction(Builder builder) {
		Intent cancelIntent = new Intent(this, TimeclockService.class);
		cancelIntent.setAction(ACTION_POMODORO_STOP);
		PendingIntent cancelPI = PendingIntent.getService(this, 3, cancelIntent, Compat.FLAG_IMMUTABLE);
		builder.addAction(new NotificationCompat.Action.Builder(
				R.drawable.ic_media_stop, "Cancel", cancelPI).build());
	}

	private void addFinishAction(Builder builder) {
		Intent finishIntent = new Intent(this, TimeclockService.class);
		finishIntent.setAction(ACTION_POMODORO_FINISH);
		PendingIntent finishPI = PendingIntent.getService(this, 5, finishIntent, Compat.FLAG_IMMUTABLE);
		builder.addAction(new NotificationCompat.Action.Builder(
				R.drawable.ic_menu_pomodoro, "Finish", finishPI).build());
	}

	private void addSkipRestAction(Builder builder) {
		Intent skipIntent = new Intent(this, TimeclockService.class);
		skipIntent.setAction(ACTION_POMODORO_SKIP_REST);
		PendingIntent skipPI = PendingIntent.getService(this, 6, skipIntent, Compat.FLAG_IMMUTABLE);
		builder.addAction(new NotificationCompat.Action.Builder(
				R.drawable.ic_media_stop, "跳过休息", skipPI).build());
	}

	private void addStartNextAction(Builder builder) {
		Intent nextIntent = new Intent(this, TimeclockService.class);
		nextIntent.setAction(ACTION_POMODORO_NEXT);
		PendingIntent nextPI = PendingIntent.getService(this, 7, nextIntent, Compat.FLAG_IMMUTABLE);
		builder.addAction(new NotificationCompat.Action.Builder(
				R.drawable.ic_menu_pomodoro, "开始下一个", nextPI).build());
	}

	private void updateTime() {
		if (notification == null || notification.contentView == null) return;

		SpannableStringBuilder itemText;
		String titleText;
		PomodoroTimer.PomodoroState pomoState = pomodoroTimer.getState();
		boolean clockRunning = clockTimer.isClockedIn();

		if (pomoState == PomodoroTimer.PomodoroState.WORK && clockRunning) {
			titleText = buildWorkTitle();
			itemText = new SpannableStringBuilder(clockTimer.getElapsedString());
		} else if (pomoState == PomodoroTimer.PomodoroState.WORK) {
			String remainingStr = pomodoroTimer.getRemainingString();
			itemText = new SpannableStringBuilder(remainingStr);
			titleText = buildWorkTitle();
			if (pomodoroTimer.isTimedOut()) {
				itemText.setSpan(new ForegroundColorSpan(Color.RED), 0,
						itemText.length(), 0);
			}
		} else if (pomoState == PomodoroTimer.PomodoroState.REST) {
			titleText = buildRestTitle();
			itemText = new SpannableStringBuilder(pomodoroTimer.getRestRemainingString());
		} else if (pomoState == PomodoroTimer.PomodoroState.WAITING_NEXT) {
			titleText = buildWaitingTitle();
			itemText = new SpannableStringBuilder("准备下一个");
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

	// ========== Alarm ==========

	private void handleAlarmDismiss() {
		stopAndReleaseAlarmSound();
		mNM.cancel(TIMEOUT_NOTIFICATION_ID);
	}

	// ========== Public state queries ==========

	public boolean isPomodoroRunning() { return pomodoroTimer.isRunning(); }
	public boolean isPomodoroActive() { return pomodoroTimer.isActive(); }
	public boolean isPomodoroTimedOut() { return pomodoroTimer.isTimedOut(); }
	public boolean isPomodoroResting() { return pomodoroTimer.isResting(); }
	public boolean isPomodoroWaitingNext() { return pomodoroTimer.isWaitingNext(); }
	public PomodoroTimer.PomodoroState getPomodoroState() { return pomodoroTimer.getState(); }
	public boolean isClockedIn() { return clockTimer.isClockedIn(); }
	public OrgNode getClockNode() { return clockNode; }

	public String getClockElapsedString() {
		return clockTimer.getElapsedString();
	}

	public String getPomodoroRemainingString() {
		return pomodoroTimer.getRemainingString();
	}

	public String getPomodoroRestRemainingString() {
		return pomodoroTimer.getRestRemainingString();
	}

	public String getPomodoroRoundProgress() {
		return pomodoroTimer.getRoundProgress();
	}

	public long getClockStartTime() { return clockTimer.isClockedIn() ? clockTimer.getStartTime() : 0; }
	public long getNodeID() { return clockTimer.isClockedIn() ? clockTimer.getNodeId() : -1; }

	// ========== Lifecycle ==========

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
