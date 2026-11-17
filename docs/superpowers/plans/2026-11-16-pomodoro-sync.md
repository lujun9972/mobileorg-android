# 番茄钟记录同步 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 番茄钟记录（`pomodoro_sessions`）随常规同步投影到远端 `pomodoros.org`，多设备 union 合并，重装自动恢复。

**架构：** 协议外魔法文件（ADR 0005）：`Synchronizer.runSynchronizer()` 末尾追加 `syncPomodoroLog()` = pull 原始文本 → `PomodoroLogParser` 解析 → 按 `started_at` 判重合并进本地 DB → 全表经 `PomodoroLogSerializer` 生成 org 文本 → `putRemoteFile` 覆写。本地 DB（合并视图）是 source of truth，只增不删。规格：`docs/superpowers/specs/2026-11-16-pomodoro-sync-design.md`。

**技术栈：** Java / Android ContentProvider / 现有 Synchronizer 抽象（WebDAV/SSH/SDCard）。JVM 测试在 `src/test/`，验证走 CI（本地无 Android SDK，绝不本地构建）。

**验证循环约定：** 每个任务 commit 后 `git push origin main`，用 `gh run watch <run-id> -R lujun9972/mobileorg-android --exit-status` 等 Build APK + Instrumentation Tests 两个 workflow。任务 1 完成后 test.yml 会跑 JVM 单测。TDD 的"红"由 CI 编译失败充当（本地无 SDK 无法本地跑测试）。

**执行方式约束：** 所有验证经共享 CI（GitHub Actions）串行排队，且每个任务直接 commit 到 main——并行 wave 会造成 CI 结果错配与 push 竞态。**推荐串行执行**（serial-executing-plans）；文末并行执行图仅作依赖参考。

**关键事实（写代码前必读）：**
- `SynchronizerInterface.putRemoteFile` 的 javadoc 契约是 **Replaces**（覆写），只有 `SDCardSynchronizer` 用 `new FileWriter(file, true)` 违反了它——任务 7 修复为覆写，不是加分支。
- `putRemoteFile` 全工程唯一调用点是 `Synchronizer.pushCaptures()`；它先下载远端 mobileorg.org 前置拼接再上传，覆写语义下 SDCard 行为同时被修正。
- `recordPomodoroSession` 唯一调用点：`TimeclockService.handlePomodoroFinish()`。
- pomodoro 的 options 菜单与 ActionMode 长按菜单共用 `R.id.menu_pomodoro` dispatch（`OutlineActivity.onOptionsItemSelected`）；`outline_node.xml` 已有该菜单项；`listView.getCheckedNodeId()` 是取当前长按节点 id 的既有模式（参考 `menu_record` 分支）。
- `inferAction()` 按 `hasExtra(POMODORO_DURATION)` 先判番茄钟——番茄 intent 携带独立 extra `POMODORO_NODE_ID`（不复用 NODE_ID）不会误路由。
- `OrgProvider` 的 insert 是通用 tableName 路由，新列随 ContentValues 自动通过，无需改 Provider。
- 统计模块 `PomodoroStatisticsRepository` 用显式列名 raw SQL，不受新列影响，零改动。
- `getNodeId()` 优先 `:ID:` 属性；NODE property 用 `getOlpId()`（纯 olp，历史快照语义）。

---

### 任务 1：CI 增加 JVM 单元测试

**依赖：** 无
**文件集：** `.github/workflows/test.yml`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`.github/workflows/test.yml`（`Run instrumentation tests` step 之前）

- [ ] **步骤 1：在 emulator step 之前插入 JVM 测试 step**

```yaml
      - name: Run JVM unit tests
        run: ./gradlew testDebugUnitTest
```

- [ ] **步骤 2：Commit + push + 等 CI**

```bash
git add .github/workflows/test.yml
git commit -m "ci: run JVM unit tests before instrumentation tests"
git push origin main
gh run list -R lujun9972/mobileorg-android --limit 2   # 取两个 run id
gh run watch <test-run-id> -R lujun9972/mobileorg-android --exit-status
```
预期：两个 workflow success（此时 src/test 只有既有 2 个测试，应通过）。

---

