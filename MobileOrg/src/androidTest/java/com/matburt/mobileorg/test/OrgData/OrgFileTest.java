package com.matburt.mobileorg.test.OrgData;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.database.Cursor;
import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileParser;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.test.util.OrgTestFiles.SimpleOrgFiles;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgFileTest extends ProviderTestCase2<OrgProvider> {

	private MockContentResolver resolver;
	private OrgNodeRepository repo;

	public OrgFileTest() {
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
	public void testAddFileSimple() throws OrgFileNotFoundException, OrgNodeNotFoundException{
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		new OrgFileRepository(resolver).addFile(orgFile);

		OrgFile insertedFile = new OrgFileRepository(resolver).getById(orgFile.id);
		assertTrue(orgFile.equals(insertedFile));
		assertEquals(insertedFile.id, orgFile.id);
		assertEquals(insertedFile.nodeId, orgFile.nodeId);

		OrgNode node = repo.getById(orgFile.nodeId);
		assertEquals(node.name, orgFile.name);
		assertTrue(orgFile.id >= 0);
		assertEquals(node.fileId, orgFile.id);
	}

	@Test
	public void testDoesFileExist() {
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		new OrgFileRepository(resolver).addFile(orgFile);

		assertTrue(new OrgFileRepository(resolver).doesFileExist(orgFile.filename));
	}

	@Test
	public void testRemoveFileSimple() throws OrgFileNotFoundException {
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		new OrgFileRepository(resolver).addFile(orgFile);
		OrgFile insertedFile = new OrgFileRepository(resolver).getById(orgFile.id);
		new OrgFileRepository(resolver).removeFile(insertedFile);

		Cursor filesCursor = resolver.query(Files.buildIdUri(orgFile.id),
				Files.DEFAULT_COLUMNS, null, null, null);
		assertEquals(0, filesCursor.getCount());
		filesCursor.close();

		Cursor dataCursor = resolver.query(OrgData.buildIdUri(insertedFile.nodeId),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertEquals(0, dataCursor.getCount());
		dataCursor.close();
	}

	@Test
	public void testRemoveFileWithNodes() throws OrgFileNotFoundException {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);
		OrgFile orgFile = repo.getOrgFile(node);

		new OrgFileRepository(resolver).removeFile(orgFile);

		Cursor filesCursor = resolver.query(Files.buildIdUri(orgFile.id),
				Files.DEFAULT_COLUMNS, null, null, null);
		assertEquals(0, filesCursor.getCount());
		filesCursor.close();

		Cursor dataCursor = resolver.query(OrgData.CONTENT_URI,
				OrgData.DEFAULT_COLUMNS, OrgData.FILE_ID + "=?",
				new String[] { Long.toString(orgFile.id) }, null);
		assertEquals(0, dataCursor.getCount());
		dataCursor.close();

		OrgTestUtils.cleanupParentScenario(resolver);
	}

	@Test
	public void testFileToStringSimple() throws OrgFileNotFoundException {
		final String filename = "filename";
		InputStream is = new ByteArrayInputStream(SimpleOrgFiles.orgFile.getBytes());
		BufferedReader breader = new BufferedReader(new InputStreamReader(is));
		OrgFile orgFile = new OrgFile(filename, "file alias", "");

		OrgDatabase db = new OrgDatabaseStub(getMockContext());
		OrgFileParser parser = new OrgFileParser(db, resolver);
		parser.parse(orgFile, breader);
		db.close();

		OrgFile file = new OrgFileRepository(resolver).getByFilename(filename);
		String fileString = new OrgFileRepository(resolver).nodesToString(file);
		assertEquals(SimpleOrgFiles.orgFile.trim(), fileString.trim());
	}

	@Test
	public void testCreateFile () {
		final String fileAlias = "test name";
		OrgFile file = new OrgFileRepository(resolver).getOrCreateFile("test file", fileAlias);

		assertTrue(file.id >= 0);
		assertTrue(new OrgFileRepository(resolver).doesFileExist(file.filename));

		try {
			OrgNode capturefileNode = new OrgFileRepository(resolver).getOrgNode(file);
			assertTrue(capturefileNode.id >= 0);
			assertTrue(capturefileNode.fileId >= 0);
			assertEquals(file.id, capturefileNode.fileId);
			assertEquals(fileAlias, capturefileNode.name);
		} catch (IllegalArgumentException e) {
			fail("OrgNode not created");
		}

		try {
			OrgFile file2 = new OrgFileRepository(resolver).getById(file.id);
			assertTrue(file.equals(file2));
		} catch (OrgFileNotFoundException e) {
			fail("File node not created");
		}
	}

	@Test
	public void testCreateCaptureFile () {
		OrgFile file = new OrgFileRepository(resolver).getOrCreateCaptureFile();

		assertTrue(file.id >= 0);
		assertTrue(new OrgFileRepository(resolver).doesFileExist(file.filename));

		try {
			OrgNode capturefileNode = new OrgFileRepository(resolver).getOrgNode(file);
			assertTrue(capturefileNode.id >= 0);
			assertTrue(capturefileNode.fileId >= 0);
			assertEquals(file.id, capturefileNode.fileId);
			assertEquals(OrgFile.CAPTURE_FILE_ALIAS, capturefileNode.name);
		} catch (IllegalArgumentException e) {
			fail("OrgNode not created");
		}

		try {
			OrgFile file2 = new OrgFileRepository(resolver).getById(file.id);
			assertTrue(file.equals(file2));
		} catch (OrgFileNotFoundException e) {
			fail("File node not created");
		}
	}

	@Test
	public void testGetCaptureFile () {
		OrgFile captureFile1 = new OrgFileRepository(resolver).getOrCreateCaptureFile();
		OrgNode node1 = new OrgFileRepository(resolver).getOrgNode(captureFile1);

		assertNotNull(node1);
		assertTrue(node1.id >= 0);
		assertTrue(node1.fileId >= 0);

		OrgFile captureFile2 = new OrgFileRepository(resolver).getOrCreateCaptureFile();
		OrgNode node2 = new OrgFileRepository(resolver).getOrgNode(captureFile2);
		assertNotNull(node2);

		assertEquals(node1.id, node2.id);
		assertEquals(node1.fileId, node2.fileId);
		assertEquals(node1.name, node2.name);
	}

	@Test
	public void testGetOrgNodeFromFilename() throws OrgFileNotFoundException{
		OrgFile file = new OrgFileRepository(resolver).getOrCreateFile("test file", "file name");
		OrgNode fileNode = new OrgFileRepository(resolver).getOrgNode(file);

		OrgNode node = repo.getOrgNodeFromFilename(file.filename);

		assertEquals(fileNode.name, node.name);
		assertEquals(fileNode.id, node.id);
		assertEquals(fileNode.fileId, node.fileId);
		assertEquals(fileNode.parentId, node.parentId);
	}
}
