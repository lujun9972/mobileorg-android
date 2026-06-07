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
import com.matburt.mobileorg.util.OrgNodeNotFoundException;
import com.matburt.mobileorg.OrgData.OrgFileRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgEditTest extends ProviderTestCase2<OrgProvider> {

	private MockContentResolver resolver;
	private OrgNodeRepository repo;

	public OrgEditTest() {
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
	public void testAddEditSimple() {
		OrgEdit edit = new OrgEdit();
		edit.title = "title";
		edit.newValue = "new value";
		edit.oldValue = "old value";
		edit.type = OrgEdit.TYPE.HEADING;
		edit.nodeId = "node id";
		long editId = edit.write(resolver);

		Cursor cursor = resolver.query(Edits.buildIdUri(editId),
				Edits.DEFAULT_COLUMNS, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());

		OrgEdit insertedEdit = new OrgEdit(cursor);
		cursor.close();
		assertTrue(edit.compare(insertedEdit));
	}

	@Test
	public void testGenerateEditsSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		OrgNode editedNode = OrgTestUtils.getDefaultOrgNode();
		assertTrue(node.equals(editedNode));
		editedNode.name += "2";
		editedNode.todo += "OO";
		final int numberOfEdits = 2;

		ArrayList<OrgEdit> generatedEdits = repo.generateApplyEditNodes(node, editedNode, "");
		assertEquals(numberOfEdits, generatedEdits.size());
	}


	@Test
	public void testNewHeadingSimple() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);

		OrgNode fileNode = repo.getById(file.nodeId);

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = fileNode.fileId;
		node.parentId = fileNode.id;

		OrgEdit edit = repo.createParentNewheading(node, "");
		assertEquals(OrgEdit.TYPE.ADDHEADING, edit.type);
	}


	@Test
	public void testNewHeadingDefaultFile() {
		OrgFile capturefile = new OrgFileRepository(resolver).getOrCreateCaptureFile();
		OrgNode capturefileNode = new OrgFileRepository(resolver).getOrgNode(capturefile);
		assertTrue(capturefileNode.fileId >= 0);

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = capturefileNode.fileId;
		node.parentId = capturefileNode.id;

		OrgEdit edit = repo.createParentNewheading(node, "");
		assertEquals(null, edit.type);
	}


	@Test
	public void testEditsToStringSimple() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);
		OrgNode fileNode = repo.getById(file.nodeId);

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = fileNode.fileId;
		node.parentId = fileNode.id;

		repo.createParentNewheading(node, "").write(resolver);

		node.level = 0;
		String correctEditString = new OrgEdit(fileNode,
				OrgEdit.TYPE.ADDHEADING, node.toString(), resolver).toString();

		String editsString = OrgEdit.editsToString(resolver);
		assertEquals(correctEditString.trim(), editsString.trim());
	}
}
