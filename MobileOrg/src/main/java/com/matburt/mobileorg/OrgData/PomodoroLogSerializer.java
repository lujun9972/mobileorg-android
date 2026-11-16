package com.matburt.mobileorg.OrgData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * pomodoro_sessions → pomodoros.org 文本投影。
 * CLOCK 行用名义时段（start → start+duration，规格见 ADR 0005 同步设计）；
 * 解析唯一可信来源是 STARTED_AT/COMPLETED_AT/DURATION properties（epoch millis）。
 * 仅同步线程调用（SimpleDateFormat 非线程安全）。
 */
public class PomodoroLogSerializer {
    public static final String HEADER =
            "# 由 MobileOrg 自动生成与管理，请勿手工编辑（Emacs 侧只读消费）\n";

    private static final SimpleDateFormat HEADLINE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    private static final SimpleDateFormat CLOCK_FMT =
            new SimpleDateFormat("yyyy-MM-dd E HH:mm", Locale.US);

    public static String serialize(List<PomodoroSession> sessions) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (PomodoroSession s : sessions) {
            long nominalEnd = s.startedAt + s.durationMin * 60000L;
            sb.append("* ").append(HEADLINE_FMT.format(new Date(s.startedAt)))
              .append(" 番茄 ").append(s.durationMin).append("min\n")
              .append(":LOGBOOK:\nCLOCK: [").append(CLOCK_FMT.format(new Date(s.startedAt)))
              .append("]--[").append(CLOCK_FMT.format(new Date(nominalEnd)))
              .append("] =>  ").append(String.format(Locale.US, "%d:%02d",
                      s.durationMin / 60, s.durationMin % 60))
              .append("\n:END:\n:PROPERTIES:\n")
              .append(":STARTED_AT: ").append(s.startedAt).append('\n')
              .append(":COMPLETED_AT: ").append(s.completedAt).append('\n')
              .append(":DURATION: ").append(s.durationMin).append('\n');
            if (s.nodeRef != null && !s.nodeRef.isEmpty())
                sb.append(":NODE: ").append(s.nodeRef).append('\n');
            if (s.device != null && !s.device.isEmpty())
                sb.append(":DEVICE: ").append(s.device).append('\n');
            sb.append(":END:\n");
        }
        return sb.toString();
    }
}
