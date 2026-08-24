# 分享节点功能实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 org 节点整棵子树以归一化层级的 org 原文，通过 `ACTION_SEND text/plain` 系统分享面板发出；入口在 Outline 长按菜单与 ViewActivity 菜单。

**架构：** 数据层 `OrgNodeRepository.getSubtreeText()` 递归序列化子树（层级归一化到 1）→ 逻辑层 `OrgUtils.shareNode()` 构建 chooser intent（含超大文本截断防御）→ UI 层三个入口接线（两份菜单 XML + OutlineActionMode + ViewActivity 动态菜单）。

**技术栈：** Android Java、ContentProvider 数据层、instrumentation 测试（ProviderTestCase2 + AndroidJUnit4）。

**规格：** `docs/superpowers/specs/2026-08-24-share-node-design.md`

**测试环境：** 本地真机 instrumentation。先 `adb connect 192.168.31.198:<port>`（IP 仅供参考，端口以设备无线调试页当下显示为准；连不上则询问用户或改推 CI）。运行单个测试类：

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.OrgData.OrgNodeTest
```

**提交规范：** 中文 commit message，`feat:`/`test:`/`refactor:` 前缀。绝不用 `git add -A`，只 add 具体文件。

---

### 任务 1：OrgNode.toString(long level) 重载

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java`
**导出/变更接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java::toString`
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java:150-173`（`toString()` 方法）
- 测试：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java`

背景：`OrgNode.level` 是 `long`（OrgNode.java:24）。现有 `toString()` 用 `this.level` 生成星号数；分享需要归一化层级，故抽出带参重载，原方法行为不变。

- [ ] **步骤 1：编写失败的测试**

在 `OrgNodeTest.java` 的 `testNodeToStringSimple`（:62）之后添加：

```java
@Test
public void testToStringWithLevelOverride() {
    OrgNode node = new OrgNode();
    node.name = "title";
    node.todo = "TODO";
    node.level = 3;

    assertEquals("* TODO title", node.toString(1));
    assertEquals("** TODO title", node.toString(2));
}
```

- [ ] **步骤 2：运行测试验证失败**

运行 OrgNodeTest（见头部命令）。预期：**编译失败**，报错 `method toString in class OrgNode cannot be applied to given types`（对 Java，编译错误即 TDD 红灯）。

- [ ] **步骤 3：实现 toString(long) 重载**

将 `OrgNode.java:150-173` 的 `toString()` 改为：

```java
public String toString() {
    return toString(this.level);
}

/**
 * Serialize with an explicit star count. Used to normalize subtree levels
 * when sharing: the subtree root becomes level 1 regardless of its depth
 * in the source file.
 */
public String toString(long level) {
    StringBuilder result = new StringBuilder();

    for(long i = 0; i < level; i++)
        result.append("*");
    result.append(" ");

    if (!TextUtils.isEmpty(todo))
        result.append(todo + " ");

    if (!TextUtils.isEmpty(priority))
        result.append("[#" + priority + "] ");

    result.append(name);

    if(tags != null && !TextUtils.isEmpty(tags))
        result.append(" ").append(":" + tags + ":");

    if (payload != null && !TextUtils.isEmpty(payload))
        result.append("\n").append(payload);

    return result.toString();
}
```

- [ ] **步骤 4：运行测试验证通过**

运行 OrgNodeTest。预期：`testToStringWithLevelOverride` 与 `testNodeToStringSimple` 均 PASS（后者证明原行为无回归）。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java \
        MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java
git commit -m "refactor: OrgNode.toString 抽出带 level 参数的重载，行为不变"
```

---

