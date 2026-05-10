package com.matburt.mobileorg.test.Synchronizers;

import java.io.IOException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;

import android.content.ContentResolver;
import android.content.Context;
import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.test.util.OrgTestFiles.SimpleOrgFiles;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
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
		Context context = getMockContext();
		this.resolver = getMockContentResolver();
		this.db = new OrgDatabase(context);
		this.parserStub = new OrgFileParserStub(db, resolver);
		this.synchronizerStub = new SynchronizerStub();
		this.notifyStub = new SynchronizerNotificationStub(getMockContext());
		this.synchronizer = new Synchronizer(context, synchronizerStub, notifyStub);
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
}
