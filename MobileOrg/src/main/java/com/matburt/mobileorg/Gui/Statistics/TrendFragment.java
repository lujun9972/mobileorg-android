package com.matburt.mobileorg.Gui.Statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.matburt.mobileorg.R;

import java.util.ArrayList;
import java.util.List;

public class TrendFragment extends Fragment {

    private StatisticsViewModel viewModel;
    private PomodoroStatisticsRepository repo;
    private com.github.mikephil.charting.charts.LineChart lineChart;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trend, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        lineChart = view.findViewById(R.id.line_chart);
        repo = new PomodoroStatisticsRepository(requireContext());
        viewModel = new ViewModelProvider(requireActivity()).get(StatisticsViewModel.class);

        ChartThemeConfig config = ChartThemeConfig.current();
        configureLineChart(config);

        viewModel.getTimeRange().observe(getViewLifecycleOwner(), this::refreshData);
    }

    private void configureLineChart(ChartThemeConfig config) {
        lineChart.getDescription().setEnabled(false);
        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.getXAxis().setGranularity(1f);
        lineChart.getXAxis().setTextColor(config.textColor);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setGranularity(1f);
        lineChart.getAxisLeft().setAxisMinimum(0f);
        lineChart.getAxisLeft().setTextColor(config.textColor);
        lineChart.setBackgroundColor(config.backgroundColor);
    }

    private void refreshData(StatisticsViewModel.TimeRange range) {
        if (range == null) return;

        int count;
        String granularity;
        switch (range.granularity) {
            case DAY:
                count = 7;
                granularity = "day";
                break;
            case WEEK:
                count = 4;
                granularity = "week";
                break;
            case MONTH:
                count = 12;
                granularity = "month";
                break;
            default:
                return;
        }

        List<PomodoroStatisticsRepository.TrendPoint> points =
                repo.getTrendData(count, granularity);

        List<Entry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            PomodoroStatisticsRepository.TrendPoint p = points.get(i);
            entries.add(new Entry(i, p.count));
            labels.add(p.label);
        }

        ChartThemeConfig config = ChartThemeConfig.current();
        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setColor(config.lineColor);
        dataSet.setValueTextColor(config.textColor);
        dataSet.setCircleColor(config.lineColor);
        dataSet.setDrawValues(true);

        lineChart.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                if (idx >= 0 && idx < labels.size()) return labels.get(idx);
                return "";
            }
        });

        lineChart.setData(new LineData(dataSet));
        lineChart.invalidate();
    }
}
