package com.matburt.mobileorg.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgNodePayload;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schedules DEADLINE/SCHEDULED reminders and daily overview notifications.
 *
 * On Android 12+ (API 31+), uses setExactAndAllowWhileIdle() for precise timing.
 * The SCHEDULE_EXACT_ALARM permission is declared in manifest; the system grants it
 * by default for pre-installed / existing apps. Falls back to setWindow() with a
 * 10-minute window if permission is not available.
 *
 * Daily overview uses setExact() (one-shot) instead of setRepeating() because
 * setRepeating() has been inexact since API 19. The DailyOverviewReceiver reschedules
 * the next alarm after each fire.
 */
public class ReminderScheduler {
    private static final String TAG = "MobileOrg";
    private static final String ACTION_REMINDER = "com.matburt.mobileorg.REMINDER";
    private static final String ACTION_DAILY_OVERVIEW = "com.matburt.mobileorg.DAILY_OVERVIEW";
    private static final String EXTRA_NODE_ID = "nodeId";
    private static final String EXTRA_DATE_TYPE = "dateType";
    private static final String EXTRA_DATE_STRING = "dateString";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[^\\d]*)(?:(\\d{1,2}):(\\d{2}))?");

    public static void scheduleAll(ContentResolver resolver, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("key_reminderEnabled", true)) {
            cancelAll(resolver, context);
            scheduleDailyOverview(context);
            return;
        }

        cancelAll(resolver, context);

        Set<String> activeTodos = new HashSet<>(OrgProviderUtils.getActiveTodos(resolver));
        long deadlineAdvance = Long.parseLong(
            prefs.getString("key_reminderDeadlineAdvance", "259200000"));
        long scheduledAdvance = Long.parseLong(
            prefs.getString("key_reminderScheduledAdvance", "0"));

        Cursor cursor = resolver.query(
            OrgData.CONTENT_URI,
            OrgData.DEFAULT_COLUMNS,
            OrgData.PAYLOAD + " LIKE ? OR " + OrgData.PAYLOAD + " LIKE ?",
            new String[]{"%DEADLINE:%", "%SCHEDULED:%"},
            null
        );

        if (cursor == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        int registered = 0;

        try {
            while (cursor.moveToNext()) {
                String todo = cursor.getString(cursor.getColumnIndex(OrgData.TODO));
                if (todo != null && !activeTodos.contains(todo)) continue;

                long nodeId = cursor.getLong(cursor.getColumnIndex(OrgData.ID));
                String payload = cursor.getString(cursor.getColumnIndex(OrgData.PAYLOAD));

                // Process DEADLINE
                OrgNodePayload nodePayload = new OrgNodePayload(payload);
                String deadlineStr = nodePayload.getDeadline();
                if (!TextUtils.isEmpty(deadlineStr)) {
                    Calendar deadlineCal = parseDateToCalendar(deadlineStr);
                    if (deadlineCal != null) {
                        long reminderTime = deadlineCal.getTimeInMillis() - deadlineAdvance;
                        if (reminderTime > now && (reminderTime - now) <= SEVEN_DAYS_MS) {
                            registerAlarm(alarmManager, context, nodeId, "deadline",
                                formatDate(deadlineStr), reminderTime);
                            registered++;
                        }
                    }
                }

                // Process SCHEDULED
                String scheduledStr = nodePayload.getScheduled();
                if (!TextUtils.isEmpty(scheduledStr)) {
                    Calendar scheduledCal = parseDateToCalendar(scheduledStr);
                    if (scheduledCal != null) {
                        long reminderTime = scheduledCal.getTimeInMillis() - scheduledAdvance;
                        if (reminderTime > now && (reminderTime - now) <= SEVEN_DAYS_MS) {
                            registerAlarm(alarmManager, context, nodeId, "scheduled",
                                formatDate(scheduledStr), reminderTime);
                            registered++;
                        }
                    }
                }
            }
        } finally {
            cursor.close();
        }
        Log.d(TAG, "ReminderScheduler: registered " + registered + " alarms");

        scheduleDailyOverview(context);
    }

    public static void cancelAll(ContentResolver resolver, Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Cursor cursor = resolver.query(
            OrgData.CONTENT_URI,
            new String[]{OrgData.ID, OrgData.PAYLOAD},
            OrgData.PAYLOAD + " LIKE ? OR " + OrgData.PAYLOAD + " LIKE ?",
            new String[]{"%DEADLINE:%", "%SCHEDULED:%"},
            null
        );

        if (cursor == null) return;

        try {
            while (cursor.moveToNext()) {
                long nodeId = cursor.getLong(cursor.getColumnIndex(OrgData.ID));
                String payload = cursor.getString(cursor.getColumnIndex(OrgData.PAYLOAD));
                boolean hasDeadline = payload.contains("DEADLINE:");
                boolean hasScheduled = payload.contains("SCHEDULED:");

                if (hasDeadline) {
                    cancelAlarm(alarmManager, context, nodeId, "deadline");
                }
                if (hasScheduled) {
                    cancelAlarm(alarmManager, context, nodeId, "scheduled");
                }
            }
        } finally {
            cursor.close();
        }
    }

    public static void rescheduleAll(ContentResolver resolver, Context context) {
        scheduleAll(resolver, context);
    }

    public static void scheduleDailyOverview(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("key_reminderEnabled", true)) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ACTION_DAILY_OVERVIEW);
            intent.setPackage(context.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                Compat.FLAG_IMMUTABLE);
            alarmManager.cancel(pi);
            return;
        }

        long timeFromMidnight = Long.parseLong(
            prefs.getString("key_reminderDailyOverviewTime", "28800000"));
        int hours = (int)(timeFromMidnight / 3600000);
        int minutes = (int)((timeFromMidnight % 3600000) / 60000);

        Calendar triggerAt = Calendar.getInstance();
        triggerAt.set(Calendar.HOUR_OF_DAY, hours);
        triggerAt.set(Calendar.MINUTE, minutes);
        triggerAt.set(Calendar.SECOND, 0);
        triggerAt.set(Calendar.MILLISECOND, 0);

        if (triggerAt.getTimeInMillis() <= System.currentTimeMillis()) {
            triggerAt.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ACTION_DAILY_OVERVIEW);
        intent.setPackage(context.getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
            Compat.FLAG_IMMUTABLE);

        // Use setExactAndAllowWhileIdle() on API 31+ for precise daily timing.
        // One-shot alarm; DailyOverviewReceiver reschedules after each fire.
        if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                triggerAt.getTimeInMillis(), pi);
        } else {
            // Fallback: setWindow with 10-minute window (Android 12+ enforces minimum)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP,
                triggerAt.getTimeInMillis(), 600_000L, pi);
        }

        Log.d(TAG, "ReminderScheduler: daily overview scheduled at " + triggerAt.getTime());
    }

    private static void registerAlarm(AlarmManager alarmManager, Context context,
            long nodeId, String dateType, String dateString, long triggerAtMillis) {
        Intent intent = new Intent(ACTION_REMINDER);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse("mobileorg://reminder/" + nodeId + "/" + dateType));
        intent.putExtra(EXTRA_NODE_ID, nodeId);
        intent.putExtra(EXTRA_DATE_TYPE, dateType);
        intent.putExtra(EXTRA_DATE_STRING, dateString);

        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
            Compat.FLAG_IMMUTABLE);

        // On API 31+, use setExactAndAllowWhileIdle() for precise reminder timing.
        // setWindow() with 1-minute window is elongated to 10 minutes by Android 12+,
        // causing reminders to fire up to 10 minutes late.
        if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                triggerAtMillis, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            // API 23-30: setExactAndAllowWhileIdle works without special permission
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                triggerAtMillis, pi);
        } else {
            // Pre-API 23: setExact is sufficient
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    private static void cancelAlarm(AlarmManager alarmManager, Context context,
            long nodeId, String dateType) {
        Intent intent = new Intent(ACTION_REMINDER);
        intent.setPackage(context.getPackageName());
        intent.setData(Uri.parse("mobileorg://reminder/" + nodeId + "/" + dateType));
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
            Compat.FLAG_IMMUTABLE);
        alarmManager.cancel(pi);
    }

    /**
     * Parse an org date string like "2024-01-15 10:00" into a Calendar.
     * Returns null if parsing fails.
     */
    public static Calendar parseDateToCalendar(String dateStr) {
        if (TextUtils.isEmpty(dateStr)) return null;
        Matcher m = DATE_PATTERN.matcher(dateStr);
        if (!m.find()) return null;
        try {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = m.group(4) != null ? Integer.parseInt(m.group(4)) : 0;
            int minute = m.group(5) != null ? Integer.parseInt(m.group(5)) : 0;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Format date string for display: extract "YYYY-MM-DD" part.
     */
    public static String formatDate(String dateStr) {
        Matcher m = DATE_PATTERN.matcher(dateStr);
        if (m.find()) {
            return m.group(1) + "-" + m.group(2) + "-" + m.group(3);
        }
        return dateStr;
    }
}
