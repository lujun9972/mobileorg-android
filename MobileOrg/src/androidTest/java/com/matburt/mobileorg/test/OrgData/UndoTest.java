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
import static org.junit.Assert.assertFalse;

import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileRepository;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
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

	// =====================================================================
	// Task 4: undoLatestBatch tests
	// =====================================================================

	@Test
	public void testUndoRestoresFieldsAndDeletesBatch() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgNode node = createNodeInDefaultFile();
		String origName = node.name;
		String origTodo = node.todo;

		OrgNode newNode = repo.getById(node.id);
		newNode.name = "changed";
		newNode.todo = "DONE";
		repo.generateApplyWriteEdits(node, newNode, "");

		// generateApplyWriteEdits 只生成 edit 行，不修改 DB
		// 模拟 EditActivityControllerEdit.saveEdits 的完整流程
		repo.write(node);  // 把 mutate 后的节点写入 DB
		OrgNode edited = repo.getById(node.id);
		assertEquals("changed", edited.name);
		assertEquals("DONE", edited.todo);

		assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());

		OrgNode restored = repo.getById(node.id);
		assertEquals(origName, restored.name);
		assertEquals(origTodo, restored.todo);
		assertEquals(null, editRepo.getLatestBatchId());
	}

	@Test
	public void testUndoLifoOrder() throws OrgNodeNotFoundException {
		OrgNode node = createNodeInDefaultFile();
		String origName = node.name;
		String origTodo = node.todo;

		// 批次1：改 TODO
		OrgNode n1 = repo.getById(node.id);
		n1.todo = "DONE";  // 改成不同的值
		OrgNode oldNode1 = repo.getById(node.id);
		repo.generateApplyWriteEdits(oldNode1, n1, "");
		repo.write(oldNode1);  // 写入 DB
		long batch1 = editRepo.getLatestBatchId();

		// 批次2：改标题
		OrgNode n2 = repo.getById(node.id);
		n2.name = "second";
		OrgNode oldNode2 = repo.getById(node.id);
		repo.generateApplyWriteEdits(oldNode2, n2, "");
		repo.write(oldNode2);  // 写入 DB
		long batch2 = editRepo.getLatestBatchId();
		assertTrue(batch2 > batch1);

		assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());
		OrgNode after = repo.getById(node.id);
		assertEquals(origName, after.name);          // 批次2（改名）被撤
		assertEquals("DONE", after.todo);            // 批次1仍在

		assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());
		OrgNode done = repo.getById(node.id);
		assertEquals(origTodo, done.todo);          // 批次1也被撤
		assertEquals(null, editRepo.getLatestBatchId());
	}

	@Test
	public void testUndoBodyEditRemovesClock() throws OrgNodeNotFoundException {
		OrgNode node = createNodeInDefaultFile();
		String origPayload = repo.getById(node.id).getPayload();

		repo.addLogbook(node, 1000L, 2000L, "00:16");
		assertTrue(repo.getById(node.id).getPayload().contains("CLOCK:"));

		assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());

		OrgNode restored = repo.getById(node.id);
		assertEquals(origPayload, restored.getPayload());
	}

	@Test
	public void testUndoNodeMissingKeepsBatch() throws OrgNodeNotFoundException {
		OrgNode node = createNodeInDefaultFile();
		OrgNode newNode = repo.getById(node.id);
		newNode.name = "changed";
		OrgNode oldNode = repo.getById(node.id);
		repo.generateApplyWriteEdits(oldNode, newNode, "");

		repo.deleteNode(node);  // 结构操作，产生无批次 DELETE 行且节点从 DB 删除

		assertEquals(OrgEditRepository.UndoResult.NODE_MISSING, editRepo.undoLatestBatch());
		Long latest = editRepo.getLatestBatchId();
		assertTrue(latest != null);  // 批次保留，未被弹出
	}

	@Test
	public void testUndoNothingToUndo() {
		assertEquals(OrgEditRepository.UndoResult.NOTHING_TO_UNDO, editRepo.undoLatestBatch());
	}

	@Test
	public void testEditStoresDbId() throws OrgNodeNotFoundException {
		OrgNode node = createNodeInDefaultFile();
		OrgNode newNode = repo.getById(node.id);
		newNode.name = node.name + "2";
		repo.generateApplyWriteEdits(repo.getById(node.id), newNode, "");
		ArrayList<OrgEdit> edits = editRepo.getBatchEdits(editRepo.getLatestBatchId());
		assertEquals(node.id, edits.get(0).dbId);
	}

	@Test
	public void testRecordingSingleBatchSingleEdit() throws OrgNodeNotFoundException {
		OrgNode node = createNodeInDefaultFile();
		String origPayload = repo.getById(node.id).getPayload();

		repo.addRecording(node, 1000L, 2000L, "00:16", "/sdcard/rec.aac");

		String payload = repo.getById(node.id).getPayload();
		assertTrue(payload.contains("CLOCK:"));
		assertTrue(payload.contains("[[file:/sdcard/rec.aac]]"));

		Long batchId = editRepo.getLatestBatchId();
		assertTrue(batchId != null);
		assertEquals(1, editRepo.getBatchEdits(batchId).size());

		assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());
		assertEquals(origPayload, repo.getById(node.id).getPayload());
	}
}
