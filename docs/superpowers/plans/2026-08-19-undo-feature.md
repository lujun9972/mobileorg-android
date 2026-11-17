# Undo（撤销最后一次本地编辑）实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 撤销最后一次本地编辑（LIFO），恢复编辑前状态；仅对未同步的编辑有效；入口在 OutlineActivity options menu。

**架构：** Edits 表加 `batch_id` 列，一次用户操作（编辑器保存/outline 快速编辑/clock-out/录音结束）产生的所有 edit 行共享一个批次号。新增 `OrgEditRepository.undoLatestBatch()` 弹出最高批次：按行内 `oldValue` 恢复节点字段/payload，然后删除该批次行。同步 push 清空 Edits 表后无可撤批次，undo 天然失效（设计边界，见 `docs/adr/0003-undo-pre-sync-batch-lifo.md`）。

**技术栈：** Android ContentProvider（`OrgProvider`）+ SQLite（`OrgDatabase`，DATABASE_VERSION 6→7）+ instrumentation 测试（`ProviderTestCase2` + `MockContentResolver`）。

**关键约束（来自 CLAUDE.md，执行时必须遵守）：**
- 结构操作（DELETE/REFILE/ADDHEADING/ARCHIVE）**不分配** batch_id（保持 null）——它们不可撤销，null 批次天然不进入 undo 范围。
- 撤销恢复**不得生成新 edit 行**（否则撤销本身变成新操作）。
- Cursor 必须关闭（throw 分支也要，用 try-finally）。
- 测试 setUp 必须清空 Edits/OrgData/Files 三表。
- 不用 `git add -A`，只 add 指定文件。
- 提交信息用英文 conventional commits（项目现状）。

---

### 任务 1：Edits 表增加 batch_id 列（schema v7）

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEdit.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgEditTest.java`
**导出/变更接口：** `OrgContract.java::EditsColumns`, `OrgEdit.java::batchId`, `OrgEdit.java::set`, `OrgEdit.java::write`, `OrgEdit.java::compare`
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java`（EditsColumns 接口 + Edits.DEFAULT_COLUMNS）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java`（DATABASE_VERSION、onCreate 的 edits 建表、onUpgrade）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEdit.java`（batchId 字段 + set/write/compare）
- 测试：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgEditTest.java`

- [x] **步骤 1：编写失败的测试**

在 `OrgEditTest.java` 追加（沿用该文件既有 import 与 setUp 模式）：

```java
@Test
public void testBatchIdRoundTrip() {
    OrgEdit edit = new OrgEdit();
    edit.title = "title";
    edit.newValue = "new";
    edit.oldValue = "old";
    edit.type = OrgEdit.TYPE.HEADING;
    edit.nodeId = "node id";
    edit.batchId = 42;
    long editId = edit.write(resolver);

    Cursor cursor = resolver.query(Edits.buildIdUri(editId),
            Edits.DEFAULT_COLUMNS, null, null, null);
    cursor.moveToFirst();
    OrgEdit inserted = new OrgEdit(cursor);
    cursor.close();
    assertEquals(42, inserted.batchId);
    assertTrue(edit.compare(inserted));
}

@Test
public void testBatchIdNullWhenUnset() {
    OrgEdit edit = new OrgEdit();
    edit.title = "t";
    edit.newValue = "n";
    edit.oldValue = "o";
    edit.type = OrgEdit.TYPE.TODO;
    edit.nodeId = "id";
    long editId = edit.write(resolver);

    Cursor cursor = resolver.query(Edits.buildIdUri(editId),
            Edits.DEFAULT_COLUMNS, null, null, null);
    cursor.moveToFirst();
    OrgEdit inserted = new OrgEdit(cursor);
    cursor.close();
    assertEquals(-1, inserted.batchId);
}
```

- [x] **步骤 2：运行测试验证失败**

先 `adb devices` 确认测试设备在线（无线调试 IP 见 CLAUDE.md/memory，端口易变，需用户开启无线调试后 `adb connect <ip>:<port>`）。运行：

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.OrgEditTest
```