### 任务 2：DB v8 迁移与契约列

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java`
**导出/变更接口：** `OrgContract.java::PomodoroSessionsColumns.NODE_REF`, `OrgContract.java::PomodoroSessionsColumns.DEVICE`, `OrgContract.java::PomodoroSessions.DEFAULT_COLUMNS`, `OrgDatabase.java::DATABASE_VERSION`
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java:57-62`（PomodoroSessionsColumns）、`:184`（DEFAULT_COLUMNS）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java:13`（版本号）、`:82-88`（onCreate）、`:114-117` 后（onUpgrade 加 case 8）

- [ ] **步骤 1：OrgContract 加列**

```java
	interface PomodoroSessionsColumns {
		String ID = "_id";
		String STARTED_AT = "started_at";
		String DURATION_MIN = "duration_min";
		String COMPLETED_AT = "completed_at";
		String NODE_REF = "node_ref";
		String DEVICE = "device";
	}
```
`PomodoroSessions.DEFAULT_COLUMNS` 改为 `{ ID, STARTED_AT, DURATION_MIN, COMPLETED_AT, NODE_REF, DEVICE }`。

- [ ] **步骤 2：OrgDatabase 版本与迁移**

`:13` 改 `DATABASE_VERSION = 8`。`onCreate` 的 pomodoro_sessions 建表语句在 `completed_at` 行后加一列：

```java
				+ "completed_at INTEGER NOT NULL,"
				+ "node_ref TEXT,"
				+ "device TEXT)");
```
（`:87-88` 的索引语句不变。）`onUpgrade` 的 `case 7` 之后加：

```java
		case 8:
			db.execSQL("alter table pomodoro_sessions add node_ref text");
			db.execSQL("alter table pomodoro_sessions add device text");
			break;
```

- [ ] **步骤 3：Commit + push + 等 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgContract.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java
git commit -m "feat(data): pomodoro_sessions 加 node_ref/device 列（DB v8）"
git push origin main && gh run watch <ids> --exit-status
```
预期：Build APK success（含 DB 迁移的 app 可正常安装启动）。

---

### 任务 3：PomodoroSession DTO 与 repo 数据访问

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroSession.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`
**导出/变更接口：** `PomodoroSession.java::PomodoroSession`, `OrgNodeRepository.java::recordPomodoroSession`, `OrgNodeRepository.java::getPomodoroSessions`, `OrgNodeRepository.java::pomodoroSessionExists`, `OrgNodeRepository.java::insertPomodoroSession`
**消费接口：** `OrgContract.java::PomodoroSessions.CONTENT_URI`, `OrgContract.java::PomodoroSessions.DEFAULT_COLUMNS`, `OrgContract.java::PomodoroSessionsColumns`
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroSession.java`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java:720-726`（recordPomodoroSession 替换 + 新增 3 个方法）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java:305`（调用点适配签名，保编译；真实值在任务 6 接线）

- [ ] **步骤 1：创建 DTO（纯 Java，无 Android import，供 parser/serializer JVM 测试复用）**

```java
package com.matburt.mobileorg.OrgData;

/** 一条番茄记录。跨设备身份是 startedAt（epoch millis）。 */
public class PomodoroSession {
    public final long startedAt;
    public final long completedAt;
    public final int durationMin;
    public final String nodeRef;  // olp 历史快照，可为 null
    public final String device;   // 可为 null

    public PomodoroSession(long startedAt, long completedAt, int durationMin,
                           String nodeRef, String device) {
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMin = durationMin;
        this.nodeRef = nodeRef;
        this.device = device;
    }
}
```

- [ ] **步骤 2：OrgNodeRepository 替换 recordPomodoroSession 并新增 3 个方法**

替换 `:720-726` 为：

