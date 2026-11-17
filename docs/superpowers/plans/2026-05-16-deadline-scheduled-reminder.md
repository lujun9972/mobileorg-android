# DEADLINE/SCHEDULED 提醒服务 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为 MobileOrg Android 添加 DEADLINE/SCHEDULED 到期系统通知 + 每日待办概览通知。

**架构：** 同步完成后 `ReminderScheduler` 扫描 DB 中含日期的节点，通过 `AlarmManager` 注册提醒。`ReminderReceiver` 处理单条提醒，`DailyOverviewReceiver` 处理每日概览。复用现有 `MobileOrgStartupIntentReceiver` 处理设备重启。

**技术栈：** AlarmManager (RTC_WAKEUP), NotificationCompat, BroadcastReceiver, SharedPreferences, ContentProvider

---

### 任务 1：资源文件（arrays.xml + strings.xml）

**依赖：** 无
**文件集：** `MobileOrg/src/main/res/values/arrays.xml`, `MobileOrg/src/main/res/values/strings.xml`
**导出/变更接口：** `arrays.xml::reminder_advance_labels`, `arrays.xml::reminder_advance_values`, `arrays.xml::daily_overview_time_labels`, `arrays.xml::daily_overview_time_values`
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/res/values/arrays.xml`（末尾追加 4 个 array）
- 修改：`MobileOrg/src/main/res/values/strings.xml`（末尾追加字符串资源）

- [ ] **步骤 1：在 arrays.xml 末尾追加提前量和概览时间选项**

在 `</resources>` 前追加：

```xml
<!-- Reminder advance time options (milliseconds) -->
<string-array name="reminder_advance_labels">
    <item>当天</item>
    <item>1 小时前</item>
    <item>3 小时前</item>
    <item>1 天前</item>
    <item>3 天前</item>
</string-array>
<string-array name="reminder_advance_values">
    <item>0</item>
    <item>3600000</item>
    <item>10800000</item>
    <item>86400000</item>
    <item>259200000</item>
</string-array>

<!-- Daily overview time options (milliseconds from midnight) -->
<string-array name="daily_overview_time_labels">
    <item>6:00</item>
    <item>7:00</item>
    <item>8:00</item>
    <item>9:00</item>
    <item>10:00</item>
</string-array>
<string-array name="daily_overview_time_values">
    <item>21600000</item>
    <item>25200000</item>
    <item>28800000</item>
    <item>32400000</item>
    <item>36000000</item>
</string-array>
```

- [ ] **步骤 2：在 strings.xml 末尾追加字符串资源**

在 `</resources>` 前追加：

```xml
<!-- Reminder settings -->
<string name="preference_reminder">任务提醒</string>
<string name="preference_reminder_enabled">启用提醒</string>
<string name="preference_reminder_enabled_summary">DEADLINE 和 SCHEDULED 到期时发送通知</string>
<string name="preference_reminder_deadline_advance">DEADLINE 提前提醒</string>
<string name="preference_reminder_scheduled_advance">SCHEDULED 提前提醒</string>
<string name="preference_reminder_daily_overview_time">每日概览时间</string>

<!-- Reminder notifications -->
<string name="reminder_notification_channel">任务提醒</string>
<string name="reminder_notification_channel_desc">DEADLINE 和 SCHEDULED 任务到期提醒</string>
<string name="reminder_daily_overview_title">今日待办</string>
<string name="reminder_daily_overview_empty">今日无待办</string>
<string name="reminder_scheduled_today">今日 SCHEDULED</string>
<string name="reminder_deadline_upcoming">即将到期 DEADLINE</string>
<string name="reminder_days_later">%d 天后</string>
<string name="reminder_tomorrow">明天</string>
```

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/res/values/arrays.xml MobileOrg/src/main/res/values/strings.xml
git commit -m "feat: add reminder resource strings and preference arrays"
```

---

