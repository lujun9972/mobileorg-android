package com.matburt.mobileorg.Gui.Statistics;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.matburt.mobileorg.OrgData.OrgDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class PomodoroStatisticsRepository {
    private final Context context;

    public PomodoroStatisticsRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    private SQLiteDatabase getDb() {
        return new OrgDatabase(context).getReadableDatabase();
    }

    public List<DailyCount> getDailyCounts(long rangeStart, long rangeEnd) {
        List<DailyCount> result = new ArrayList<>();
        SQLiteDatabase db = getDb();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT date(completed_at / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS cnt "
                + "FROM pomodoro_sessions WHERE completed_at >= ? AND completed_at < ? "
                + "GROUP BY day ORDER BY day",
                new String[]{String.valueOf(rangeStart), String.valueOf(rangeEnd)});
            while (cursor.moveToNext()) {
                result.add(new DailyCount(cursor.getString(0), cursor.getInt(1)));
            }
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return result;
    }

    public List<TrendPoint> getTrendData(int count, String granularity) {
        List<TrendPoint> result = new ArrayList<>();
        SQLiteDatabase db = getDb();
        try {
            for (int i = count - 1; i >= 0; i--) {
                Calendar base = Calendar.getInstance();
                long start, end;
                String label;
                if ("day".equals(granularity)) {
                    base.add(Calendar.DAY_OF_MONTH, -i);
                    start = dayStart(base).getTimeInMillis();
                    label = formatMonthDay(base);
                    base.add(Calendar.DAY_OF_MONTH, 1);
                    end = dayStart(base).getTimeInMillis();
                } else if ("week".equals(granularity)) {
                    base.add(Calendar.WEEK_OF_YEAR, -i);
                    Calendar ws = weekStart(base);
                    start = ws.getTimeInMillis();
                    label = formatMonthDay(ws);
                    ws.add(Calendar.DAY_OF_MONTH, 7);
                    end = ws.getTimeInMillis();
                } else {
                    base.add(Calendar.MONTH, -i);
                    base.set(Calendar.DAY_OF_MONTH, 1);
                    start = dayStart(base).getTimeInMillis();
                    label = String.format(Locale.getDefault(), "%d月", base.get(Calendar.MONTH) + 1);
                    base.add(Calendar.MONTH, 1);
                    end = dayStart(base).getTimeInMillis();
                }
                int cnt = countInRange(db, start, end);
                result.add(new TrendPoint(label, cnt));
            }
        } finally {
            db.close();
        }
        return result;
    }

    public int getStreak() {
        SQLiteDatabase db = getDb();
        try {
            Calendar cal = Calendar.getInstance();
            int streak = 0;
            if (countInRange(db, dayStart(cal).getTimeInMillis(),
                    dayStart(cal).getTimeInMillis() + 86400000L) > 0) {
                streak = 1;
            }
            cal.add(Calendar.DAY_OF_MONTH, -1);
            while (true) {
                long dayMs = dayStart(cal).getTimeInMillis();
                if (countInRange(db, dayMs, dayMs + 86400000L) > 0) {
                    streak++;
                    cal.add(Calendar.DAY_OF_MONTH, -1);
                } else {
                    break;
                }
            }
            return streak;
        } finally {
            db.close();
        }
    }

    public List<PomodoroSession> getSessionsForDate(long dayStart) {
        List<PomodoroSession> result = new ArrayList<>();
        SQLiteDatabase db = getDb();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT _id, started_at, duration_min FROM pomodoro_sessions "
                + "WHERE completed_at >= ? AND completed_at < ? ORDER BY completed_at",
                new String[]{String.valueOf(dayStart), String.valueOf(dayStart + 86400000L)});
            while (cursor.moveToNext()) {
                result.add(new PomodoroSession(
                    cursor.getLong(0), cursor.getLong(1), cursor.getInt(2)));
            }
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return result;
    }

    public Summary getSummary(long rangeStart, long rangeEnd, int dayCount) {
        SQLiteDatabase db = getDb();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(duration_min), 0) FROM pomodoro_sessions "
                + "WHERE completed_at >= ? AND completed_at < ?",
                new String[]{String.valueOf(rangeStart), String.valueOf(rangeEnd)});
            if (cursor.moveToFirst()) {
                int totalCount = cursor.getInt(0);
                int totalMinutes = cursor.getInt(1);
                float dailyAvg = dayCount > 0 ? (float) totalCount / dayCount : 0f;
                return new Summary(totalCount, totalMinutes, dailyAvg);
            }
            return new Summary(0, 0, 0f);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
    }

    private int countInRange(SQLiteDatabase db, long start, long end) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT COUNT(*) FROM pomodoro_sessions WHERE completed_at >= ? AND completed_at < ?",
                new String[]{String.valueOf(start), String.valueOf(end)});
            if (cursor.moveToFirst()) return cursor.getInt(0);
            return 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private Calendar dayStart(Calendar cal) {
        Calendar copy = (Calendar) cal.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private Calendar weekStart(Calendar cal) {
        Calendar copy = dayStart(cal);
        String weekStartPref = android.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .getString("week_start_day", "monday");
        int firstDay = "sunday".equals(weekStartPref)
            ? Calendar.SUNDAY : Calendar.MONDAY;
        copy.set(Calendar.DAY_OF_WEEK, firstDay);
        if (copy.getTimeInMillis() > cal.getTimeInMillis()) {
            copy.add(Calendar.WEEK_OF_YEAR, -1);
        }
        return copy;
    }

    private String formatMonthDay(Calendar cal) {
        return String.format(Locale.getDefault(), "%d/%d",
            cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    public static class DailyCount {
        public final String date;
        public final int count;
        public DailyCount(String date, int count) {
            this.date = date;
            this.count = count;
        }
    }

    public static class TrendPoint {
        public final String label;
        public final int count;
        public TrendPoint(String label, int count) {
            this.label = label;
            this.count = count;
        }
    }

    public static class PomodoroSession {
        public final long id;
        public final long startedAt;
        public final int durationMin;
        public PomodoroSession(long id, long startedAt, int durationMin) {
            this.id = id;
            this.startedAt = startedAt;
            this.durationMin = durationMin;
        }
    }

    public static class Summary {
        public final int totalCount;
        public final int totalMinutes;
        public final float dailyAvg;
        public Summary(int totalCount, int totalMinutes, float dailyAvg) {
            this.totalCount = totalCount;
            this.totalMinutes = totalMinutes;
            this.dailyAvg = dailyAvg;
        }
    }
}