```java
    public void recordPomodoroSession(long startedAt, int durationMin, String nodeRef, String device) {
        ContentValues values = new ContentValues();
        values.put(OrgContract.PomodoroSessions.STARTED_AT, startedAt);
        values.put(OrgContract.PomodoroSessions.DURATION_MIN, durationMin);
        values.put(OrgContract.PomodoroSessions.COMPLETED_AT, System.currentTimeMillis());
        if (nodeRef != null && !nodeRef.isEmpty())
            values.put(OrgContract.PomodoroSessions.NODE_REF, nodeRef);
        if (device != null && !device.isEmpty())
            values.put(OrgContract.PomodoroSessions.DEVICE, device);
        resolver.insert(OrgContract.PomodoroSessions.CONTENT_URI, values);
    }

    /** 恢复/合并用：completedAt 来自远端 property，不取当前时间。 */
    public void insertPomodoroSession(PomodoroSession s) {
        ContentValues values = new ContentValues();
        values.put(OrgContract.PomodoroSessions.STARTED_AT, s.startedAt);
        values.put(OrgContract.PomodoroSessions.DURATION_MIN, s.durationMin);
        values.put(OrgContract.PomodoroSessions.COMPLETED_AT, s.completedAt);
        if (s.nodeRef != null && !s.nodeRef.isEmpty())
            values.put(OrgContract.PomodoroSessions.NODE_REF, s.nodeRef);
        if (s.device != null && !s.device.isEmpty())
            values.put(OrgContract.PomodoroSessions.DEVICE, s.device);
        resolver.insert(OrgContract.PomodoroSessions.CONTENT_URI, values);
    }

    public boolean pomodoroSessionExists(long startedAt) {
        Cursor cursor = resolver.query(OrgContract.PomodoroSessions.CONTENT_URI,
                new String[] { OrgContract.PomodoroSessions.ID },
                OrgContract.PomodoroSessions.STARTED_AT + "=?",
                new String[] { String.valueOf(startedAt) }, null);
        boolean exists = cursor != null && cursor.moveToFirst();
        if (cursor != null) cursor.close();
        return exists;
    }

    /** 按 started_at 升序，供投影生成。 */
    public ArrayList<PomodoroSession> getPomodoroSessions() {
        Cursor cursor = resolver.query(OrgContract.PomodoroSessions.CONTENT_URI,
                OrgContract.PomodoroSessions.DEFAULT_COLUMNS, null, null,
                OrgContract.PomodoroSessions.STARTED_AT + " ASC");
        ArrayList<PomodoroSession> sessions = new ArrayList<>();
        try {
            while (cursor != null && cursor.moveToNext()) {
                sessions.add(new PomodoroSession(cursor.getLong(1), cursor.getLong(3),
                        cursor.getInt(2), cursor.getString(4), cursor.getString(5)));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return sessions;
    }
```
（检查文件头已有 `ArrayList`/`Cursor`/`ContentValues` import，缺则补。）

- [ ] **步骤 3：TimeclockService 调用点适配（暂传 null 保编译，任务 6 接真实值）**

`TimeclockService.java:305` 改为：

```java
		repo.recordPomodoroSession(pomodoroTimer.getStartTime(), pomodoroTimer.getDurationMinutes(), null, null);
```

- [ ] **步骤 4：Commit + push + 等 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroSession.java MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java
git commit -m "feat(data): PomodoroSession DTO 与 repo 读写（含恢复插入）"
git push origin main && gh run watch <ids> --exit-status
```

---

### 任务 4：PomodoroLogSerializer（TDD）

**依赖：** 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroLogSerializer.java`, `MobileOrg/src/test/java/com/matburt/mobileorg/OrgData/PomodoroLogSerializerTest.java`
**导出/变更接口：** `PomodoroLogSerializer.java::serialize`
**消费接口：** `PomodoroSession.java::PomodoroSession`
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/test/java/com/matburt/mobileorg/OrgData/PomodoroLogSerializerTest.java`
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroLogSerializer.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.matburt.mobileorg.OrgData;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PomodoroLogSerializerTest {

    private PomodoroSession session() {
        return new PomodoroSession(1763258400000L, 1763260500000L, 25,
                "olp:home.org:工作/MobileOrg 开发", "红米Note11");
    }

    @Test
    public void serialize_containsClockLineAndProperties() {
        List<PomodoroSession> sessions = new ArrayList<>();
        sessions.add(session());
        String out = PomodoroLogSerializer.serialize(sessions);
        // headline 与 CLOCK 行结构（不断言日期字符串——CI 与本地时区不同）
        assertTrue(out.contains("番茄 25min"));
        assertTrue(out.contains("CLOCK: ["));
        assertTrue(out.contains(":STARTED_AT: 1763258400000"));
        assertTrue(out.contains(":COMPLETED_AT: 1763260500000"));
        assertTrue(out.contains(":DURATION: 25"));
        assertTrue(out.contains(":NODE: olp:home.org:工作/MobileOrg 开发"));
        assertTrue(out.contains(":DEVICE: 红米Note11"));
        assertTrue(out.contains("=>  0:25"));
    }

    @Test
    public void serialize_nullNodeOmitsNodeProperty() {
        List<PomodoroSession> sessions = new ArrayList<>();
        sessions.add(new PomodoroSession(1763258400000L, 1763260500000L, 25, null, null));
        String out = PomodoroLogSerializer.serialize(sessions);
        assertFalse(out.contains(":NODE:"));
        assertFalse(out.contains(":DEVICE:"));
    }

    @Test
    public void serialize_emptyListProducesHeaderOnly() {
        String out = PomodoroLogSerializer.serialize(new ArrayList<>());
        assertTrue(out.contains("MobileOrg 自动生成"));
        assertFalse(out.contains("* "));
    }
}
```

