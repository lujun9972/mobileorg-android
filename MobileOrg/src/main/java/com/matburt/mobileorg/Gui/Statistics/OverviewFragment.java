package com.matburt.mobileorg.Gui.Statistics;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.card.MaterialCardView;
import com.matburt.mobileorg.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class OverviewFragment extends Fragment {

    private StatisticsViewModel viewModel;
    private PomodoroStatisticsRepository repo;
    private com.github.mikephil.charting.charts.BarChart barChart;
    private TextView streakText;
    private TextView streakSubtitle;
    private TextView summaryText;
    private MaterialCardView streakCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        barChart = view.findViewById(R.id.bar_chart);
        streakText = view.findViewById(R.id.streak_text);
        streakSubtitle = view.findViewById(R.id.streak_subtitle);
        summaryText = view.findViewById(R.id.summary_text);
        streakCard = view.findViewById(R.id.streak_card);

        repo = new PomodoroStatisticsRepository(requireContext());
        viewModel = new ViewModelProvider(requireActivity()).get(StatisticsViewModel.class);

        ChartThemeConfig config = ChartThemeConfig.current();
        configureBarChart(config);
        streakCard.setCardBackgroundColor(config.streakCardColor);

        viewModel.getTimeRange().observe(getViewLifecycleOwner(), this::refreshData);
    }

    private void configureBarChart(ChartThemeConfig config) {
        barChart.getDescription().setEnabled(false);
        barChart.setFitBars(true);
        barChart.getXAxis().setGranularity(1f);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setTextColor(config.textColor);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setGranularity(1f);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setTextColor(config.textColor);
        barChart.setBackgroundColor(config.backgroundColor);

        barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                StatisticsViewModel.TimeRange range = viewModel.getTimeRange().getValue();
                if (range == null) return;
                long baseStart;
                if (range.granularity == StatisticsViewModel.Granularity.DAY) {
                    baseStart = range.rangeStart - 6L * 86400000L;
                } else {
                    baseStart = range.rangeStart;
                }
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(baseStart);
                cal.add(Calendar.DAY_OF_MONTH, (int) e.getX());
                long dayStart = dayStartMillis(cal);
                DayDetailBottomSheet.newInstance(dayStart)
                        .show(getChildFragmentManager(), "day_detail");
            }

            @Override
            public void onNothingSelected() {}
        });
    }

    private void refreshData(StatisticsViewModel.TimeRange range) {
        if (range == null) return;

        int streak = repo.getStreak();
        if (streak > 0) {
            streakText.setText(getString(R.string.statistics_streak_format, streak));
            streakSubtitle.setText(R.string.statistics_streak_subtitle);
        } else {
            streakText.setText(R.string.statistics_streak_empty);
            streakSubtitle.setText("");
        }

        List<PomodoroStatisticsRepository.DailyCount> counts;
        List<String> xLabels = new ArrayList<>();
        int dayCount;

        switch (range.granularity) {
            case DAY:
                long start6 = range.rangeStart - 6L * 86400000L;
                counts = repo.getDailyCounts(start6, range.rangeEnd);
                fillDayLabels(xLabels, start6, 7);
                dayCount = 1;
                break;
            case WEEK:
                counts = repo.getDailyCounts(range.rangeStart, range.rangeEnd);
                fillWeekDayLabels(xLabels);
                dayCount = 7;
                break;
            case MONTH:
                counts = repo.getDailyCounts(range.rangeStart, range.rangeEnd);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(range.rangeStart);
                int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                fillMonthLabels(xLabels, daysInMonth);
                dayCount = daysInMonth;
                break;
            default:
                return;
        }

        List<BarEntry> entries = buildBarEntries(range, counts, xLabels);
        ChartThemeConfig config = ChartThemeConfig.current();

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(config.barColor);
        dataSet.setValueTextColor(config.textColor);
        BarData barData = new BarData(dataSet);
        barChart.setData(barData);

        barChart.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                if (idx >= 0 && idx < xLabels.size()) return xLabels.get(idx);
                return "";
            }
        });

        if (range.granularity == StatisticsViewModel.Granularity.MONTH) {
            barChart.setVisibleXRangeMaximum(14);
        } else {
            barChart.setVisibleXRangeMaximum(7);
        }

        barChart.invalidate();

        PomodoroStatisticsRepository.Summary summary = repo.getSummary(
                range.rangeStart, range.rangeEnd, dayCount);
        String summaryStr;
        switch (range.granularity) {
            case DAY:
                summaryStr = getString(R.string.statistics_summary_day_format,
                        summary.totalCount, formatMinutes(summary.totalMinutes));
                break;
            case WEEK:
                summaryStr = getString(R.string.statistics_summary_week_format,
                        summary.totalCount, formatMinutes(summary.totalMinutes), summary.dailyAvg);
                break;
            case MONTH:
                summaryStr = getString(R.string.statistics_summary_month_format,
                        summary.totalCount, formatMinutes(summary.totalMinutes), summary.dailyAvg);
                break;
            default:
                summaryStr = "";
        }
        summaryText.setText(summaryStr);
    }

    private List<BarEntry> buildBarEntries(StatisticsViewModel.TimeRange range,
                                            List<PomodoroStatisticsRepository.DailyCount> counts,
                                            List<String> xLabels) {
        int size = xLabels.size();
        int[] values = new int[size];

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();

        switch (range.granularity) {
            case DAY: {
                long start6 = range.rangeStart - 6L * 86400000L;
                for (PomodoroStatisticsRepository.DailyCount dc : counts) {
                    try {
                        cal.setTime(sdf.parse(dc.date));
                        int idx = (int) ((cal.getTimeInMillis() - start6) / 86400000L);
                        if (idx >= 0 && idx < size) values[idx] = dc.count;
                    } catch (Exception ignored) {}
                }
                break;
            }
            case WEEK: {
                for (int i = 0; i < 7; i++) {
                    cal.setTimeInMillis(range.rangeStart);
                    cal.add(Calendar.DAY_OF_MONTH, i);
                    String dayStr = sdf.format(cal.getTime());
                    for (PomodoroStatisticsRepository.DailyCount dc : counts) {
                        if (dc.date.equals(dayStr)) { values[i] = dc.count; break; }
                    }
                }
                break;
            }
            case MONTH: {
                for (int i = 0; i < size; i++) {
                    cal.setTimeInMillis(range.rangeStart);
                    cal.add(Calendar.DAY_OF_MONTH, i);
                    String dayStr = sdf.format(cal.getTime());
                    for (PomodoroStatisticsRepository.DailyCount dc : counts) {
                        if (dc.date.equals(dayStr)) { values[i] = dc.count; break; }
                    }
                }
                break;
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new BarEntry(i, values[i]));
        }
        return entries;
    }

    private void fillDayLabels(List<String> labels, long start, int count) {
        SimpleDateFormat sdf = new SimpleDateFormat("M/d", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < count; i++) {
            cal.setTimeInMillis(start + i * 86400000L);
            labels.add(sdf.format(cal.getTime()));
        }
    }

    private void fillWeekDayLabels(List<String> labels) {
        String weekStartPref = android.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getString("week_start_day", "monday");
        if ("sunday".equals(weekStartPref)) {
            labels.add("日"); labels.add("一"); labels.add("二");
            labels.add("三"); labels.add("四"); labels.add("五"); labels.add("六");
        } else {
            labels.add("一"); labels.add("二"); labels.add("三");
            labels.add("四"); labels.add("五"); labels.add("六"); labels.add("日");
        }
    }

    private void fillMonthLabels(List<String> labels, int daysInMonth) {
        for (int i = 1; i <= daysInMonth; i++) {
            labels.add(String.valueOf(i));
        }
    }

    private long dayStartMillis(Calendar cal) {
        Calendar copy = (Calendar) cal.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy.getTimeInMillis();
    }

    private String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h > 0) return h + "h" + (m > 0 ? m + "m" : "");
        return m + "m";
    }
}
