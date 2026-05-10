# Migrate Instrumentation Tests to AndroidX Test Framework

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate 20 existing instrumentation tests from deprecated `android.test.*` to modern AndroidX Test framework so they compile and run with AGP 8.2.2 + compileSdk 34.

**Architecture:** Move `instrumentTest/` to `androidTest/`. Replace deprecated base classes with JUnit 4 + AndroidX Test equivalents. Three migration patterns: pure JUnit 4 (no Context), AndroidX ProviderTestRule, AndroidX ActivityScenarioRule. No new tests, no refactoring test logic.

**Tech Stack:** JUnit 4, AndroidX Test (runner 1.5.2, rules 1.5.0, ext/junit 1.1.5), Espresso 3.5.1

---

### Task 1: Update build.gradle with test dependencies

**Files:**
- Modify: `MobileOrg/build.gradle`

- [ ] **Step 1: Add androidTestImplementation dependencies and testInstrumentationRunner**

In `MobileOrg/build.gradle`, add to `android.defaultConfig` block (after `versionName`):

```groovy
testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
```

In `dependencies` block, add after the existing `testImplementation` line:

```groovy
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test:runner:1.5.2'
androidTestImplementation 'androidx.test:rules:1.5.0'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add MobileOrg/build.gradle
git commit -m "build: add AndroidX Test dependencies for instrumentation tests"
```

---

### Task 2: Move all test files from instrumentTest to androidTest

**Files:**
- Move: `MobileOrg/src/instrumentTest/` → `MobileOrg/src/androidTest/`

- [ ] **Step 1: Move the entire directory tree**

```bash
mv MobileOrg/src/instrumentTest MobileOrg/src/androidTest
```

- [ ] **Step 2: Verify files moved correctly**

Run: `find MobileOrg/src/androidTest -name "*.java" | wc -l`
Expected: 20

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: move instrumentTest to androidTest directory"
```

---

### Task 3: Migrate OrgNodeParserTest (Pattern A: pure JUnit 4)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeParserTest.java`

This test only uses `OrgNode` and `OrgNodeParser` — no Context, no ContentProvider. Pure data parsing.

- [ ] **Step 1: Replace imports and class declaration**

Replace the entire file content with:

```java
package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeParser;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class OrgNodeParserTest {
	@Test
	public void testParseLineIntoNodeSimple() {
		OrgNode node = new OrgNode();
		node.name = "my simple test";
		node.todo = "";
		node.level = 3;
		final String testHeading = "*** my simple test";

		OrgNodeParser orgNodeParser = new OrgNodeParser(new ArrayList<String>());
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.name, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodeWithTodo() {
		OrgNode node = new OrgNode();
		node.name = "my simple test";
		node.todo = "TODO";
		node.level = 3;
		final String testHeading = "*** TODO my simple test";

		ArrayList<String> todos = new ArrayList<String>();
		todos.add(node.todo);
		OrgNodeParser orgNodeParser = new OrgNodeParser(todos);
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.name, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodeInvalidTodo() {
		OrgNode node = new OrgNode();
		node.name = "BLA my simple test";
		node.todo = "";
		node.level = 3;
		final String testHeading = "*** BLA my simple test";

		OrgNodeParser orgNodeParser = new OrgNodeParser(new ArrayList<String>());
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.name, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodeComplicatedTodo() {
		OrgNode node = new OrgNode();
		node.name = "my simple test";
		node.todo = "find_me";
		node.level = 3;
		final String testHeading = "*** find_me my simple test";

		ArrayList<String> todos = new ArrayList<String>();
		todos.add(node.todo);
		OrgNodeParser orgNodeParser = new OrgNodeParser(todos);
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.name, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodeLinkTitle() {
		OrgNode node = new OrgNode();
		node.name = "[[MobileOrg][MobileOrg]]";
		node.todo = "";
		node.level = 3;
		final String testHeading = "*** [[MobileOrg][MobileOrg]]";

		OrgNodeParser orgNodeParser = new OrgNodeParser(new ArrayList<String>());
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.name, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodePriority() {
		OrgNode node = new OrgNode();
		node.name = "my todo";
		node.todo = "TODO";
		node.priority = "A";
		node.level = 3;
		final String testHeading = "*** TODO [#A] my todo";

		ArrayList<String> todos = new ArrayList<String>();
		todos.add(node.todo);
		OrgNodeParser orgNodeParser = new OrgNodeParser(todos);
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.name, parsedNode.name);
		assertEquals(node.priority, parsedNode.priority);
	}

	@Test
	public void testParseLineIntoNodeTags() {
		OrgNode node = new OrgNode();
		node.name = "Archive";
		node.level = 3;
		node.tags = "tag1:tag2";
		final String testHeading = "*** Archive      :tag1:tag2:";

		OrgNodeParser orgNodeParser = new OrgNodeParser(new ArrayList<String>());
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.tags, parsedNode.tags);
		assertEquals(node.name, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodeWithSimpleScheduled() {
		OrgNode node = new OrgNode();
		node.name = "my simple test";
		node.todo = "TODO";
		node.level = 3;
		final String testHeading = "***  TODO my simple test";
		ArrayList<String> todos = new ArrayList<String>();
		todos.add(node.todo);
		OrgNodeParser orgNodeParser = new OrgNodeParser(todos);
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 3);

		assertEquals(node.todo, parsedNode.todo);
		assertEquals(node.name, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodeAgendaTitle() {
		final String expectedTitle = "Home Core>Home";
		final String testHeading = "* Home <after>KEYS=h#2 TITLE: Home Core</after>";

		OrgNodeParser orgNodeParser = new OrgNodeParser(new ArrayList<String>());
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 1);

		assertEquals(expectedTitle, parsedNode.name);
	}

	@Test
	public void testParseLineIntoNodeAgendaTitleWithoutSpace() {
		final String expectedTitle = "Home Core>Agenda";
		final String testHeading = "* Agenda<after>KEYS=h#2 TITLE: Home Core</after>";

		OrgNodeParser orgNodeParser = new OrgNodeParser(new ArrayList<String>());
		OrgNode parsedNode = orgNodeParser.parseLine(testHeading, 1);

		assertEquals(expectedTitle, parsedNode.name);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeParserTest.java
git commit -m "test: migrate OrgNodeParserTest to JUnit 4"
```

