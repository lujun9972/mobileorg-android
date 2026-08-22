# Checkbox 勾选 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 渲染视图中的 org checkbox（`- [ ]`/`- [X]`）可点击切换并自动刷新统计 cookie（`[1/3]`、`[33%]`）。

**架构：** 识别/翻转/cookie 刷新为 `OrgUtils` 纯静态函数；`OrgRenderer` 无序列表分支输出 `orgcheckbox:<nodeId>:<原始行号>` 字符链接（`preClean` 附带 cleaned→raw 行号映射，因 PROPERTIES/LOGBOOK 等行被删导致错位）；`ViewFragment` 复用现有 scheme 拦截模式，走 `generateApplyWriteEdits`（BODY edit，undo/同步兼容）写回并回调 `ViewActivity` 重渲染。规格：`docs/superpowers/specs/2026-08-22-checkbox-toggle-design.md`。

**技术栈：** Android instrumentation test（AndroidJUnit4）。

**执行分支：** 基于最新 `main` 创建专用 worktree：

```bash
cd /home/lujun9972/github/mobileorg-android
git fetch origin main && git worktree add .claude/worktrees/checkbox-toggle -b checkbox-toggle origin/main
cd .claude/worktrees/checkbox-toggle
echo "sdk.dir=/home/lujun9972/android-sdk" > local.properties  # SDK 路径不进版本库
```

**编码约束：**
- `OrgUtils.java` 缩进为 TAB；`OrgRenderer.java` 缩进为 TAB（render/preClean 区域）与 4 空格（文件后半部 processTable 起）混合，改动处跟随所在区域现状
- 测试验证统一走 CI：`git push` + `gh workflow run test.yml --ref checkbox-toggle`（设备装的是 release 签名 APK，本地 `connectedDebugAndroidTest` 会因签名冲突装不上，勿走本地路径）
- checkbox 正则与 cookie 正则只在 `OrgUtils` 定义一份，渲染与写回共用

---

### 任务 1：checkbox 行识别与翻转（TDD）

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java`
**导出/变更接口：** `OrgUtils.java::CHECKBOX_LINE`, `OrgUtils.java::toggleCheckboxLine`
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`（类尾部 `lookUpValueFromArray` 之后加静态成员）
- 修改：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java`（追加测试）

- [ ] **步骤 1：编写失败的测试**

在 `OrgUtilsTest` 追加（沿用文件既有 TAB 缩进）：

```java
	@Test
	public void testToggleUncheckedToChecked() {
		assertEquals("- [X] 买菜",
				OrgUtils.toggleCheckboxLine("- [ ] 买菜", 0));
	}

	@Test
	public void testToggleCheckedToUnchecked() {
		assertEquals("- [ ] 买菜",
				OrgUtils.toggleCheckboxLine("- [X] 买菜", 0));
		assertEquals("- [ ] 买菜",
				OrgUtils.toggleCheckboxLine("- [x] 买菜", 0));
	}

	@Test
	public void testTogglePlusMarker() {
		assertEquals("+ [X] 买菜",
				OrgUtils.toggleCheckboxLine("+ [ ] 买菜", 0));
	}

	@Test
	public void testToggleNonCheckboxLineUnchanged() {
		assertEquals("普通文本", OrgUtils.toggleCheckboxLine("普通文本", 0));
	}

	@Test
	public void testToggleLineOutOfRangeUnchanged() {
		String payload = "- [ ] a";
		assertEquals(payload, OrgUtils.toggleCheckboxLine(payload, 9));
	}

	@Test
	public void testTogglePreservesOtherLines() {
		String payload = "标题\n  - [ ] a\n- [X] b";
		assertEquals("标题\n  - [X] a\n- [X] b",
				OrgUtils.toggleCheckboxLine(payload, 1));
	}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