### 任务 2：设置界面（preferences.xml）

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/res/xml/preferences.xml`
**导出/变更接口：** `preferences.xml::key_reminderEnabled`, `preferences.xml::key_reminderDeadlineAdvance`, `preferences.xml::key_reminderScheduledAdvance`, `preferences.xml::key_reminderDailyOverviewTime`
**消费接口：** `arrays.xml::reminder_advance_labels`, `arrays.xml::reminder_advance_values`, `arrays.xml::daily_overview_time_labels`, `arrays.xml::daily_overview_time_values`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/res/xml/preferences.xml`

**Preference key 常量约定（供后续任务使用）：**
- `key_reminderEnabled` (boolean, default: true)
- `key_reminderDeadlineAdvance` (string/millis, default: "259200000" = 3天)
- `key_reminderScheduledAdvance` (string/millis, default: "0" = 当天)
- `key_reminderDailyOverviewTime` (string/millis, default: "28800000" = 8:00)

- [ ] **步骤 1：在 preferences.xml 的主 PreferenceCategory 中，`preference_calendar` PreferenceScreen 之后、`preference_actions` PreferenceCategory 之前，插入新的 PreferenceScreen**

在第 191 行（`</PreferenceScreen>` 关闭 calendar）之后、第 193 行（`<PreferenceCategory android:title="@string/preference_actions"`）之前插入：

```xml
        <PreferenceScreen android:title="@string/preference_reminder" >
            <PreferenceCategory android:title="@string/preference_reminder" >
                <CheckBoxPreference
                    android:key="key_reminderEnabled"
                    android:defaultValue="true"
                    android:summary="@string/preference_reminder_enabled_summary"
                    android:title="@string/preference_reminder_enabled" />

                <ListPreference
                    android:dependency="key_reminderEnabled"
                    android:key="key_reminderDeadlineAdvance"
                    android:title="@string/preference_reminder_deadline_advance"
                    android:entries="@array/reminder_advance_labels"
                    android:entryValues="@array/reminder_advance_values"
                    android:defaultValue="259200000" />

                <ListPreference
                    android:dependency="key_reminderEnabled"
                    android:key="key_reminderScheduledAdvance"
                    android:title="@string/preference_reminder_scheduled_advance"
                    android:entries="@array/reminder_advance_labels"
                    android:entryValues="@array/reminder_advance_values"
                    android:defaultValue="0" />

                <ListPreference
                    android:dependency="key_reminderEnabled"
                    android:key="key_reminderDailyOverviewTime"
                    android:title="@string/preference_reminder_daily_overview_time"
                    android:entries="@array/daily_overview_time_labels"
                    android:entryValues="@array/daily_overview_time_values"
                    android:defaultValue="28800000" />
            </PreferenceCategory>
        </PreferenceScreen>
```

- [ ] **步骤 2：Commit**

```bash
git add MobileOrg/src/main/res/xml/preferences.xml
git commit -m "feat: add reminder settings screen to preferences"
```

---

### 任务 3：通知渠道 + Compat 扩展

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/MobileOrgApplication.java`
**导出/变更接口：** `Compat.java::createNotificationChannelHigh`, `Compat.java::CHANNEL_REMINDER`
**消费接口：** `strings.xml::reminder_notification_channel`, `strings.xml::reminder_notification_channel_desc`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/MobileOrgApplication.java`

- [ ] **步骤 1：在 Compat.java 中添加提醒渠道常量和 HIGH importance 渠道创建方法**

添加常量：
```java
public static final String CHANNEL_REMINDER = "mobileorg_reminder";
```

添加方法（在 `createNotificationChannel` 方法之后）：
```java
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
```

- [ ] **步骤 2：在 MobileOrgApplication.onCreate() 中创建提醒通知渠道**

在 `SyncService.startAlarm(getApplicationContext());` 之后（约第 41 行后）添加：

