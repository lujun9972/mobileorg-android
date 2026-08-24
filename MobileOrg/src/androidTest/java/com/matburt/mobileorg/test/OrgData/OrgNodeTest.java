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
	public void testToStringWithLevelOverride() {
		OrgNode node = new OrgNode();
		node.name = "title";
		node.todo = "TODO";
		node.level = 3;

		assertEquals("* TODO title", node.toString(1));
		assertEquals("** TODO title", node.toString(2));
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

	/**
	 * Regression: OrgNode(OrgNode) copy constructor used to drop id/parentId/fileId.
	 * ViewFragment.handleCheckboxToggle copies the node, mutates the payload, then
	 * persists via updateAllNodes — with id=-1 the id-URI update matched 0 rows and
	 * (after the empty-nodeId guard) the LIKE fallback was skipped too, silently
	 * losing the write. Mirrors the real caller sequence: getById → copy → mutate →
	 * updateAllNodes → re-read.
	 */
	@Test
	public void testCopyConstructorKeepsIdentityAndWritesBack()
			throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.setPayload("- [ ] 买菜");
		repo.write(node);

		OrgNode oldNode = repo.getById(node.id);
		OrgNode newNode = new OrgNode(oldNode);
		assertEquals(node.id, newNode.id);
		assertEquals(node.parentId, newNode.parentId);
		assertEquals(node.fileId, newNode.fileId);

		newNode.setPayload("- [X] 买菜");
		repo.updateAllNodes(newNode);

		assertEquals("- [X] 买菜", repo.getById(node.id).getPayload());
	}

	/**
	 * Regression: capture-style nodes are saved with default parentId=-1 / fileId=-1
	 * and no :ID: property (see EditActivityControllerCreate.saveEdits). For such a
	 * node getNodeId() returns "", and updateAllNodes must NOT fall through to a
	 * PAYLOAD LIKE '%%' update that would overwrite every row in the table.
	 */
	@Test
	public void testUpdateAllNodesOnCaptureStyleNodeMustNotOverwriteTable()
			throws OrgNodeNotFoundException {
		OrgFileRepository fileRepo = new OrgFileRepository(resolver);
		OrgFile file = fileRepo.getOrCreateFile("normal.org", "normal");

		OrgNode normalNode = OrgTestUtils.getDefaultOrgNode();
		normalNode.parentId = file.nodeId;
		normalNode.fileId = file.id;
		normalNode.name = "正常标题";
		normalNode.setPayload("正常 payload");
		repo.write(normalNode);

		OrgNode captureNode = new OrgNode();
		captureNode.name = "Checkbox测试";
		captureNode.level = 1;
		captureNode.setPayload("- [ ] item");
		repo.write(captureNode);

		assertEquals("", repo.getNodeId(captureNode));

		OrgNode updated = repo.getById(captureNode.id);
		updated.setPayload("- [X] item");
		repo.updateAllNodes(updated);

		assertEquals("- [X] item", repo.getById(captureNode.id).getPayload());

		OrgNode after = repo.getById(normalNode.id);
		assertEquals("正常标题", after.name);
		assertEquals("正常 payload", after.getPayload());
		assertEquals("normal", repo.getById(file.nodeId).name);
	}

	@Test
	public void testGetSubtreeTextNormalizesLevels() throws OrgNodeNotFoundException {
		OrgNode root = OrgTestUtils.getDefaultOrgNode();
		root.name = "root";
		root.level = 2;
		repo.write(root);
		OrgNode child = OrgTestUtils.getDefaultOrgNode();
		child.name = "child";
		child.parentId = root.id;
		child.level = 3;
		repo.write(child);
		OrgNode grandchild = OrgTestUtils.getDefaultOrgNode();
		grandchild.name = "grandchild";
		grandchild.parentId = child.id;
		grandchild.level = 4;
		repo.write(grandchild);

		String text = repo.getSubtreeText(root.id);
		assertTrue(text.startsWith("* TODO root\n"));
		assertTrue(text.contains("** TODO child\n"));
		assertTrue(text.contains("*** TODO grandchild\n"));
	}

	@Test
	public void testGetSubtreeTextFullContent() throws OrgNodeNotFoundException {
		OrgNode root = OrgTestUtils.getComplexOrgNode();
		root.name = "complex root";
		root.level = 1;
		root.setPayload("   SCHEDULED: <2026-08-24 一 09:00>\n   some body");
		repo.write(root);
		OrgNode child = OrgTestUtils.getDefaultOrgNode();
		child.name = "plain child";
		child.parentId = root.id;
		child.level = 2;
		repo.write(child);

		String text = repo.getSubtreeText(root.id);
		assertTrue(text.contains("[#C]"));
		assertTrue(text.contains(":tag1:tag2::tag3:"));
		assertTrue(text.contains("SCHEDULED: <2026-08-24 一 09:00>"));
		assertTrue(text.contains("** TODO plain child"));
	}

	@Test
	public void testGetSubtreeTextExcludesInheritedTags() throws OrgNodeNotFoundException {
		OrgNode root = OrgTestUtils.getDefaultOrgNode();
		root.name = "parent";
		root.tags = "work";
		root.level = 1;
		repo.write(root);
		OrgNode child = OrgTestUtils.getDefaultOrgNode();
		child.name = "child";
		child.parentId = root.id;
		child.level = 2;
		child.tags_inherited = "work";
		repo.write(child);

		String text = repo.getSubtreeText(root.id);
		assertTrue(text.contains("* TODO parent :work:"));
		assertFalse(text.contains("** TODO child :work:"));
		assertTrue(text.contains("** TODO child\n"));
	}

	@Test
	public void testGetSubtreeTextNodeNotFound() {
		try {
			repo.getSubtreeText(-1);
			fail("Expected OrgNodeNotFoundException");
		} catch (OrgNodeNotFoundException e) {}
	}

}