git add -A MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java
git commit -m "test: checkbox line toggle (red)"
git push origin checkbox-toggle && gh workflow run test.yml --ref checkbox-toggle
```

用 `gh run watch <id> --exit-status` 等待。预期：FAIL——`OrgUtilsTest` 编译错误 `cannot find symbol: toggleCheckboxLine`。

- [ ] **步骤 3：实现**

`OrgUtils` 类中新增（`import java.util.regex.Pattern;` 已存在）：

```java
	public static final Pattern CHECKBOX_LINE = Pattern
			.compile("^(\\s*[-+]\\s+)\\[( |X|x)\\]\\s*(.*)$");

	public static String toggleCheckboxLine(String payload, int rawLineIdx) {
		if (payload == null || rawLineIdx < 0)
			return payload;
		String[] lines = payload.split("\n", -1);
		if (rawLineIdx >= lines.length)
			return payload;
		Matcher m = CHECKBOX_LINE.matcher(lines[rawLineIdx]);
		if (!m.find())
			return payload;
		String mark = m.group(2).trim().isEmpty() ? "[X]" : "[ ]";
		lines[rawLineIdx] = m.group(1) + mark + " " + m.group(3);
		return String.join("\n", lines);
	}
```

注意：`split("\n", -1)` 保留尾部空行；需补 `import java.util.regex.Matcher;`。

- [ ] **步骤 4：运行测试验证通过**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java
git commit -m "feat: toggle org checkbox line in payload"
git push origin checkbox-toggle && gh workflow run test.yml --ref checkbox-toggle
```

预期：PASS，OrgUtilsTest 全部通过（含 share-capture 既有 12 个）。

### 任务 2：cookie 刷新（TDD）

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java`
**导出/变更接口：** `OrgUtils.java::refreshCookies`
**消费接口：** `OrgUtils.java::CHECKBOX_LINE`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`（任务 1 代码之后追加）
- 修改：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java`（追加测试）

- [ ] **步骤 1：编写失败的测试**

```java
	@Test
	public void testCookieFractionUpdated() {
		String payload = "- 任务 [0/2]\n  - [ ] a\n  - [X] b\n";
		assertEquals("- 任务 [1/2]\n  - [ ] a\n  - [X] b\n",
				OrgUtils.refreshCookies(payload));
	}

	@Test
	public void testCookiePercentUpdated() {
		String payload = "- 任务 [0%]\n  - [X] a\n  - [ ] b\n";
		assertEquals("- 任务 [50%]\n  - [X] a\n  - [ ] b\n",
				OrgUtils.refreshCookies(payload));
	}

	@Test
	public void testCookieNoCheckboxBlockUnchanged() {
		String payload = "- 任务 [0/2]\n正文行\n";
		assertEquals(payload, OrgUtils.refreshCookies(payload));
	}

	@Test
	public void testCookieNoCookieLineUnchanged() {
		String payload = "- [ ] a\n- [X] b";
		assertEquals(payload, OrgUtils.refreshCookies(payload));
	}

	@Test
	public void testCookieNestedDescendantsCounted() {
		String payload = "- 父 [0/0]\n  - [ ] a\n    - [X] deep\n";
		assertEquals("- 父 [1/2]\n  - [ ] a\n    - [X] deep\n",
				OrgUtils.refreshCookies(payload));
	}

	@Test
	public void testCookieStopsAtLowerIndentSibling() {
		String payload = "- 任务A [0/1]\n  - [X] a\n- 任务B [0/1]\n  - [ ] b\n";
		assertEquals("- 任务A [1/1]\n  - [X] a\n- 任务B [0/1]\n  - [ ] b\n",
				OrgUtils.refreshCookies(payload));
	}
