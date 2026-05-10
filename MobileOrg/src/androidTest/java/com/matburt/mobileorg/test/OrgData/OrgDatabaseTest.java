package com.matburt.mobileorg.test.OrgData;

import android.database.Cursor;
import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

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
public class OrgDatabaseTest extends ProviderTestCase2<OrgProvider> {

	private MockContentResolver resolver;
	private OrgDatabase db;

	public OrgDatabaseTest() {
		super(OrgProvider.class, OrgProvider.class.getName());
	}

	@Before
	public void setUp() throws Exception {
		setContext(ApplicationProvider.getApplicationContext());
		super.setUp();  // THIS IS CRITICAL - initializes ProviderTestCase2
		this.resolver = getMockContentResolver();
		this.db = new OrgDatabase(getMockContext());
	}

	@After
	public void tearDown() throws Exception {
		this.db.close();
		super.tearDown();  // THIS IS CRITICAL
	}

	@Test
	public void testNodeEquals() {
		OrgNode node1 = OrgTestUtils.getDefaultOrgNode();
		OrgNode node2 = OrgTestUtils.getDefaultOrgNode();

		node1.fileId = 300;
		node1.parentId = 400;
		node1.level = 3;
		node1.id = 1000;

		assertTrue(node1.equals(node2));

		node1.name = "hej";
		assertFalse(node1.equals(node2));
	}

	@Test
	public void testFastInsertNodeSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		long id = db.fastInsertNode(node);
		Cursor cursor = resolver.query(OrgData.buildIdUri(id),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());
		assertTrue(cursor.getColumnCount() > 0);

		OrgNode insertedNode = new OrgNode();

		try {
			insertedNode.set(cursor);
		} catch (OrgNodeNotFoundException e) {}
		cursor.close();

		assertEquals("", insertedNode.getPayload());
		insertedNode.setPayload("");
		assertTrue(node.equals(insertedNode));
	}

	@Test
	public void testFastInsertNodePayloadSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		long id = db.fastInsertNode(node);
		final String testPayload = "this is a test payload";
		db.fastInsertNodePayload(id, testPayload);

		Cursor cursor = resolver.query(OrgData.buildIdUri(Long.toString(id)),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());
		assertTrue(cursor.getColumnCount() > 0);

		OrgNode insertedNode = new OrgNode();
		try {
			insertedNode.set(cursor);
		} catch (OrgNodeNotFoundException e) {}
		cursor.close();

		assertEquals(testPayload, insertedNode.getPayload());
	}

	@Test
	public void testFastInsertNodePayloadUpdate() {
		final String testPayload = "second payload";

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		long id = db.fastInsertNode(node);
		db.fastInsertNodePayload(id, "first payload");
		db.fastInsertNodePayload(id, testPayload);

		Cursor cursor = resolver.query(OrgData.buildIdUri(Long.toString(id)),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		OrgNode insertedNode = new OrgNode();
		try {
			insertedNode.set(cursor);
		} catch (OrgNodeNotFoundException e) {}
		cursor.close();

		assertEquals(testPayload, insertedNode.getPayload());
	}

}