```java
// Create reminder notification channel
Compat.createNotificationChannelHigh(getApplicationContext(),
    Compat.CHANNEL_REMINDER,
    getString(R.string.reminder_notification_channel),
    getString(R.string.reminder_notification_channel_desc));
```

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/Compat.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/MobileOrgApplication.java
git commit -m "feat: add reminder notification channel (HIGH importance)"
```

---

### 任务 4：ReminderScheduler 核心逻辑

**依赖：** 任务 1, 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/ReminderScheduler.java`
**导出/变更接口：** `ReminderScheduler.java::scheduleAll`, `ReminderScheduler.java::cancelAll`, `ReminderScheduler.java::rescheduleAll`, `ReminderScheduler.java::scheduleDailyOverview`
**消费接口：** `OrgContract.java::OrgData.CONTENT_URI`, `OrgContract.java::OrgData.DEFAULT_COLUMNS`, `OrgContract.java::OrgData.TODO`, `OrgContract.java::OrgData.PAYLOAD`, `OrgContract.java::OrgData.ID`, `OrgProviderUtils.java::getActiveTodos`, `OrgNodePayload.java::new(String)`, `OrgNodePayload.java::getScheduled`, `OrgNodePayload.java::getDeadline`, `OrgNodeTimeDate.java::TYPE`, `OrgNodeTimeDate.java::parseDate`, `Compat.java::CHANNEL_REMINDER`
**复杂度：** deep

**文件：**
- 新建：`MobileOrg/src/main/java/com/matburt/mobileorg/util/ReminderScheduler.java`

**关键实现约束：**

1. 查询条件：`PAYLOAD LIKE '%DEADLINE:%' OR PAYLOAD LIKE '%SCHEDULED:%'`，遍历时过滤：`todo == null || activeTodos.contains(todo)`
2. 日期解析：用 `new OrgNodePayload(payload).getScheduled()` / `getDeadline()` 获取日期字符串，再用 `OrgNodeTimeDate` 的 regex 解析为 Calendar
3. 提前量：DEADLINE 和 SCHEDULED 各自从 SharedPreferences 读取（key: `key_reminderDeadlineAdvance`, `key_reminderScheduledAdvance`）
4. PendingIntent 唯一性：`Uri.parse("mobileorg://reminder/" + nodeId + "/" + dateType)`，dateType 为 `"deadline"` 或 `"scheduled"`
5. Intent extras：`nodeId` (long), `dateType` (String), `dateString` (String)
6. 只注册未来 7 天内的提醒
7. `cancelAll()` 查询 DB 重建 PendingIntent 来 cancel
8. 全局开关 `key_reminderEnabled` 关闭时 `scheduleAll()` 直接返回
9. `scheduleDailyOverview()` 用 `AlarmManager.setRepeating(RTC_WAKEUP, ...)` 注册每日闹钟

- [ ] **步骤 1：创建 ReminderScheduler.java**

