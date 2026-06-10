package com.matburt.mobileorg.Services;

/**
 * Pure-logic pomodoro timer with consecutive mode state machine.
 * No Android dependencies. Tracks countdown state, timeout detection,
 * rest periods, and round progress. Testable with plain JUnit.
 *
 * States: IDLE → WORK → (timeout) → WORK(timedOut) → (finish) → REST → WAITING_NEXT → WORK
 */
public class PomodoroTimer {

    public enum PomodoroState {
        IDLE, WORK, REST, WAITING_NEXT
    }

    private long startTime;
    private int durationMinutes;
    private boolean running = false;
    private boolean timedOut = false;

    private int totalCount = 1;
    private int currentRound = 0;
    private PomodoroState state = PomodoroState.IDLE;

    private long restStartTime;
    private int restDurationMinutes;

    /** Start a new consecutive pomodoro session with N rounds. */
    public void start(int durationMinutes, int totalCount) {
        this.durationMinutes = durationMinutes;
        this.totalCount = Math.max(1, totalCount);
        this.currentRound = 1;
        this.startTime = System.currentTimeMillis();
        this.running = true;
        this.timedOut = false;
        this.state = PomodoroState.WORK;
    }

    /** Stop/cancel the pomodoro (user-initiated). Reset to IDLE. */
    public void stop() {
        this.running = false;
        this.timedOut = false;
        this.state = PomodoroState.IDLE;
        this.totalCount = 1;
        this.currentRound = 0;
    }

    /** Mark that the pomodoro has timed out. Stays in WORK state. */
    public void markTimeout() {
        this.timedOut = true;
    }

    /** Switch to REST state with given duration. Duration 0 is allowed. */
    public void startRest(int durationMinutes) {
        this.restDurationMinutes = durationMinutes;
        this.restStartTime = System.currentTimeMillis();
        this.running = false;
        this.timedOut = false;
        this.state = PomodoroState.REST;
    }

    /** Switch to WAITING_NEXT state (rest ended, waiting for user confirmation). */
    public void setWaitingNext() {
        this.state = PomodoroState.WAITING_NEXT;
    }

    /** Advance to next work round. Resets timer with same duration. */
    public void advanceToNextWork(int durationMinutes) {
        this.currentRound++;
        this.durationMinutes = durationMinutes;
        this.startTime = System.currentTimeMillis();
        this.running = true;
        this.timedOut = false;
        this.state = PomodoroState.WORK;
    }

    // --- State queries ---

    public boolean isRunning() { return running; }
    public boolean isTimedOut() { return timedOut; }
    public long getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getTotalCount() { return totalCount; }
    public int getCurrentRound() { return currentRound; }
    public PomodoroState getState() { return state; }

    /** Active = any state except IDLE. Used by outline menu to show Stop/Pomodoro. */
    public boolean isActive() { return state != PomodoroState.IDLE; }

    public boolean isResting() { return state == PomodoroState.REST; }
    public boolean isWaitingNext() { return state == PomodoroState.WAITING_NEXT; }

    /** Round progress string, e.g. "2/4". Empty if totalCount <= 1. */
    public String getRoundProgress() {
        if (totalCount <= 1) return "";
        return currentRound + "/" + totalCount;
    }

    // --- Time display ---

    /** Remaining time string for WORK state, e.g. "24:30" or "+5:10" if overtime. Empty if not in WORK. */
    public String getRemainingString() {
        if (state != PomodoroState.WORK) return "";
        if (timedOut) {
            long overtime = System.currentTimeMillis() - (startTime + durationMinutes * 60L * 1000L);
            return "+" + formatMillisAsTime(overtime);
        }
        long remaining = (durationMinutes * 60L * 1000L) - (System.currentTimeMillis() - startTime);
        return formatMillisAsTime(Math.max(0, remaining));
    }

    /** Title string for notification: "🍅 2/4 | 23:30" or "🍅 23:30" */
    public String getTitleString(String nodeName) {
        String timeStr = getRemainingString();
        String progress = getRoundProgress();
        StringBuilder sb = new StringBuilder("\uD83C\uDF45 ");
        sb.append(timeStr);
        if (!progress.isEmpty()) {
            sb.append(" | ").append(progress);
        }
        // nodeName parameter kept for API compatibility but not used (spec: no node name display)
        return sb.toString();
    }

    /** Remaining millis for WORK state. Used for scheduling timeout. */
    public long getRemainingMillis() {
        return (durationMinutes * 60L * 1000L) - (System.currentTimeMillis() - startTime);
    }

    /** Rest remaining time string, e.g. "3:21". Empty if not resting. */
    public String getRestRemainingString() {
        if (state != PomodoroState.REST) return "";
        long elapsed = System.currentTimeMillis() - restStartTime;
        long remaining = (restDurationMinutes * 60L * 1000L) - elapsed;
        return formatMillisAsTime(Math.max(0, remaining));
    }

    /** Rest remaining millis. Used for scheduling rest timeout. */
    public long getRestRemainingMillis() {
        if (state != PomodoroState.REST) return 0;
        long elapsed = System.currentTimeMillis() - restStartTime;
        return (restDurationMinutes * 60L * 1000L) - elapsed;
    }

    /** Get rest duration in minutes. */
    public int getRestDurationMinutes() { return restDurationMinutes; }

    /** Format milliseconds as "H:MM". */
    public static String formatMillisAsTime(long millis) {
        long totalMinutes = millis / (60 * 1000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%d:%02d", hours, minutes);
    }
}
