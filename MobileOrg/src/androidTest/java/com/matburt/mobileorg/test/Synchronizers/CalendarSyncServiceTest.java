package com.matburt.mobileorg.test.Synchronizers;

import java.util.ArrayList;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeDate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class CalendarSyncServiceTest {

	@Test
	public void testOrgNodePayloadGetDates() {
		OrgNode node = new OrgNode();
		node.setPayload("<2012-09-13 Thu>");
		ArrayList<OrgNodeDate> dates = node.getOrgNodePayload().getDates(node.getCleanedName());

		assertEquals(1, dates.size());
	}
}