```java
package com.matburt.mobileorg.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.matburt.mobileorg.OrgData.OrgData;
import com.matburt.mobileorg.OrgData.OrgNodePayload;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        while (cursor.moveToNext()) {
            String todo = cursor.getString(cursor.getColumnIndex(OrgData.TODO));
            // Include: no TODO state OR active TODO
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
        cursor.close();
        Log.d(TAG, "ReminderScheduler: registered " + registered + " alarms");

        scheduleDailyOverview(context);
    }

    public static void cancelAll(ContentResolver resolver, Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Query DB to rebuild PendingIntents and cancel them
        Cursor cursor = resolver.query(
            OrgData.CONTENT_URI,
            new String[]{OrgData.ID, OrgData.PAYLOAD},
            OrgData.PAYLOAD + " LIKE ? OR " + OrgData.PAYLOAD + " LIKE ?",
            new String[]{"%DEADLINE:%", "%SCHEDULED:%"},
            null
        );

        if (cursor == null) return;

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
        cursor.close();
    }

    public static void rescheduleAll(ContentResolver resolver, Context context) {
        scheduleAll(resolver, context);
    }

    public static void scheduleDailyOverview(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("key_reminderEnabled", true)) {
            // Cancel existing daily overview
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

        Calendar triggerAt = Calendar.getInstance();
        triggerAt.set(Calendar.HOUR_OF_DAY, 0);
        triggerAt.set(Calendar.MINUTE, 0);
        triggerAt.set(Calendar.SECOND, 0);
        triggerAt.set(Calendar.MILLISECOND, 0);
        triggerAt.add(Calendar.MILLISECOND, (int) timeFromMidnight);

        // If today's time has passed, schedule for tomorrow
        if (triggerAt.getTimeInMillis() <= System.currentTimeMillis()) {
            triggerAt.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ACTION_DAILY_OVERVIEW);
        intent.setPackage(context.getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
            Compat.FLAG_IMMUTABLE);

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
            triggerAt.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);

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
        // Use setWindow with 1-minute window for battery optimization
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP,
            triggerAtMillis, 60000, pi);
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
    static Calendar parseDateToCalendar(String dateStr) {
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
    static String formatDate(String dateStr) {
        Matcher m = DATE_PATTERN.matcher(dateStr);
        if (m.find()) {
            return m.group(1) + "-" + m.group(2) + "-" + m.group(3);
        }
        return dateStr;
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/ReminderScheduler.java
git commit -m "feat: implement ReminderScheduler core logic"
```

---

### 任务 5：ReminderReceiver

**依赖：** 任务 3, 任务 4
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/ReminderReceiver.java`
**导出/变更接口：** `ReminderReceiver.java::ReminderReceiver`
**消费接口：** `Compat.java::CHANNEL_REMINDER`, `OrgProviderUtils.java::getActiveTodos`, `ViewActivity.java::NODE_ID`
**复杂度：** standard

**文件：**
- 新建：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/ReminderReceiver.java`

- [ ] **步骤 1：创建 ReminderReceiver.java**

```java
package com.matburt.mobileorg.Services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.matburt.mobileorg.Gui.ViewActivity;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.Compat;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import java.util.HashSet;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "MobileOrg";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Compat.hasNotificationPermission(context)) return;

        long nodeId = intent.getLongExtra("nodeId", -1);
        String dateType = intent.getStringExtra("dateType");
        String dateString = intent.getStringExtra("dateString");

        if (nodeId == -1 || dateType == null) return;

        ContentResolver resolver = context.getContentResolver();

        // Re-check: skip if node is now in done state
        try {
            OrgNode node = new OrgNode(nodeId, resolver);
            HashSet<String> activeTodos = new HashSet<>(OrgProviderUtils.getActiveTodos(resolver));
            if (node.todo != null && !activeTodos.contains(node.todo)) {
                Log.d(TAG, "ReminderReceiver: node " + nodeId + " is done, skipping");
                return;
            }

            String label = dateType.equals("deadline") ? "DEADLINE" : "SCHEDULED";

            Intent viewIntent = new Intent(context, ViewActivity.class);
            viewIntent.putExtra(ViewActivity.NODE_ID, nodeId);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                context, (int) nodeId, viewIntent, Compat.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Compat.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(node.name)
                .setContentText(label + ": " + dateString)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

            int notifyId = (nodeId + dateType).hashCode();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(notifyId, builder.build());

            Log.d(TAG, "ReminderReceiver: notified node " + nodeId + " " + label);
        } catch (OrgNodeNotFoundException e) {
            Log.d(TAG, "ReminderReceiver: node " + nodeId + " not found, skipping");
        }
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/ReminderReceiver.java
git commit -m "feat: implement ReminderReceiver for individual deadline/scheduled notifications"
```

---

### 任务 6：DailyOverviewReceiver

