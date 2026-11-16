package com.matburt.mobileorg.OrgData;

import android.util.Log;

import java.util.ArrayList;

/**
 * pomodoros.org 文本 → session 行。
 * 只信 properties 的 STARTED_AT（epoch millis）；损坏条目跳过并打日志，
 * 不影响其余条目（容错规则见同步设计规格 §5）。
 */
public class PomodoroLogParser {

    public static ArrayList<PomodoroSession> parse(String orgText) {
        ArrayList<PomodoroSession> out = new ArrayList<>();
        if (orgText == null || orgText.isEmpty()) return out;

        long startedAt = -1, completedAt = -1;
        int durationMin = -1;
        String nodeRef = null, device = null;

        for (String rawLine : orgText.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("* ")) {
                // 新条目开始：先结算上一条
                PomodoroSession s = build(startedAt, completedAt, durationMin, nodeRef, device);
                if (s != null) out.add(s);
                startedAt = -1; completedAt = -1; durationMin = -1;
                nodeRef = null; device = null;
            } else if (line.startsWith(":STARTED_AT:")) {
                startedAt = parseLong(value(line));
            } else if (line.startsWith(":COMPLETED_AT:")) {
                completedAt = parseLong(value(line));
            } else if (line.startsWith(":DURATION:")) {
                durationMin = (int) parseLong(value(line));
            } else if (line.startsWith(":NODE:")) {
                nodeRef = value(line);
            } else if (line.startsWith(":DEVICE:")) {
                device = value(line);
            }
            // 其余行（头注释、headline 文本、LOGBOOK/CLOCK、:PROPERTIES:/:END:、
            // 未知 property）：忽略，向前兼容
        }
        PomodoroSession s = build(startedAt, completedAt, durationMin, nodeRef, device);
        if (s != null) out.add(s);
        return out;
    }

    private static PomodoroSession build(long startedAt, long completedAt,
                                         int durationMin, String nodeRef, String device) {
        if (startedAt <= 0) {
            if (durationMin >= 0 || completedAt > 0)
                logSkip("invalid STARTED_AT");
            return null;
        }
        if (durationMin <= 0 && completedAt > startedAt)
            durationMin = (int) ((completedAt - startedAt) / 60000L);
        if (durationMin <= 0) {
            logSkip("invalid DURATION");
            return null;
        }
        if (completedAt <= 0) completedAt = startedAt + durationMin * 60000L;
        return new PomodoroSession(startedAt, completedAt, durationMin, nodeRef, device);
    }

    /** JVM 单测无 android.jar 实现（Log 抛 RuntimeException），吞掉；设备上正常打日志。 */
    private static void logSkip(String reason) {
        try {
            Log.w("MobileOrg", "[PomodoroLog] skip entry: " + reason);
        } catch (RuntimeException ignored) {}
    }

    /** 提取 ":KEY: value" 的 value 部分；value 内的 ": " 之外冒号不受影响（olp 路径安全）。 */
    private static String value(String line) {
        int idx = line.indexOf(": ", 1);
        return idx < 0 ? "" : line.substring(idx + 2).trim();
    }

    private static long parseLong(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