预期：编译失败（`batchId` 字段不存在）。

- [x] **步骤 3：实现 schema 与字段**

`OrgContract.java` — `EditsColumns` 接口加一行，`Edits.DEFAULT_COLUMNS` 数组加入 `BATCH_ID`：

```java
interface EditsColumns {
    String ID = "_id";
    String TYPE = "type";
    String DATA_ID = "data_id";
    String TITLE = "title";
    String OLD_VALUE = "old_value";
    String NEW_VALUE = "new_value";
    String BATCH_ID = "batch_id";
}
```

`OrgDatabase.java`：
- `DATABASE_VERSION` 6 → 7
- onCreate 的 edits 建表语句末尾加列：`"changed integer," + "batch_id integer)"`（替换原 `"changed integer)"`）
- onUpgrade switch 加 case：

```java
case 7:
    db.execSQL("alter table edits add batch_id integer");
    break;
```

（switch 匹配 newVersion，见既有 case 5/6 写法；末尾 `onCreate(db)` 幂等无害。）

`OrgEdit.java`：

```java
public long batchId = -1;
```

`set(Cursor)` 中追加（放在 `setType(...)` 之前）：

```java
int batchIdx = cursor.getColumnIndex(Edits.BATCH_ID);
if (batchIdx >= 0 && !cursor.isNull(batchIdx))
    this.batchId = cursor.getLong(batchIdx);
```

`write(ContentResolver)` 的 values 块中追加：

```java
values.put(Edits.BATCH_ID, batchId > 0 ? batchId : null);
```

`compare(OrgEdit)` 的返回条件追加 `&& batchId == edit.batchId`。

- [x] **步骤 4：运行测试验证通过**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.OrgEditTest
```

预期：全部 PASS（含原有 5 个测试）。

- [x] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEdit.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgEditTest.java
git commit -m "feat: add batch_id column to edits table for undo support"
```

---

### 任务 2：OrgEditRepository — 批次查询与描述

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEditRepository.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java`
**导出/变更接口：** `OrgEditRepository.java::OrgEditRepository`, `OrgEditRepository.java::nextBatchId`, `OrgEditRepository.java::getLatestBatchId`, `OrgEditRepository.java::getBatchEdits`, `OrgEditRepository.java::describeLatestBatch`
**消费接口：** `OrgContract.java::EditsColumns`, `OrgEdit.java::OrgEdit`
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEditRepository.java`
- 测试（创建）：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java`

- [x] **步骤 1：编写失败的测试**

新建 `UndoTest.java`，setUp/tearDown/类结构照抄 `OrgEditTest.java`（ProviderTestCase2 + ApplicationProvider + 三表清空，import 块一致）：

```java
@RunWith(AndroidJUnit4.class)
public class UndoTest extends ProviderTestCase2<OrgProvider> {
    private MockContentResolver resolver;
    private OrgNodeRepository repo;
    private OrgEditRepository editRepo;
    // 构造器 + setUp（清三表，另 new OrgEditRepository(resolver)）+ tearDown 同 OrgEditTest

    private long writeEdit(long batchId, OrgEdit.TYPE type, String title) {
        OrgEdit edit = new OrgEdit();
        edit.type = type;
        edit.title = title;
        edit.nodeId = "fake";
        edit.oldValue = "old";
        edit.newValue = "new";
        edit.batchId = batchId;
        return edit.write(resolver);
    }

    @Test
    public void testNextBatchIdStartsAtOneAndIncrements() {
        assertEquals(1, editRepo.nextBatchId());
        writeEdit(1, OrgEdit.TYPE.HEADING, "a");
        assertEquals(2, editRepo.nextBatchId());
    }

    @Test
    public void testLatestBatchIdIgnoresNullBatches() {
        OrgEdit noBatch = new OrgEdit();
        noBatch.type = OrgEdit.TYPE.DELETE;  // 结构操作：不分配批次
        noBatch.title = "d";
        noBatch.nodeId = "fake";
        noBatch.write(resolver);
        assertEquals(null, editRepo.getLatestBatchId());

        writeEdit(5, OrgEdit.TYPE.TODO, "b");
        assertEquals(Long.valueOf(5), editRepo.getLatestBatchId());
    }

