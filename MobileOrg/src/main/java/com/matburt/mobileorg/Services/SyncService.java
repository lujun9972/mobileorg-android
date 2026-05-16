package com.matburt.mobileorg.Services;

import java.util.ArrayList;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.SynchronizerNotification;
import com.matburt.mobileorg.Gui.SynchronizerNotificationCompat;
import com.matburt.mobileorg.OrgData.MobileOrgApplication;
import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgFileParser;
import com.matburt.mobileorg.Synchronizers.NullSynchronizer;
import com.matburt.mobileorg.Synchronizers.SDCardSynchronizer;
import com.matburt.mobileorg.Synchronizers.SSHSynchronizer;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.Synchronizers.SynchronizerInterface;
import com.matburt.mobileorg.Synchronizers.WebDAVSynchronizer;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.ReminderScheduler;
import android.util.Log;

public class SyncService extends Service implements
		SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String ACTION = "action";
	private static final String START_ALARM = "START_ALARM";
	private static final String STOP_ALARM = "STOP_ALARM";
	private static final String CHANNEL_ID = SynchronizerNotificationCompat.CHANNEL_ID;
	private static final int FOREGROUND_NOTIFY_ID = 1;

	private SharedPreferences appSettings;
	private MobileOrgApplication appInst;

	private AlarmManager alarmManager;
	private PendingIntent alarmIntent;
	private boolean alarmScheduled = false;

	private boolean syncRunning;

	@Override
	public void onCreate() {
		super.onCreate();
		this.appSettings = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		this.appSettings.registerOnSharedPreferenceChangeListener(this);
		this.appInst = (MobileOrgApplication) this.getApplication();
		this.alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	}

	@Override
	public void onDestroy() {
		unsetAlarm();
		this.appSettings.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	public static void stopAlarm(Context context) {
		Intent intent = new Intent(context, SyncService.class);
		intent.putExtra(ACTION, SyncService.STOP_ALARM);
		Compat.startService(context, intent);
	}

	public static void startAlarm(Context context) {
		Intent intent = new Intent(context, SyncService.class);
		intent.putExtra(ACTION, SyncService.START_ALARM);
		Compat.startService(context, intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null) {
			stopSelf();
			return START_NOT_STICKY;
		}

		String action = intent.getStringExtra(ACTION);

		if (Compat.isAtLeastO()) {
			Compat.createNotificationChannel(this, CHANNEL_ID, "MobileOrg Sync");
			if (Build.VERSION.SDK_INT >= 34) {
				startForeground(FOREGROUND_NOTIFY_ID, createForegroundNotification(),
						android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
			} else {
				startForeground(FOREGROUND_NOTIFY_ID, createForegroundNotification());
			}
		}

		if (action != null && action.equals(START_ALARM)) {
			setAlarm();
			stopForegroundAndSelf();
		} else if (action != null && action.equals(STOP_ALARM)) {
			unsetAlarm();
			stopForegroundAndSelf();
		} else if(!this.syncRunning) {
			this.syncRunning = true;
			runSynchronizer();
		} else {
			// sync already running, just return
		}
		return START_NOT_STICKY;
	}

	private Notification createForegroundNotification() {
		return new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.icon)
				.setContentTitle(getString(R.string.sync_synchronizing_changes))
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.build();
	}

	private void stopForegroundAndSelf() {
		if (Compat.isAtLeastO()) {
			stopForeground(true);
		}
		stopSelf();
	}

    public Synchronizer getSynchronizer() {
        SynchronizerInterface synchronizer = null;
		String syncSource = appSettings.getString("syncSource", "");
		Context c = getApplicationContext();

		if (syncSource.equals("webdav"))
			synchronizer =new WebDAVSynchronizer(c);
		else if (syncSource.equals("sdcard"))
			synchronizer = new SDCardSynchronizer(c);
		else if (syncSource.equals("scp"))
			synchronizer = new SSHSynchronizer(c);
        else if (syncSource.equals("null"))
            synchronizer = new NullSynchronizer();
		else
			synchronizer = null;

		SynchronizerNotificationCompat notification;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
			notification = new SynchronizerNotification(this);
		else
			notification = new SynchronizerNotificationCompat(this);

		return new Synchronizer(c, synchronizer, notification);
    }

	private void runSynchronizer() {
		unsetAlarm();
		final Synchronizer synchronizer = this.getSynchronizer();
		final OrgDatabase db = new OrgDatabase(this);
		final OrgFileParser parser = new OrgFileParser(db, getContentResolver());
		final boolean calendarEnabled = appSettings.getBoolean("calendarEnabled", false);

		Thread syncThread = new Thread() {
			public void run() {
				try {
					ArrayList<String> changedFiles = synchronizer.runSynchronizer(parser);
					String[] files = changedFiles.toArray(new String[changedFiles.size()]);

					if(calendarEnabled) {
						Intent calIntent = new Intent(getBaseContext(), CalendarSyncService.class);
						calIntent.putExtra(CalendarSyncService.PUSH, true);
						calIntent.putExtra(CalendarSyncService.FILELIST, files);
						getBaseContext().startService(calIntent);
					}
					synchronizer.close();
					db.close();
				} finally {
					syncRunning = false;

					// Schedule reminder alarms after sync
					try {
						ReminderScheduler.scheduleAll(getContentResolver(), getBaseContext());
					} catch (Exception e) {
						Log.w("MobileOrg", "ReminderScheduler failed: " + e);
					}

					setAlarm();

					if (Compat.isAtLeastO()) {
						stopForeground(true);
					}
					stopSelf();
				}
			}
		};

		syncThread.start();
	}


	private void setAlarm() {
		boolean doAutoSync = this.appSettings.getBoolean("doAutoSync", false);
		if (!this.alarmScheduled && doAutoSync) {

			int interval = Integer.parseInt(
					this.appSettings.getString("autoSyncInterval", "1800000"),
					10);

			Intent intent = new Intent(this, SyncService.class);
			this.alarmIntent = Compat.getServicePendingIntent(appInst, 0,
					intent, Compat.FLAG_IMMUTABLE);
			alarmManager.setRepeating(AlarmManager.RTC,
					System.currentTimeMillis() + interval, interval,
					alarmIntent);

			this.alarmScheduled = true;
		}
	}

	private void unsetAlarm() {
		if (this.alarmScheduled) {
			this.alarmManager.cancel(this.alarmIntent);
			this.alarmScheduled = false;
		}
	}

	private void resetAlarm() {
		unsetAlarm();
		setAlarm();
	}


	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("doAutoSync")) {
			if (sharedPreferences.getBoolean("doAutoSync", false)
					&& !this.alarmScheduled)
				setAlarm();
			else if (!sharedPreferences.getBoolean("doAutoSync", false)
					&& this.alarmScheduled)
				unsetAlarm();
		} else if (key.equals("autoSyncInterval"))
			resetAlarm();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