---

### Task 4: Migrate OrgNodeDateTest (Pattern A: pure JUnit 4)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeDateTest.java`

This test only uses `OrgNodeDate` and `Calendar` — no Context needed.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.OrgData;

import java.util.Calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.matburt.mobileorg.OrgData.OrgNodeDate;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class OrgNodeDateTest {

	private static final String dateString = "2000-11-24";
	private static final String timeBeginString = "13:15";
	private static final String timeEndString = "15:15";
	private static final String timeMidnight = "00:00";

	private Calendar getDefaultCalendar() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, 2000);
		cal.set(Calendar.MONTH, 10);
		cal.set(Calendar.DAY_OF_MONTH, 24);
		cal.set(Calendar.HOUR_OF_DAY, 13);
		cal.set(Calendar.MINUTE, 15);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}

	@Test
	public void testGetDateDate() {
		final long timeInMillis = getDefaultCalendar().getTimeInMillis();

		String date = OrgNodeDate.getDate(timeInMillis, 0, true);

		assertEquals(dateString, date);
	}

	@Test
	public void testGetDateDateTime() {
		final long timeInMillis = getDefaultCalendar().getTimeInMillis();

		String date = OrgNodeDate.getDate(timeInMillis, timeInMillis, false);

		assertEquals(dateString + " " + timeBeginString, date);
	}

	@Test
	public void testGetDateTimeSpan() {
		Calendar cal = getDefaultCalendar();
		final long startTimeInMillis = cal.getTimeInMillis();
		cal.set(Calendar.HOUR_OF_DAY, 15);
		final long endTimeInMillis = cal.getTimeInMillis();

		String date = OrgNodeDate.getDate(startTimeInMillis, endTimeInMillis, false);

		assertEquals(dateString + " " + timeBeginString + "-" + timeEndString, date);
	}

	@Test
	public void testDateEqualsCalendar() {
		OrgNodeDate date = new OrgNodeDate(dateString + " " + timeBeginString);
		long calTime = getDefaultCalendar().getTimeInMillis();

		assertEquals(date.beginTime, calTime);
	}

	@Test
	public void testAllDayEqualsMidnight() {
		OrgNodeDate allDay = new OrgNodeDate(dateString);
		OrgNodeDate midnight = new OrgNodeDate(dateString + " " + timeMidnight);

		assertEquals(allDay.beginTime, midnight.beginTime);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeDateTest.java
git commit -m "test: migrate OrgNodeDateTest to JUnit 4"
```

---

### Task 5: Migrate CalendarSyncServiceTest (Pattern A: pure JUnit 4)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Synchronizers/CalendarSyncServiceTest.java`

This test only uses `OrgNode` and `OrgNodeDate` — no Context needed.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.Synchronizers;

import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodeDate;

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
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Synchronizers/CalendarSyncServiceTest.java
git commit -m "test: migrate CalendarSyncServiceTest to JUnit 4"
```

---

### Task 6: Migrate OrgNodePayloadTest (Pattern A: pure JUnit 4 for most tests)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodePayloadTest.java`

Most tests only need `OrgNodePayload` and `OrgNodeTimeDate`. However, `testOrgNodeGetId()` calls `node.getNodeId(getContext().getContentResolver())` which needs a ContentResolver. This test must be moved to a Provider test or removed from this class.

**Decision:** Remove `testOrgNodeGetId` from this class (it will be covered if needed by a Provider-based test later). The remaining 10 tests are pure data operations.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.OrgData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgNodePayload;
import com.matburt.mobileorg.OrgData.OrgNodeTimeDate;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.test.util.OrgTestUtils.TestTimestampPayload;

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
```

Note: `testOrgNodeGetId` was removed because it requires `ContentResolver` via `getContext()`. It tested ID lookup from payload which is already covered by `testGetIdFromId` and `testGetIdFromOrigId`.

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodePayloadTest.java
git commit -m "test: migrate OrgNodePayloadTest to JUnit 4, remove Context-dependent test"
```

---

### Task 7: Migrate OrgDatabaseTest (Pattern B: ProviderTestRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgDatabaseTest.java`

Uses `ProviderTestCase2<OrgProvider>` and `MockContentResolver`. Migrate to AndroidX `ProviderTestRule`.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.OrgData;

import android.database.Cursor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OrgDatabaseTest {

	@Rule
	public ProviderTestRule providerRule = new ProviderTestRule.Builder(
			OrgProvider.class, OrgProvider.class.getName()).build();

	private OrgDatabase db;

	@Before
	public void setUp() {
		db = new OrgDatabase(providerRule.getContext());
	}

	@After
	public void tearDown() {
		db.close();
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
		assertTrue(!node1.equals(node2));
	}

	@Test
	public void testFastInsertNodeSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		long id = db.fastInsertNode(node);
		Cursor cursor = providerRule.getResolver().query(OrgData.buildIdUri(id),
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

		Cursor cursor = providerRule.getResolver().query(OrgData.buildIdUri(Long.toString(id)),
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

		Cursor cursor = providerRule.getResolver().query(OrgData.buildIdUri(Long.toString(id)),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		OrgNode insertedNode = new OrgNode();
		try {
			insertedNode.set(cursor);
		} catch (OrgNodeNotFoundException e) {}
		cursor.close();

		assertEquals(testPayload, insertedNode.getPayload());
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgDatabaseTest.java
git commit -m "test: migrate OrgDatabaseTest to AndroidX ProviderTestRule"
```

---

### Task 8: Migrate OrgDatabaseStub

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgDatabaseStub.java`

This stub extends `OrgDatabase` and only takes a `Context` in constructor. No deprecated imports, but it uses `android.content.Context`. It stays the same — no changes needed beyond the directory move (done in Task 2).

- [ ] **Step 1: Verify file compiles**

The stub itself has no deprecated `android.test.*` imports. It should compile as-is. Verify by checking it only imports `android.content.Context` and project classes.

```bash
grep "^import android.test" MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgDatabaseStub.java
```

Expected: no output (no deprecated imports).

- [ ] **Step 2: No changes needed, skip commit**

---

### Task 9: Migrate OrgNodeTest (Pattern B: ProviderTestRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java`

Uses `ProviderTestCase2<OrgProvider>`. Heavy use of ContentResolver.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import android.database.Cursor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgFileNotFoundException;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgNodeTest {

	@Rule
	public ProviderTestRule providerRule = new ProviderTestRule.Builder(
			OrgProvider.class, OrgProvider.class.getName()).build();

	private android.content.ContentResolver resolver;

	@Before
	public void setUp() {
		resolver = providerRule.getResolver();
	}

	@Test
	public void testNodeToStringSimple() {
		OrgNode node = new OrgNode();
		node.name = "my simple test";
		node.todo = "TODO";
		node.level = 3;

		assertEquals("*** TODO my simple test", node.toString());
	}

	@Test
	public void testAddNodeSimple() throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);

		Cursor cursor = resolver.query(OrgData.buildIdUri(node.id),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());
		OrgNode insertedNode = new OrgNode(cursor);
		cursor.close();

		assertTrue(node.equals(insertedNode));
	}

	@Test
	public void testAddAndUpdateNode() throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);

		node.todo = "DONE";
		node.write(resolver);

		Cursor orgDataCursor = resolver.query(OrgData.CONTENT_URI, null, null,
				null, null);
		assertEquals(1, orgDataCursor.getCount());
		orgDataCursor.close();
		Cursor cursor = resolver.query(OrgData.buildIdUri(node.id),
				OrgData.DEFAULT_COLUMNS, null, null, null);
		assertNotNull(cursor);
		assertEquals(1, cursor.getCount());
		OrgNode insertedNode = new OrgNode(cursor);
		cursor.close();

		assertTrue(node.equals(insertedNode));
	}

	@Test
	public void testGetParentSimple() throws OrgNodeNotFoundException {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);

		OrgNode childNode = OrgTestUtils.getDefaultOrgNode();
		childNode.parentId = node.id;
		childNode.write(resolver);

		OrgNode parent = childNode.getParent(resolver);
		assertEquals(node.id, parent.id);
	}

	@Test
	public void testGetParentFileNode() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.parentId = file.nodeId;
		node.write(resolver);

		OrgNode parent = node.getParent(resolver);
		assertEquals(file.nodeId, parent.id);
	}

	@Test
	public void testGetParentWithTopLevel() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);

		OrgNode node = new OrgNode(file.nodeId, resolver);

		try {
			node.getParent(resolver);
			fail("File shouldn't exist");
		} catch (OrgNodeNotFoundException e) {}
	}

	@Test
	public void testGetChildrenSimple() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);

		OrgNode child1 = OrgTestUtils.getDefaultOrgNode();
		child1.parentId = node.id;
		child1.write(resolver);
		OrgNode child2 = OrgTestUtils.getDefaultOrgNode();
		child2.parentId = node.id;
		child2.write(resolver);

		ArrayList<OrgNode> children = node.getChildren(resolver);
		assertEquals(2, children.size());
	}

	@Test
	public void testArchiveNode() {
		OrgNode childNode = OrgTestUtils.setupParentScenario(resolver);
		childNode.archiveNode(resolver);

		try {
			new OrgNode(childNode.id, resolver);
			fail("Node should not exist");
		} catch (OrgNodeNotFoundException e) {}

		OrgTestUtils.cleanupParentScenario(resolver);
	}

	@Test
	public void testArchiveNodeGeneratesEdit() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);

		Cursor editCursor = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = node.archiveNode(resolver);
		edit.type.equals(OrgEdit.TYPE.ARCHIVE);

		Cursor editCursor2 = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testArchiveNodeToSibling() throws OrgNodeNotFoundException {
		OrgNode childNode = OrgTestUtils.setupParentScenario(resolver);
		OrgNode parent = childNode.getParent(resolver);

		childNode.archiveNodeToSibling(resolver);

		OrgNode archiveNode = parent.getChild(OrgNode.ARCHIVE_NODE, resolver);
		assertNotNull(archiveNode);

		assertEquals(archiveNode.id, childNode.parentId);
		assertEquals(archiveNode.fileId, childNode.fileId);
	}

	@Test
	public void testArchiveNodeToSiblingGeneratesEdit() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);
		node.write(resolver);

		Cursor editCursor = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int baseOfEdits = editCursor.getCount();
		editCursor.close();

		OrgEdit edit = node.archiveNodeToSibling(resolver);
		edit.type.equals(OrgEdit.TYPE.ARCHIVE_SIBLING);

		Cursor editCursor2 = resolver.query(Edits.CONTENT_URI, Edits.DEFAULT_COLUMNS, null, null, null);
		int numberOfEdits = editCursor2.getCount();
		editCursor2.close();

		assertEquals(baseOfEdits + 1, numberOfEdits);
	}

	@Test
	public void testGetOlpLink() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		String olp = node.getOlpId(resolver);
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp);
	}

	@Test
	public void testGetNodeFromOlpLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		String olp = node.getOlpId(resolver);
		OrgNode nodeFromOlpPath = OrgProviderUtils.getOrgNodeFromOlpPath(olp, resolver);
		assertEquals(node.id, nodeFromOlpPath.id);
	}

	@Test
	public void testGetNodeFromOlpFileLink() throws OrgNodeNotFoundException, OrgFileNotFoundException {
		OrgTestUtils.setupParentScenario(resolver);
		final String filename = OrgTestUtils.defaultTestfilename;
		OrgNode fileNode = OrgProviderUtils.getOrgNodeFromFilename(filename, resolver);
		final String olp = "olp:" + filename;

		OrgNode nodeFromOlpPath = OrgProviderUtils.getOrgNodeFromOlpPath(olp, resolver);

		assertEquals(fileNode.id, nodeFromOlpPath.id);
	}

	@Test
	public void testGetOlpLinkWithCookie() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);
		node.name += " [1/3]";

		String olp = node.getOlpId(resolver);
		assertEquals(OrgTestUtils.setupParentScenarioChild2ChildOlpId, olp.trim());
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java
git commit -m "test: migrate OrgNodeTest to AndroidX ProviderTestRule"
```

---

### Task 10: Migrate OrgFileTest (Pattern B: ProviderTestRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgFileTest.java`

