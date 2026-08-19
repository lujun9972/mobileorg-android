package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgEditRepository;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;
import com.matburt.mobileorg.util.FileUtils;

@RunWith(AndroidJUnit4.class)
public class UndoTest extends ProviderTestCase2<OrgProvider> {

	private MockContentResolver resolver;
	private OrgNodeRepository repo;
	private OrgEditRepository editRepo;

	public UndoTest() {
		super(OrgProvider.class, OrgProvider.class.getName());
	}

	@Before
	public void setUp() throws Exception {
		setContext(ApplicationProvider.getApplicationContext());
		super.setUp();
		this.resolver = getMockContentResolver();
		this.repo = new OrgNodeRepository(resolver);
		this.editRepo = new OrgEditRepository(resolver);
		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();
	}

	private long writeEdit(long batchId, OrgEdit.TYPE type, String title) {
		OrgEdit edit = new OrgEdit();
		edit.type = type;
		edit.title = title;
		edit.nodeId = "fake";
		edit.oldValue = "old";
		edit.newValue = "new";
		edit.batchId = batchId;
		return edit.write(resolver);
	}

	@Test
	public void testNextBatchIdStartsAtOneAndIncrements() {
		assertEquals(1, editRepo.nextBatchId());
		writeEdit(1, OrgEdit.TYPE.HEADING, "a");
		assertEquals(2, editRepo.nextBatchId());
	}

	@Test
	public void testLatestBatchIdIgnoresNullBatches() {
		OrgEdit noBatch = new OrgEdit();
		noBatch.type = OrgEdit.TYPE.DELETE;
		noBatch.title = "d";
		noBatch.nodeId = "fake";
		noBatch.write(resolver);
		assertNull(editRepo.getLatestBatchId());

		writeEdit(5, OrgEdit.TYPE.TODO, "b");
		assertEquals(Long.valueOf(5), editRepo.getLatestBatchId());
	}

	@Test
	public void testGetBatchEditsReturnsRowsOrdered() {
		writeEdit(3, OrgEdit.TYPE.HEADING, "a");
		writeEdit(3, OrgEdit.TYPE.TODO, "a");
		writeEdit(4, OrgEdit.TYPE.TAGS, "b");
		ArrayList<OrgEdit> batch = editRepo.getBatchEdits(3);
		assertEquals(2, batch.size());
		assertEquals(OrgEdit.TYPE.HEADING, batch.get(0).type);
		assertEquals(OrgEdit.TYPE.TODO, batch.get(1).type);
	}

	@Test
	public void testDescribeLatestBatch() {
		writeEdit(2, OrgEdit.TYPE.HEADING, "学日语");
		assertEquals("修改标题 '学日语'", editRepo.describeLatestBatch());
	}

	@Test
	public void testDescribeLatestBatchNullWhenEmpty() {
		assertNull(editRepo.describeLatestBatch());
	}

	private OrgNode createNodeInDefaultFile() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		new OrgFileRepository(resolver).write(file);
		OrgNode fileNode = repo.getById(file.nodeId);
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = fileNode.fileId;
		node.parentId = fileNode.id;
		repo.write(node);
		return node;
	}

	@Test
	public void testGenerateEditsAssignsSameBatch() throws OrgNodeNotFoundException {
		OrgNode node = createNodeInDefaultFile();

		OrgNode newNode = repo.getById(node.id);
		newNode.name = node.name + "2";
		newNode.todo = "DONE";
		repo.generateApplyWriteEdits(node, newNode, "");

		ArrayList<OrgEdit> edits = editRepo.getBatchEdits(editRepo.getLatestBatchId());
		assertEquals(2, edits.size());
		long batch = edits.get(0).batchId;
		assertEquals(batch, edits.get(1).batchId);
	}

	@Test
	public void testAddLogbookAssignsBatch() throws OrgNodeNotFoundException {
		OrgNode node = createNodeInDefaultFile();
		long before = editRepo.getLatestBatchId() == null ? 0 : editRepo.getLatestBatchId();

		repo.addLogbook(node, 1000L, 2000L, "00:16");

		Long after = editRepo.getLatestBatchId();
		assertTrue(after != null && after > before);
		OrgNode reloaded = repo.getById(node.id);
		assertTrue(reloaded.getPayload().contains("CLOCK:"));
	}

	@Test
	public void testCaptureFileEditHasNoBatch() throws OrgNodeNotFoundException {
		OrgFile capture = new OrgFileRepository(resolver).getOrCreateCaptureFile();
		OrgNode captureNode = new OrgFileRepository(resolver).getOrgNode(capture);
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = captureNode.fileId;
		node.parentId = captureNode.id;
		repo.write(node);

		OrgNode newNode = repo.getById(node.id);
		newNode.name = node.name + "2";
		repo.generateApplyWriteEdits(node, newNode, "");

		assertEquals(null, editRepo.getLatestBatchId());
	}
}