- [ ] **步骤 2：push 验证测试失败（类不存在编译失败即红）**

commit 仅测试文件，push 后 CI 的 `testDebugUnitTest` 编译失败：

```bash
git add MobileOrg/src/test/java/com/matburt/mobileorg/OrgData/PomodoroLogSerializerTest.java
git commit -m "test(data): PomodoroLogSerializer 失败测试"
git push origin main && gh run watch <test-run-id> --exit-status || true
```
预期：FAIL（cannot find symbol PomodoroLogSerializer）。

- [ ] **步骤 3：实现 serializer**

```java
package com.matburt.mobileorg.OrgData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** pomodoro_sessions → pomodoros.org 文本投影。CLOCK 行用名义时段（start → start+duration）。 */
public class PomodoroLogSerializer {
    public static final String HEADER =
            "# 由 MobileOrg 自动生成与管理，请勿手工编辑（Emacs 侧只读消费）\n";

    private static final SimpleDateFormat HEADLINE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    private static final SimpleDateFormat CLOCK_FMT =
            new SimpleDateFormat("yyyy-MM-dd E HH:mm", Locale.US);

    public static String serialize(List<PomodoroSession> sessions) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (PomodoroSession s : sessions) {
            long nominalEnd = s.startedAt + s.durationMin * 60000L;
            sb.append("* ").append(HEADLINE_FMT.format(new Date(s.startedAt)))
              .append(" 番茄 ").append(s.durationMin).append("min\n")
              .append(":LOGBOOK:\nCLOCK: [").append(CLOCK_FMT.format(new Date(s.startedAt)))
              .append("]--[").append(CLOCK_FMT.format(new Date(nominalEnd)))
              .append("] =>  ").append(String.format(Locale.US, "%d:%02d",
                      s.durationMin / 60, s.durationMin % 60))
              .append("\n:END:\n:PROPERTIES:\n")
              .append(":STARTED_AT: ").append(s.startedAt).append('\n')
              .append(":COMPLETED_AT: ").append(s.completedAt).append('\n')
              .append(":DURATION: ").append(s.durationMin).append('\n');
            if (s.nodeRef != null && !s.nodeRef.isEmpty())
                sb.append(":NODE: ").append(s.nodeRef).append('\n');
            if (s.device != null && !s.device.isEmpty())
                sb.append(":DEVICE: ").append(s.device).append('\n');
            sb.append(":END:\n");
        }
        return sb.toString();
    }
}
```

- [ ] **步骤 4：Commit（测试+实现一起）+ push + 等 CI 绿**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroLogSerializer.java
git commit -m "feat(data): PomodoroLogSerializer 名义时段投影"
git push origin main && gh run watch <ids> --exit-status
```
预期：`testDebugUnitTest` PASS（3 个新测试 + 既有测试）。

---

### 任务 5：PomodoroLogParser（TDD）

**依赖：** 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroLogParser.java`, `MobileOrg/src/test/java/com/matburt/mobileorg/OrgData/PomodoroLogParserTest.java`
**导出/变更接口：** `PomodoroLogParser.java::parse`
**消费接口：** `PomodoroSession.java::PomodoroSession`
**复杂度：** deep