    @Test
    public void testGetBatchEditsReturnsRowsOrdered() {
        writeEdit(3, OrgEdit.TYPE.HEADING, "a");
        writeEdit(3, OrgEdit.TYPE.TODO, "a");
        writeEdit(4, OrgEdit.TYPE.TAGS, "b");
        ArrayList<OrgEdit> batch = editRepo.getBatchEdits(3);
        assertEquals(2, batch.size());
        assertEquals(OrgEdit.TYPE.HEADING, batch.get(0).type);
        assertEquals(OrgEdit.TYPE.TODO, batch.get(1).type);
    }

    @Test
    public void testDescribeLatestBatch() {
        writeEdit(2, OrgEdit.TYPE.HEADING, "学日语");
        assertEquals("修改标题 '学日语'", editRepo.describeLatestBatch());
    }

    @Test
    public void testDescribeLatestBatchNullWhenEmpty() {
        assertEquals(null, editRepo.describeLatestBatch());
    }
}
```

- [x] **步骤 2：运行测试验证失败**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.UndoTest
```

预期：编译失败（`OrgEditRepository` 不存在）。

- [x] **步骤 3：实现 OrgEditRepository（读侧）**

```java
package com.matburt.mobileorg.OrgData;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.database.Cursor;

import com.matburt.mobileorg.OrgData.OrgContract.Edits;

public class OrgEditRepository {

    public enum UndoResult { SUCCESS, NOTHING_TO_UNDO, NODE_MISSING }

    private final ContentResolver resolver;

    public OrgEditRepository(ContentResolver resolver) {
        this.resolver = resolver;
    }

    /** Next batch id = max existing batch_id + 1 (starts at 1). */
    public long nextBatchId() {
        Long latest = getLatestBatchId();
        return (latest == null) ? 1 : latest + 1;
    }

    /** Highest non-null batch_id, or null when no undoable batch exists. */
    public Long getLatestBatchId() {
        Cursor cursor = resolver.query(Edits.CONTENT_URI,
                new String[]{Edits.BATCH_ID}, null, null,
                Edits.BATCH_ID + " DESC");
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0))
                return cursor.getLong(0);
            return null;
        } finally {
            cursor.close();
        }
    }

    /** All edit rows of one batch, ordered by insertion. */
    public ArrayList<OrgEdit> getBatchEdits(long batchId) {
        Cursor cursor = resolver.query(Edits.CONTENT_URI,
                Edits.DEFAULT_COLUMNS, Edits.BATCH_ID + "=?",
                new String[]{String.valueOf(batchId)}, Edits.ID + " ASC");
        ArrayList<OrgEdit> result = new ArrayList<OrgEdit>();
        try {
            while (cursor.moveToNext())
                result.add(new OrgEdit(cursor));
        } finally {
            cursor.close();
        }
        return result;
    }

    /** Human label for the undo menu, e.g. "修改标题 '学日语'"; null when nothing to undo. */
    public String describeLatestBatch() {
        Long batchId = getLatestBatchId();
        if (batchId == null)
            return null;
        ArrayList<OrgEdit> edits = getBatchEdits(batchId);
        if (edits.isEmpty())
            return null;
        OrgEdit first = edits.get(0);
        String verb;
        switch (first.type) {
        case HEADING:  verb = "修改标题";   break;
        case TODO:     verb = "修改状态";   break;
        case PRIORITY: verb = "修改优先级"; break;
        case TAGS:     verb = "修改标签";   break;
        default:       verb = "编辑内容";   break;
        }
        return verb + " '" + first.title + "'";
    }
}
```

注意：`OrgEdit(Cursor)` 构造器内部调用 `set(cursor)` 并在需要时 `moveToFirst()`；游标循环用 `moveToNext()` 前先确认 `set()` 不会干扰（`set()` 只在 `isBeforeFirst/isAfterLast` 时 moveToFirst——逐行 moveToNext 安全）。若测试发现首行丢失，改为 `if (cursor.isBeforeFirst()) cursor.moveToNext();` 后再循环。

