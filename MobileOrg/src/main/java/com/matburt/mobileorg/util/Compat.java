package com.matburt.mobileorg.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Compatibility helpers for API 26+ features.
 */
public class Compat {

    public static final int SDK_O = 26;
    public static final int FLAG_IMMUTABLE = 0x04000000;
    public static final String CHANNEL_REMINDER = "mobileorg_reminder";

    public static boolean isAtLeastO() {
        return Build.VERSION.SDK_INT >= SDK_O;
    }

    /**
     * Create a NotificationChannel (API 26+). No-op on older versions.
     */
    public static void createNotificationChannel(Context context, String channelId, String name) {
        if (!isAtLeastO()) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(name);
        nm.createNotificationChannel(channel);
    }

    /**
     * Create a HIGH importance NotificationChannel for reminders (API 26+).
     */
    public static void createNotificationChannelHigh(Context context, String channelId, String name, String description) {
        if (!isAtLeastO()) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.enableVibration(true);
        nm.createNotificationChannel(channel);
    }

    /**
     * Start a service as foreground service on API 26+, regular service on older versions.
     */
    public static void startService(Context context, Intent intent) {
        if (isAtLeastO()) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Get a PendingIntent for a foreground service on API 26+, regular service on older versions.
     */
    public static PendingIntent getServicePendingIntent(Context context, int requestCode, Intent intent, int flags) {
        if (isAtLeastO()) {
            return PendingIntent.getForegroundService(context, requestCode, intent, flags);
        } else {
            return PendingIntent.getService(context, requestCode, intent, flags);
        }
    }

    public static final int SDK_TIRAMISU = 33;

    /**
     * Check if the app has POST_NOTIFICATIONS permission (required on API 33+).
     * Returns true on API < 33 (permission not required).
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < SDK_TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Start a service in the foreground, handling API 34+ service type requirement.
     */
    public static void startForeground(Service service, int id, Notification notification, int serviceType) {
        if (Build.VERSION.SDK_INT >= 34) {
            service.startForeground(id, notification, serviceType);
        } else {
            service.startForeground(id, notification);
        }
    }
}
