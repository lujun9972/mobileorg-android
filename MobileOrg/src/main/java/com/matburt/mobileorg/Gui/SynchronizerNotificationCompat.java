package com.matburt.mobileorg.Gui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Builder;
import android.widget.RemoteViews;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Gui.Outline.OutlineActivity;
import com.matburt.mobileorg.util.Compat;

public class SynchronizerNotificationCompat {
    public static final String CHANNEL_ID = "mobileorg_sync";

	private NotificationManager notificationManager;
	private Notification notification;
	private int notifyRef = 1;
	private Context context;

	public SynchronizerNotificationCompat(Context context) {
		this.context = context;
	}

	protected void createNotificationChannel() {
		Compat.createNotificationChannel(context, CHANNEL_ID, "MobileOrg Sync");
	}

	private NotificationManager getNotificationManager() {
		if (notificationManager == null) {
			notificationManager = (NotificationManager) context
					.getSystemService(Context.NOTIFICATION_SERVICE);
		}
		return notificationManager;
	}

	public void errorNotification(String errorMsg) {
		createNotificationChannel();
		Intent notifyIntent = new Intent(context, OutlineActivity.class);
		notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notifyIntent, Compat.FLAG_IMMUTABLE);

		Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
		builder.setContentIntent(contentIntent);
		builder.setSmallIcon(R.drawable.icon);
		builder.setContentTitle("Synchronization failed");

		notification = builder.getNotification();
		notification.contentView = notification.contentView = new RemoteViews(
				context.getPackageName(), R.layout.sync_notification);

		notification.contentView.setImageViewResource(R.id.status_icon,
				R.drawable.icon);
		notification.contentView.setTextViewText(R.id.status_text, errorMsg);
		notification.contentView.setProgressBar(R.id.status_progress, 100, 100,
				false);
		getNotificationManager().notify(notifyRef, notification);
	}

	public void setupNotification() {
		createNotificationChannel();
		Intent notifyIntent = new Intent(context, OutlineActivity.class);
		notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notifyIntent, Compat.FLAG_IMMUTABLE);

		Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
		builder.setContentIntent(contentIntent);
		builder.setSmallIcon(R.drawable.icon);
		builder.setOngoing(true);
		builder.setContentTitle("Started synchronization");
		builder.setContentText("Started synchronization");
		notification = builder.getNotification();

		notification.contentView = new RemoteViews(context.getPackageName(),
				R.layout.sync_notification);

		notification.contentView.setImageViewResource(R.id.status_icon,
				R.drawable.icon);
		notification.contentView.setTextViewText(R.id.status_text,
				context.getString(R.string.sync_synchronizing_changes));
		notification.contentView.setProgressBar(R.id.status_progress, 100, 0,
				true);

		getNotificationManager().notify(notifyRef, notification);
	}

	public void updateNotification(String message) {
		if(notification == null)
			return;

		if(message != null) {
			notification.contentView.setTextViewText(R.id.status_text, message);
			getNotificationManager().notify(notifyRef, notification);
		}
	}

	public void updateNotification(int progress) {
		updateNotification(progress, null);
	}

	public void updateNotification(int progress, String message) {
		if(notification == null)
			return;

		if(message != null)
			notification.contentView.setTextViewText(R.id.status_text, message);

		notification.contentView.setProgressBar(R.id.status_progress, 100,
				progress, false);
		getNotificationManager().notify(notifyRef, notification);
	}

	public void finalizeNotification() {
		getNotificationManager().cancel(notifyRef);
	}
}
