package com.matburt.mobileorg.test.util;

import com.matburt.mobileorg.util.ReminderScheduler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(JUnit4.class)
public class ReminderSchedulerTest {

    // === parseDateToCalendar ===

    @Test
    public void testParseDateWithTime() {
        Calendar cal = ReminderScheduler.parseDateToCalendar("2024-06-15 14:30");
        assertEquals(2024, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, cal.get(Calendar.MINUTE));
    }

    @Test
    public void testParseDateWithoutTime() {
        Calendar cal = ReminderScheduler.parseDateToCalendar("2024-06-15");
        assertEquals(2024, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, cal.get(Calendar.MINUTE));
    }

    @Test
    public void testParseDateWithOrgDayOfWeek() {
        // Org-mode format: "2024-06-15 Tue"
        Calendar cal = ReminderScheduler.parseDateToCalendar("2024-06-15 Tue");
        assertEquals(2024, cal.get(Calendar.YEAR));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testParseDateWithAngleBrackets() {
        // From org payload: "<2024-06-15 Sat>"
        Calendar cal = ReminderScheduler.parseDateToCalendar("2024-06-15 Sat");
        assertEquals(2024, cal.get(Calendar.YEAR));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testParseDateSingleDigitMonth() {
        Calendar cal = ReminderScheduler.parseDateToCalendar("2024-1-5 09:00");
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(5, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void testParseDateNull() {
        assertNull(ReminderScheduler.parseDateToCalendar(null));
    }

    @Test
    public void testParseDateEmpty() {
        assertNull(ReminderScheduler.parseDateToCalendar(""));
    }

    @Test
    public void testParseDateInvalid() {
        assertNull(ReminderScheduler.parseDateToCalendar("not a date"));
    }

    @Test
    public void testParseDateSecondsAreZero() {
        Calendar cal = ReminderScheduler.parseDateToCalendar("2024-06-15 14:30");
        assertEquals(0, cal.get(Calendar.SECOND));
        assertEquals(0, cal.get(Calendar.MILLISECOND));
    }

    // === formatDate ===

    @Test
    public void testFormatDateWithTime() {
        assertEquals("2024-06-15", ReminderScheduler.formatDate("2024-06-15 14:30"));
    }

    @Test
    public void testFormatDateWithoutTime() {
        assertEquals("2024-06-15", ReminderScheduler.formatDate("2024-06-15"));
    }

    @Test
    public void testFormatDateWithDayOfWeek() {
        assertEquals("2024-06-15", ReminderScheduler.formatDate("2024-06-15 Tue"));
    }

    @Test
    public void testFormatDateNoMatch() {
        assertEquals("not a date", ReminderScheduler.formatDate("not a date"));
    }
}