```

- [ ] **步骤 2：运行测试验证失败**

同任务 1 步骤 2 模式（commit → push → 触发 test.yml）。预期：FAIL，`cannot find symbol: refreshCookies`。

- [ ] **步骤 3：实现**

```java
	private static final Pattern COOKIE_FRACTION = Pattern
			.compile("^(\\s*[-+]+.*\\s)\\[(\\d*)/(\\d*)\\]\\s*$");
	private static final Pattern COOKIE_PERCENT = Pattern
			.compile("^(\\s*[-+]+.*\\s)\\[(\\d+)%\\]\\s*$");

	public static String refreshCookies(String payload) {
		if (payload == null)
			return payload;
		String[] lines = payload.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			Matcher frac = COOKIE_FRACTION.matcher(lines[i]);
			Matcher pct = COOKIE_PERCENT.matcher(lines[i]);
			boolean isFrac = frac.find();
			if (!isFrac && !pct.find())
				continue;
			Matcher cookie = isFrac ? frac : pct;
			int cookieIndent = indentWidth(lines[i]);
			int done = 0;
			int total = 0;
			for (int j = i + 1; j < lines.length; j++) {
				String l = lines[j];
				if (!l.trim().isEmpty() && indentWidth(l) <= cookieIndent)
					break;
				Matcher m = CHECKBOX_LINE.matcher(l);
				if (m.find()) {
					total++;
					if (!m.group(2).trim().isEmpty())
						done++;
				}
			}
			if (total == 0)
				continue;
			if (isFrac)
				lines[i] = cookie.group(1) + "[" + done + "/" + total + "] ";
			else
				lines[i] = cookie.group(1) + "["
						+ Math.round(100.0 * done / total) + "%] ";
		}
		return String.join("\n", lines);
	}

	private static int indentWidth(String line) {
		int n = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == ' ' || c == '\t')
				n++;
			else
				break;
		}
		return n;
	}
```

统计语义：cookie 项之后、直到首个"非空且缩进 ≤ cookie 行缩进"的行之前的全部后代 checkbox（含嵌套）。

- [ ] **步骤 4：运行测试验证通过**

同模式触发 CI。预期：PASS。

### 任务 3：渲染层 checkbox 链接（TDD）

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgRenderer.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/util/OrgRendererTest.java`
**导出/变更接口：** `OrgRenderer.java::preClean`, `OrgRenderer.java::render`
**消费接口：** `OrgUtils.java::CHECKBOX_LINE`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgRenderer.java`（`preClean`、`render`、`nodeToHTML`、`payloadToHTML`）
- 创建：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/util/OrgRendererTest.java`（包名 `com.matburt.mobileorg.util`，同包访问包私有方法）

- [ ] **步骤 1：编写失败的测试**

```java
package com.matburt.mobileorg.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OrgRendererTest {

	private OrgRenderer newRenderer() {
		return new OrgRenderer(null, ApplicationProvider.getApplicationContext());
	}

	@Test
	public void testCheckboxLineRendersToggleLink() {
		OrgRenderer r = newRenderer();
		List<Integer> map = new ArrayList<>();
		map.add(0);
		String html = r.render("- [ ] 买菜", map, 5L);
		assertTrue(html.contains("<a href=\"orgcheckbox:5:0\">☐</a>"));
		assertTrue(html.contains("买菜"));
	}

	@Test
	public void testCheckedCheckboxRendersCheckedSymbol() {
		OrgRenderer r = newRenderer();
		List<Integer> map = new ArrayList<>();
		map.add(0);
		String html = r.render("- [X] 买菜", map, 5L);
		assertTrue(html.contains("orgcheckbox:5:0"));
		assertTrue(html.contains("☑"));
	}

	@Test
	public void testPreCleanMappingSkipsPropertiesLines() {
		OrgRenderer r = newRenderer();
		String payload = ":PROPERTIES:\n:X: 1\n:END:\n- [ ] a";
		List<Integer> map = new ArrayList<>();
		String cleaned = r.preClean(payload, map);
		assertEquals("- [ ] a", cleaned);
		assertEquals(1, map.size());
		assertEquals(Integer.valueOf(3), map.get(0));
	}

	@Test
	public void testRenderWithoutMapRendersPlainSymbol() {
		OrgRenderer r = newRenderer();
		String html = r.render("- [ ] 买菜", null, -1);
		assertTrue(html.contains("☐"));
		assertTrue(!html.contains("orgcheckbox"));
	}
}
```

依赖说明：`androidx.test:core` 的 `ApplicationProvider` 已由 `androidx.test.ext:junit` 传递提供，`androidTestImplementation` 无需新增。

- [ ] **步骤 2：运行测试验证失败**

commit → push → `gh workflow run test.yml --ref checkbox-toggle`。预期：FAIL，编译错误 `method preClean(String,List<Integer>) not found` / `render(String,List<Integer>,long) not found`。

- [ ] **步骤 3：实现**

