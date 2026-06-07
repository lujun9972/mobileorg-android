package com.matburt.mobileorg.test.Gui;

import android.content.ContentResolver;
import android.content.Intent;

import com.matburt.mobileorg.Gui.Capture.EditActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivityController;
import com.matburt.mobileorg.Gui.Capture.LocationFragment;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.OrgData.OrgFileRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class LocationFragmentTest {
	private final String LOCATION_FRAGMENT = "locationFragment";

	@Rule
	public ActivityTestRule<EditActivity> activityRule = new ActivityTestRule<>(EditActivity.class, true, false);

	private ContentResolver resolver;
	private OrgNodeRepository repo;
	private LocationFragment locationFragment;
	private long nodeId = -1;

	@Before
	public void setUp() throws Exception {
		this.resolver = InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver();
		this.repo = new OrgNodeRepository(resolver);
	}

	@After
	public void tearDown() throws Exception {
		if(activityRule.getActivity() != null) {
			activityRule.getActivity().finish();
		}
		if(nodeId >= 0)
			resolver.delete(OrgData.buildIdUri(nodeId), null, null);
		this.nodeId = -1;
	}

	private void prepareActivityWithNode(OrgNode node, String actionMode) {
		Intent intent = new Intent();
		intent.putExtra(EditActivityController.ACTIONMODE, actionMode);
		intent.putExtra(EditActivityController.NODE_ID, node.id);
		activityRule.launchActivity(intent);

		this.locationFragment = ((LocationFragment) activityRule.getActivity()
				.getSupportFragmentManager().findFragmentByTag(LOCATION_FRAGMENT));
	}

	@Test
	public void testSetup() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		repo.write(node);
		this.nodeId = node.id;

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_EDIT);

		assertNotNull(activityRule.getActivity());
		assertNotNull(this.locationFragment);
	}

	@Test
	public void test_Create_Simple() {
		OrgNode node = new OrgNode();

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_CREATE);
		OrgNode locationNode = locationFragment.getLocationSelection();

		OrgFile captureFile = new OrgFileRepository(resolver).getOrCreateCaptureFile();
		OrgNode captureFileNode = new OrgFileRepository(resolver).getOrgNode(captureFile);
		assertEquals(captureFileNode.fileId, locationNode.fileId);
		assertEquals(captureFileNode.id, locationNode.id);
	}

	@Test
	public void test_Addchild_ToplevelFile() {
		OrgFile file = new OrgFileRepository(resolver).getOrCreateFile("test file.org", "delete me");
		OrgNode fileNode = new OrgFileRepository(resolver).getOrgNode(file);

		prepareActivityWithNode(fileNode, EditActivityController.ACTIONMODE_ADDCHILD);
		OrgNode locationNode = locationFragment.getLocationSelection();

		assertEquals(fileNode.name, locationNode.name);
		assertEquals(fileNode.id, locationNode.id);
		assertEquals(fileNode.fileId, locationNode.fileId);
	}

	@Test
	public void test_Addchild_ToplevelFileWithAddChild() {
		OrgFile captureFile = new OrgFileRepository(resolver).getOrCreateCaptureFile();
		OrgNode fileNode = new OrgFileRepository(resolver).getOrgNode(captureFile);

		prepareActivityWithNode(fileNode, EditActivityController.ACTIONMODE_ADDCHILD);

		activityRule.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				locationFragment.addChild(null, "");
			}
		});
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		OrgNode locationNode = locationFragment.getLocationSelection();

		assertEquals(fileNode.id, locationNode.id);
		assertEquals(fileNode.fileId, locationNode.fileId);
	}

	@Test
	public void test_Addchild_NestedChild() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_ADDCHILD);
		OrgNode locationNode = locationFragment.getLocationSelection();

		OrgTestUtils.cleanupParentScenario(resolver);
		assertEquals(node.id, locationNode.id);
		assertEquals(node.fileId, locationNode.fileId);
	}

	@Test
	public void test_Edit_NestedChild() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_EDIT);
		OrgNode locationNode = locationFragment.getLocationSelection();

		OrgTestUtils.cleanupParentScenario(resolver);
		assertEquals(node.parentId, locationNode.id);
		assertEquals(node.fileId, locationNode.fileId);
	}
}
