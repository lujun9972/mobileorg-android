package com.matburt.mobileorg.Gui.Statistics;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.matburt.mobileorg.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DayDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DAY_START = "day_start_millis";

    public DayDetailBottomSheet newInstance(long dayStartMillis) {
        DayDetailBottomSheet sheet = new DayDetailBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_DAY_START, dayStartMillis);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_day_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        long dayStart = getArguments() != null ? getArguments().getLong(ARG_DAY_START, 0) : 0;

        TextView dayTitle = view.findViewById(R.id.day_title);
        RecyclerView sessionList = view.findViewById(R.id.session_list);
        TextView emptyText = view.findViewById(R.id.empty_text);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayStart);
        SimpleDateFormat titleFormat = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE);
        dayTitle.setText(titleFormat.format(cal.getTime()));

        PomodoroStatisticsRepository repo = new PomodoroStatisticsRepository(requireContext());
        List<PomodoroStatisticsRepository.PomodoroSession> sessions = repo.getSessionsForDate(dayStart);

        if (sessions.isEmpty()) {
            emptyText.setText(R.string.statistics_no_data);
            emptyText.setVisibility(View.VISIBLE);
            sessionList.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            sessionList.setVisibility(View.VISIBLE);
            sessionList.setLayoutManager(new LinearLayoutManager(requireContext()));
            sessionList.setAdapter(new SessionAdapter(sessions));
        }
    }

    private static class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {
        private final List<PomodoroStatisticsRepository.PomodoroSession> sessions;

        SessionAdapter(List<PomodoroStatisticsRepository.PomodoroSession> sessions) {
            this.sessions = sessions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 16, 32, 16);
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PomodoroStatisticsRepository.PomodoroSession session = sessions.get(position);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(session.startedAt);
            String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.getTime());
            String durationStr = holder.itemView.getContext()
                    .getString(R.string.duration_minutes_format, session.durationMin);
            ((TextView) holder.itemView).setText(timeStr + "  " + durationStr);
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(TextView tv) {
                super(tv);
            }
        }
    }
}
