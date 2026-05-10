package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import android.database.Cursor;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgEditTest {

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
	public void testAddEditSimple() {
		OrgEdit edit = new OrgEdit();
		edit.title = "title";
		edit.newValue = "new value";
		edit.oldValue = "old value";
		edit.type = OrgEdit.TYPE.HEADING;
		edit.nodeId = "node id";
		long editId = edit.write(providerRule.getResolver());

		Cursor cursor = providerRule.getResolver().query(Edits.buildIdUri(editId),
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

		ArrayList<OrgEdit> generatedEdits = node.generateApplyEditNodes(editedNode,
				providerRule.getResolver());
		assertEquals(numberOfEdits, generatedEdits.size());
	}


	@Test
	public void testNewHeadingSimple() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(providerRule.getResolver());

		OrgNode fileNode = new OrgNode(file.nodeId, providerRule.getResolver());

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = fileNode.fileId;
		node.parentId = fileNode.id;

		OrgEdit edit = node.createParentNewheading(providerRule.getResolver());
		assertEquals(OrgEdit.TYPE.ADDHEADING, edit.type);
	}


	@Test
	public void testNewHeadingDefaultFile() {
		OrgNode capturefileNode = OrgProviderUtils
				.getOrCreateCaptureFile(providerRule.getResolver()).getOrgNode(providerRule.getResolver());
		assertTrue(capturefileNode.fileId >= 0);

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = capturefileNode.fileId;
		node.parentId = capturefileNode.id;

		OrgEdit edit = node.createParentNewheading(providerRule.getResolver());
		assertEquals(null, edit.type);
	}


	@Test
	public void testEditsToStringSimple() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(providerRule.getResolver());
		OrgNode fileNode = new OrgNode(file.nodeId, providerRule.getResolver());

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = fileNode.fileId;
		node.parentId = fileNode.id;

		node.createParentNewheading(providerRule.getResolver()).write(providerRule.getResolver());

		node.level = 0;
		String correctEditString = new OrgEdit(fileNode,
				OrgEdit.TYPE.ADDHEADING, node.toString(), providerRule.getResolver()).toString();

		String editsString = OrgEdit.editsToString(providerRule.getResolver());
		assertEquals(correctEditString.trim(), editsString.trim());
	}
}