**依赖：** 任务 3, 任务 4
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/DailyOverviewReceiver.java`
**导出/变更接口：** `DailyOverviewReceiver.java::DailyOverviewReceiver`
**消费接口：** `Compat.java::CHANNEL_REMINDER`, `ReminderScheduler.java::parseDateToCalendar`, `OrgProviderUtils.java::getActiveTodos`, `OrgNodePayload.java::new(String)`, `OrgNodePayload.java::getScheduled`, `OrgNodePayload.java::getDeadline`
**复杂度：** standard

**文件：**
- 新建：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/DailyOverviewReceiver.java`

- [ ] **步骤 1：创建 DailyOverviewReceiver.java**

```java
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
import com.matburt.mobileorg.OrgData.OrgData;
import com.matburt.mobileorg.OrgData.OrgNodePayload;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
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
        if (!Compat.hasNotificationPermission(context)) return;

        ContentResolver resolver = context.getContentResolver();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        HashSet<String> activeTodos = new HashSet<>(OrgProviderUtils.getActiveTodos(resolver));
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
                    if (ms >= todayMs && ms <= deadlineWindowMs) {
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
        cursor.close();

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
            StringBuilder inboxStyle = new StringBuilder();
            inboxStyle.append(context.getString(R.string.reminder_daily_overview_title))
                .append(" (").append(total).append(")\n\n");

            if (!scheduledItems.isEmpty()) {
                inboxStyle.append(context.getString(R.string.reminder_scheduled_today))
                    .append(" (").append(scheduledItems.size()).append("):\n");
                for (String item : scheduledItems) {
                    inboxStyle.append("· ").append(item).append("\n");
                }
                inboxStyle.append("\n");
            }

            if (!deadlineItems.isEmpty()) {
                inboxStyle.append(context.getString(R.string.reminder_deadline_upcoming))
                    .append(" (").append(deadlineItems.size()).append("):\n");
                for (String item : deadlineItems) {
                    inboxStyle.append("· ").append(item).append("\n");
                }
            }

            Intent outlineIntent = new Intent(context, OutlineActivity.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                context, 0, outlineIntent, Compat.FLAG_IMMUTABLE);

            builder = new NotificationCompat.Builder(context, Compat.CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(context.getString(R.string.reminder_daily_overview_title) + " (" + total + ")")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(inboxStyle.toString().trim()))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        nm.notify(OVERVIEW_NOTIFY_ID, builder.build());
        Log.d(TAG, "DailyOverview: " + total + " items (" + scheduledItems.size() + " scheduled, " + deadlineItems.size() + " deadline)");
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/DailyOverviewReceiver.java
git commit -m "feat: implement DailyOverviewReceiver for daily summary notification"
```

---

### 任务 7：集成（SyncService + BootReceiver + AndroidManifest）

**依赖：** 任务 4, 任务 5, 任务 6
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/SyncService.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Services/MobileOrgStartupIntentReceiver.java`, `MobileOrg/src/main/AndroidManifest.xml`
**导出/变更接口：** 无
**消费接口：** `ReminderScheduler.java::scheduleAll`, `ReminderScheduler.java::rescheduleAll`, `ReminderScheduler.java::scheduleDailyOverview`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/SyncService.java`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/MobileOrgStartupIntentReceiver.java`
- 修改：`MobileOrg/src/main/AndroidManifest.xml`

- [ ] **步骤 1：SyncService.runSynchronizer() 的 finally 块中添加 ReminderScheduler.scheduleAll() 调用**

在 `SyncService.java` 约 172 行的 `syncRunning = false;` 之后、`setAlarm();` 之前，添加：

```java
// Schedule reminder alarms after sync
try {
    ReminderScheduler.scheduleAll(getContentResolver(), getBaseContext());
} catch (Exception e) {
    Log.w("MobileOrg", "ReminderScheduler failed: " + e);
}
```

需在文件头部添加 import：
```java
import com.matburt.mobileorg.util.ReminderScheduler;
import android.util.Log;
```

- [ ] **步骤 2：MobileOrgStartupIntentReceiver.onReceive() 末尾添加提醒重注册**

在现有 `context.startService(calIntent);` 之后添加：

```java
// Re-register reminder alarms after boot
try {
    ContentResolver resolver = context.getContentResolver();
    ReminderScheduler.rescheduleAll(resolver, context);
    ReminderScheduler.scheduleDailyOverview(context);
} catch (Exception e) {
    // Silently ignore - reminders will be re-registered on next sync
}
```

需在文件头部添加 import：
```java
import android.content.ContentResolver;
import com.matburt.mobileorg.util.ReminderScheduler;
```

- [ ] **步骤 3：AndroidManifest.xml 注册两个新 BroadcastReceiver**

在现有 `MobileOrgStartupIntentReceiver` 声明之后（约 67 行后）添加：

```xml
<receiver android:name=".Services.ReminderReceiver"
    android:exported="false" >
    <intent-filter>
        <action android:name="com.matburt.mobileorg.REMINDER" />
    </intent-filter>