3 处修改（均在 `OrgRenderer.java`）：

(a) `preClean` 加映射重载（原方法改 for-index 循环委托）：

```java
	String preClean(String rawPayload) {
		return preClean(rawPayload, null);
	}

	String preClean(String rawPayload, List<Integer> rawLineMap) {
		// 方法体 = 原 preClean，仅两处变化：
		// 1. for (String line : lines) → for (int i = 0; i < lines.length; i++) { String line = lines[i];
		// 2. 每个 result.append(line).append("\n"); 前加 if (rawLineMap != null) rawLineMap.add(i);
		// 删除分支（PROPERTIES/LOGBOOK/SCHEDULED 等 continue 路径）不记录
	}
```

需补 `import java.util.List;`、`import java.util.ArrayList;`（若缺）。

(b) `render` 加重载，原签名委托：

```java
	String render(String cleanedPayload) {
		return render(cleanedPayload, null, -1);
	}

	String render(String cleanedPayload, List<Integer> rawLineMap, long nodeId) {
		// 方法体 = 原 render，仅改无序列表分支：
	}
```

无序列表分支（原 `if (isUnorderedItem) {...}` 内部）改为：

```java
					if (isUnorderedItem) {
						if (!inUnorderedList) {
							closeListIfNeeded(result, inUnorderedList, inOrderedList);
							result.append("<ul>\n");
							inUnorderedList = true;
							inOrderedList = false;
						}
						String content;
						Matcher cb = OrgUtils.CHECKBOX_LINE.matcher(line);
						if (cb.find() && rawLineMap != null && nodeId >= 0
								&& i < rawLineMap.size()) {
							String symbol = cb.group(2).trim().isEmpty() ? "☐" : "☑";
							content = htmlEncode(cb.group(3).trim());
							content = applyInlineMarkup(content);
							content = convertLinks(content);
							result.append("<li><a href=\"orgcheckbox:").append(nodeId)
									.append(":").append(rawLineMap.get(i))
									.append("\">").append(symbol).append("</a> ")
									.append(content).append("</li>\n");
						} else if (cb.find()) {
							// 无映射/无 nodeId（兼容路径）：纯符号不可点击
							String symbol = cb.group(2).trim().isEmpty() ? "☐" : "☑";
							content = htmlEncode(cb.group(3).trim());
							content = applyInlineMarkup(content);
							content = convertLinks(content);
							result.append("<li>").append(symbol).append(" ")
									.append(content).append("</li>\n");
						} else {
							content = htmlEncode(trimmed.substring(1).trim());
							content = applyInlineMarkup(content);
							content = convertLinks(content);
							result.append("<li>").append(content).append("</li>\n");
						}
						break;
					}
```

注意：`Matcher.find()` 消耗状态，第二个分支需重新匹配——实现时用 `boolean isCb = cb.find();` 一次判定后分支复用 `cb.group(...)`。需补 `import java.util.regex.Matcher;`、`import com.matburt.mobileorg.util.OrgUtils` 同包无需 import（OrgRenderer 已在 `util` 包）。

(c) 接线两个调用点：

```java
	// nodeToHTML 内：
		String payload = node.getPayload();
		if (payload != null && !payload.trim().isEmpty()) {
			List<Integer> map = new ArrayList<>();
			String cleaned = preClean(payload, map);
			String rendered = render(cleaned, map, node.id);
			result.append(rendered).append("\n<br/>\n");
		}

	// payloadToHTML 内：
		List<Integer> map = new ArrayList<>();
		String cleaned = preClean(payload, map);
		String rendered = render(cleaned, map, node.id);
		return wrapInTemplate(rendered);
```

- [ ] **步骤 4：运行测试验证通过**

commit → push → CI。预期：PASS（OrgRendererTest 4 个 + 全量回归）。

### 任务 4：拦截、写回与刷新