- [x] **步骤 4：运行测试验证通过**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.UndoTest
```

预期：全部 PASS。

- [x] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEditRepository.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java
git commit -m "feat: add OrgEditRepository with batch query and undo label"
```

---

### 任务 3：写入点接线 batch_id

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java`
**导出/变更接口：** `OrgNodeRepository.java::generateApplyEditNodes`, `OrgNodeRepository.java::writePayloadWithEdits`
**消费接口：** `OrgEditRepository.java::nextBatchId`
**复杂度：** standard

背景：只需改 `OrgNodeRepository` 的两个方法即可覆盖全部 4 个入口——`EditActivityControllerEdit.saveEdits()` 与 `OutlineItem.setNewTodo()` 走 `generateApplyWriteEdits → generateApplyEditNodes`；`TimeclockService.doClockOut()` 与 `RecordingService` 走 `addLogbook → writePayloadWithEdits`。结构操作（deleteNode/archiveNode/createParentNewheading）各自直接 `edit.write()`，不经过这两个方法，保持 batchId=-1（写入 null）——这是设计边界。

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java:347-381`（generateApplyEditNodes）、`:494-502`（writePayloadWithEdits）
- 测试：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java`

- [x] **步骤 1：编写失败的测试**

`UndoTest.java` 追加（`OrgTestUtils.getDefaultOrgNode()` 的用法参考 `OrgEditTest.testGenerateEditsSimple` 与 `testEditsToStringSimple`——先建 file 再挂 node）：

```java
private OrgNode createNodeInDefaultFile() throws OrgNodeNotFoundException {
    OrgFile file = OrgTestUtils.getDefaultOrgFile();
    new OrgFileRepository(resolver).write(file);
    OrgNode fileNode = repo.getById(file.nodeId);
    OrgNode node = OrgTestUtils.getDefaultOrgNode();
    node.fileId = fileNode.fileId;
    node.parentId = fileNode.id;
    repo.write(node);
    return node;
}

@Test
public void testGenerateEditsAssignsSameBatch() throws OrgNodeNotFoundException {
    OrgNode node = createNodeInDefaultFile();

    OrgNode newNode = repo.getById(node.id);
    newNode.name = node.name + "2";
    newNode.todo = "DONE";
    repo.generateApplyWriteEdits(node, newNode, "");

    ArrayList<OrgEdit> edits = editRepo.getBatchEdits(editRepo.getLatestBatchId());
    assertEquals(2, edits.size());
    long batch = edits.get(0).batchId;
    assertEquals(batch, edits.get(1).batchId);
}

@Test
public void testAddLogbookAssignsBatch() throws OrgNodeNotFoundException {
    OrgNode node = createNodeInDefaultFile();
    long before = editRepo.getLatestBatchId() == null ? 0 : editRepo.getLatestBatchId();

    repo.addLogbook(node, 1000L, 2000L, "00:16");

    Long after = editRepo.getLatestBatchId();
    assertTrue(after != null && after > before);
    OrgNode reloaded = repo.getById(node.id);
    assertTrue(reloaded.getPayload().contains("CLOCK:"));
}

@Test
public void testCaptureFileEditHasNoBatch() {
    OrgFile capture = new OrgFileRepository(resolver).getOrCreateCaptureFile();
    OrgNode captureNode = new OrgFileRepository(resolver).getOrgNode(capture);
    OrgNode node = OrgTestUtils.getDefaultOrgNode();
    node.fileId = captureNode.fileId;
    node.parentId = captureNode.id;
    repo.write(node);

    OrgNode newNode = repo.getById(node.id);
    newNode.name = node.name + "2";
    repo.generateApplyWriteEdits(node, newNode, "");

    assertEquals(null, editRepo.getLatestBatchId());  // capture 不产生 edit 行
}
```

- [x] **步骤 2：运行测试验证失败**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.UndoTest
```

