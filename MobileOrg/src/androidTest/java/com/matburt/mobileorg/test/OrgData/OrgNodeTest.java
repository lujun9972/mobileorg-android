package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import android.database.Cursor;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgNodeTest {

	@Rule
	public ProviderTestRule providerRule = new ProviderTestRule.Builder(
			OrgProvider.class, OrgProvider.class.getName()).build();

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testNodeToStringSimple() {
		OrgNode node = new OrgNode();
		node.name = "my simple test";
		node.todo = "TODO";
		node.level = 3;

		assertEquals("*** TODO my simple test", node.toString());
	}

	@Test
	public void testAddNodeSimple() throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(providerRule.getResolver());

		Cursor cursor = providerRule.getResolver().query(OrgData.buildIdUri(node.id),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());
		OrgNode insertedNode = new OrgNode(cursor);
		cursor.close();

		assertTrue(node.equals(insertedNode));
	}

	@Test
	public void testAddAndUpdateNode() throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(providerRule.getResolver());

		node.todo = "DONE";
		node.write(providerRule.getResolver());

		Cursor orgDataCursor = providerRule.getResolver().query(OrgData.CONTENT_URI, null, null,
				null, null);
		assertEquals(1, orgDataCursor.getCount());
		orgDataCursor.close();
		Cursor cursor = providerRule.getResolver().query(OrgData.buildIdUri(node.id),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());
		OrgNode insertedNode = new OrgNode(cursor);
		cursor.close();

		assertTrue(node.equals(insertedNode));
	}

	@Test
	public void testGetParentSimple() throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(providerRule.getResolver());

		OrgNode childNode = OrgTestUtils.getDefaultOrgNode();
		childNode.parentId = node.id;
		childNode.write(providerRule.getResolver());

		OrgNode parent = childNode.getParent(providerRule.getResolver());
		assertEquals(node.id, parent.id);
	}

	@Test
	public void testGetParentFileNode() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(providerRule.getResolver());
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.parentId = file.nodeId;
		node.write(providerRule.getResolver());

		OrgNode parent = node.getParent(providerRule.getResolver());
		assertEquals(file.nodeId, parent.id);
	}

	@Test
	public void testGetParentWithTopLevel() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(providerRule.getResolver());

		OrgNode node = new OrgNode(file.nodeId, providerRule.getResolver());

		try {
			node.getParent(providerRule.getResolver());
			fail("File shouldn't exist");
		} catch (OrgNodeNotFoundException e) {}
	}

	@Test
	public void testGetChildrenSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(providerRule.getResolver());

		OrgNode child1 = OrgTestUtils.getDefaultOrgNode();
		child1.parentId = node.id;
		child1.write(providerRule.getResolver());
		OrgNode child2 = OrgTestUtils.getDefaultOrgNode();
		child2.parentId = node.id;
		child2.write(providerRule.getResolver());

		ArrayList<OrgNode> children = node.getChildren(providerRule.getResolver());
		assertEquals(2, children.size());
	}

	@Test
	public void testArchiveNode() {
		OrgNode childNode = OrgTestUtils.setupParentScenario(providerRule.getResolver());
		childNode.archiveNode(providerRule.getResolver());

		try {
			new OrgNode(childNode.id, providerRule.getResolver());
			fail("Node should not exist");
		} catch (OrgNodeNotFoundException e) {}

		OrgTestUtils.cleanupParentScenario(providerRule.getResolver());
	}

	@Test
	public void testArchiveNodeGeneratesEdit() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(providerRule.getResolver());

		Cursor editCursor = providerRule.getResolver().query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = node.archiveNode(providerRule.getResolver());
		edit.type.equals(OrgEdit.TYPE.ARCHIVE);

		Cursor editCursor2 = providerRule.getResolver().query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testArchiveNodeToSibling() throws OrgNodeNotFoundException {
		OrgNode childNode = OrgTestUtils.setupParentScenario(providerRule.getResolver());
		OrgNode parent = childNode.getParent(providerRule.getResolver());

		childNode.archiveNodeToSibling(providerRule.getResolver());

		OrgNode archiveNode = parent.getChild(OrgNode.ARCHIVE_NODE, providerRule.getResolver());
		assertNotNull(archiveNode);

		assertEquals(archiveNode.id, childNode.parentId);
		assertEquals(archiveNode.fileId, childNode.fileId);
	}

	@Test
	public void testArchiveNodeToSiblingGeneratesEdit() {
		OrgNode node = OrgTestUtils.setupParentScenario(providerRule.getResolver());
		node.write(providerRule.getResolver());

		Cursor editCursor = providerRule.getResolver().query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = node.archiveNodeToSibling(providerRule.getResolver());
		edit.type.equals(OrgEdit.TYPE.ARCHIVE_SIBLING);

		Cursor editCursor2 = providerRule.getResolver().query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testGetOlpLink() {
		OrgNode node = OrgTestUtils.setupParentScenario(providerRule.getResolver());

		String olp = node.getOlpId(providerRule.getResolver());
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp);
	}

	@Test
	public void testGetNodeFromOlpLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgNode node = OrgTestUtils.setupParentScenario(providerRule.getResolver());

		String olp = node.getOlpId(providerRule.getResolver());
		OrgNode nodeFromOlpPath = OrgProviderUtils.getOrgNodeFromOlpPath(olp, providerRule.getResolver());
		assertEquals(node.id, nodeFromOlpPath.id);
	}

	@Test
	public void testGetNodeFromOlpFileLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgTestUtils.setupParentScenario(providerRule.getResolver());
		final String filename = OrgTestUtils.defaultTestfilename;
		OrgNode fileNode = OrgProviderUtils.getOrgNodeFromFilename(filename, providerRule.getResolver());
		final String olp = "olp:" + filename;

		OrgNode nodeFromOlpPath = OrgProviderUtils.getOrgNodeFromOlpPath(olp, providerRule.getResolver());

		assertEquals(fileNode.id, nodeFromOlpPath.id);
	}

	/**
	 * Checks if cookies ([1/3]) are stripped out of olp paths.
	 */
	@Test
	public void testGetOlpLinkWithCookie() {
		OrgNode node = OrgTestUtils.setupParentScenario(providerRule.getResolver());
		node.name += " [1/3]";

		String olp = node.getOlpId(providerRule.getResolver());
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp.trim());
	}

}