**依赖：** 任务 1, 任务 2, 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewActivity.java`
**导出/变更接口：** `ViewFragment.java::OnNodeChangedListener`
**消费接口：** `OrgUtils.java::toggleCheckboxLine`, `OrgUtils.java::refreshCookies`, `OrgNodeRepository.java::generateApplyWriteEdits`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java`（`shouldOverrideUrlLoading` 加分支 + `handleCheckboxToggle` + 接口定义）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewActivity.java`（实现接口 + `lastLevel` 记录 + 刷新）

- [ ] **步骤 1：ViewFragment 拦截与写回**

`shouldOverrideUrlLoading` 在 `orginternal:` 分支后追加：

```java
			if (url.startsWith("orgcheckbox:")) {
				handleCheckboxToggle(url.substring("orgcheckbox:".length()));
				return true;
			}
```

类内新增（import 补 `com.matburt.mobileorg.util.OrgUtils`）：

```java
	public interface OnNodeChangedListener {
		void onNodeChanged();
	}

	private void handleCheckboxToggle(String ref) {
		try {
			String[] parts = ref.split(":");
			long nodeId = Long.parseLong(parts[0]);
			int rawLine = Integer.parseInt(parts[1]);
			OrgNodeRepository repo = new OrgNodeRepository(resolver);
			OrgNode oldNode = repo.getById(nodeId);
			OrgNode newNode = new OrgNode(oldNode);
			newNode.setPayload(OrgUtils.refreshCookies(
					OrgUtils.toggleCheckboxLine(oldNode.getPayload(), rawLine)));
			repo.generateApplyWriteEdits(oldNode, newNode, "");
			repo.updateAllNodes(oldNode);
			if (getActivity() instanceof OnNodeChangedListener)
				((OnNodeChangedListener) getActivity()).onNodeChanged();
		} catch (Exception e) {
			Toast.makeText(getActivity(), R.string.node_not_found, Toast.LENGTH_SHORT).show();
		}
	}
```

`OrgNode(OrgNode)` 拷贝构造已存在（`OrgNode.java:37`）；`olpPath` 传 `""`（BODY edit 不消费该参数）。

- [ ] **步骤 2：ViewActivity 刷新**

```java
public class ViewActivity extends AppCompatActivity
		implements ViewFragment.OnNodeChangedListener {
	// ...
	private int lastLevel = 0;

	public void viewNode(int levelOfRecursion) {
		this.lastLevel = levelOfRecursion;
		// ...原有内容不变
	}

	@Override
	public void onNodeChanged() {
		try {
			this.node = new OrgNodeRepository(resolver).getById(nodeId);
			viewNode(lastLevel);
		} catch (OrgNodeNotFoundException e) {
		}
	}
}
```

- [ ] **步骤 3：编译与全量 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewActivity.java
git commit -m "feat: toggle checkbox from rendered view with cookie refresh"
git push origin checkbox-toggle
gh workflow run test.yml --ref checkbox-toggle
gh workflow run build.yml --ref checkbox-toggle
```

用 `gh run watch` 等待两者成功。

- [ ] **步骤 4：真机手测**

```bash
rm -rf /tmp/checkbox-apk && mkdir -p /tmp/checkbox-apk
gh run download <build-run-id> -n MobileOrg-release -D /tmp/checkbox-apk
adb install -r /tmp/checkbox-apk/release/MobileOrg-release.apk
```

手测清单：
1. 节点 payload 含 `- 大任务 [0/2]\n  - [ ] a\n  - [X] b` → 详情页显示 ☐/☑ 符号，可点击样式
2. 点未勾符号 → 立即变 ☑ 且 cookie 变 `[2/2]`（页面无弹窗、自动重渲染）
3. 再点已勾符号 → 变回 ☐，cookie 回落 `[1/2]`
4. outline 菜单 Undo → 勾选变更回滚（BODY edit batch 生效）
5. 大纲递归视图（View 菜单提高层级）中子节点的 checkbox 同样可点击
6. payload 含 `:PROPERTIES:` 块的节点，其 checkbox 点击后状态正确（行号映射不错位的实证）
7. 同步一次后确认桌面 org 文件中 `- [X]` 与 cookie 落盘正确

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 4（任务 3 与任务 2 并行后汇入任务 4）

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2（依赖 1）, 任务 3（依赖 1）
- Wave 3（依赖 Wave 2）：任务 4（依赖 1, 2, 3）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