### 任务 2：OrgNodeRepository.getSubtreeText()

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java`
**导出/变更接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java::getSubtreeText`
**消费接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNode.java::toString`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java::getById`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java::getChildren`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java:536`（Serialization 区块内、`nodesToString` 旁）
- 测试：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java`

背景：不复用现有 `nodesToString(nodeId, level)`——它 `level == 0` 时跳过根节点（:547）且星号数取自 `node.level` 无法归一化，另有调用者不动它。`getChildren(long)` 返回 `ArrayList<OrgNode>`，子节点不存在时返回空列表，递归自然终止。节点间以单个换行分隔（与 `nodesToString` 拼接行为一致）。

- [ ] **步骤 1：编写失败的测试（4 个用例）**

在 `OrgNodeTest.java` 末尾（最后一个 `@Test` 后）添加：

```java
@Test
public void testGetSubtreeTextNormalizesLevels() throws OrgNodeNotFoundException {
    OrgNode root = OrgTestUtils.getDefaultOrgNode();
    root.name = "root";
    root.level = 2;
    repo.write(root);
    OrgNode child = OrgTestUtils.getDefaultOrgNode();
    child.name = "child";
    child.parentId = root.id;
    child.level = 3;
    repo.write(child);
    OrgNode grandchild = OrgTestUtils.getDefaultOrgNode();
    grandchild.name = "grandchild";
    grandchild.parentId = child.id;
    grandchild.level = 4;
    repo.write(grandchild);

    String text = repo.getSubtreeText(root.id);
    assertTrue(text.startsWith("* TODO root\n"));
    assertTrue(text.contains("** TODO child\n"));
    assertTrue(text.contains("*** TODO grandchild\n"));
}

@Test
public void testGetSubtreeTextFullContent() throws OrgNodeNotFoundException {
    OrgNode root = OrgTestUtils.getComplexOrgNode();
    root.name = "complex root";
    root.level = 1;
    root.setPayload("   SCHEDULED: <2026-08-24 一 09:00>\n   some body");
    repo.write(root);
    OrgNode child = OrgTestUtils.getDefaultOrgNode();
    child.name = "plain child";
    child.parentId = root.id;
    child.level = 2;
    repo.write(child);

    String text = repo.getSubtreeText(root.id);
    assertTrue(text.contains("[#C]"));
    assertTrue(text.contains(":tag1:tag2::tag3:"));
    assertTrue(text.contains("SCHEDULED: <2026-08-24 一 09:00>"));
    assertTrue(text.contains("** TODO plain child"));
}

@Test
public void testGetSubtreeTextExcludesInheritedTags() throws OrgNodeNotFoundException {
    OrgNode root = OrgTestUtils.getDefaultOrgNode();
    root.name = "parent";
    root.tags = "work";
    root.level = 1;
    repo.write(root);
    OrgNode child = OrgTestUtils.getDefaultOrgNode();
    child.name = "child";
    child.parentId = root.id;
    child.level = 2;
    child.tags_inherited = "work";
    repo.write(child);

    String text = repo.getSubtreeText(root.id);
    assertTrue(text.contains("* TODO parent :work:"));
    assertFalse(text.contains("** TODO child :work:"));
    assertTrue(text.contains("** TODO child\n"));
}

@Test
public void testGetSubtreeTextNodeNotFound() {
    try {
        repo.getSubtreeText(-1);
        fail("Expected OrgNodeNotFoundException");
    } catch (OrgNodeNotFoundException e) {}
}
```

- [ ] **步骤 2：运行测试验证失败**

运行 OrgNodeTest。预期：编译失败，`cannot find symbol: method getSubtreeText(long)`。

- [ ] **步骤 3：实现 getSubtreeText**

在 `OrgNodeRepository.java` 的 Serialization 区块（:536 注释之后、`nodesToString` 前或后）添加：

```java
/**
 * Serialize a node and its entire subtree to org-format text with levels
 * normalized: the subtree root is level 1 (single star) regardless of its
 * depth in the source file. Payload kept verbatim; inherited tags are not
 * written (org semantics: they don't persist to file).
 * @throws OrgNodeNotFoundException nodeId invalid
 */
public String getSubtreeText(long nodeId) throws OrgNodeNotFoundException {
    OrgNode root = getById(nodeId);
    StringBuilder result = new StringBuilder();
    appendSubtree(result, root, root.level);
    return result.toString();
}

