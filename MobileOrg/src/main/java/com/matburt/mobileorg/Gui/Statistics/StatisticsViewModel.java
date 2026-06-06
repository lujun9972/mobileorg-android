package com.matburt.mobileorg.Gui.Statistics;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Calendar;

public class StatisticsViewModel extends AndroidViewModel {

    public enum Granularity { DAY, WEEK, MONTH }

    public static class TimeRange {
        public final Granularity granularity;
        public final long rangeStart;
        public final long rangeEnd;
        public TimeRange(Granularity g, long start, long end) {
            this.granularity = g;
            this.rangeStart = start;
            this.rangeEnd = end;
        }
    }

    private final MutableLiveData<TimeRange> timeRange = new MutableLiveData<>();

    public StatisticsViewModel(@NonNull Application application) {
        super(application);
        setTimeRange(Granularity.WEEK);
    }

    public LiveData<TimeRange> getTimeRange() { return timeRange; }

    public void setTimeRange(Granularity granularity) {
        setTimeRange(granularity, null);
    }

    public void setTimeRange(Granularity granularity, Long anchorMillis) {
        long anchor = anchorMillis != null ? anchorMillis : System.currentTimeMillis();
        long start, end;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(anchor);

        switch (granularity) {
            case DAY:
                start = dayStart(cal).getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, 1);
                end = cal.getTimeInMillis();
                break;
            case WEEK:
                start = weekStart(cal).getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, 7);
                end = cal.getTimeInMillis();
                break;
            case MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                start = dayStart(cal).getTimeInMillis();
                cal.add(Calendar.MONTH, 1);
                end = cal.getTimeInMillis();
                break;
            default:
                start = 0;
                end = 0;
        }
        timeRange.setValue(new TimeRange(granularity, start, end));
    }

    public void navigatePrevious() {
        TimeRange current = timeRange.getValue();
        if (current == null) return;
        long anchor = current.rangeStart - 1;
        setTimeRange(current.granularity, anchor);
    }

    public void navigateNext() {
        TimeRange current = timeRange.getValue();
        if (current == null) return;
        setTimeRange(current.granularity, current.rangeEnd);
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
                .getDefaultSharedPreferences(getApplication())
                .getString("week_start_day", "monday");
        int firstDay = "sunday".equals(weekStartPref) ? Calendar.SUNDAY : Calendar.MONDAY;
        copy.set(Calendar.DAY_OF_WEEK, firstDay);
        if (copy.getTimeInMillis() > cal.getTimeInMillis()) {
            copy.add(Calendar.WEEK_OF_YEAR, -1);
        }
        return copy;
    }
}
