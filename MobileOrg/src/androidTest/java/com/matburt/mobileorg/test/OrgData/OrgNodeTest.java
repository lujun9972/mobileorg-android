package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import android.database.Cursor;
import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;
import com.matburt.mobileorg.OrgData.OrgFileRepository;

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
	private OrgNodeRepository repo;

	public OrgNodeTest() {
		super(OrgProvider.class, OrgProvider.class.getName());
	}

	@Before
	public void setUp() throws Exception {
		setContext(ApplicationProvider.getApplicationContext());
		super.setUp();  // THIS IS CRITICAL - initializes ProviderTestCase2
		this.resolver = getMockContentResolver();
		this.repo = new OrgNodeRepository(resolver);
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
		repo.write(node);

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
		repo.write(node);

		node.todo = "DONE";
		repo.write(node);

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
		repo.write(node);

		OrgNode childNode = OrgTestUtils.getDefaultOrgNode();
		childNode.parentId = node.id;
		repo.write(childNode);

		OrgNode parent = repo.getParent(childNode.id);
		assertEquals(node.id, parent.id);
	}

	@Test
	public void testGetParentFileNode() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		new OrgFileRepository(resolver).write(file);
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.parentId = file.nodeId;
		repo.write(node);

		OrgNode parent = repo.getParent(node.id);
		assertEquals(file.nodeId, parent.id);
	}

	@Test
	public void testGetParentWithTopLevel() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		new OrgFileRepository(resolver).write(file);

		OrgNode node = repo.getById(file.nodeId);

		try {
			repo.getParent(node.id);
			fail("File shouldn't exist");
		} catch (OrgNodeNotFoundException e) {}
	}

	@Test
	public void testGetChildrenSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		repo.write(node);

		OrgNode child1 = OrgTestUtils.getDefaultOrgNode();
		child1.parentId = node.id;
		repo.write(child1);
		OrgNode child2 = OrgTestUtils.getDefaultOrgNode();
		child2.parentId = node.id;
		repo.write(child2);

		ArrayList<OrgNode> children = repo.getChildren(node.id);
		assertEquals(2, children.size());
	}

	@Test
	public void testArchiveNode() {
		OrgNode childNode = OrgTestUtils.setupParentScenario(resolver);
		repo.archiveNode(childNode);

		try {
			repo.getById(childNode.id);
			fail("Node should not exist");
		} catch (OrgNodeNotFoundException e) {}

		OrgTestUtils.cleanupParentScenario(resolver);
	}

	@Test
	public void testArchiveNodeGeneratesEdit() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		repo.write(node);

		Cursor editCursor = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = repo.archiveNode(node);
		edit.type.equals(OrgEdit.TYPE.ARCHIVE);

		Cursor editCursor2 = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testArchiveNodeToSibling() throws OrgNodeNotFoundException {
		OrgNode childNode = OrgTestUtils.setupParentScenario(resolver);
		OrgNode parent = repo.getParent(childNode.id);

		repo.archiveNodeToSibling(childNode);

		OrgNode archiveNode = repo.getChild(parent.id, OrgNode.ARCHIVE_NODE);
		assertNotNull(archiveNode);

		assertEquals(archiveNode.id, childNode.parentId);
		assertEquals(archiveNode.fileId, childNode.fileId);
	}

	@Test
	public void testArchiveNodeToSiblingGeneratesEdit() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);
		repo.write(node);

		Cursor editCursor = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = repo.archiveNodeToSibling(node);
		edit.type.equals(OrgEdit.TYPE.ARCHIVE_SIBLING);

		Cursor editCursor2 = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testGetOlpLink() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		String olp = repo.getOlpId(node);
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp);
	}

	@Test
	public void testGetNodeFromOlpLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		String olp = repo.getOlpId(node);
		OrgNode nodeFromOlpPath = repo.getOrgNodeFromOlpPath(olp);
		assertEquals(node.id, nodeFromOlpPath.id);
	}

	@Test
	public void testGetNodeFromOlpFileLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgTestUtils.setupParentScenario(resolver);
		final String filename = OrgTestUtils.defaultTestfilename;
		OrgNode fileNode = repo.getOrgNodeFromFilename(filename);
		final String olp = "olp:" + filename;

		OrgNode nodeFromOlpPath = repo.getOrgNodeFromOlpPath(olp);

		assertEquals(fileNode.id, nodeFromOlpPath.id);
	}

	/**
	 * Checks if cookies ([1/3]) are stripped out of olp paths.
	 */
	@Test
	public void testGetOlpLinkWithCookie() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);
		node.name += " [1/3]";

		String olp = repo.getOlpId(node);
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp.trim());
	}

	@Test
	public void testAppendFileLink() throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		repo.write(node);

		String filePath = "/storage/emulated/0/Music/MobileOrg/test_node-20260511.aac";
		repo.appendFileLink(node, filePath);

		assertTrue("Payload should contain file link",
				node.getPayload().contains("[[file:" + filePath + "]]"));
	}

}
