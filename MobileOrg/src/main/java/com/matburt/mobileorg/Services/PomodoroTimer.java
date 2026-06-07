package com.matburt.mobileorg.Services;

/**
 * Pure-logic pomodoro timer. No Android dependencies.
 * Tracks countdown state, timeout detection, and remaining/overtime display.
 * Testable with plain JUnit.
 */
public class PomodoroTimer {
    private long startTime;
    private int durationMinutes;
    private boolean running = false;
    private boolean timedOut = false;

    /** Start a new pomodoro session. */
    public void start(int durationMinutes) {
        this.durationMinutes = durationMinutes;
        this.startTime = System.currentTimeMillis();
        this.running = true;
        this.timedOut = false;
    }

    /** Stop the pomodoro (user-initiated). */
    public void stop() {
        this.running = false;
        this.timedOut = false;
    }

    /** Mark that the pomodoro has timed out. */
    public void markTimeout() {
        this.timedOut = true;
    }

    public boolean isRunning() { return running; }
    public boolean isTimedOut() { return timedOut; }
    public long getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }

    /** Remaining time string, e.g. "24:30", or "+5:10" if overtime. Empty if not running. */
    public String getRemainingString() {
        if (!running) return "";
        if (timedOut) {
            long overtime = System.currentTimeMillis() - (startTime + durationMinutes * 60L * 1000L);
            return "+" + formatMillisAsTime(overtime);
        }
        long remaining = (durationMinutes * 60L * 1000L) - (System.currentTimeMillis() - startTime);
        return formatMillisAsTime(Math.max(0, remaining));
    }

    /** Title string for notification: "🍅 24:30" or "🍅 24:30 | TaskName" */
    public String getTitleString(String nodeName) {
        String timeStr = getRemainingString();
        if (nodeName != null && !nodeName.isEmpty()) {
            return "\uD83C\uDF45 " + timeStr + " | " + nodeName;
        }
        return "\uD83C\uDF45 " + timeStr;
    }

    /** Get remaining millis. Negative means overtime. Used for scheduling timeout. */
    public long getRemainingMillis() {
        return (durationMinutes * 60L * 1000L) - (System.currentTimeMillis() - startTime);
    }

    /** Format milliseconds as "H:MM". */
    public static String formatMillisAsTime(long millis) {
        long totalMinutes = millis / (60 * 1000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%d:%02d", hours, minutes);
    }
}
