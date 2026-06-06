package com.matburt.mobileorg.Gui.Statistics;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class StatisticsActivity extends AppCompatActivity {
    private StatisticsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OrgUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.statistics_title);
        }

        viewModel = new ViewModelProvider(this).get(StatisticsViewModel.class);

        viewModel.getTimeRange().observe(this, range -> {
            TextView label = findViewById(R.id.range_label);
            label.setText(formatRangeLabel(range));
        });

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        viewPager.setAdapter(new StatsPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager,
            (tab, pos) -> tab.setText(pos == 0 ? R.string.statistics_tab_overview : R.string.statistics_tab_trend)
        ).attach();

        findViewById(R.id.btn_prev).setOnClickListener(v -> viewModel.navigatePrevious());
        findViewById(R.id.btn_next).setOnClickListener(v -> viewModel.navigateNext());

        MaterialButtonToggleGroup toggle = findViewById(R.id.granularity_toggle);
        toggle.check(R.id.btn_week);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_day) viewModel.setTimeRange(StatisticsViewModel.Granularity.DAY);
            else if (checkedId == R.id.btn_week) viewModel.setTimeRange(StatisticsViewModel.Granularity.WEEK);
            else if (checkedId == R.id.btn_month) viewModel.setTimeRange(StatisticsViewModel.Granularity.MONTH);
        });
    }

    private String formatRangeLabel(StatisticsViewModel.TimeRange range) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        switch (range.granularity) {
            case DAY:
                return sdf.format(new Date(range.rangeStart));
            case WEEK:
                return sdf.format(new Date(range.rangeStart)) + " ~ " + sdf.format(new Date(range.rangeEnd - 1));
            case MONTH:
                return new SimpleDateFormat("yyyy年M月").format(new Date(range.rangeStart));
        }
        return "";
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class StatsPagerAdapter extends FragmentStateAdapter {
        public StatsPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }
        @Override
        public int getItemCount() { return 2; }
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return position == 0 ? new OverviewFragment() : new TrendFragment();
        }
    }
}
