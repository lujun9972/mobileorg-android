package com.matburt.mobileorg.test.Services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.matburt.mobileorg.Services.CalendarWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Behavioral spec for calendar selection state.
 *
 * Root-cause lock for the crash:
 *   java.lang.IllegalArgumentException: Couldn't find selected calendar:
 *   (empty name) at CalendarWrapper.insertEntry
 *
 * Enabling calendarEnabled without ever picking a calendar leaves the
 * calendarName preference at its XML default "" — getCalendarID("") can never
 * match a device calendar, so calendarId stays -1 and the push path used to
 * blow up the sync thread. A wrapper whose calendar is not selected must say
 * so, so service entry points can skip the push instead of crashing.
 *
 * Execution-order note: these tests shell-grant calendar permissions and never
 * revoke (revoking a granted permission force-kills the app process, i.e. the
 * instrumentation itself). Any test class that depends on the DENIED state
 * (e.g. Gui.SettingsCalendarPermissionTest) must run BEFORE this class —
 * class discovery is alphabetical and Gui < Services.
 */
@RunWith(AndroidJUnit4.class)
public class CalendarWrapperTest {

	private static final String APP_PKG = "com.matburt.mobileorg";
	private static final String KEY_CALENDAR_NAME = "calendarName";

	private Context context;
	private SharedPreferences prefs;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.edit().remove(KEY_CALENDAR_NAME).commit();
		// refreshPreferences() queries the calendar provider; without
		// READ_CALENDAR that query throws SecurityException before the
		// selection state is even computed.
		grantCalendarPermissions();
	}

	@Test
	public void testEmptyCalendarNameMeansNotSelected() {
		CalendarWrapper wrapper = new CalendarWrapper(context);
		wrapper.refreshPreferences();

		assertFalse("未选择日历（calendarName 为空）时 isCalendarSelected 必须为 false",
				wrapper.isCalendarSelected());
	}

	@Test
	public void testEmptyCalendarNameYieldsNoCalendarId() {
		CalendarWrapper wrapper = new CalendarWrapper(context);

		assertEquals("空日历名在设备日历中必然找不到，应返回 -1",
				-1, wrapper.getCalendarID(""));
	}

	private static void grantCalendarPermissions() {
		shell("pm grant " + APP_PKG + " android.permission.READ_CALENDAR");
		shell("pm grant " + APP_PKG + " android.permission.WRITE_CALENDAR");
	}

	private static String shell(String cmd) {
		try {
			ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
					.getUiAutomation().executeShellCommand(cmd);
			if (pfd == null)
				return "";
			StringBuilder sb = new StringBuilder();
			try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = fis.read(buf)) > 0) {
					sb.append(new String(buf, 0, n, "UTF-8"));
				}
			}
			return sb.toString();
		} catch (IOException e) {
			return "";
		}
	}
}