**文件：**
- 创建：`MobileOrg/src/test/java/com/matburt/mobileorg/OrgData/PomodoroLogParserTest.java`
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroLogParser.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.matburt.mobileorg.OrgData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;

public class PomodoroLogParserTest {

    private static final String ENTRY = "* 2026-11-16 10:00 番茄 25min\n"
            + ":LOGBOOK:\nCLOCK: [2026-11-16 Mon 10:00]--[2026-11-16 Mon 10:25] =>  0:25\n:END:\n"
            + ":PROPERTIES:\n:STARTED_AT: 1763258400000\n:COMPLETED_AT: 1763260500000\n:DURATION: 25\n"
            + ":NODE: olp:home.org:工作/MobileOrg 开发\n:DEVICE: 红米Note11\n:END:\n";

    @Test
    public void parse_extractsAllFieldsIgnoringNonPropertyLines() {
        String text = "# 由 MobileOrg 自动生成与管理，请勿手工编辑（Emacs 侧只读消费）\n\n"
                + ENTRY
                + "* 2026-11-17 09:00 番茄 35min\n:PROPERTIES:\n:STARTED_AT: 1763344800000\n"
                + ":COMPLETED_AT: 1763346900000\n:DURATION: 35\n:UNKNOWN: x\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(2, out.size());
        PomodoroSession a = out.get(0);
        assertEquals(1763258400000L, a.startedAt);
        assertEquals(1763260500000L, a.completedAt);
        assertEquals(25, a.durationMin);
        assertEquals("olp:home.org:工作/MobileOrg 开发", a.nodeRef);
        assertEquals("红米Note11", a.device);
        assertNull(out.get(1).nodeRef);
        assertNull(out.get(1).device);
    }

    @Test
    public void parse_skipsMalformedStartedAt() {
        String text = ENTRY
                + "* bad\n:PROPERTIES:\n:STARTED_AT: abc\n:COMPLETED_AT: 1763260500000\n"
                + ":DURATION: 25\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(1, out.size());
        assertEquals(1763258400000L, out.get(0).startedAt);
    }

    @Test
    public void parse_missingCompletedAtDerivesFromStartPlusDuration() {
        String text = "* s\n:PROPERTIES:\n:STARTED_AT: 1763258400000\n:DURATION: 25\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(1, out.size());
        assertEquals(1763258400000L + 25 * 60000L, out.get(0).completedAt);
    }

    @Test
    public void parse_invalidDurationFallsBackToTimeDiff() {
        String text = "* s\n:PROPERTIES:\n:STARTED_AT: 1763258400000\n:COMPLETED_AT: 1763260500000\n"
                + ":DURATION: 0\n:END:\n";
        ArrayList<PomodoroSession> out = PomodoroLogParser.parse(text);
        assertEquals(1, out.size());
        assertEquals(35, out.get(0).durationMin);   // (completed-started)/60000 = 35
    }

}
```

- [ ] **步骤 2：push 验证失败**

```bash
git add MobileOrg/src/test/java/com/matburt/mobileorg/OrgData/PomodoroLogParserTest.java
git commit -m "test(data): PomodoroLogParser 失败测试"
git push origin main && gh run watch <test-run-id> --exit-status || true
```
预期：FAIL（cannot find symbol PomodoroLogParser）。

- [ ] **步骤 3：实现 parser（容错规则见规格 §5）**

```java
package com.matburt.mobileorg.OrgData;

import android.util.Log;

import java.util.ArrayList;

/**
 * pomodoros.org 文本 → session 行。只信 properties 的 STARTED_AT（epoch millis）；
 * 损坏条目跳过并 Log.w，不影响其余条目（规格 §5）。
 */
public class PomodoroLogParser {

