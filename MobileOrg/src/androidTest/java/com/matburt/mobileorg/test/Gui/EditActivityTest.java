package com.matburt.mobileorg.test.Gui;

import android.content.ContentResolver;
import android.content.Intent;

import com.matburt.mobileorg.Gui.Capture.EditActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivityController;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.test.util.OrgTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class EditActivityTest {

	@Rule
	public ActivityTestRule<EditActivity> activityRule = new ActivityTestRule<>(EditActivity.class, true, false);

	private ContentResolver resolver;
	private OrgNodeRepository repo;
	private long nodeId;

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
		resolver.delete(OrgData.buildIdUri(nodeId), null, null);
	}

	private void prepareActivityWithNode(OrgNode node) {
		repo.write(node);
		this.nodeId = node.id;

		Intent intent = new Intent();
		intent.putExtra(EditActivityController.ACTIONMODE, EditActivityController.ACTIONMODE_EDIT);
		intent.putExtra(EditActivityController.NODE_ID, node.id);
		activityRule.launchActivity(intent);
	}

	@Test
	public void testSimple() {
		OrgNode node = new OrgNode();
		prepareActivityWithNode(node);

		assertFalse(activityRule.getActivity().hasEdits());
		OrgNode newNode = activityRule.getActivity().getEditedNode();
		assertTrue(node.equals(newNode));
	}

	@Test
	public void testGetUneditedBasic() {
		OrgNode node = OrgTestUtils.getComplexOrgNode();
		prepareActivityWithNode(node);

		OrgNode newNode = activityRule.getActivity().getEditedNode();
		assertTrue(node.equals(newNode));
	}
}