预期：`testGenerateEditsAssignsSameBatch` 与 `testAddLogbookAssignsBatch` 失败（batch_id 为 null）。

- [x] **步骤 3：实现接线**

`generateApplyEditNodes`（`OrgNodeRepository.java:347`）方法体开头加：

```java
long batchId = new OrgEditRepository(resolver).nextBatchId();
```

每个 `edits.add(new OrgEdit(...))` 之后紧跟一行（共 5 处：HEADING/TODO/PRIORITY/BODY/TAGS）：

```java
lastEdit.batchId = batchId;
```

（把 `edits.add(new OrgEdit(...))` 改为 `OrgEdit e = new OrgEdit(...); e.batchId = batchId; edits.add(e);` 形式。注意 REFILE 分支也一并接线——虽然当前无入口产生 refile edit 行，接线无害且未来一致。）

`writePayloadWithEdits`（`OrgNodeRepository.java:494`）中 `edit.write(resolver)` 之前加：

```java
edit.batchId = new OrgEditRepository(resolver).nextBatchId();
```

注意：`generateApplyEditNodes` 也被 `hasEdits()`（仅计数，不 write）调用——分配 batchId 无副作用（未落库），无需处理。

- [x] **步骤 4：运行测试验证通过**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.UndoTest
```

预期：全部 PASS。

- [x] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java
git commit -m "feat: assign batch_id to edits at all field-level write points"
```

---

### 任务 4：undoLatestBatch — 弹栈恢复

**依赖：** 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEdit.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEditRepository.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java`
**导出/变更接口：** `OrgContract.java::EditsColumns`, `OrgEdit.java::dbId`, `OrgEditRepository.java::undoLatestBatch`
**消费接口：** `OrgEditRepository.java::getLatestBatchId`, `OrgEditRepository.java::getBatchEdits`, `OrgNodeRepository.java::getById`, `OrgNodeRepository.java::updateAllNodes`
**复杂度：** deep

关键实现事实（含方案修正）：
- ~~`edit.nodeId` 反查~~ **已废弃**：olp 路径在 HEADING 改名后必然失效（路径含旧标题），:ID: 又非必有——反查方案被测试证伪。**改为 Edits 表补 `db_id` 列**（DB row id），undo 直接 `getById(dbId)` 定位：精确、无歧义、改名/移动均不受影响。`nodeId` 列语义不变（仅供同步序列化）。
- 撤销 OrgNodeRepository 的任务 4 改动（`git checkout` 恢复）：`findNodeByEditNodeId` 与 `getNodeByHeading` 的 `file.nodeId→file.id` 顺手修改都不要——后者是既有方法且被 `ViewFragment` 调用，语义变更未经验证。
- 恢复写入用 `updateAllNodes(node)`（`getContentValues` 含 payload——`addLogbook` 修复已验证 `write()` 持久化 payload），同时同步恢复共享 `:ID:` 的节点，与正向路径 `EditActivityControllerEdit.saveEdits()` 对称。
- 批次内所有行同属一个节点（一次保存/一次 clock-out 均单节点）。
- 撤销**不得生成新 edit 行**。

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java`（EditsColumns + DEFAULT_COLUMNS 加 DB_ID）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java`（onCreate 建表 + case 7 alter 补 db_id 列）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEdit.java`（dbId 字段，构造器记录 node.id）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEditRepository.java`（新增 undoLatestBatch）
- 测试：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java`

- [x] **步骤 1：编写失败的测试**

`UndoTest.java` 追加：

