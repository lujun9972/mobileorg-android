package com.matburt.mobileorg.OrgData;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PomodoroLogSerializerTest {

    private PomodoroSession session() {
        return new PomodoroSession(1763258400000L, 1763260500000L, 25,
                "olp:home.org:工作/MobileOrg 开发", "红米Note11");
    }

    @Test
    public void serialize_containsClockLineAndProperties() {
        List<PomodoroSession> sessions = new ArrayList<>();
        sessions.add(session());
        String out = PomodoroLogSerializer.serialize(sessions);
        // headline 与 CLOCK 行结构（不断言日期字符串——CI 与本地时区不同）
        assertTrue(out.contains("番茄 25min"));
        assertTrue(out.contains("CLOCK: ["));
        assertTrue(out.contains(":STARTED_AT: 1763258400000"));
        assertTrue(out.contains(":COMPLETED_AT: 1763260500000"));
        assertTrue(out.contains(":DURATION: 25"));
        assertTrue(out.contains(":NODE: olp:home.org:工作/MobileOrg 开发"));
        assertTrue(out.contains(":DEVICE: 红米Note11"));
        assertTrue(out.contains("=>  0:25"));
    }

    @Test
    public void serialize_nullNodeOmitsNodeProperty() {
        List<PomodoroSession> sessions = new ArrayList<>();
        sessions.add(new PomodoroSession(1763258400000L, 1763260500000L, 25, null, null));
        String out = PomodoroLogSerializer.serialize(sessions);
        assertFalse(out.contains(":NODE:"));
        assertFalse(out.contains(":DEVICE:"));
    }

    @Test
    public void serialize_emptyListProducesHeaderOnly() {
        String out = PomodoroLogSerializer.serialize(new ArrayList<>());
        assertTrue(out.contains("MobileOrg 自动生成"));
        assertFalse(out.contains("* "));
    }
}