</receiver>
<receiver android:name=".Services.DailyOverviewReceiver"
    android:exported="false" >
    <intent-filter>
        <action android:name="com.matburt.mobileorg.DAILY_OVERVIEW" />
    </intent-filter>
</receiver>
```

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/SyncService.java MobileOrg/src/main/java/com/matburt/mobileorg/Services/MobileOrgStartupIntentReceiver.java MobileOrg/src/main/AndroidManifest.xml
git commit -m "feat: integrate reminder scheduler into sync, boot, and manifest"
```

---

### 任务 8：端到端验证

**依赖：** 任务 7
**文件集：** 无新文件
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

- [ ] **步骤 1：推送并检查 CI 编译**

```bash
git push
gh run list --limit 1
```

等待 CI 完成，确认编译通过。如有编译错误，根据 CI 日志修复后重新推送。

- [ ] **步骤 2：代码审查——检查 CLAUDE.md 是否需要更新**

此功能不涉及新的 Android 兼容性陷阱（使用了现有的 AlarmManager + BroadcastReceiver + NotificationCompat 模式），但需确认：
- 所有 PendingIntent 使用 `FLAG_IMMUTABLE`（已在 ReminderScheduler 中确保）
- BroadcastReceiver 在 Manifest 中声明（任务 7 已处理）
- 通知渠道在发通知前创建（任务 3 在 Application.onCreate 中创建）

如发现新的兼容性问题，记录到 CLAUDE.md 的 "Android Compatibility (Applied Fixes)" 部分。

- [ ] **步骤 3：更新 CLAUDE.md Known Pitfalls 部分（如有必要）**

在 `## Architecture` 的 `### Key Packages` 中补充提醒服务的说明：

```markdown
- **`util/ReminderScheduler.java`** — Scans OrgData for DEADLINE/SCHEDULED dates, registers AlarmManager reminders. Called after sync and on boot.
- **`Services/ReminderReceiver`** — Fires individual deadline/scheduled notifications.
- **`Services/DailyOverviewReceiver`** — Fires daily summary notification.
```

如步骤 2 发现新兼容性问题，也在此一并更新。

- [ ] **步骤 4：最终 Commit**

```bash
git add -A
git commit -m "docs: update CLAUDE.md with reminder service architecture"
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 4 → 任务 5/6 → 任务 7 → 任务 8

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2（依赖 1）, 任务 3（依赖 1）
- Wave 3（依赖 Wave 1-2）：任务 4（依赖 1, 3）
- Wave 4（依赖 Wave 3）：任务 5（依赖 3, 4）, 任务 6（依赖 3, 4）
- Wave 5（依赖 Wave 4）：任务 7（依赖 4, 5, 6）
- Wave 6（依赖 Wave 5）：任务 8（依赖 7）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
