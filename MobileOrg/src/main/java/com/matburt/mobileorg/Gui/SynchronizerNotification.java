package com.matburt.mobileorg.Gui;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Outline.OutlineActivity;
import com.matburt.mobileorg.util.Compat;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class SynchronizerNotification extends SynchronizerNotificationCompat {
	private NotificationManager notificationManager;
	private Notification notification;
	private Notification.Builder builder;
	private int notifyRef = 1;
	private Context context;

	public SynchronizerNotification(Context context) {
		super(context);
		this.context = context;
	}

	@SuppressWarnings("deprecation")
	private Notification.Builder createBuilder() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			return new Notification.Builder(context, CHANNEL_ID);
		} else {
			return new Notification.Builder(context);
		}
	}

	@Override
	public void errorNotification(String errorMsg) {
		createNotificationChannel();
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent notifyIntent = new Intent(context, OutlineActivity.class);
		notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
		                      | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		notifyIntent.putExtra("ERROR_MESSAGE", errorMsg);
		notifyIntent.setAction("com.matburt.mobileorg.SYNC_FAILED");

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notifyIntent, Compat.FLAG_IMMUTABLE);

		builder = createBuilder();
		builder.setContentIntent(contentIntent);
		builder.setSmallIcon(R.drawable.icon);
		builder.setContentTitle(context.getString(R.string.sync_failed));
		builder.setContentText(errorMsg);
		notification = builder.build();
		notification.flags = Notification.FLAG_AUTO_CANCEL;

		notificationManager.notify(notifyRef, notification);
	}


	@Override
	public void setupNotification() {
		createNotificationChannel();
		this.notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent notifyIntent = new Intent(context, OutlineActivity.class);
		notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notifyIntent, Compat.FLAG_IMMUTABLE);

		builder = createBuilder();
		builder.setContentIntent(contentIntent);
		builder.setSmallIcon(R.drawable.icon);
		builder.setOngoing(true);
		builder.setContentTitle(context.getString(R.string.sync_synchronizing_changes));
		builder.setProgress(100, 0, true);
		notification = builder.build();

		notificationManager.notify(notifyRef, notification);
	}

	@Override
	public void updateNotification(String message) {
		if(notification == null)
			return;

		if(message != null) {
			notificationManager.notify(notifyRef, notification);
		}
	}

	@Override
	public void updateNotification(int progress) {
		updateNotification(progress, null);
	}

	@Override
	public void updateNotification(int progress, String message) {
		if(notification == null)
			return;

		if(message != null)
			builder.setContentText(message);
		builder.setProgress(100, progress, false);
		notification = builder.build();

		notificationManager.notify(notifyRef, notification);
	}

	@Override
	public void finalizeNotification() {
		notificationManager.cancel(notifyRef);
	}

}