```java
@Test
public void testUndoRestoresFieldsAndDeletesBatch() throws OrgNodeNotFoundException {
    OrgNode node = createNodeInDefaultFile();
    String origName = node.name;
    String origTodo = node.todo;

    OrgNode newNode = repo.getById(node.id);
    newNode.name = "changed";
    newNode.todo = "DONE";
    repo.generateApplyWriteEdits(node, newNode, "");
    // 注意：正路径之后 node 被 mutate（generateApplyEditNodes 副作用），这里重新读
    OrgNode edited = repo.getById(node.id);
    assertEquals("changed", edited.name);

    assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());

    OrgNode restored = repo.getById(node.id);
    assertEquals(origName, restored.name);
    assertEquals(origTodo, restored.todo);
    assertEquals(null, editRepo.getLatestBatchId());
}

@Test
public void testUndoLifoOrder() throws OrgNodeNotFoundException {
    OrgNode node = createNodeInDefaultFile();
    String origName = node.name;

    OrgNode n1 = repo.getById(node.id);
    n1.todo = "TODO";
    repo.generateApplyWriteEdits(repo.getById(node.id), n1, "");
    long batch1 = editRepo.getLatestBatchId();

    OrgNode n2 = repo.getById(node.id);
    n2.name = "second";
    repo.generateApplyWriteEdits(repo.getById(node.id), n2, "");
    long batch2 = editRepo.getLatestBatchId();
    assertTrue(batch2 > batch1);

    assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());
    OrgNode after = repo.getById(node.id);
    assertEquals(origName, after.name);          // 批次2（改名）被撤
    assertEquals("TODO", after.todo);            // 批次1仍在

    assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());
    OrgNode done = repo.getById(node.id);
    assertEquals(node.todo, done.todo);          // 批次1也被撤
    assertEquals(null, editRepo.getLatestBatchId());
}

@Test
public void testUndoBodyEditRemovesClock() throws OrgNodeNotFoundException {
    OrgNode node = createNodeInDefaultFile();
    String origPayload = repo.getById(node.id).getPayload();

    repo.addLogbook(node, 1000L, 2000L, "00:16");
    assertTrue(repo.getById(node.id).getPayload().contains("CLOCK:"));

    assertEquals(OrgEditRepository.UndoResult.SUCCESS, editRepo.undoLatestBatch());

    OrgNode restored = repo.getById(node.id);
    assertEquals(origPayload, restored.getPayload());
}

@Test
public void testUndoNodeMissingKeepsBatch() throws OrgNodeNotFoundException {
    OrgNode node = createNodeInDefaultFile();
    OrgNode newNode = repo.getById(node.id);
    newNode.name = "changed";
    repo.generateApplyWriteEdits(repo.getById(node.id), newNode, "");

    repo.deleteNode(node);  // 结构操作，产生无批次 DELETE 行
    // 用 getById 失败验证节点已删（deleteNode 删除该行）

    assertEquals(OrgEditRepository.UndoResult.NODE_MISSING, editRepo.undoLatestBatch());
    Long latest = editRepo.getLatestBatchId();
    assertTrue(latest != null);  // 批次保留，未被弹出
}

@Test
public void testUndoNothingToUndo() {
    assertEquals(OrgEditRepository.UndoResult.NOTHING_TO_UNDO, editRepo.undoLatestBatch());
}
```

- [x] **步骤 2：运行测试验证失败**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.UndoTest
```

预期：编译失败（`undoLatestBatch`/`findNodeByEditNodeId` 不存在）。

- [x] **步骤 3：实现 db_id 列与 undoLatestBatch**

`OrgContract.java` — `EditsColumns` 加 `String DB_ID = "db_id";`，`Edits.DEFAULT_COLUMNS` 加入 `DB_ID`。

`OrgDatabase.java`：
- onCreate 的 edits 建表语句把 `"batch_id integer)"` 替换为 `"batch_id integer," + "db_id integer)"`（schema v7 尚未发布，直接改建表语句）
- onUpgrade case 7 的 alter 改为两列：

```java
case 7:
    db.execSQL("alter table edits add batch_id integer");
    db.execSQL("alter table edits add db_id integer");
    break;
```

`OrgEdit.java`：
- 加字段 `public long dbId = -1;`
- 两个带 `OrgNode node` 参数的构造器中加 `this.dbId = node.id;`
- `set(Cursor)` 追加（仿 batchId 的读法）：

```java
int dbIdx = cursor.getColumnIndex(Edits.DB_ID);
if (dbIdx >= 0 && !cursor.isNull(dbIdx))
    this.dbId = cursor.getLong(dbIdx);
