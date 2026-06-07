package com.matburt.mobileorg.Services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.matburt.mobileorg.Gui.Outline.OutlineActivity;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgNodePayload;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.ReminderScheduler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;

public class DailyOverviewReceiver extends BroadcastReceiver {
    private static final String TAG = "MobileOrg";
    private static final int OVERVIEW_NOTIFY_ID = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "DailyOverviewReceiver.onReceive() called");
        if (!Compat.hasNotificationPermission(context)) {
            Log.w(TAG, "DailyOverviewReceiver: no notification permission, skipping");
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        HashSet<String> activeTodos = new HashSet<>(new OrgFileRepository(resolver).getActiveTodos());
        long deadlineAdvance = Long.parseLong(
            prefs.getString("key_reminderDeadlineAdvance", "259200000"));

        ArrayList<String> scheduledItems = new ArrayList<>();
        ArrayList<String> deadlineItems = new ArrayList<>();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayMs = today.getTimeInMillis();
        long tomorrowMs = todayMs + 86400000;
        long deadlineWindowMs = todayMs + deadlineAdvance;

        Cursor cursor = resolver.query(
            OrgData.CONTENT_URI,
            OrgData.DEFAULT_COLUMNS,
            OrgData.PAYLOAD + " LIKE ? OR " + OrgData.PAYLOAD + " LIKE ?",
            new String[]{"%DEADLINE:%", "%SCHEDULED:%"},
            null
        );

        if (cursor == null) return;

        try {
            while (cursor.moveToNext()) {
                String todo = cursor.getString(cursor.getColumnIndex(OrgData.TODO));
                if (todo != null && !activeTodos.contains(todo)) continue;

                String name = cursor.getString(cursor.getColumnIndex(OrgData.NAME));
                String payload = cursor.getString(cursor.getColumnIndex(OrgData.PAYLOAD));
                OrgNodePayload nodePayload = new OrgNodePayload(payload);

                // Check SCHEDULED today
                String scheduledStr = nodePayload.getScheduled();
                if (!TextUtils.isEmpty(scheduledStr)) {
                    Calendar scheduledCal = ReminderScheduler.parseDateToCalendar(scheduledStr);
                    if (scheduledCal != null) {
                        long ms = scheduledCal.getTimeInMillis();
                        if (ms >= todayMs && ms < tomorrowMs) {
                            scheduledItems.add(name);
                        }
                    }
                }

                // Check DEADLINE within advance window
                String deadlineStr = nodePayload.getDeadline();
                if (!TextUtils.isEmpty(deadlineStr)) {
                    Calendar deadlineCal = ReminderScheduler.parseDateToCalendar(deadlineStr);
                    if (deadlineCal != null) {
                        long ms = deadlineCal.getTimeInMillis();
                        if (ms >= todayMs && ms < deadlineWindowMs) {
                            long daysDiff = (ms - todayMs) / 86400000;
                            if (daysDiff == 0) {
                                deadlineItems.add(name);
                            } else if (daysDiff == 1) {
                                deadlineItems.add(name + " (明天)");
                            } else {
                                deadlineItems.add(name + " (" + daysDiff + "天后)");
                            }
                        }
                    }
                }
            }
        } finally {
            cursor.close();
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int total = scheduledItems.size() + deadlineItems.size();
        NotificationCompat.Builder builder;

        if (total == 0) {
            // Empty overview - click dismisses
            builder = new NotificationCompat.Builder(context, Compat.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(context.getString(R.string.reminder_daily_overview_empty))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        } else {
            // Build sectioned summary
            StringBuilder content = new StringBuilder();
            content.append(context.getString(R.string.reminder_daily_overview_title))
                .append(" (").append(total).append(")\n\n");

            if (!scheduledItems.isEmpty()) {
                content.append(context.getString(R.string.reminder_scheduled_today))
                    .append(" (").append(scheduledItems.size()).append("):\n");
                for (String item : scheduledItems) {
                    content.append("· ").append(item).append("\n");
                }
                content.append("\n");
            }

            if (!deadlineItems.isEmpty()) {
                content.append(context.getString(R.string.reminder_deadline_upcoming))
                    .append(" (").append(deadlineItems.size()).append("):\n");
                for (String item : deadlineItems) {
                    content.append("· ").append(item).append("\n");
                }
            }

            Intent outlineIntent = new Intent(context, OutlineActivity.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                context, 0, outlineIntent, Compat.FLAG_IMMUTABLE);

            builder = new NotificationCompat.Builder(context, Compat.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(context.getString(R.string.reminder_daily_overview_title) + " (" + total + ")")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content.toString().trim()))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        nm.notify(OVERVIEW_NOTIFY_ID, builder.build());
        Log.d(TAG, "DailyOverview: " + total + " items (" + scheduledItems.size() + " scheduled, " + deadlineItems.size() + " deadline)");

        // Reschedule tomorrow's daily overview (one-shot alarm, not repeating)
        ReminderScheduler.scheduleDailyOverview(context);
    }
}
