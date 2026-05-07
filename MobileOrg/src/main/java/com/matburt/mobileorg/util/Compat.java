package com.matburt.mobileorg.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Compatibility helpers for building against compileSdk 23 while supporting API 26+ features.
 * All API 26+ classes and methods are accessed via reflection to avoid compile-time dependencies.
 */
public class Compat {

    public static final int SDK_O = 26;
    public static final int FLAG_IMMUTABLE = 0x04000000;
    public static final int IMPORTANCE_LOW = 2;

    public static boolean isAtLeastO() {
        return Build.VERSION.SDK_INT >= SDK_O;
    }

    /**
     * Create a NotificationChannel (API 26+). No-op on older versions.
     * Uses reflection to avoid compileSdk 23 limitation.
     */
    public static void createNotificationChannel(Context context, String channelId, String name) {
        if (!isAtLeastO()) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Constructor<?> constructor = channelClass.getConstructor(String.class, CharSequence.class, int.class);
            Object channel = constructor.newInstance(channelId, name, IMPORTANCE_LOW);
            Method createChannel = NotificationManager.class.getMethod("createNotificationChannel", channelClass);
            createChannel.invoke(nm, channel);
        } catch (Exception e) {
            // NotificationChannel not available or creation failed
        }
    }

    /**
     * Start a service as foreground service on API 26+, regular service on older versions.
     */
    public static void startService(Context context, Intent intent) {
        if (isAtLeastO()) {
            try {
                Method method = Context.class.getMethod("startForegroundService", Intent.class);
                method.invoke(context, intent);
            } catch (Exception e) {
                context.startService(intent);
            }
        } else {
            context.startService(intent);
        }
    }

    /**
     * Get a PendingIntent for a foreground service on API 26+, regular service on older versions.
     */
    public static PendingIntent getServicePendingIntent(Context context, int requestCode, Intent intent, int flags) {
        if (isAtLeastO()) {
            try {
                Method method = PendingIntent.class.getMethod("getForegroundService", Context.class, int.class, Intent.class, int.class);
                return (PendingIntent) method.invoke(null, context, requestCode, intent, flags);
            } catch (Exception e) {
                return PendingIntent.getService(context, requestCode, intent, flags);
            }
        } else {
            return PendingIntent.getService(context, requestCode, intent, flags);
        }
    }
}
