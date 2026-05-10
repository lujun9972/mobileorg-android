package com.matburt.mobileorg.test.OrgData;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.database.Cursor;

import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileParser;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.test.util.OrgTestFiles.SimpleOrgFiles;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgFileTest {

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
	public void testAddFileSimple() throws OrgFileNotFoundException, OrgNodeNotFoundException{
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		orgFile.addFile(providerRule.getResolver());

		OrgFile insertedFile = new OrgFile(orgFile.id, providerRule.getResolver());
		assertTrue(orgFile.equals(insertedFile));
		assertEquals(insertedFile.id, orgFile.id);
		assertEquals(insertedFile.nodeId, orgFile.nodeId);

		OrgNode node = new OrgNode(orgFile.nodeId, providerRule.getResolver());
		assertEquals(node.name, orgFile.name);
		assertTrue(orgFile.id >= 0);
		assertEquals(node.fileId, orgFile.id);
	}

	@Test
	public void testDoesFileExist() {
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		orgFile.addFile(providerRule.getResolver());

		assertTrue(orgFile.doesFileExist(providerRule.getResolver()));
	}

	@Test
	public void testRemoveFileSimple() throws OrgFileNotFoundException {
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		orgFile.addFile(providerRule.getResolver());
		OrgFile insertedFile = new OrgFile(orgFile.id, providerRule.getResolver());
		insertedFile.removeFile(providerRule.getResolver());

		Cursor filesCursor = providerRule.getResolver().query(Files.buildIdUri(orgFile.id),
				Files.DEFAULT_COLUMNS, null, null, null);
		assertEquals(0, filesCursor.getCount());
		filesCursor.close();

		Cursor dataCursor = providerRule.getResolver().query(OrgData.buildIdUri(insertedFile.nodeId),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertEquals(0, dataCursor.getCount());
		dataCursor.close();
	}

	@Test
	public void testRemoveFileWithNodes() throws OrgFileNotFoundException {
		OrgNode node = OrgTestUtils.setupParentScenario(providerRule.getResolver());
		OrgFile orgFile = node.getOrgFile(providerRule.getResolver());

		orgFile.removeFile(providerRule.getResolver());

		Cursor filesCursor = providerRule.getResolver().query(Files.buildIdUri(orgFile.id),
				Files.DEFAULT_COLUMNS, null, null, null);
		assertEquals(0, filesCursor.getCount());
		filesCursor.close();

		Cursor dataCursor = providerRule.getResolver().query(OrgData.CONTENT_URI,
				OrgData.DEFAULT_COLUMNS, OrgData.FILE_ID + "=?",
				new String[] { Long.toString(orgFile.id) }, null);
		assertEquals(0, dataCursor.getCount());
		dataCursor.close();

		OrgTestUtils.cleanupParentScenario(providerRule.getResolver());
	}

	@Test
	public void testFileToStringSimple() throws OrgFileNotFoundException {
		final String filename = "filename";
		InputStream is = new ByteArrayInputStream(SimpleOrgFiles.orgFile.getBytes());
		BufferedReader breader = new BufferedReader(new InputStreamReader(is));
		OrgFile orgFile = new OrgFile(filename, "file alias", "");

		OrgDatabase db = new OrgDatabaseStub(providerRule.getContext());
		OrgFileParser parser = new OrgFileParser(db, providerRule.getResolver());
		parser.parse(orgFile, breader);
		db.close();

		OrgFile file = new OrgFile(filename, providerRule.getResolver());
		String fileString = file.toString(providerRule.getResolver());
		assertEquals(SimpleOrgFiles.orgFile.trim(), fileString.trim());
	}

	@Test
	public void testCreateFile () {
		final String fileAlias = "test name";
		OrgFile file = OrgProviderUtils.getOrCreateFile("test file", fileAlias, providerRule.getResolver());

		assertTrue(file.id >= 0);
		assertTrue(file.doesFileExist(providerRule.getResolver()));

		try {
			OrgNode capturefileNode = file.getOrgNode(providerRule.getResolver());
			assertTrue(capturefileNode.id >= 0);
			assertTrue(capturefileNode.fileId >= 0);
			assertEquals(file.id, capturefileNode.fileId);
			assertEquals(fileAlias, capturefileNode.name);
		} catch (IllegalArgumentException e) {
			fail("OrgNode not created");
		}

		try {
			OrgFile file2 = new OrgFile(file.id, providerRule.getResolver());
			assertTrue(file.equals(file2));
		} catch (OrgFileNotFoundException e) {
			fail("File node not created");
		}
	}

	@Test
	public void testCreateCaptureFile () {
		OrgFile file = OrgProviderUtils.getOrCreateCaptureFile(providerRule.getResolver());

		assertTrue(file.id >= 0);
		assertTrue(file.doesFileExist(providerRule.getResolver()));

		try {
			OrgNode capturefileNode = file.getOrgNode(providerRule.getResolver());
			assertTrue(capturefileNode.id >= 0);
			assertTrue(capturefileNode.fileId >= 0);
			assertEquals(file.id, capturefileNode.fileId);
			assertEquals(OrgFile.CAPTURE_FILE_ALIAS, capturefileNode.name);
		} catch (IllegalArgumentException e) {
			fail("OrgNode not created");
		}

		try {
			OrgFile file2 = new OrgFile(file.id, providerRule.getResolver());
			assertTrue(file.equals(file2));
		} catch (OrgFileNotFoundException e) {
			fail("File node not created");
		}
	}

	@Test
	public void testGetCaptureFile () {
		OrgNode node1 = OrgProviderUtils.getOrCreateCaptureFile(providerRule.getResolver())
				.getOrgNode(providerRule.getResolver());

		assertNotNull(node1);
		assertTrue(node1.id >= 0);
		assertTrue(node1.fileId >= 0);

		OrgNode node2 = OrgProviderUtils.getOrCreateCaptureFile(providerRule.getResolver())
				.getOrgNode(providerRule.getResolver());
		assertNotNull(node2);

		assertEquals(node1.id, node2.id);
		assertEquals(node1.fileId, node2.fileId);
		assertEquals(node1.name, node2.name);
	}

	@Test
	public void testGetOrgNodeFromFilename() throws OrgFileNotFoundException{
		OrgFile file = OrgProviderUtils.getOrCreateFile("test file", "file name", providerRule.getResolver());
		OrgNode fileNode = file.getOrgNode(providerRule.getResolver());

		OrgNode node = OrgProviderUtils.getOrgNodeFromFilename(file.filename, providerRule.getResolver());

		assertEquals(fileNode.name, node.name);
		assertEquals(fileNode.id, node.id);
		assertEquals(fileNode.fileId, node.fileId);
		assertEquals(fileNode.parentId, node.parentId);
	}
}
