package com.matburt.mobileorg.test.OrgData;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodePayload;
import com.matburt.mobileorg.OrgData.OrgNodeTimeDate;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.test.util.OrgTestUtils.TestTimestampPayload;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class OrgNodePayloadTest {

	@Test
	public void testGetIdFromId() {
		OrgNodePayload payload = new OrgNodePayload(OrgTestUtils.testIdPayload);
		assertEquals(OrgTestUtils.testId, payload.getId());
	}

	@Test
	public void testGetIdFromOrigId() {
		OrgNodePayload payload = new OrgNodePayload(OrgTestUtils.testIdAgendasPayload);
		assertEquals(OrgTestUtils.testId, payload.getId());
	}

	// REMOVED: testOrgNodeGetId - requires Android Context (ContentResolver)


	@Test
	public void testScheduledGet() {
		OrgNodePayload payload = new OrgNodePayload(TestTimestampPayload.payload);
		assertEquals(TestTimestampPayload.scheduled, payload.getScheduled());
	}

	@Test
	public void testDeadlineGet() {
		OrgNodePayload payload = new OrgNodePayload(TestTimestampPayload.payload);
		assertEquals(TestTimestampPayload.deadline, payload.getDeadline());
	}

	@Test
	public void testTimestampGetSimple() {
		OrgNodePayload payload = new OrgNodePayload(TestTimestampPayload.payloadSimple);
		assertEquals(TestTimestampPayload.timestamp, payload.getTimestamp());
	}

	@Test
	public void testTimestampGet() {
		OrgNodePayload payload = new OrgNodePayload(TestTimestampPayload.payload);
		assertEquals(TestTimestampPayload.timestamp, payload.getTimestamp());
	}


	@Test
	public void testScheduledModify() {
		OrgNodePayload payload = new OrgNodePayload(TestTimestampPayload.payload);
		final String newTimestamp = TestTimestampPayload.timestampNotInPayload;
		payload.insertOrReplaceDate(OrgNodeTimeDate.TYPE.Scheduled, newTimestamp);
		assertEquals(newTimestamp, payload.getScheduled());
	}

	@Test
	public void testDeadlineModify() {
		OrgNodePayload payload = new OrgNodePayload(TestTimestampPayload.payload);
		final String newTimestamp = TestTimestampPayload.timestampNotInPayload;
		payload.insertOrReplaceDate(OrgNodeTimeDate.TYPE.Deadline, newTimestamp);
		assertEquals(newTimestamp, payload.getDeadline());
	}

	@Test
	public void testTimestampModify() {
		OrgNodePayload payload = new OrgNodePayload(TestTimestampPayload.payload);
		final String newTimestamp = TestTimestampPayload.timestampNotInPayload;
		payload.insertOrReplaceDate(OrgNodeTimeDate.TYPE.Timestamp, newTimestamp);
		assertEquals(newTimestamp, payload.getTimestamp());
	}


	@Test
	public void testScheduledInsertNew() {
		OrgNodePayload payload = new OrgNodePayload(OrgTestUtils.testIdAgendasPayload);
		final String newTimestamp = TestTimestampPayload.timestampNotInPayload;
		payload.insertOrReplaceDate(OrgNodeTimeDate.TYPE.Scheduled, newTimestamp);
		assertEquals(newTimestamp, payload.getScheduled());
	}

	@Test
	public void testDeadlineInsertNew() {
		OrgNodePayload payload = new OrgNodePayload(OrgTestUtils.testIdAgendasPayload);
		final String newTimestamp = TestTimestampPayload.timestampNotInPayload;
		payload.insertOrReplaceDate(OrgNodeTimeDate.TYPE.Deadline, newTimestamp);
		assertEquals(newTimestamp, payload.getDeadline());
	}

	@Test
	public void testTimestampInsertNew() {
		OrgNodePayload payload = new OrgNodePayload(OrgTestUtils.testIdAgendasPayload);
		final String newTimestamp = TestTimestampPayload.timestampNotInPayload;
		payload.insertOrReplaceDate(OrgNodeTimeDate.TYPE.Timestamp, newTimestamp);
		assertEquals(newTimestamp, payload.getTimestamp());
	}
}
