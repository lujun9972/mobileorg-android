package com.matburt.mobileorg.test.Services;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.Services.CalendarSyncService;
import com.matburt.mobileorg.test.util.OrgTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Behavioral spec: pushing org nodes to the device calendar must be a no-op
 * (logged skip), never a crash, when no calendar has been selected.
 *
 * Reproduces the crash chain: SyncService finishes an org sync with
 * calendarEnabled=true but calendarName never chosen ("" default) →
 * CalendarSyncService push → insertEntry with calendarId=-1 → uncaught
 * IllegalArgumentException kills the process. Before the fix this test kills
 * the instrumentation process itself; after it, the push is skipped and
 * "no calendar selected" is logged.
 *
 * Execution-order note: shell-grants calendar permissions in setUp and never
 * revokes (revoking a granted permission force-kills the instrumentation
 * process). Classes depending on the DENIED state (e.g.
 * Gui.SettingsCalendarPermissionTest) must run first — discovery is
 * alphabetical and Gui < Services.
 */
@RunWith(AndroidJUnit4.class)
public class CalendarSyncServiceGuardTest {

	private static final String APP_PKG = "com.matburt.mobileorg";
	private static final String TEST_FILENAME = "calguard test.org";
	private static final String SKIP_MARKER = "no calendar selected";

	private Context context;
	private ContentResolver resolver;
	private SharedPreferences prefs;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		resolver = context.getContentResolver();
		prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// CalendarSyncService.onCreate stops itself without calendar
		// permissions, which would make this test vacuous.
		shell("pm grant " + APP_PKG + " android.permission.READ_CALENDAR");
		shell("pm grant " + APP_PKG + " android.permission.WRITE_CALENDAR");

		prefs.edit().remove("calendarName").commit();

		// DB state persists between tests — clear, then seed one file with a
		// node carrying SCHEDULED/DEADLINE dates so the push path has
		// something to insert.
		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);

		OrgFile file = new OrgFileRepository(resolver).getOrCreateFile(
				TEST_FILENAME, "calguard");
		OrgNode node = new OrgNode();
		node.name = "crash repro";
		node.parentId = file.nodeId;
		node.fileId = file.id;
		node.setPayload(OrgTestUtils.TestTimestampPayload.payload);
		new OrgNodeRepository(resolver).write(node);

		shell("logcat -c");
	}

	@After
	public void tearDown() {
		// NOTE: never revoke the calendar permissions here — revoking a
		// granted permission force-kills the app process (the instrumentation
		// itself). Gradle reinstalls both APKs after every run, so the next
		// run starts from the clean denied state.
		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);
		prefs.edit().remove("calendarName").commit();
	}

	@Test
	public void testPushWithoutSelectedCalendarSkipsSafely() throws Exception {
		Intent intent = new Intent(context, CalendarSyncService.class);
		intent.putExtra(CalendarSyncService.PUSH, true);
		intent.putExtra(CalendarSyncService.FILELIST,
				new String[] { TEST_FILENAME });
		context.startService(intent);

		// If the guard is missing, the service thread dies on an uncaught
		// IllegalArgumentException from insertEntry and this process (the
		// instrumentation) crashes before the marker ever appears.
		assertTrue("未选择日历时 push 应被跳过并记录日志（" + SKIP_MARKER + "）",
				waitForLogMarker(SKIP_MARKER, 15000));
		// Reaching this assertion means the process survived the push.
	}

	private boolean waitForLogMarker(String marker, long timeoutMs)
			throws Exception {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (shell("logcat -d -s MobileOrg:W").contains(marker))
				return true;
			Thread.sleep(250);
		}
		return false;
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