private void appendSubtree(StringBuilder result, OrgNode node, long rootLevel) {
    result.append(node.toString(node.level - rootLevel + 1)).append("\n");
    for (OrgNode child : getChildren(node.id))
        appendSubtree(result, child, rootLevel);
}
```

- [ ] **步骤 4：运行测试验证通过**

运行 OrgNodeTest。预期：4 个新用例与全部原有用例 PASS。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java \
        MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/OrgData/OrgNodeTest.java
git commit -m "feat: OrgNodeRepository.getSubtreeText 归一化层级序列化子树"
```

---

### 任务 3：OrgUtils.shareNode + 字符串 + 图标

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`, `MobileOrg/src/main/res/values/strings.xml`, `MobileOrg/src/main/res/drawable/ic_menu_share.xml`
**导出/变更接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java::shareNode`
**消费接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java::getSubtreeText`, `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgNodeRepository.java::getById`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`（新增 import 与静态方法）
- 修改：`MobileOrg/src/main/res/values/strings.xml:84`（`menu_view` 行附近）
- 创建：`MobileOrg/src/main/res/drawable/ic_menu_share.xml`

背景：intent 构建逻辑靠编译验证 + 真机手测（规格约定，不写自动化测试）。超大文本截断防御：Intent extras 走 Binder 事务（~1MB 硬限，实际 ~200-500KB 即可能 `TransactionTooLargeException`）。`OrgUtils` 现有 import 已含 `Intent`、`Context`、`R`、`OrgNode`；需新增 `Toast` 与 `OrgNodeRepository`（`OrgNodeNotFoundException` 与 OrgUtils 同包，无需 import）。

- [ ] **步骤 1：strings.xml 添加 3 个字符串**

在 `strings.xml` 的 `menu_view` 行（:84）后添加：

```xml
<string name="menu_share">Share node</string>
<string name="share_node_not_found">Node not found</string>
<string name="share_truncated">Content too long, truncated to 400000 chars</string>
```

- [ ] **步骤 2：创建 ic_menu_share.xml**

遵循项目 vector 惯例（参照 `ic_menu_pomodoro.xml`：24dp viewport、`#FFFFFF` fillColor、**必须** `xmlns:android`，用 `res-auto` 会 AAPT 构建失败）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"/>
</vector>
```

- [ ] **步骤 3：实现 shareNode**

`OrgUtils.java` 头部 import 区新增：

```java
import android.widget.Toast;
import com.matburt.mobileorg.OrgData.OrgNodeRepository;
```

类内添加：

```java
public static final int MAX_SHARE_LENGTH = 400000;

/**
 * Serialize the node's subtree and hand it to the system share sheet
 * (ACTION_SEND, text/plain). Shows a toast and returns silently if the
 * node no longer exists; truncates overly large text to stay under the
 * Binder transaction limit.
 */
public static void shareNode(Context context, long nodeId) {
    OrgNodeRepository repo = new OrgNodeRepository(context.getContentResolver());
    OrgNode node;
    String text;
    try {
        node = repo.getById(nodeId);
        text = repo.getSubtreeText(nodeId);
    } catch (com.matburt.mobileorg.util.OrgNodeNotFoundException e) {
        Toast.makeText(context, R.string.share_node_not_found, Toast.LENGTH_SHORT).show();
        return;
    }
    if (text.length() > MAX_SHARE_LENGTH) {
        text = text.substring(0, MAX_SHARE_LENGTH);
        Toast.makeText(context, R.string.share_truncated, Toast.LENGTH_SHORT).show();
    }
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TEXT, text);
    intent.putExtra(Intent.EXTRA_SUBJECT, node.name);
    context.startActivity(Intent.createChooser(intent,
            context.getString(R.string.menu_share)));
}
```

（`OrgNodeNotFoundException` 同包可省限定名，直接写 `OrgNodeNotFoundException` 即可。）

- [ ] **步骤 4：编译验证**

```bash
./gradlew assembleDebug
```
预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java \
        MobileOrg/src/main/res/values/strings.xml \
        MobileOrg/src/main/res/drawable/ic_menu_share.xml
git commit -m "feat: OrgUtils.shareNode 子树文本分享与超大截断防御"
```

---

### 任务 4：菜单接线（Outline 长按菜单 + ViewActivity）

