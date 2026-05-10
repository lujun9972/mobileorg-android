# Migrate Instrumentation Tests to AndroidX Test Framework

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate existing 20 instrumentation tests from deprecated `android.test.*` to modern AndroidX Test framework so they can run on CI.

**Architecture:** Move tests from `instrumentTest/` to `androidTest/`. Replace deprecated base classes with JUnit 4 + AndroidX Test equivalents. No new test cases — only migration.

**Tech Stack:** JUnit 4, AndroidX Test (runner, rules, ext/junit), AGP 8.2.2

---

## 1. Dependencies and Directory Structure

**Directory change:** `MobileOrg/src/instrumentTest/` → `MobileOrg/src/androidTest/`

**build.gradle additions:**

```groovy
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test:runner:1.5.2'
androidTestImplementation 'androidx.test:rules:1.5.0'
```

Keep existing `testImplementation 'junit:junit:4.12'` unchanged.

Add to `android.defaultConfig`:

```groovy
testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
```

## 2. Test Base Class Migration

Three migration patterns:

### Pattern A: `AndroidTestCase` → JUnit 4 (7 files)

Tests that don't need Android Context. Remove `extends AndroidTestCase`, add `@RunWith(JUnit4.class)` and `@Test` annotations.

Files: `OrgNodeParserTest`, `OrgNodePayloadTest`, `OrgNodeDateTest`, `OrgNodeTest`, `CalendarSyncServiceTest`, `AgendaTests`, `TagsFragmentTest` (partial — split if needed)

```java
// Before
import android.test.AndroidTestCase;
public class OrgNodeParserTest extends AndroidTestCase {
    public void testXxx() { ... }
}

// After
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
@RunWith(JUnit4.class)
public class OrgNodeParserTest {
    @Test
    public void testXxx() { ... }
}
```

### Pattern B: `ProviderTestCase2` → AndroidX ProviderTestCase2 (7 files)

Tests that need ContentProvider. Use `androidx.test.ext.junit.runners.AndroidJUnit4` and AndroidX `ProviderTestCase2`.

Files: `OrgDatabaseTest`, `OrgFileParserTest`, `OrgFileTest`, `OrgEditTest`, `SynchronizerTest`, `OrgNodeTest` (partial — if uses Provider)

```java
// Before
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;
public class OrgDatabaseTest extends ProviderTestCase2<OrgProvider> { ... }

// After
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.provider.ProviderTestRule;
import org.junit.Rule;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class OrgDatabaseTest {
    @Rule
    public ProviderTestRule providerRule = new ProviderTestRule.Builder(OrgProvider.class, OrgProvider.class.getName()).build();
    // Replace getMockContentResolver() with providerRule.getResolver()
    // Replace getMockContext() with providerRule.getContext()
}
```

### Pattern C: `ActivityInstrumentationTestCase2` → ActivityScenarioRule (3 files)

UI tests need Espresso dependency.

Files: `EditActivityTest`, `LocationFragmentTest`, `AgendaTests` (partial)

Additional dependency:
```groovy
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
```

```java
// Before
import android.test.ActivityInstrumentationTestCase2;
public class EditActivityTest extends ActivityInstrumentationTestCase2<EditActivity> { ... }

// After
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
@RunWith(AndroidJUnit4.class)
public class EditActivityTest {
    @Rule
    public ActivityScenarioRule<EditActivity> rule = new ActivityScenarioRule<>(EditActivity.class);
}
```

## 3. Implementation Order

By dependency, bottom to top:

1. **build.gradle** — Add dependencies and testInstrumentationRunner
2. **Helper classes** — Move `OrgTestFiles`, `OrgTestUtils` to androidTest/ (no code changes, just directory)
3. **Stubs** — Move `OrgDatabaseStub`, `OrgFileParserStub`, `SynchronizerStub`, `SynchronizerNotificationStub` to androidTest/ (update package if needed)
4. **Pure data tests** — `OrgNodeParserTest`, `OrgNodePayloadTest`, `OrgNodeDateTest`
5. **Database tests** — `OrgDatabaseTest`, `OrgFileTest`, `OrgEditTest`, `OrgNodeTest`
6. **Parser tests** — `OrgFileParserTest`
7. **Synchronizer tests** — `SynchronizerTest`, `CalendarSyncServiceTest`
8. **UI tests** — `EditActivityTest`, `LocationFragmentTest`, `AgendaTests`

## 4. Scope

- **In scope:** Migrate all 20 existing tests to compile and run on modern framework
- **Out of scope:** Writing new test cases, refactoring test logic, adding CI workflow for tests
