package com.matburt.mobileorg.test.Gui;

import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;

import com.matburt.mobileorg.Gui.Capture.EditActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivityController;
import com.matburt.mobileorg.Gui.Capture.TagsFragment;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.test.util.OrgTestUtils;

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
public class TagsFragmentTest {
	private final String TAGS_FRAGMENT = "tagsFragment";

	@Rule
	public ActivityTestRule<EditActivity> activityRule = new ActivityTestRule<>(EditActivity.class, true, false);

	private ContentResolver resolver;
	private OrgNodeRepository repo;
	private OrgNode node;
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

	private void prepareActivityWithTags(String tags) {
		this.node = OrgTestUtils.getDefaultOrgNode();
		this.node.tags = tags;
		this.repo.write(node);
		this.nodeId = node.id;

		Intent intent = new Intent();
		intent.putExtra(EditActivityController.ACTIONMODE, EditActivityController.ACTIONMODE_EDIT);
		intent.putExtra(EditActivityController.NODE_ID, node.id);
		activityRule.launchActivity(intent);
	}

	private void saveAndRestoreState(final TagsFragment tagsFragment) {
		activityRule.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				Bundle outState = new Bundle();
				tagsFragment.onSaveInstanceState(outState);
				tagsFragment.restoreFromBundle(outState);
			}
		});
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testSetup() {
		prepareActivityWithTags("");
		assertNotNull(activityRule.getActivity());

		TagsFragment tagsFragment = ((TagsFragment) activityRule.getActivity()
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT));
		assertNotNull(tagsFragment);
	}

	@Test
	public void testSimple() {
		final String tags = "tag1:tag2";
		prepareActivityWithTags(tags);

		TagsFragment tagsFragment = ((TagsFragment) activityRule.getActivity()
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT));
		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}

	@Test
	public void testSaveAndRestore() {
		final String tags = "tag1:tag2::tag4:tag10";
		prepareActivityWithTags(tags);

		final TagsFragment tagsFragment = ((TagsFragment) activityRule.getActivity()
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT));
		saveAndRestoreState(tagsFragment);

		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}

	@Test
	public void testAddEntry() {
		String tags = "tag1:tag4::tag2:tag10";
		prepareActivityWithTags(tags);

		final TagsFragment tagsFragment = ((TagsFragment) activityRule.getActivity()
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT));
		final String addedTag = "hello";
		activityRule.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				tagsFragment.addTagEntry(addedTag);
			}
		});
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		tags += ":" + addedTag;
		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}

	@Test
	public void testAddEntryAndSaveAndRestore() {
		String tags = "tag1:tag4::tag2:tag10";
		prepareActivityWithTags(tags);

		final TagsFragment tagsFragment = ((TagsFragment) activityRule.getActivity()
				.getSupportFragmentManager().findFragmentByTag("tagsFragment"));
		final String addedTag = "hello";
		activityRule.getActivity().runOnUiThread(new Runnable() {
			public void run() {
				tagsFragment.addTagEntry(addedTag);
			}
		});
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		saveAndRestoreState(tagsFragment);

		tags += ":" + addedTag;
		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}
}
