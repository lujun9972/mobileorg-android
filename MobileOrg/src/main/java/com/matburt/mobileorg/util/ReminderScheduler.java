package com.matburt.mobileorg.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.matburt.mobileorg.OrgData.OrgNodePayload;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.OrgData.OrgFileRepository;

import java.util.Calendar;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schedules DEADLINE/SCHEDULED reminders and daily overview notifications.
 *
 * Alarm strategy selection (see {@link #chooseAlarmStrategy(int, boolean)}):
 * <ul>
 *   <li>API 31+ with {@code SCHEDULE_EXACT_ALARM} granted (special permission,
 *       user must enable in system settings — manifest declaration alone is
 *       insufficient): {@code setExactAndAllowWhileIdle()} for precise timing.</li>
 *   <li>API 31+ without the permission: fallback to {@code setWindow()} with a
 *       10-minute window — calling {@code setExact*()} here throws
 *       {@link SecurityException}.</li>
 *   <li>API 23-30: {@code setExactAndAllowWhileIdle()} (no special permission needed).</li>
 *   <li>API &lt; 23: {@code setExact()}.</li>
 * </ul>
 *
 * Daily overview uses one-shot alarms (not {@code setRepeating()}, which has been
 * inexact since API 19). {@code DailyOverviewReceiver} reschedules the next alarm
 * after each fire.
 */
public class ReminderScheduler {
    private static final String TAG = "MobileOrg";
    private static final String ACTION_REMINDER = "com.matburt.mobileorg.REMINDER";
    private static final String ACTION_DAILY_OVERVIEW = "com.matburt.mobileorg.DAILY_OVERVIEW";
    private static final String EXTRA_NODE_ID = "nodeId";
    private static final String EXTRA_DATE_TYPE = "dateType";
    private static final String EXTRA_DATE_STRING = "dateString";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long FALLBACK_WINDOW_MS = 600_000L; // 10 min, enforced minimum on API 31+

    /** Which AlarmManager API to call. See {@link #chooseAlarmStrategy(int, boolean)}. */
    public enum AlarmStrategy { EXACT_ALLOW_IDLE, EXACT, WINDOW }

    /**
     * Decide which AlarmManager scheduling API to use.
     *
     * On API 31+, {@code setExactAndAllowWhileIdle} / {@code setExact} require the
     * {@code SCHEDULE_EXACT_ALARM} <em>special</em> permission — granted by the user
     * in system settings, not merely declared in manifest. When unavailable we must
     * fall back to {@code setWindow}; calling {@code setExact*()} throws
     * {@link SecurityException}. On API 23-30 the exact APIs need no special permission.
     */
    public static AlarmStrategy chooseAlarmStrategy(int apiLevel, boolean canScheduleExactAlarms) {
        if (apiLevel >= 31 && !canScheduleExactAlarms) {
            return AlarmStrategy.WINDOW;
        }
        if (apiLevel >= 23) {
            return AlarmStrategy.EXACT_ALLOW_IDLE;
        }
        return AlarmStrategy.EXACT;
    }

    /** Apply the chosen strategy to an AlarmManager. */
    private static void applyStrategy(AlarmManager am, AlarmStrategy strategy,
            int type, long triggerAtMillis, PendingIntent pi) {
        switch (strategy) {
            case EXACT_ALLOW_IDLE:
                am.setExactAndAllowWhileIdle(type, triggerAtMillis, pi);
                break;
            case EXACT:
                am.setExact(type, triggerAtMillis, pi);
                break;
            case WINDOW:
                am.setWindow(type, triggerAtMillis, FALLBACK_WINDOW_MS, pi);
                break;
        }
    }

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

        Set<String> activeTodos = new HashSet<>(new OrgFileRepository(resolver).getActiveTodos());
        long deadlineAdvance = Long.parseLong(
            prefs.getString("key_reminderDeadlineAdvance", "259200000"));
        long scheduledAdvance = Long.parseLong(
            prefs.getString("key_reminderScheduledAdvance", "0"));

        ArrayList<OrgNode> nodes = new OrgNodeRepository(resolver).getReminderEligibleNodes();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        int registered = 0;

        for (OrgNode node : nodes) {
            if (node.todo != null && !activeTodos.contains(node.todo)) continue;

            OrgNodePayload nodePayload = new OrgNodePayload(node.getPayload());
            String deadlineStr = nodePayload.getDeadline();
            if (!TextUtils.isEmpty(deadlineStr)) {
                Calendar deadlineCal = parseDateToCalendar(deadlineStr);
                if (deadlineCal != null) {
                    long reminderTime = deadlineCal.getTimeInMillis() - deadlineAdvance;
                    if (reminderTime > now && (reminderTime - now) <= SEVEN_DAYS_MS) {
                        registerAlarm(alarmManager, context, node.id, "deadline",
                            formatDate(deadlineStr), reminderTime);
                        registered++;
                    }
                }
            }

            String scheduledStr = nodePayload.getScheduled();
            if (!TextUtils.isEmpty(scheduledStr)) {
                Calendar scheduledCal = parseDateToCalendar(scheduledStr);
                if (scheduledCal != null) {
                    long reminderTime = scheduledCal.getTimeInMillis() - scheduledAdvance;
                    if (reminderTime > now && (reminderTime - now) <= SEVEN_DAYS_MS) {
                        registerAlarm(alarmManager, context, node.id, "scheduled",
                            formatDate(scheduledStr), reminderTime);
                        registered++;
                    }
                }
            }
        }
        Log.d(TAG, "ReminderScheduler: registered " + registered + " alarms");

        scheduleDailyOverview(context);
    }

    public static void cancelAll(ContentResolver resolver, Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        ArrayList<OrgNode> nodes = new OrgNodeRepository(resolver).getReminderEligibleNodes();

        for (OrgNode node : nodes) {
            String payload = node.getPayload();
            boolean hasDeadline = payload.contains("DEADLINE:");
            boolean hasScheduled = payload.contains("SCHEDULED:");

            if (hasDeadline) {
                cancelAlarm(alarmManager, context, node.id, "deadline");
            }
            if (hasScheduled) {
                cancelAlarm(alarmManager, context, node.id, "scheduled");
            }
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

        // One-shot alarm; DailyOverviewReceiver reschedules after each fire.
        applyStrategy(alarmManager,
            chooseAlarmStrategy(Build.VERSION.SDK_INT, alarmManager.canScheduleExactAlarms()),
            AlarmManager.RTC_WAKEUP, triggerAt.getTimeInMillis(), pi);

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

        applyStrategy(alarmManager,
            chooseAlarmStrategy(Build.VERSION.SDK_INT, alarmManager.canScheduleExactAlarms()),
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
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
