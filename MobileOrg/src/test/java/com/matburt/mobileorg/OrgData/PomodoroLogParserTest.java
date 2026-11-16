package com.matburt.mobileorg.OrgData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;

public class PomodoroLogParserTest {

    private static final String ENTRY = "* 2026-11-16 10:00 番茄 25min\n"
            + ":LOGBOOK:\nCLOCK: [2026-11-16 Mon 10:00]--[2026-11-16 Mon 10:25] =>  0:25\n:END:\n"
            + ":PROPERTIES:\n:STARTED_AT: 1763258400000\n:COMPLETED_AT: 1763260500000\n:DURATION: 25\n"
            + ":NODE: olp:home.org:工作/MobileOrg 开发\n:DEVICE: 红米Note11\n:END:\n";

    @Test
    public void parse_extractsAllFieldsIgnoringNonPropertyLines() {
        String text = "# 由 MobileOrg 自动生成与管理，请勿手工编辑（Emacs 侧只读消费）\n\n"
                + ENTRY
                + "* 2026-11-17 09:00 番茄 35min\n:PROPERTIES:\n:STARTED_AT: 1763344800000\n"
                + ":COMPLETED_AT: 1763346900000\n:DURATION: 35\n:UNKNOWN: x\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(2, out.size());
        PomodoroSession a = out.get(0);
        assertEquals(1763258400000L, a.startedAt);
        assertEquals(1763260500000L, a.completedAt);
        assertEquals(25, a.durationMin);
        assertEquals("olp:home.org:工作/MobileOrg 开发", a.nodeRef);
        assertEquals("红米Note11", a.device);
        assertNull(out.get(1).nodeRef);
        assertNull(out.get(1).device);
    }

    @Test
    public void parse_skipsMalformedStartedAt() {
        String text = ENTRY
                + "* bad\n:PROPERTIES:\n:STARTED_AT: abc\n:COMPLETED_AT: 1763260500000\n"
                + ":DURATION: 25\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(1, out.size());
        assertEquals(1763258400000L, out.get(0).startedAt);
    }

    @Test
    public void parse_missingCompletedAtDerivesFromStartPlusDuration() {
        String text = "* s\n:PROPERTIES:\n:STARTED_AT: 1763258400000\n:DURATION: 25\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(1, out.size());
        assertEquals(1763258400000L + 25 * 60000L, out.get(0).completedAt);
    }

    @Test
    public void parse_invalidDurationFallsBackToTimeDiff() {
        String text = "* s\n:PROPERTIES:\n:STARTED_AT: 1763258400000\n:COMPLETED_AT: 1763260500000\n"
                + ":DURATION: 0\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(1, out.size());
        assertEquals(35, out.get(0).durationMin);   // (completed-started)/60000 = 35
    }

    @Test
    public void parse_emptyInputReturnsEmptyList() {
        assertEquals(0, PomodoroLogParser.parse("").size());
        assertEquals(0, PomodoroLogParser.parse(null).size());
    }
}