```

- `write(ContentResolver)` 追加 `values.put(Edits.DB_ID, dbId >= 0 ? dbId : null);`

`OrgEditRepository.java`：

```java
/**
 * Pop the latest batch: restore old values to the node, then delete the
 * batch's edit rows. Undo never generates new edits.
 */
public UndoResult undoLatestBatch() {
    Long batchId = getLatestBatchId();
    if (batchId == null)
        return UndoResult.NOTHING_TO_UNDO;

    ArrayList<OrgEdit> edits = getBatchEdits(batchId);
    if (edits.isEmpty())
        return UndoResult.NOTHING_TO_UNDO;

    OrgNodeRepository nodeRepo = new OrgNodeRepository(resolver);
    OrgNode node;
    try {
        node = nodeRepo.getById(edits.get(0).dbId);
    } catch (OrgNodeNotFoundException e) {
        return UndoResult.NODE_MISSING;
    }

    try {
        for (OrgEdit edit : edits) {
            switch (edit.type) {
            case HEADING:  node.name = edit.oldValue;     break;
            case TODO:     node.todo = edit.oldValue;     break;
            case PRIORITY: node.priority = edit.oldValue; break;
            case TAGS:     node.tags = edit.oldValue;     break;
            case BODY:     node.setPayload(edit.oldValue); break;
            default:
                return UndoResult.NOTHING_TO_UNDO;  // 防御：批次含不可撤类型
            }
        }
    } catch (OrgNodeNotFoundException e) {
        return UndoResult.NOTHING_TO_UNDO;  // setPayload 解析失败
    }

    nodeRepo.updateAllNodes(node);
    resolver.delete(Edits.CONTENT_URI, Edits.BATCH_ID + "=?",
            new String[]{String.valueOf(batchId)});
    return UndoResult.SUCCESS;
}
```

（`node.setPayload` 若声明 `throws OrgNodeNotFoundException` 则需包住——编译时按实际签名处理。）

- [x] **步骤 4：运行测试验证通过**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.UndoTest
```

预期：全部 PASS。若 `testUndoNodeMissingKeepsBatch` 因 `getNodeId` 生成 `olp:` 且 olp 解析仍找到文件根之外的路径而行为异常，优先检查 `deleteNode` 后节点是否真不可达，再调整测试断言而非实现。

- [x] **步骤 5：运行全量测试防回归**

```bash
./gradlew connectedDebugAndroidTest
```

预期：94 个既有测试 + 新增测试全部 PASS（特别是 `OrgEditTest`、`SynchronizerTest` —— push 序列化 `toString()` 不含 batch_id，不影响 `mobileorg.org` 格式）。

- [x] **步骤 6：Commit**

先 `git checkout -- MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java` 撤销该文件的任务 4 改动（findNodeByEditNodeId/getNodeByHeading 均不保留），确认 `git diff` 中该文件无改动后：

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEdit.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgEditRepository.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/UndoTest.java
git commit -m "feat: undo latest edit batch (LIFO) restoring pre-edit state"
```

---

### 任务 5：OutlineActivity 菜单入口

**依赖：** 任务 4
**文件集：** `MobileOrg/src/main/res/menu/outline_menu.xml`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`, `MobileOrg/src/main/res/values/strings.xml`, `MobileOrg/src/main/res/values-zh/strings.xml`
**导出/变更接口：** `OutlineActivity.java::onPrepareOptionsMenu`, `OutlineActivity.java::onOptionsItemSelected`
**消费接口：** `OrgEditRepository.java::undoLatestBatch`, `OrgEditRepository.java::describeLatestBatch`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/res/menu/outline_menu.xml`（加 menu_undo item）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java:324-399`
- 修改：`MobileOrg/src/main/res/values/strings.xml`、`MobileOrg/src/main/res/values-zh/strings.xml`

- [x] **步骤 1：strings 资源**

`values/strings.xml`：

