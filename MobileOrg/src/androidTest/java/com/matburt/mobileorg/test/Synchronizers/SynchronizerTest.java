package com.matburt.mobileorg.test.Synchronizers;

import java.io.IOException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.test.util.OrgTestFiles.SimpleOrgFiles;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.HashMap;

import com.matburt.mobileorg.OrgData.OrgProviderUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SynchronizerTest extends ProviderTestCase2<OrgProvider> {

	private MockContentResolver resolver;
	private Synchronizer synchronizer;
	private OrgFileParserStub parserStub;
	private OrgDatabase db;
	private SynchronizerStub synchronizerStub;
	private SynchronizerNotificationStub notifyStub;

	public SynchronizerTest() {
		super(OrgProvider.class, OrgProvider.class.getName());
	}

	@Before
	public void setUp() throws Exception {
		setContext(ApplicationProvider.getApplicationContext());
		super.setUp();  // THIS IS CRITICAL - initializes ProviderTestCase2
		this.resolver = getMockContentResolver();
		this.db = new OrgDatabase(getMockContext());
		this.parserStub = new OrgFileParserStub(db, resolver);
		this.synchronizerStub = new SynchronizerStub();
		this.notifyStub = new SynchronizerNotificationStub(getMockContext());

		// Wrap real context with test ContentResolver for Synchronizer
		// (MockContext.getPackageName throws UnsupportedOperationException)
		final ContentResolver testResolver = this.resolver;
		Context syncContext = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
			@Override
			public ContentResolver getContentResolver() {
				return testResolver;
			}
		};
		this.synchronizer = new Synchronizer(syncContext, synchronizerStub, notifyStub);

		// Clean up data from previous tests
		resolver.delete(Edits.CONTENT_URI, null, null);
		resolver.delete(OrgData.CONTENT_URI, null, null);
		resolver.delete(Files.CONTENT_URI, null, null);
	}

	@After
	public void tearDown() throws Exception {
		db.close();
		super.tearDown();  // THIS IS CRITICAL
	}

	@Test
	public void testSynchronizeSimple() throws SSLHandshakeException, CertificateException, IOException, Exception {
		synchronizerStub.addFile("index.org", SimpleOrgFiles.indexFile);
		synchronizerStub.addFile("checksums.dat", SimpleOrgFiles.checksumsFile);
		synchronizerStub.addFile("GTD.org", SimpleOrgFiles.orgFile);
		synchronizer.pull(parserStub);
		assertTrue(parserStub.filesParsed.contains("GTD.org"));
		assertEquals(1, parserStub.filesParsed.size());
	}

	@Test
	public void testPullWithMissingIndex() throws CertificateException, Exception {
		synchronizerStub.addFile("checksums.dat", SimpleOrgFiles.checksumsFile);
		synchronizerStub.addFile("GTD.org", SimpleOrgFiles.orgFile);
		try {
			synchronizer.pull(parserStub);
			fail("Should have thrown IOException");
		} catch (IOException e) {}
		assertEquals(0, parserStub.filesParsed.size());
	}

	@Test
	public void testPullWithMissingChecksums() throws CertificateException, Exception {
		synchronizerStub.addFile("index.org", SimpleOrgFiles.indexFile);
		synchronizerStub.addFile("GTD.org", SimpleOrgFiles.orgFile);
		try {
			synchronizer.pull(parserStub);
			fail("Should have thrown IOException");
		} catch (IOException e) {}
		assertEquals(0, parserStub.filesParsed.size());
	}

	@Test
	public void testPullWithMissingOrgfile() throws CertificateException, Exception {
		synchronizerStub.addFile("index.org", SimpleOrgFiles.indexFile);
		synchronizerStub.addFile("checksums.dat", SimpleOrgFiles.checksumsFile);
		try {
			synchronizer.pull(parserStub);
			fail("Should have thrown IOException");
		} catch (IOException e) {}
		assertEquals(0, parserStub.filesParsed.size());
	}

	@Test
	public void testPushWithoutCaptures() throws SSLHandshakeException, CertificateException, IOException, Exception {
		synchronizer.pushCaptures();
	}

	@Test
	public void testPushWithCaptures() throws SSLHandshakeException, CertificateException, IOException, Exception {
		synchronizerStub.addFile(Synchronizer.CAPTURE_FILE, "");
		OrgFile file = new OrgFile(Synchronizer.CAPTURE_FILE, Synchronizer.CAPTURE_FILE, "");
		file.write(resolver);

		OrgNode node = new OrgNode();
		node.setFilename(Synchronizer.CAPTURE_FILE, resolver);
		node.write(resolver);
		synchronizer.pushCaptures();

		// TODO Make actual test out of this
		//assertEquals(node.toString(), synchronizerStub.files.get(Synchronizer.CAPTURE_FILE));
	}

	@Test
	public void testPushWithCapturesAndEdits() throws SSLHandshakeException, CertificateException, IOException, Exception {
		synchronizerStub.addFile(Synchronizer.CAPTURE_FILE, "");
		OrgFile file = new OrgFile(Synchronizer.CAPTURE_FILE, Synchronizer.CAPTURE_FILE, "");
		file.write(resolver);

		OrgNode node = new OrgNode();
		node.setFilename(Synchronizer.CAPTURE_FILE, resolver);
		node.write(resolver);

		OrgEdit edit = new OrgEdit(node, OrgEdit.TYPE.ADDHEADING, resolver);
		edit.write(resolver);
		synchronizer.pushCaptures();

		// TODO Make actual test out of this
		//assertEquals(edit.toString(), synchronizerStub.files.get(Synchronizer.CAPTURE_FILE));
	}

	@Test
	public void testPullRemovesRemoteDeletedFiles() throws Exception {
		// Step 1: Simulate a previously synced archive.org in local DB
		OrgFile archiveFile = new OrgFile("archive.org", "Archive", "old_checksum");
		archiveFile.write(resolver);

		HashMap<String, String> localBefore = OrgProviderUtils.getFileChecksums(resolver);
		assertTrue("archive.org should be in local DB before sync", localBefore.containsKey("archive.org"));

		// Step 2: Sync with remote that no longer has archive.org
		String indexWithoutArchive = "#+READONLY\n"
				+ "#+TODO: TODO | DONE\n"
				+ "#+TAGS: { Home Computer Errands }\n"
				+ "#+ALLPRIORITIES: A B C\n"
				+ "* [[file:GTD.org][GTD.org]]\n";
		String checksumsWithoutArchive = "25aade750f6b60aa1df155fcbb357191  index.org\n"
				+ "42055316a0808ad634d7981653cf4400faddb91f  GTD.org";
		synchronizerStub.addFile("index.org", indexWithoutArchive);
		synchronizerStub.addFile("checksums.dat", checksumsWithoutArchive);
		synchronizerStub.addFile("GTD.org", SimpleOrgFiles.orgFile);

		synchronizer.pull(parserStub);

		// Step 3: Verify archive.org was removed from local DB
		HashMap<String, String> localAfter = OrgProviderUtils.getFileChecksums(resolver);
		assertFalse("archive.org should be removed from local DB after sync",
				localAfter.containsKey("archive.org"));
	}

	@Test
	public void testPullDoesNotRemoveCaptureFile() throws Exception {
		// Capture file should never be removed even if not in remote index
		OrgFile captureFile = new OrgFile(Synchronizer.CAPTURE_FILE, "Captures", "cap_checksum");
		captureFile.write(resolver);

		String indexEmpty = "#+READONLY\n#+TODO: TODO | DONE\n#+TAGS:\n#+ALLPRIORITIES:\n";
		String checksumsEmpty = "abc123  index.org\n";
		synchronizerStub.addFile("index.org", indexEmpty);
		synchronizerStub.addFile("checksums.dat", checksumsEmpty);

		synchronizer.pull(parserStub);

		HashMap<String, String> localAfter = OrgProviderUtils.getFileChecksums(resolver);
		assertTrue("capture file should NOT be removed", localAfter.containsKey(Synchronizer.CAPTURE_FILE));
	}

	@Test
	public void testPullDoesNotRemoveAgendaFile() throws Exception {
		// Agenda file should never be removed even if not in remote index
		OrgFile agendaFile = new OrgFile(OrgFile.AGENDA_FILE, OrgFile.AGENDA_FILE_ALIAS, "agenda_checksum");
		agendaFile.write(resolver);

		String indexEmpty = "#+READONLY\n#+TODO: TODO | DONE\n#+TAGS:\n#+ALLPRIORITIES:\n";
		String checksumsEmpty = "abc123  index.org\n";
		synchronizerStub.addFile("index.org", indexEmpty);
		synchronizerStub.addFile("checksums.dat", checksumsEmpty);

		synchronizer.pull(parserStub);

		HashMap<String, String> localAfter = OrgProviderUtils.getFileChecksums(resolver);
		assertTrue("agenda file should NOT be removed", localAfter.containsKey(OrgFile.AGENDA_FILE));
	}
}