**依赖：** 任务 3
**文件集：** `MobileOrg/src/main/res/menu/outline_node.xml`, `MobileOrg/src/main/res/menu/outline_node_uneditable.xml`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewActivity.java`
**导出/变更接口：** 无
**消费接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java::shareNode`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/res/menu/outline_node.xml`（`</menu>` 前追加 item）
- 修改：`MobileOrg/src/main/res/menu/outline_node_uneditable.xml`（同上）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java:101-133`（`onActionItemClicked` case 链）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewActivity.java:76-110`（菜单构建与选择处理）

背景：菜单 XML 必须用 `app:showAsAction`（AppCompat，`android:showAsAction` 会被静默忽略）。分享是只读操作，uneditable 菜单也加。`ViewActivity.onOptionsItemSelected` 现用 `order` 0-4 判断递归层级（ViewActivity.java:103），且 `Menu.NONE == 0`——新项必须用 itemId 判断且 order 取 5，否则会被 `viewNode(order)` 误吃。

- [ ] **步骤 1：两份菜单 XML 追加 menu_share**

`outline_node.xml` 的 `</menu>` 前追加：

```xml
<item
    android:id="@+id/menu_share"
    android:icon="@drawable/ic_menu_share"
    app:showAsAction="ifRoom"
    android:title="@string/menu_share"/>
```

`outline_node_uneditable.xml` 同样追加（该文件现有 item 用 `ifRoom`，保持一致）。

- [ ] **步骤 2：OutlineActionMode 加 case**

`onActionItemClicked`（:101）的 `else if (id == R.id.menu_view)` 分支前插入：

```java
} else if (id == R.id.menu_share) {
    OrgUtils.shareNode(context, node.id);
```

（`OrgUtils` 已在 :33 import。）

- [ ] **步骤 3：ViewActivity 加菜单项与处理**

`onCreateOptionsMenu`（:77）的 `return true;` 前添加：

```java
MenuItem shareItem = menu.add(Menu.NONE, R.id.menu_share, 5, R.string.menu_share);
shareItem.setIcon(R.drawable.ic_menu_share);
shareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
```

`onOptionsItemSelected`（:101）改为在 order 判断**之前**用 itemId 短路：

```java
@Override
public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.menu_share) {
        OrgUtils.shareNode(this, nodeId);
        return true;
    }
    int order = item.getOrder();
    if (order >= 0 && order <= 4) {
        viewNode(order);
    } else {
        return super.onOptionsItemSelected(item);
    }

    return true;
}
```

（`OrgUtils` 已在 :15 import。）

- [ ] **步骤 4：编译验证**

```bash
./gradlew assembleDebug
```
预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/res/menu/outline_node.xml \
        MobileOrg/src/main/res/menu/outline_node_uneditable.xml \
        MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActionMode.java \
        MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewActivity.java
git commit -m "feat: Outline 长按菜单与查看页菜单接入节点分享"
```

---

### 任务 5：推送 + CI 验证 + 真机手测

**依赖：** 任务 4
**文件集：** 无
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：推送并等待 CI**

```bash
git push
gh run watch $(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')
```
预期：CI 全绿（含 94+ instrumentation 测试在 API 30 模拟器上通过）。

- [ ] **步骤 2：真机手测**

设备 `adb connect` 后安装 debug APK，验证：
1. Outline 长按一个有子树且非 level 1 的节点 → 分享 → 分享面板出现 → 发给备忘录/邮件 → 文本以 `* ` 开头、子节点 `** `、payload/SCHEDULED 保留
2. 查看页（ViewActivity）overflow 菜单 → 分享 → 同样结果
3. 长按 agenda/不可编辑节点 → 分享菜单项存在且可用

验证通过后向用户汇报手测结果。

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 3 → 任务 4 → 任务 5

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2
- Wave 3（依赖 Wave 2）：任务 3
- Wave 4（依赖 Wave 3）：任务 4
- Wave 5（依赖 Wave 4）：任务 5
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真

依赖链为真实的数据层→逻辑层→UI 层线性依赖，无并行空间。
