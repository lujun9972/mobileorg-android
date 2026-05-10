package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import android.database.Cursor;
import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgNodeTest extends ProviderTestCase2<OrgProvider> {

	private MockContentResolver resolver;

	public OrgNodeTest() {
		super(OrgProvider.class, OrgProvider.class.getName());
	}

	@Before
	public void setUp() throws Exception {
		setContext(ApplicationProvider.getApplicationContext());
		super.setUp();  // THIS IS CRITICAL - initializes ProviderTestCase2
		this.resolver = getMockContentResolver();
		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();  // THIS IS CRITICAL
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
		node.write(resolver);

		Cursor cursor = resolver.query(OrgData.buildIdUri(node.id),
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
		node.write(resolver);

		node.todo = "DONE";
		node.write(resolver);

		Cursor orgDataCursor = resolver.query(OrgData.CONTENT_URI, null, null,
				null, null);
		assertEquals(1, orgDataCursor.getCount());
		orgDataCursor.close();
		Cursor cursor = resolver.query(OrgData.buildIdUri(node.id),
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
		node.write(resolver);

		OrgNode childNode = OrgTestUtils.getDefaultOrgNode();
		childNode.parentId = node.id;
		childNode.write(resolver);

		OrgNode parent = childNode.getParent(resolver);
		assertEquals(node.id, parent.id);
	}

	@Test
	public void testGetParentFileNode() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.parentId = file.nodeId;
		node.write(resolver);

		OrgNode parent = node.getParent(resolver);
		assertEquals(file.nodeId, parent.id);
	}

	@Test
	public void testGetParentWithTopLevel() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);

		OrgNode node = new OrgNode(file.nodeId, resolver);

		try {
			node.getParent(resolver);
			fail("File shouldn't exist");
		} catch (OrgNodeNotFoundException e) {}
	}

	@Test
	public void testGetChildrenSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);

		OrgNode child1 = OrgTestUtils.getDefaultOrgNode();
		child1.parentId = node.id;
		child1.write(resolver);
		OrgNode child2 = OrgTestUtils.getDefaultOrgNode();
		child2.parentId = node.id;
		child2.write(resolver);

		ArrayList<OrgNode> children = node.getChildren(resolver);
		assertEquals(2, children.size());
	}

	@Test
	public void testArchiveNode() {
		OrgNode childNode = OrgTestUtils.setupParentScenario(resolver);
		childNode.archiveNode(resolver);

		try {
			new OrgNode(childNode.id, resolver);
			fail("Node should not exist");
		} catch (OrgNodeNotFoundException e) {}

		OrgTestUtils.cleanupParentScenario(resolver);
	}

	@Test
	public void testArchiveNodeGeneratesEdit() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);

		Cursor editCursor = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = node.archiveNode(resolver);
		edit.type.equals(OrgEdit.TYPE.ARCHIVE);

		Cursor editCursor2 = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testArchiveNodeToSibling() throws OrgNodeNotFoundException {
		OrgNode childNode = OrgTestUtils.setupParentScenario(resolver);
		OrgNode parent = childNode.getParent(resolver);

		childNode.archiveNodeToSibling(resolver);

		OrgNode archiveNode = parent.getChild(OrgNode.ARCHIVE_NODE, resolver);
		assertNotNull(archiveNode);

		assertEquals(archiveNode.id, childNode.parentId);
		assertEquals(archiveNode.fileId, childNode.fileId);
	}

	@Test
	public void testArchiveNodeToSiblingGeneratesEdit() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);
		node.write(resolver);

		Cursor editCursor = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = node.archiveNodeToSibling(resolver);
		edit.type.equals(OrgEdit.TYPE.ARCHIVE_SIBLING);

		Cursor editCursor2 = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testGetOlpLink() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		String olp = node.getOlpId(resolver);
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp);
	}

	@Test
	public void testGetNodeFromOlpLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		String olp = node.getOlpId(resolver);
		OrgNode nodeFromOlpPath = OrgProviderUtils.getOrgNodeFromOlpPath(olp, resolver);
		assertEquals(node.id, nodeFromOlpPath.id);
	}

	@Test
	public void testGetNodeFromOlpFileLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgTestUtils.setupParentScenario(resolver);
		final String filename = OrgTestUtils.defaultTestfilename;
		OrgNode fileNode = OrgProviderUtils.getOrgNodeFromFilename(filename, resolver);
		final String olp = "olp:" + filename;

		OrgNode nodeFromOlpPath = OrgProviderUtils.getOrgNodeFromOlpPath(olp, resolver);

		assertEquals(fileNode.id, nodeFromOlpPath.id);
	}

	/**
	 * Checks if cookies ([1/3]) are stripped out of olp paths.
	 */
	@Test
	public void testGetOlpLinkWithCookie() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);
		node.name += " [1/3]";

		String olp = node.getOlpId(resolver);
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp.trim());
	}

}
