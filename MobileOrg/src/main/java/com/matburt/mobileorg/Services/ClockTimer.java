package com.matburt.mobileorg.Services;

/**
 * Pure-logic clock timer. No Android dependencies.
 * Tracks clock-in/out state and elapsed time.
 * Testable with plain JUnit.
 */
public class ClockTimer {
    private long startTime;
    private long nodeId;
    private boolean clockedIn = false;

    /** Clock in to a specific node. */
    public void clockIn(long nodeId) {
        this.nodeId = nodeId;
        this.startTime = System.currentTimeMillis();
        this.clockedIn = true;
    }

    /**
     * Clock out and return the computed result.
     * @param editedDurationMinutes if > 0, use edited duration instead of actual elapsed
     * @return result with computed times, or null if not clocked in
     */
    public ClockOutResult clockOut(int editedDurationMinutes) {
        if (!clockedIn) return null;
        long endTime = System.currentTimeMillis();
        long computedStartTime;
        String elapsedTime;
        if (editedDurationMinutes > 0) {
            long durationMillis = editedDurationMinutes * 60L * 1000L;
            computedStartTime = endTime - durationMillis;
            int h = editedDurationMinutes / 60;
            int m = editedDurationMinutes % 60;
            elapsedTime = String.format("%d:%02d", h, m);
        } else {
            computedStartTime = startTime;
            elapsedTime = PomodoroTimer.formatMillisAsTime(endTime - startTime);
        }
        ClockOutResult result = new ClockOutResult(nodeId, computedStartTime, endTime, elapsedTime);
        this.clockedIn = false;
        this.nodeId = -1;
        this.startTime = 0;
        return result;
    }

    public boolean isClockedIn() { return clockedIn; }
    public long getNodeId() { return nodeId; }
    public long getStartTime() { return startTime; }

    /** Elapsed time string, e.g. "1:23". "0:00" if not clocked in. */
    public String getElapsedString() {
        if (!clockedIn) return "0:00";
        return PomodoroTimer.formatMillisAsTime(System.currentTimeMillis() - startTime);
    }

    /** Immutable result of a clock-out operation. */
    public static class ClockOutResult {
        public final long nodeId;
        public final long startTime;
        public final long endTime;
        public final String elapsedTime;

        public ClockOutResult(long nodeId, long startTime, long endTime, String elapsedTime) {
            this.nodeId = nodeId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.elapsedTime = elapsedTime;
        }
    }
}
