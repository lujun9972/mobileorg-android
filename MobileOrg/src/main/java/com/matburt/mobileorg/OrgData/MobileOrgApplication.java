package com.matburt.mobileorg.OrgData;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;
import com.matburt.mobileorg.Services.SyncService;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MobileOrgApplication extends Application {

	private static MobileOrgApplication instance;

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		MultiDex.install(this);
	}

	@Override
	public void onCreate() {
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			private final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				log("UNCAUGHT EXCEPTION: " + Log.getStackTraceString(e));
				if (defaultHandler != null)
					defaultHandler.uncaughtException(t, e);
			}
		});
		instance = this;
		log("MobileOrgApplication.onCreate() start");
		try {
			SyncService.startAlarm(getApplicationContext());
			log("MobileOrgApplication.onCreate() SyncService.startAlarm done");
		} catch (Exception e) {
			log("MobileOrgApplication.onCreate() ERROR: " + e);
		}
		log("MobileOrgApplication.onCreate() complete");
	}

	public static Context getContext() {
		return instance;
	}

	public static void log(String msg) {
		String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
		String line = timestamp + " " + msg;
		Log.i("MobileOrg", line);
		try {
			File dir = instance.getExternalFilesDir(null);
			if (dir == null) dir = instance.getFilesDir();
			File logFile = new File(dir, "mobileorg_debug.log");
			PrintWriter writer = new PrintWriter(new FileWriter(logFile, true));
			writer.println(line);
			writer.close();
		} catch (Exception e) {
			Log.e("MobileOrg", "Failed to write debug log: " + e.toString());
		}
	}
}