    public static ArrayList<PomodoroSession> parse(String orgText) {
        ArrayList<PomodoroSession> out = new ArrayList<>();
        if (orgText == null || orgText.isEmpty()) return out;

        long startedAt = -1, completedAt = -1;
        int durationMin = -1;
        String nodeRef = null, device = null;

        for (String rawLine : orgText.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("* ")) {
                // 新条目开始：先结算上一条
                PomodoroSession s = build(startedAt, completedAt, durationMin, nodeRef, device);
                if (s != null) out.add(s);
                startedAt = -1; completedAt = -1; durationMin = -1;
                nodeRef = null; device = null;
            } else if (line.startsWith(":STARTED_AT:")) {
                startedAt = parseLong(value(line));
            } else if (line.startsWith(":COMPLETED_AT:")) {
                completedAt = parseLong(value(line));
            } else if (line.startsWith(":DURATION:")) {
                durationMin = (int) parseLong(value(line));
            } else if (line.startsWith(":NODE:")) {
                nodeRef = value(line);
            } else if (line.startsWith(":DEVICE:")) {
                device = value(line);
            }
        }
        PomodoroSession s = build(startedAt, completedAt, durationMin, nodeRef, device);
        if (s != null) out.add(s);
        return out;
    }

    private static PomodoroSession build(long startedAt, long completedAt,
                                         int durationMin, String nodeRef, String device) {
        if (startedAt <= 0) {
            if (durationMin >= 0 || completedAt > 0)
                logSkip("invalid STARTED_AT");
            return null;
        }
        if (durationMin <= 0 && completedAt > startedAt)
            durationMin = (int) ((completedAt - startedAt) / 60000L);
        if (durationMin <= 0) {
            logSkip("invalid DURATION");
            return null;
        }
        if (completedAt <= 0) completedAt = startedAt + durationMin * 60000L;
        return new PomodoroSession(startedAt, completedAt, durationMin, nodeRef, device);
    }

    /** JVM 单测无 android.jar 实现（Log 抛 RuntimeException），吞掉；设备上正常打日志。 */
    private static void logSkip(String reason) {
        try {
            Log.w("MobileOrg", "[PomodoroLog] skip entry: " + reason);
        } catch (RuntimeException ignored) {}
    }

    private static String value(String line) {
        int idx = line.indexOf(': ', 1);
        return idx < 0 ? "" : line.substring(idx + 2).trim();
    }

    private static long parseLong(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
```
注意：`android.util.Log` 在 JVM 单测（无 android 实现）抛 `RuntimeException`，`logSkip()` 的 try-catch 吞掉——设备上正常打日志，单测中跳过路径静默通过。build.gradle 未开 `returnDefaultValues`（已查实），这个包装是必须的。

- [ ] **步骤 4：Commit（测试+实现）+ push + 等 CI 绿**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/PomodoroLogParser.java
git commit -m "feat(data): PomodoroLogParser 容错解析"
git push origin main && gh run watch <ids> --exit-status
```

---

### 任务 6：节点归属与设备名链路

**依赖：** 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTimeclockController.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`
**导出/变更接口：** `TimeclockService.java::POMODORO_NODE_ID`, `OutlineTimeclockController.java::showPomodoroDurationPicker`
**消费接口：** `OrgNodeRepository.java::recordPomodoroSession`, `OrgNodeRepository.java::getById`, `OrgNodeRepository.java::getOlpId`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`（onOptionsItemSelected 的 `R.id.menu_pomodoro` 分支，`:402` 附近 else 分支）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTimeclockController.java:97`（签名加 nodeId）、`:151-155`（intent 附加 extra）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`（常量 `:41` 附近、字段、`handlePomodoroStart :186-189`、`handlePomodoroFinish :294-305`）

- [ ] **步骤 1：OutlineActivity dispatch 传 checkedNodeId**

`menu_pomodoro` 分支最后的 `else { timeclockController.showPomodoroDurationPicker(); }` 改为：

```java
			} else {
				timeclockController.showPomodoroDurationPicker(listView.getCheckedNodeId());
			}
```

- [ ] **步骤 2：Controller 签名与 extra**

`showPomodoroDurationPicker(long nodeId)`；intent 组装处（`:148-154`）在 `putExtra(POMODORO_COUNT, count)` 后加：

```java
                    if (nodeId >= 0)
                        intent.putExtra(TimeclockService.POMODORO_NODE_ID, nodeId);
```

- [ ] **步骤 3：TimeclockService 接收、暂存、落库**

常量区（`POMODORO_COUNT` 之后）加：

```java
	public static final String POMODORO_NODE_ID = "pomodoro_node_id";
```

字段区加 `private long pomodoroNodeId = -1;`

`handlePomodoroStart` 开头加：

```java
		pomodoroNodeId = intent.getLongExtra(POMODORO_NODE_ID, -1);
```

`handlePomodoroFinish` 中任务 3 的临时调用替换为：

```java
		repo.recordPomodoroSession(pomodoroTimer.getStartTime(), pomodoroTimer.getDurationMinutes(),
				resolveNodeRef(), resolveDeviceName());
```

并新增两个私有方法（放在 `handlePomodoroFinish` 之后）：

```java
	/** olp 历史快照，落库时刻解析；节点已删除则放弃归属。 */
	private String resolveNodeRef() {
		if (pomodoroNodeId < 0) return null;
		try {
			return repo.getOlpId(repo.getById(pomodoroNodeId));
		} catch (OrgNodeNotFoundException e) {
			Log.w("MobileOrg", "[Pomodoro] node gone at finish: " + pomodoroNodeId);
			return null;
		}
	}

	/** API 25+ 读系统设备名，取不到回退 Build.MODEL。 */
	private String resolveDeviceName() {
		try {
			String name = Settings.Global.getString(getContentResolver(), "device_name");
			if (name != null && !name.isEmpty()) return name;
		} catch (Exception ignored) {
			// API < 25 或读取失败
		}
		return Build.MODEL;
	}
```
import 补 `android.provider.Settings`、`android.os.Build`（若缺）。确认 `OrgNodeNotFoundException` 已 import（`handleClockIn` 在用）。

- [ ] **步骤 4：Commit + push + 等 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTimeclockController.java MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java
git commit -m "feat(pomodoro): 记录节点归属(olp快照)与来源设备"
git push origin main && gh run watch <ids> --exit-status
```

---

### 任务 7：Synchronizer.syncPomodoroLog 与 SDCard 覆写

**依赖：** 任务 3, 任务 4, 任务 5
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Synchronizers/Synchronizer.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Synchronizers/SDCardSynchronizer.java`
**导出/变更接口：** `Synchronizer.java::syncPomodoroLog`, `Synchronizer.java::POMODORO_LOG_FILE`, `SDCardSynchronizer.java::putRemoteFile`
**消费接口：** `PomodoroLogParser.java::parse`, `PomodoroLogSerializer.java::serialize`, `OrgNodeRepository.java::getPomodoroSessions`, `OrgNodeRepository.java::pomodoroSessionExists`, `OrgNodeRepository.java::insertPomodoroSession`, `PomodoroSession.java::PomodoroSession`
**复杂度：** deep

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Synchronizers/SDCardSynchronizer.java:34-41`（putRemoteFile 覆写语义）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Synchronizers/Synchronizer.java`（常量、runSynchronizer 挂载 `:96-97`、两个新私有方法）

- [ ] **步骤 1：SDCardSynchronizer 恢复覆写契约**

`putRemoteFile` 中 `new FileWriter(file, true)` 改为 `new FileWriter(file)`。
理由（写进 commit message）：接口 javadoc 契约是 Replaces；SDCard append 违反契约，且 `pushCaptures()`（`Synchronizer.java:150-153`）已前置拼接远端内容再上传——append 语义下 mobileorg.org 内容会在 SDCard 模式每次 push 重复膨胀（既有 bug，本修复同时根治）。全工程 `putRemoteFile` 仅 `pushCaptures()` 一个调用点，无其他 append 依赖。

- [ ] **步骤 2：Synchronizer 挂载 syncPomodoroLog**

类常量区加：

```java
	/** 协议外魔法文件（ADR 0005）：app 全权管理，不进 index.org 白名单，不进大纲。 */
	public static final String POMODORO_LOG_FILE = "pomodoros.org";
```

`runSynchronizer()` 的 try 块内 `pushCaptures();` 之后加一行：

```java
			syncPomodoroLog();
```

类尾部加两个方法（`resolver` 是 `Synchronizer.java:63` 的既有字段，构造函数 `:70` 赋值；仓库惯例 `new OrgFileRepository(resolver)` 同款）：

```java
	/**
	 * 番茄钟日志同步（union 合并，只增不删）。
	 * pull 原始文本 → 判重合并进本地 → 全表投影覆写远端。
	 * 独立 try-catch：失败仅 Log.w，不影响主同步结果。
	 */
	private void syncPomodoroLog() {
		try {
			mergePomodoroLog();
			pushPomodoroLog();
		} catch (Exception e) {
			Log.w("MobileOrg", "[PomodoroLog] sync failed: " + e);
		}
	}

	private void mergePomodoroLog() throws IOException {
		String remote;
		BufferedReader reader = null;
		try {
			reader = syncher.getRemoteFile(POMODORO_LOG_FILE);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null)
				sb.append(line).append('\n');
			remote = sb.toString();
		} catch (IOException e) {
			return;   // 远端尚无文件（首次 push 前常态）
		} finally {
			if (reader != null)
				try { reader.close(); } catch (IOException ignored) {}
		}
		if (remote.startsWith("{\"error\":")) return;

		OrgNodeRepository nodeRepo = new OrgNodeRepository(resolver);
		for (PomodoroSession s : PomodoroLogParser.parse(remote)) {
			if (!nodeRepo.pomodoroSessionExists(s.startedAt))
				nodeRepo.insertPomodoroSession(s);
		}
	}

	private void pushPomodoroLog() throws IOException {
		OrgNodeRepository nodeRepo = new OrgNodeRepository(resolver);
		String text = PomodoroLogSerializer.serialize(nodeRepo.getPomodoroSessions());
		syncher.putRemoteFile(POMODORO_LOG_FILE, text);
	}
```
import 补 `PomodoroLogParser`/`PomodoroLogSerializer`/`PomodoroSession`/`OrgNodeRepository`（同包 OrgData）。

- [ ] **步骤 3：Commit + push + 等 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Synchronizers/Synchronizer.java MobileOrg/src/main/java/com/matburt/mobileorg/Synchronizers/SDCardSynchronizer.java
git commit -m "feat(sync): pomodoros.org 协议外魔法文件同步（union 合并，只增不删）"
git push origin main && gh run watch <ids> --exit-status
```

---

### 任务 8：端到端验证与手测交接

**依赖：** 任务 6, 任务 7
**文件集：** 无
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：确认两个 workflow 最新 run 全绿**

```bash
gh run list -R lujun9972/mobileorg-android --limit 4
```
预期：Build APK 与 Instrumentation Tests 最新 run 均 success。

- [ ] **步骤 2：请用户连接设备，运行 `./deploy.sh` 安装最新 APK**

```bash
adb get-state   # 无设备则请用户 adb connect 或插 USB
./deploy.sh
```

- [ ] **步骤 3：向用户交接手测清单（规格 §7，需两台设备）**

1. 设备 A 完成 2 个番茄、设备 B 完成 1 个 → 双方各同步一次 → 两边统计页都显示 3 个
2. 设备 A 重装（先确认无未同步 edits！）→ 同步一次 → 统计页恢复全部历史
3. 从节点长按菜单启动的番茄 → 服务器 `pomodoros.org` 该条含 `:NODE:`；通知栏/三点菜单启动的不含
4. Emacs 侧 `org-clocktable` 对 `pomodoros.org` 出报表，时长合计与 app 统计一致
5. 手工破坏文件中一条记录的 `STARTED_AT` → 同步不崩，坏行跳过，其余行正常合并

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 2 → 任务 3 → 任务 4 → 任务 7 → 任务 8

- Wave 1（无依赖）：任务 1, 任务 2
- Wave 2（依赖 Wave 1）：任务 3（依赖 2）
- Wave 3（依赖 Wave 2）：任务 4（依赖 3）, 任务 5（依赖 3）, 任务 6（依赖 3）
- Wave 4（依赖 Wave 3）：任务 7（依赖 3, 4, 5）
- Wave 5（依赖 Wave 4）：任务 8（依赖 6, 7）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