Uses `ProviderTestCase2<OrgProvider>`.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.OrgData;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.database.Cursor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class OrgFileTest {

	@Rule
	public ProviderTestRule providerRule = new ProviderTestRule.Builder(
			OrgProvider.class, OrgProvider.class.getName()).build();

	private android.content.ContentResolver resolver;

	@Before
	public void setUp() {
		resolver = providerRule.getResolver();
	}

	@Test
	public void testAddFileSimple() throws OrgFileNotFoundException, OrgNodeNotFoundException{
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		orgFile.addFile(resolver);

		OrgFile insertedFile = new OrgFile(orgFile.id, resolver);
		assertTrue(orgFile.equals(insertedFile));
		assertEquals(insertedFile.id, orgFile.id);
		assertEquals(insertedFile.nodeId, orgFile.nodeId);

		OrgNode node = new OrgNode(orgFile.nodeId, resolver);
		assertEquals(node.name, orgFile.name);
		assertTrue(orgFile.id >= 0);
		assertEquals(node.fileId, orgFile.id);
	}

	@Test
	public void testDoesFileExist() {
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		orgFile.addFile(resolver);

		assertTrue(orgFile.doesFileExist(resolver));
	}

	@Test
	public void testRemoveFileSimple() throws OrgFileNotFoundException {
		OrgFile orgFile = new OrgFile("filename", "name", "checksum");
		orgFile.addFile(resolver);
		OrgFile insertedFile = new OrgFile(orgFile.id, resolver);
		insertedFile.removeFile(resolver);

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
		OrgFile orgFile = node.getOrgFile(resolver);

		orgFile.removeFile(resolver);

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

		OrgDatabase db = new OrgDatabaseStub(providerRule.getContext());
		OrgFileParser parser = new OrgFileParser(db, resolver);
		parser.parse(orgFile, breader);
		db.close();

		OrgFile file = new OrgFile(filename, resolver);
		String fileString = file.toString(resolver);
		assertEquals(SimpleOrgFiles.orgFile.trim(), fileString.trim());
	}

	@Test
	public void testCreateFile () {
		final String fileAlias = "test name";
		OrgFile file = OrgProviderUtils.getOrCreateFile("test file", fileAlias, resolver);

		assertTrue(file.id >= 0);
		assertTrue(file.doesFileExist(resolver));

		try {
			OrgNode capturefileNode = file.getOrgNode(resolver);
			assertTrue(capturefileNode.id >= 0);
			assertTrue(capturefileNode.fileId >= 0);
			assertEquals(file.id, capturefileNode.fileId);
			assertEquals(fileAlias, capturefileNode.name);
		} catch (IllegalArgumentException e) {
			fail("OrgNode not created");
		}

		try {
			OrgFile file2 = new OrgFile(file.id, resolver);
			assertTrue(file.equals(file2));
		} catch (OrgFileNotFoundException e) {
			fail("File node not created");
		}
	}

	@Test
	public void testCreateCaptureFile () {
		OrgFile file = OrgProviderUtils.getOrCreateCaptureFile(resolver);

		assertTrue(file.id >= 0);
		assertTrue(file.doesFileExist(resolver));

		try {
			OrgNode capturefileNode = file.getOrgNode(resolver);
			assertTrue(capturefileNode.id >= 0);
			assertTrue(capturefileNode.fileId >= 0);
			assertEquals(file.id, capturefileNode.fileId);
			assertEquals(OrgFile.CAPTURE_FILE_ALIAS, capturefileNode.name);
		} catch (IllegalArgumentException e) {
			fail("OrgNode not created");
		}

		try {
			OrgFile file2 = new OrgFile(file.id, resolver);
			assertTrue(file.equals(file2));
		} catch (OrgFileNotFoundException e) {
			fail("File node not created");
		}
	}

	@Test
	public void testGetCaptureFile () {
		OrgNode node1 = OrgProviderUtils.getOrCreateCaptureFile(resolver)
				.getOrgNode(resolver);

		assertNotNull(node1);
		assertTrue(node1.id >= 0);
		assertTrue(node1.fileId >= 0);

		OrgNode node2 = OrgProviderUtils.getOrCreateCaptureFile(resolver)
				.getOrgNode(resolver);
		assertNotNull(node2);

		assertEquals(node1.id, node2.id);
		assertEquals(node1.fileId, node2.fileId);
		assertEquals(node1.name, node2.name);
	}

	@Test
	public void testGetOrgNodeFromFilename() throws OrgFileNotFoundException{
		OrgFile file = OrgProviderUtils.getOrCreateFile("test file", "file name", resolver);
		OrgNode fileNode = file.getOrgNode(resolver);

		OrgNode node = OrgProviderUtils.getOrgNodeFromFilename(file.filename, resolver);

		assertEquals(fileNode.name, node.name);
		assertEquals(fileNode.id, node.id);
		assertEquals(fileNode.fileId, node.fileId);
		assertEquals(fileNode.parentId, node.parentId);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgFileTest.java
git commit -m "test: migrate OrgFileTest to AndroidX ProviderTestRule"
```

---

### Task 11: Migrate OrgEditTest (Pattern B: ProviderTestRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgEditTest.java`

Uses `ProviderTestCase2<OrgProvider>`.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.OrgData;

import java.util.ArrayList;

import android.database.Cursor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OrgEditTest {

	@Rule
	public ProviderTestRule providerRule = new ProviderTestRule.Builder(
			OrgProvider.class, OrgProvider.class.getName()).build();

	private android.content.ContentResolver resolver;

	@Before
	public void setUp() {
		resolver = providerRule.getResolver();
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

		ArrayList<OrgEdit> generatedEdits = node.generateApplyEditNodes(editedNode,
				resolver);
		assertEquals(numberOfEdits, generatedEdits.size());
	}

	@Test
	public void testNewHeadingSimple() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);

		OrgNode fileNode = new OrgNode(file.nodeId, resolver);

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = fileNode.fileId;
		node.parentId = fileNode.id;

		OrgEdit edit = node.createParentNewheading(resolver);
		assertEquals(OrgEdit.TYPE.ADDHEADING, edit.type);
	}

	@Test
	public void testNewHeadingDefaultFile() {
		OrgNode capturefileNode = OrgProviderUtils
				.getOrCreateCaptureFile(resolver).getOrgNode(resolver);
		assertTrue(capturefileNode.fileId >= 0);

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = capturefileNode.fileId;
		node.parentId = capturefileNode.id;

		OrgEdit edit = node.createParentNewheading(resolver);
		assertEquals(null, edit.type);
	}

	@Test
	public void testEditsToStringSimple() throws OrgNodeNotFoundException {
		OrgFile file = OrgTestUtils.getDefaultOrgFile();
		file.write(resolver);
		OrgNode fileNode = new OrgNode(file.nodeId, resolver);

		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.fileId = fileNode.fileId;
		node.parentId = fileNode.id;

		node.createParentNewheading(resolver).write(resolver);

		node.level = 0;
		String correctEditString = new OrgEdit(fileNode,
				OrgEdit.TYPE.ADDHEADING, node.toString(), resolver).toString();

		String editsString = OrgEdit.editsToString(resolver);
		assertEquals(correctEditString.trim(), editsString.trim());
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgEditTest.java
git commit -m "test: migrate OrgEditTest to AndroidX ProviderTestRule"
```

---

### Task 12: Migrate OrgFileParserTest (Pattern B: ProviderTestRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgFileParserTest.java`

Uses `ProviderTestCase2<OrgProvider>` with `OrgDatabaseStub`.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.OrgData;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

import android.database.Cursor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgFileParser;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.test.util.OrgTestFiles;
import com.matburt.mobileorg.test.util.OrgTestFiles.OrgFileWithEmphasisedNode;
import com.matburt.mobileorg.test.util.OrgTestFiles.OrgFileWithStarNewlineNode;
import com.matburt.mobileorg.test.util.OrgTestFiles.OrgIndexWithFileDirectorySpaces;
import com.matburt.mobileorg.test.util.OrgTestFiles.SimpleOrgFiles;
import com.matburt.mobileorg.test.util.OrgTestUtils;
import com.matburt.mobileorg.util.OrgNodeNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OrgFileParserTest {

	@Rule
	public ProviderTestRule providerRule = new ProviderTestRule.Builder(
			OrgProvider.class, OrgProvider.class.getName()).build();

	private OrgDatabaseStub db;
	private OrgFileParser parser;

	@Before
	public void setUp() {
		db = new OrgDatabaseStub(providerRule.getContext());
		parser = new OrgFileParser(db, providerRule.getResolver());
	}

	@After
	public void tearDown() {
		db.close();
	}

	@Test
	public void testParseSimple() {
		Cursor cursor = providerRule.getResolver().query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS,
				null, null, null);
		assertNotNull(cursor);
		cursor.close();

		InputStream is = new ByteArrayInputStream(SimpleOrgFiles.orgFile.getBytes());
		BufferedReader breader = new BufferedReader(new InputStreamReader(is));
		OrgFile orgFile = new OrgFile("new file", "file alias", "");
		parser.parse(orgFile, breader);

		assertEquals(2, db.fastInsertNodeCalls);
		assertEquals(3, db.fastInsertNodePayloadCalls);

		cursor = providerRule.getResolver().query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS,
				null, null, null);
		assertEquals(3, cursor.getCount());
		cursor.close();

		assertTrue(orgFile.id > -1);
		cursor = providerRule.getResolver().query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, OrgData.FILE_ID + "=?",
				new String[] { Long.toString(orgFile.id) }, OrgData.ID + " DESC");
		assertEquals(3, cursor.getCount());
		cursor.close();
	}

	@Test
	public void testParseParentChildRelation() throws OrgNodeNotFoundException {
		InputStream is = new ByteArrayInputStream(SimpleOrgFiles.orgFile.getBytes());
		BufferedReader breader = new BufferedReader(new InputStreamReader(is));
		final String name = "file alias";
		OrgFile orgFile = new OrgFile("GTD.org", name, "");
		OrgProviderUtils.setTodos(OrgTestUtils.getTodos(), providerRule.getResolver());
		parser.parse(orgFile, breader);

		Cursor cursor = providerRule.getResolver().query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, OrgData.NAME + "=?",
				new String[] { name }, null);
		OrgNode fileNode = new OrgNode(cursor);
		cursor.close();

		cursor = providerRule.getResolver().query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, OrgData.NAME + "=?",
				new String[] { SimpleOrgFiles.orgFileTopHeading }, null);
		OrgNode topNode = new OrgNode(cursor);
		cursor.close();

		cursor = providerRule.getResolver().query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS, OrgData.NAME + "=?",
				new String[] { SimpleOrgFiles.orgFileChildHeading }, null);
		OrgNode childNode = new OrgNode(cursor);
		cursor.close();

		assertEquals(-1, fileNode.parentId);
		assertEquals(fileNode.id, topNode.parentId);
		assertEquals(topNode.id, childNode.parentId);
	}

	@Test
	public void testGetFilesFromIndex() {
		HashMap<String,String> files = OrgFileParser.getFilesFromIndex(SimpleOrgFiles.indexFile);

		for(String file: SimpleOrgFiles.files) {
			if(files.get(file) == null)
				assertTrue("Didn't find all files", false);
		}
	}

	@Test
	public void testGetFilesFromIndexWithSpaces() {
		final String filename = OrgIndexWithFileDirectorySpaces.filename;
		final String fileAlias = OrgIndexWithFileDirectorySpaces.fileAlias;
		HashMap<String,String> files = OrgFileParser.getFilesFromIndex(OrgIndexWithFileDirectorySpaces.indexFile);

		assertTrue(files.containsKey(filename));
		String retrievedFileAlias = files.get(filename);

		assertEquals(fileAlias, retrievedFileAlias);
	}

	@Test
	public void testGetFilesFromIndexWithSpacesWithoutAlias() {
		final String filename = OrgIndexWithFileDirectorySpaces.filenameWithoutAlias;
		HashMap<String,String> files = OrgFileParser.getFilesFromIndex(OrgIndexWithFileDirectorySpaces.indexFile);

		assertTrue(files.containsKey(filename));
		String retrievedFileAlias = files.get(filename);
		assertEquals(filename, retrievedFileAlias);
	}

	@Test
	public void testGetTodosFromIndex() {
		ArrayList<String> tagsFromIndex = OrgFileParser.getTagsFromIndex(SimpleOrgFiles.indexFile);

		for(String tag: SimpleOrgFiles.tags) {
			if(tagsFromIndex.contains(tag) == false)
				assertTrue("Didn't find all tags", false);
		}
	}

	@Test
	public void testGetPrioritiesFromIndex() {
		ArrayList<String> prioritiesFromIndex = OrgFileParser.getPrioritiesFromIndex(SimpleOrgFiles.indexFile);

		for(String priorities: SimpleOrgFiles.priorities) {
			if(prioritiesFromIndex.contains(priorities) == false)
				assertTrue("Didn't find all priorities", false);
		}
	}

	@Test
	public void testGetTagsFromIndex() {
		ArrayList<String> tagsFromIndex = OrgFileParser.getTagsFromIndex(SimpleOrgFiles.indexFile);

		for(String tag: SimpleOrgFiles.tags) {
			if(tagsFromIndex.contains(tag) == false)
				assertTrue("Didn't find all tags", false);
		}
	}

	@Test
	public void testGetTagsFromIndexEmptyTags() {
		ArrayList<String> tagsFromIndex = OrgFileParser.getTagsFromIndex(OrgTestFiles.indexFileWithEmptyDrawers);
		assertEquals(0, tagsFromIndex.size());
	}

	@Test
	public void testParseFileWithEmphasisNode() {
		InputStream is = new ByteArrayInputStream(OrgFileWithEmphasisedNode.orgFile.getBytes());
		BufferedReader breader = new BufferedReader(new InputStreamReader(is));
		OrgFile orgFile = new OrgFile("new file", "file alias", "");
		parser.parse(orgFile, breader);

		assertEquals(OrgFileWithEmphasisedNode.numberOfHeadings, db.fastInsertNodeCalls);
		assertTrue(db.fastInsertNodePayloadCalls >= 1);
	}

	@Test
	public void testParseFileWithStarNewline() {
		InputStream is = new ByteArrayInputStream(OrgFileWithStarNewlineNode.orgFile.getBytes());
		BufferedReader breader = new BufferedReader(new InputStreamReader(is));
		OrgFile orgFile = new OrgFile("new file", "file alias", "");
		parser.parse(orgFile, breader);

		assertEquals(OrgFileWithStarNewlineNode.numberOfHeadings, db.fastInsertNodeCalls);
		assertTrue(db.fastInsertNodePayloadCalls >= 1);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgFileParserTest.java
git commit -m "test: migrate OrgFileParserTest to AndroidX ProviderTestRule"
```

---

### Task 13: Migrate SynchronizerTest (Pattern B: ProviderTestRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Synchronizers/SynchronizerTest.java`

Uses `ProviderTestCase2<OrgProvider>`.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.Synchronizers;

import java.io.IOException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;

import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgEdit;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.Synchronizers.Synchronizer;
import com.matburt.mobileorg.test.util.OrgTestFiles.SimpleOrgFiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SynchronizerTest {

	@Rule
	public ProviderTestRule providerRule = new ProviderTestRule.Builder(
			OrgProvider.class, OrgProvider.class.getName()).build();

	private Synchronizer synchronizer;
	private OrgFileParserStub parserStub;
	private OrgDatabase db;
	private SynchronizerStub synchronizerStub;
	private SynchronizerNotificationStub notifyStub;

	@Before
	public void setUp() throws Exception {
		this.db = new OrgDatabase(providerRule.getContext());
		this.parserStub = new OrgFileParserStub(db, providerRule.getResolver());
		this.synchronizerStub = new SynchronizerStub();
		this.notifyStub = new SynchronizerNotificationStub(providerRule.getContext());
		this.synchronizer = new Synchronizer(providerRule.getContext(), synchronizerStub, notifyStub);
	}

	@After
	public void tearDown() throws Exception {
		db.close();
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
		file.write(providerRule.getResolver());

		OrgNode node = new OrgNode();
		node.setFilename(Synchronizer.CAPTURE_FILE, providerRule.getResolver());
		node.write(providerRule.getResolver());
		synchronizer.pushCaptures();
	}

	@Test
	public void testPushWithCapturesAndEdits() throws SSLHandshakeException, CertificateException, IOException, Exception {
		synchronizerStub.addFile(Synchronizer.CAPTURE_FILE, "");
		OrgFile file = new OrgFile(Synchronizer.CAPTURE_FILE, Synchronizer.CAPTURE_FILE, "");
		file.write(providerRule.getResolver());

		OrgNode node = new OrgNode();
		node.setFilename(Synchronizer.CAPTURE_FILE, providerRule.getResolver());
		node.write(providerRule.getResolver());

		OrgEdit edit = new OrgEdit(node, OrgEdit.TYPE.ADDHEADING, providerRule.getResolver());
		edit.write(providerRule.getResolver());
		synchronizer.pushCaptures();
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Synchronizers/SynchronizerTest.java
git commit -m "test: migrate SynchronizerTest to AndroidX ProviderTestRule"
```

---

### Task 14: Migrate AgendaTests (Pattern B: Context needed)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/AgendaTests.java`

Uses `AndroidTestCase` but actually needs a `Context` for file I/O (`OrgAgenda.writeAgendas`/`readAgendas`). Use `InstrumentationRegistry.getTargetContext()` from AndroidX Test.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.Gui;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.matburt.mobileorg.Gui.Agenda.OrgAgenda;
import com.matburt.mobileorg.Gui.Agenda.OrgQueryBuilder;

import android.content.Context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class AgendaTests {

	private Context context;

	@Before
	public void setUp() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
	}

	@Test
	public void testBlockSerialization() throws IOException {
		ArrayList<OrgAgenda> agendas = new ArrayList<OrgAgenda>();
		OrgAgenda blockAgenda = new OrgAgenda();
		blockAgenda.title = "test";
		agendas.add(blockAgenda);

		OrgAgenda.writeAgendas(agendas, context);
		ArrayList<OrgAgenda> readAgendas = OrgAgenda.readAgendas(context);

		assertEquals(agendas.size(), readAgendas.size());
		OrgAgenda readBlockAgenda = readAgendas.get(0);
		assertEquals(blockAgenda.title, readBlockAgenda.title);
	}

	@Test
	public void testQuerySerialization() throws IOException {
		ArrayList<OrgAgenda> agendas = new ArrayList<OrgAgenda>();
		OrgAgenda blockAgenda = new OrgAgenda();
		agendas.add(blockAgenda);

		OrgQueryBuilder builder = new OrgQueryBuilder("test");
		blockAgenda.queries.add(builder);

		OrgAgenda.writeAgendas(agendas, context);
		ArrayList<OrgAgenda> readAgendas = OrgAgenda.readAgendas(context);

		OrgAgenda readBlockAgenda = readAgendas.get(0);
		assertEquals(blockAgenda.queries.size(), readBlockAgenda.queries.size());
		assertEquals(blockAgenda.queries.get(0).title, readBlockAgenda.queries.get(0).title);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/AgendaTests.java
git commit -m "test: migrate AgendaTests to AndroidX InstrumentationRegistry"
```

---

### Task 15: Migrate EditActivityTest (Pattern C: ActivityScenarioRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/EditActivityTest.java`

Uses `ActivityInstrumentationTestCase2<EditActivity>`. Needs Espresso.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.Gui;

import android.content.ContentResolver;
import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.matburt.mobileorg.Gui.Capture.EditActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivityController;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.test.util.OrgTestUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class EditActivityTest {

	private ContentResolver resolver;
	private long nodeId = -1;

	@Rule
	public ActivityTestRule<EditActivity> activityRule = new ActivityTestRule<>(
			EditActivity.class, true, false);

	@Before
	public void setUp() {
		resolver = InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver();
	}

	@After
	public void tearDown() {
		if (nodeId >= 0) {
			resolver.delete(OrgData.buildIdUri(nodeId), null, null);
		}
	}

	private EditActivity prepareActivityWithNode(OrgNode node) {
		node.write(resolver);
		this.nodeId = node.id;

		Intent intent = new Intent();
		intent.putExtra(EditActivityController.ACTIONMODE, EditActivityController.ACTIONMODE_EDIT);
		intent.putExtra(EditActivityController.NODE_ID, node.id);

		return activityRule.launchActivity(intent);
	}

	@Test
	public void testSimple() {
		OrgNode node = new OrgNode();
		EditActivity activity = prepareActivityWithNode(node);

		assertFalse(activity.hasEdits());
		OrgNode newNode = activity.getEditedNode();
		assertTrue(node.equals(newNode));
		activity.finish();
	}

	@Test
	public void testGetUneditedBasic() {
		OrgNode node = OrgTestUtils.getComplexOrgNode();
		EditActivity activity = prepareActivityWithNode(node);

		OrgNode newNode = activity.getEditedNode();
		assertTrue(node.equals(newNode));
		activity.finish();
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/EditActivityTest.java
git commit -m "test: migrate EditActivityTest to AndroidX ActivityTestRule"
```

---

### Task 16: Migrate LocationFragmentTest (Pattern C: ActivityScenarioRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/LocationFragmentTest.java`

Uses `ActivityInstrumentationTestCase2<EditActivity>`.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.Gui;

import android.content.ContentResolver;
import android.content.Intent;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.matburt.mobileorg.Gui.Capture.EditActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivityController;
import com.matburt.mobileorg.Gui.Capture.LocationFragment;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProviderUtils;
import com.matburt.mobileorg.test.util.OrgTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class LocationFragmentTest {
	private final String LOCATION_FRAGMENT = "locationFragment";

	private ContentResolver resolver;
	private LocationFragment locationFragment;
	private long nodeId = -1;
	private EditActivity activity;

	@Rule
	public ActivityTestRule<EditActivity> activityRule = new ActivityTestRule<>(
			EditActivity.class, true, false);

	@Before
	public void setUp() {
		resolver = InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver();
	}

	@After
	public void tearDown() {
		if (nodeId >= 0) {
			resolver.delete(OrgData.buildIdUri(nodeId), null, null);
		}
		nodeId = -1;
		if (activity != null) {
			activity.finish();
		}
	}

	private void prepareActivityWithNode(OrgNode node, String actionMode) {
		Intent intent = new Intent();
		intent.putExtra(EditActivityController.ACTIONMODE, actionMode);
		intent.putExtra(EditActivityController.NODE_ID, node.id);

		activity = activityRule.launchActivity(intent);
		resolver = activity.getContentResolver();

		locationFragment = (LocationFragment) activity
				.getSupportFragmentManager().findFragmentByTag(LOCATION_FRAGMENT);
	}

	@Test
	public void testSetup() {
		OrgNode node = OrgTestUtils.getDefaultOrgNode();
		node.write(resolver);
		this.nodeId = node.id;

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_EDIT);

		assertNotNull(activity);
		assertNotNull(locationFragment);
	}

	@Test
	public void test_Create_Simple() {
		OrgNode node = new OrgNode();

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_CREATE);
		OrgNode locationNode = locationFragment.getLocationSelection();

		OrgNode captureFile = OrgProviderUtils.getOrCreateCaptureFile(resolver).getOrgNode(resolver);
		assertEquals(captureFile.fileId, locationNode.fileId);
		assertEquals(captureFile.id, locationNode.id);
	}

	@Test
	public void test_Addchild_ToplevelFile() {
		OrgFile file = OrgProviderUtils.getOrCreateFile("test file.org", "delete me", resolver);
		OrgNode fileNode = file.getOrgNode(resolver);

		prepareActivityWithNode(fileNode, EditActivityController.ACTIONMODE_ADDCHILD);
		OrgNode locationNode = locationFragment.getLocationSelection();

		assertEquals(fileNode.name, locationNode.name);
		assertEquals(fileNode.id, locationNode.id);
		assertEquals(fileNode.fileId, locationNode.fileId);
	}

	@Test
	public void test_Addchild_ToplevelFileWithAddChild() throws InterruptedException {
		OrgNode fileNode = OrgProviderUtils.getOrCreateCaptureFile(resolver).getOrgNode(resolver);

		prepareActivityWithNode(fileNode, EditActivityController.ACTIONMODE_ADDCHILD);

		activity.runOnUiThread(new Runnable() {
			public void run() {
				locationFragment.addChild(null, "");
			}
		});
		Thread.sleep(500);

		OrgNode locationNode = locationFragment.getLocationSelection();

		assertEquals(fileNode.id, locationNode.id);
		assertEquals(fileNode.fileId, locationNode.fileId);
	}

	@Test
	public void test_Addchild_NestedChild() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_ADDCHILD);
		OrgNode locationNode = locationFragment.getLocationSelection();

		OrgTestUtils.cleanupParentScenario(resolver);
		assertEquals(node.id, locationNode.id);
		assertEquals(node.fileId, locationNode.fileId);
	}

	@Test
	public void test_Edit_NestedChild() {
		OrgNode node = OrgTestUtils.setupParentScenario(resolver);

		prepareActivityWithNode(node, EditActivityController.ACTIONMODE_EDIT);
		OrgNode locationNode = locationFragment.getLocationSelection();

		OrgTestUtils.cleanupParentScenario(resolver);
		assertEquals(node.parentId, locationNode.id);
		assertEquals(node.fileId, locationNode.fileId);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/LocationFragmentTest.java
git commit -m "test: migrate LocationFragmentTest to AndroidX ActivityTestRule"
```

---

### Task 17: Migrate TagsFragmentTest (Pattern C: ActivityScenarioRule)

**Files:**
- Modify: `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/TagsFragmentTest.java`

Uses `ActivityInstrumentationTestCase2<EditActivity>`.

- [ ] **Step 1: Replace entire file content**

```java
package com.matburt.mobileorg.test.Gui;

import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.matburt.mobileorg.Gui.Capture.EditActivity;
import com.matburt.mobileorg.Gui.Capture.EditActivityController;
import com.matburt.mobileorg.Gui.Capture.TagsFragment;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.test.util.OrgTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class TagsFragmentTest {
	private final String TAGS_FRAGMENT = "tagsFragment";

	private ContentResolver resolver;

	private OrgNode node;
	private long nodeId = -1;
	private EditActivity activity;

	@Rule
	public ActivityTestRule<EditActivity> activityRule = new ActivityTestRule<>(
			EditActivity.class, true, false);

	@Before
	public void setUp() {
		resolver = InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver();
	}

	@After
	public void tearDown() {
		if (nodeId >= 0) {
			resolver.delete(OrgData.buildIdUri(nodeId), null, null);
		}
		nodeId = -1;
		if (activity != null) {
			activity.finish();
		}
	}

	private void prepareActivityWithTags(String tags) {
		this.node = OrgTestUtils.getDefaultOrgNode();
		this.node.tags = tags;
		this.node.write(resolver);
		this.nodeId = node.id;

		Intent intent = new Intent();
		intent.putExtra(EditActivityController.ACTIONMODE, EditActivityController.ACTIONMODE_EDIT);
		intent.putExtra(EditActivityController.NODE_ID, node.id);

		activity = activityRule.launchActivity(intent);
	}

	private void saveAndRestoreState(final TagsFragment tagsFragment) throws InterruptedException {
		activity.runOnUiThread(new Runnable() {
			public void run() {
				Bundle outState = new Bundle();
				tagsFragment.onSaveInstanceState(outState);
				tagsFragment.restoreFromBundle(outState);
			}
		});
		Thread.sleep(500);
	}

	@Test
	public void testSetup() {
		prepareActivityWithTags("");
		assertNotNull(activity);

		TagsFragment tagsFragment = (TagsFragment) activity
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT);
		assertNotNull(tagsFragment);
	}

	@Test
	public void testSimple() {
		final String tags = "tag1:tag2";
		prepareActivityWithTags(tags);

		TagsFragment tagsFragment = (TagsFragment) activity
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT);
		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}

	@Test
	public void testSaveAndRestore() throws InterruptedException {
		final String tags = "tag1:tag2::tag4:tag10";
		prepareActivityWithTags(tags);

		final TagsFragment tagsFragment = (TagsFragment) activity
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT);
		saveAndRestoreState(tagsFragment);

		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}

	@Test
	public void testAddEntry() throws InterruptedException {
		String tags = "tag1:tag4::tag2:tag10";
		prepareActivityWithTags(tags);

		final TagsFragment tagsFragment = (TagsFragment) activity
				.getSupportFragmentManager().findFragmentByTag(TAGS_FRAGMENT);
		final String addedTag = "hello";
		activity.runOnUiThread(new Runnable() {
			public void run() {
				tagsFragment.addTagEntry(addedTag);
			}
		});
		Thread.sleep(500);

		tags += ":" + addedTag;
		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}

	@Test
	public void testAddEntryAndSaveAndRestore() throws InterruptedException {
		String tags = "tag1:tag4::tag2:tag10";
		prepareActivityWithTags(tags);

		final TagsFragment tagsFragment = (TagsFragment) activity
				.getSupportFragmentManager().findFragmentByTag("tagsFragment");
		final String addedTag = "hello";
		activity.runOnUiThread(new Runnable() {
			public void run() {
				tagsFragment.addTagEntry(addedTag);
			}
		});
		Thread.sleep(500);
		saveAndRestoreState(tagsFragment);

		tags += ":" + addedTag;
		String resultTags = tagsFragment.getTags();
		assertEquals(tags, resultTags);
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/TagsFragmentTest.java
git commit -m "test: migrate TagsFragmentTest to AndroidX ActivityTestRule"
```

---

### Task 18: Verify all tests compile and clean up

**Files:**
- Verify: all test files

- [ ] **Step 1: Push to CI and verify build**

```bash
git push
```

Check CI at: `gh run list --repo lujun9972/mobileorg-android --limit 1`

- [ ] **Step 2: Delete old instrumentTest directory if any remnants remain**

```bash
ls MobileOrg/src/instrumentTest 2>/dev/null && echo "still exists" || echo "already removed"
```

Expected: "already removed"

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test: complete instrumentation test migration to AndroidX"
```
