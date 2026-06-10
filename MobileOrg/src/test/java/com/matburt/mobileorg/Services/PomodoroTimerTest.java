package com.matburt.mobileorg.Services;

import org.junit.Test;
import static org.junit.Assert.*;

public class PomodoroTimerTest {

    // --- start() ---

    @Test
    public void startSingle_setsState() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 1);
        assertEquals(PomodoroTimer.PomodoroState.WORK, t.getState());
        assertTrue(t.isRunning());
        assertFalse(t.isTimedOut());
        assertEquals(1, t.getTotalCount());
        assertEquals(1, t.getCurrentRound());
        assertEquals(25, t.getDurationMinutes());
        assertTrue(t.isActive());
    }

    @Test
    public void startConsecutive_setsState() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        assertEquals(PomodoroTimer.PomodoroState.WORK, t.getState());
        assertEquals(4, t.getTotalCount());
        assertEquals(1, t.getCurrentRound());
        assertEquals("1/4", t.getRoundProgress());
    }

    @Test
    public void startZeroCount_treatedAsOne() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 0);
        assertEquals(1, t.getTotalCount());
        assertEquals("", t.getRoundProgress());
    }

    @Test
    public void startNegativeCount_treatedAsOne() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, -1);
        assertEquals(1, t.getTotalCount());
    }

    // --- markTimeout() ---

    @Test
    public void markTimeout_staysInWorkState() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        assertTrue(t.isTimedOut());
        assertEquals(PomodoroTimer.PomodoroState.WORK, t.getState());
        assertTrue(t.isRunning());
    }

    // --- startRest() ---

    @Test
    public void startRest_switchesToRestState() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        t.startRest(5);
        assertEquals(PomodoroTimer.PomodoroState.REST, t.getState());
        assertFalse(t.isRunning());
        assertFalse(t.isTimedOut());
        assertTrue(t.isResting());
        assertEquals(5, t.getRestDurationMinutes());
    }

    @Test
    public void startRestZero_allowed() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        t.startRest(0);
        assertEquals(PomodoroTimer.PomodoroState.REST, t.getState());
        assertEquals(0, t.getRestDurationMinutes());
        assertEquals(0, t.getRestRemainingMillis());
    }

    @Test
    public void getRestRemainingString_returnsEmptyWhenNotResting() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        assertEquals("", t.getRestRemainingString());
    }

    // --- setWaitingNext() ---

    @Test
    public void setWaitingNext_switchesState() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        t.startRest(5);
        t.setWaitingNext();
        assertEquals(PomodoroTimer.PomodoroState.WAITING_NEXT, t.getState());
        assertTrue(t.isWaitingNext());
        assertTrue(t.isActive());
    }

    // --- advanceToNextWork() ---

    @Test
    public void advanceToNextWork_incrementsRound() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        t.startRest(5);
        t.setWaitingNext();
        t.advanceToNextWork(25);
        assertEquals(2, t.getCurrentRound());
        assertEquals(PomodoroTimer.PomodoroState.WORK, t.getState());
        assertTrue(t.isRunning());
        assertFalse(t.isTimedOut());
        assertEquals("2/4", t.getRoundProgress());
    }

    // --- stop() ---

    @Test
    public void stop_fromWork_resetsToIdle() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.stop();
        assertEquals(PomodoroTimer.PomodoroState.IDLE, t.getState());
        assertFalse(t.isRunning());
        assertFalse(t.isActive());
        assertEquals(1, t.getTotalCount());
        assertEquals(0, t.getCurrentRound());
    }

    @Test
    public void stop_fromRest_resetsToIdle() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        t.startRest(5);
        t.stop();
        assertEquals(PomodoroTimer.PomodoroState.IDLE, t.getState());
        assertFalse(t.isActive());
    }

    @Test
    public void stop_fromWaitingNext_resetsToIdle() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        t.startRest(5);
        t.setWaitingNext();
        t.stop();
        assertEquals(PomodoroTimer.PomodoroState.IDLE, t.getState());
    }

    @Test
    public void stop_fromTimedOut_resetsToIdle() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        t.markTimeout();
        t.stop();
        assertEquals(PomodoroTimer.PomodoroState.IDLE, t.getState());
        assertFalse(t.isTimedOut());
    }

    // --- getRoundProgress() ---

    @Test
    public void getRoundProgress_singlePomodoro_returnsEmpty() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 1);
        assertEquals("", t.getRoundProgress());
    }

    @Test
    public void getRoundProgress_consecutive_returnsProgress() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        assertEquals("1/4", t.getRoundProgress());
        t.markTimeout();
        t.startRest(5);
        t.setWaitingNext();
        t.advanceToNextWork(25);
        assertEquals("2/4", t.getRoundProgress());
    }

    // --- getRemainingString() ---

    @Test
    public void getRemainingString_returnsEmptyWhenNotWorking() {
        PomodoroTimer t = new PomodoroTimer();
        assertEquals("", t.getRemainingString());

        t.start(25, 4);
        t.markTimeout();
        t.startRest(5);
        assertEquals("", t.getRemainingString());

        t.setWaitingNext();
        assertEquals("", t.getRemainingString());
    }

    // --- isActive() ---

    @Test
    public void isActive_falseWhenIdle() {
        PomodoroTimer t = new PomodoroTimer();
        assertFalse(t.isActive());
    }

    @Test
    public void isActive_trueForAllNonIdleStates() {
        PomodoroTimer t = new PomodoroTimer();

        t.start(25, 4);
        assertTrue(t.isActive());

        t.markTimeout();
        assertTrue(t.isActive());

        t.startRest(5);
        assertTrue(t.isActive());

        t.setWaitingNext();
        assertTrue(t.isActive());

        t.stop();
        assertFalse(t.isActive());
    }

    // --- getTitleString() ---

    @Test
    public void getTitleString_singlePomodoro_noProgress() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 1);
        String title = t.getTitleString(null);
        assertTrue(title.startsWith("\uD83C\uDF45 "));
        assertFalse(title.contains("/"));
    }

    @Test
    public void getTitleString_consecutive_includesProgress() {
        PomodoroTimer t = new PomodoroTimer();
        t.start(25, 4);
        String title = t.getTitleString(null);
        assertTrue(title.contains("1/4"));
    }

    // --- formatMillisAsTime ---

    @Test
    public void formatMillisAsTime_zero() {
        assertEquals("0:00", PomodoroTimer.formatMillisAsTime(0));
    }

    @Test
    public void formatMillisAsTime_minutes() {
        assertEquals("25:00", PomodoroTimer.formatMillisAsTime(25 * 60 * 1000));
    }

    @Test
    public void formatMillisAsTime_hoursAndMinutes() {
        assertEquals("1:30", PomodoroTimer.formatMillisAsTime(90 * 60 * 1000));
    }
}
