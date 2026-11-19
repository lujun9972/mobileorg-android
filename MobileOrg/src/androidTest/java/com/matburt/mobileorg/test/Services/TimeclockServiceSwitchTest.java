package com.matburt.mobileorg.test.Services;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.Services.TimeclockService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Behavioral spec: switching the clock from one node to another (second
 * ACTION_CLOCK_IN while clocked in) must keep the service alive with the
 * foreground notification intact, pointing at the new node.
 *
 * Reproduces the death chain: handleClockIn's switch path calls
 * doClockOut(true, -1) → checkStopSelf() sees the idle intermediate state
 * (old clock out, new clock not yet in) → cancelNotification() → its
 * trailing stopSelf() kills the service after onStartCommand returns —
 * even though handleClockIn then clock-ins the new node and re-posts the
 * notification. onDestroy → cancelNotification() wipes both. Before the
 * fix the service is dead (getInstance() == null, no notification) after
 * a switch; after it, the service survives and clocks the new node.
 *
 * Real-DB caveat: this suite clears the app's content tables in setUp —
 * CI emulator only. Never run connectedDebugAndroidTest against a device
 * holding unsynced user data.
 */
@RunWith(AndroidJUnit4.class)
public class TimeclockServiceSwitchTest {

	private static final String TEST_FILENAME = "timeswitch test.org";
	private static final long AWAIT_TIMEOUT_MS = 5000;

	private Context context;
	private ContentResolver resolver;
	private OrgNode nodeA;
	private OrgNode nodeB;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		resolver = context.getContentResolver();

		context.stopService(new Intent(context, TimeclockService.class));

		// DB state persists between tests — clear, then seed one file with
		// two switchable nodes.
		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);

		OrgFileRepository fileRepo = new OrgFileRepository(resolver);
		OrgNodeRepository nodeRepo = new OrgNodeRepository(resolver);
		OrgFile file = fileRepo.getOrCreateFile(TEST_FILENAME, "timeswitch");
		nodeA = new OrgNode();
		nodeA.name = "switch source";
		nodeA.parentId = file.nodeId;
		nodeA.fileId = file.id;
		nodeRepo.write(nodeA);
		nodeB = new OrgNode();
		nodeB.name = "switch target";
		nodeB.parentId = file.nodeId;
		nodeB.fileId = file.id;
		nodeRepo.write(nodeB);
	}

	@After
	public void tearDown() {
		context.stopService(new Intent(context, TimeclockService.class));
		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);
	}

	@Test
	public void testSwitchClockKeepsServiceAliveAndRetargets() throws Exception {
		clockIn(nodeA.id);
		awaitClockedOn(nodeA.id, "first clock in");

		// The bug path: switching while clocked in killed the service via
		// checkStopSelf() → cancelNotification() → stopSelf().
		clockIn(nodeB.id);
		awaitClockedOn(nodeB.id, "switch to another node");

		TimeclockService service = TimeclockService.getInstance();
		assertNotNull("service died during switch", service);

		NotificationManager nm = context.getSystemService(NotificationManager.class);
		assertTrue("foreground notification vanished after switch",
				nm.getActiveNotifications().length >= 1);
	}

	private void clockIn(long nodeId) {
		Intent intent = new Intent(context, TimeclockService.class);
		intent.setAction(TimeclockService.ACTION_CLOCK_IN);
		intent.putExtra(TimeclockService.NODE_ID, nodeId);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}

	/** Poll the in-process singleton; fail with a cause diagnosis on timeout. */
	private void awaitClockedOn(long nodeId, String stage) throws InterruptedException {
		long deadline = SystemClock.elapsedRealtime() + AWAIT_TIMEOUT_MS;
		while (SystemClock.elapsedRealtime() < deadline) {
			TimeclockService s = TimeclockService.getInstance();
			if (s != null && s.isClockedIn() && s.getNodeID() == nodeId) {
				return;
			}
			SystemClock.sleep(50);
		}
		TimeclockService s = TimeclockService.getInstance();
		fail(stage + ": never clocked on node " + nodeId
				+ " — service alive=" + (s != null)
				+ ", clockedIn=" + (s != null && s.isClockedIn()));
	}
}
