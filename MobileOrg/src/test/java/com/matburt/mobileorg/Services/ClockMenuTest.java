package com.matburt.mobileorg.Services;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockMenuTest {

    // --- decide() : 长按菜单 clock 项的状态决策 ---

    @Test
    public void noClockIn_menuShowsClockIn() {
        assertEquals(ClockMenu.State.CLOCK_IN,
                ClockMenu.decide(-1, 42));
    }

    @Test
    public void thisNodeClocked_menuShowsClockOut() {
        assertEquals(ClockMenu.State.CLOCK_OUT,
                ClockMenu.decide(42, 42));
    }

    @Test
    public void otherNodeClocked_menuShowsSwitch() {
        assertEquals(ClockMenu.State.SWITCH,
                ClockMenu.decide(7, 42));
    }

    @Test
    public void serviceDead_menuShowsClockIn() {
        // service 为 null 时调用方传 -1
        assertEquals(ClockMenu.State.CLOCK_IN,
                ClockMenu.decide(-1, 42));
    }
}