```xml
<string name="undo_menu">Undo</string>
<string name="undo_node_missing">Node was deleted; cannot undo</string>
```

`values-zh/strings.xml`：

```xml
<string name="undo_menu">撤销</string>
<string name="undo_node_missing">节点已删除，无法撤销</string>
```

- [x] **步骤 2：menu item**

`outline_menu.xml` 末尾追加（必须用 `app:showAsAction`，CLAUDE.md 坑）：

```xml
<item
    android:id="@+id/menu_undo"
    android:title="@string/undo_menu"
    app:showAsAction="never" />
```

- [x] **步骤 3：OutlineActivity 接线**

import：`com.matburt.mobileorg.OrgData.OrgEditRepository`。

`onPrepareOptionsMenu`（约 332 行，`syncController.onPrepareOptionsMenu(menu)` 之前）：

```java
MenuItem undoItem = menu.findItem(R.id.menu_undo);
String undoDesc = new OrgEditRepository(getContentResolver()).describeLatestBatch();
if (undoDesc != null) {
    undoItem.setEnabled(true);
    undoItem.setTitle("撤销：" + undoDesc);
} else {
    undoItem.setEnabled(false);
    undoItem.setTitle(R.string.undo_menu);
}
```

`onOptionsItemSelected` 的 else-if 链中（`menu_statistics` 分支后）：

```java
} else if (id == R.id.menu_undo) {
    OrgEditRepository.UndoResult result =
            new OrgEditRepository(getContentResolver()).undoLatestBatch();
    if (result == OrgEditRepository.UndoResult.NODE_MISSING)
        Toast.makeText(this, R.string.undo_node_missing, Toast.LENGTH_SHORT).show();
    invalidateOptionsMenu();
    // 与 SYNC_DONE 相同的列表刷新：找到现有刷新调用（onRefreshDisplay /
    // adapter.refresh()，参考 SynchServiceReceiver 的 SYNC_DONE 处理）并复用
    return true;
```

菜单状态刷新时机核查：`onResume()` 已有 `invalidateOptionsMenu()`（约 115 行）；确认 SYNC_DONE 广播处理路径最终触发 `invalidateOptionsMenu()`（sync 清空 Edits 后菜单须变灰），若否则在该处理处补一行。

- [x] **步骤 4：编译验证（无本地构建规则例外——instrumentation 单类运行即可触发编译）**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.UndoTest
```

预期：PASS（同时证明整个 app 编译通过）。

- [x] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/res/menu/outline_menu.xml MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java MobileOrg/src/main/res/values/strings.xml MobileOrg/src/main/res/values-zh/strings.xml
git commit -m "feat: add undo menu entry to OutlineActivity"
```

---

### 任务 6：CI 验证

**依赖：** 任务 5
**文件集：** 无
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [x] **步骤 1：推送并监控 CI**

```bash
git push
gh run list --limit 3
gh run watch <run-id>   # 或 gh run view <run-id> 轮询
```

预期：GitHub Actions 构建 + instrumentation 测试（API 30 emulator）全部通过。

- [ ] **步骤 2：手测清单（CI 出包后用户自测）**

1. 编辑节点改名保存 → Outline 菜单出现"撤销：修改标题 'xxx'" → 点击 → 名称恢复、菜单变灰
2. clock-out 一个节点 → 菜单出现"撤销：编辑内容 'xxx'" → 点击 → 节点 payload 中 CLOCK 行消失
3. 连续两次编辑 → 连续撤销两次均正确（LIFO）
4. 同步 → 菜单变灰（edits 已消费）
5. 删除节点 → 菜单点击 → toast"节点已删除，无法撤销"

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 3 → 任务 4 → 任务 5 → 任务 6

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2
- Wave 3（依赖 Wave 2）：任务 3
- Wave 4（依赖 Wave 3）：任务 4
- Wave 5（依赖 Wave 4）：任务 5
- Wave 6（依赖 Wave 5）：任务 6
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真

计划为严格线性依赖链（接口逐级消费 + 共享文件交替），**建议串行执行**。
