package com.matburt.mobileorg.OrgData;

/** 一条番茄记录。跨设备身份是 startedAt（epoch millis）。纯数据载体，供同步投影/解析复用。 */
public class PomodoroSession {
    public final long startedAt;
    public final long completedAt;
    public final int durationMin;
    public final String nodeRef;  // olp 历史快照，可为 null
    public final String device;   // 可为 null

    public PomodoroSession(long startedAt, long completedAt, int durationMin,
                           String nodeRef, String device) {
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMin = durationMin;
        this.nodeRef = nodeRef;
        this.device = device;
    }
}
