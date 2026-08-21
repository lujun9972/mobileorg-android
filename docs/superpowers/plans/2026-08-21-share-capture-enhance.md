# Share-to-Capture 增强 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 无 SUBJECT 分享自动生成标题（首非空行，≤40 字整行，超长截断加 `…`，正文保留全文）；新增 PROCESS_TEXT 入口（选中文本工具栏直达 capture）。

**架构：** 全部收敛在 `OrgUtils.getCaptureIntentContents` 单点——SEND、PROCESS_TEXT、语音备注入口统一受益。`EXTRA_TEXT` 为空时兜底读 `EXTRA_PROCESS_TEXT`；manifest 给 `EditActivity` 追加 PROCESS_TEXT intent-filter。规格：`docs/superpowers/specs/2026-08-21-share-capture-enhance-design.md`。

**技术栈：** Android instrumentation test（AndroidJUnit4，无 Provider 依赖）。

**执行分支：** 基于最新 `main` 创建专用 worktree：

```bash
cd /home/lujun9972/github/mobileorg-android
git fetch origin main && git worktree add .claude/worktrees/share-capture -b share-capture origin/main
cd .claude/worktrees/share-capture
echo "sdk.dir=/home/lujun9972/android-sdk" > local.properties  # SDK 路径不进版本库
```

**编码约束：**
- `OrgUtils.java` 缩进为 TAB
- 验证设备不在线时，本地 instrumentation 测试步骤改为 push + `gh workflow run test.yml --ref share-capture` 后等 CI

---

### 任务 1：标题生成与 PROCESS_TEXT 兜底（TDD）

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java`
**导出/变更接口：** `OrgUtils.java::getCaptureIntentContents`
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java:50-69`（`getCaptureIntentContents`）
- 创建：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java`

- [ ] **步骤 1：编写失败的测试**

创建 `OrgUtilsTest.java`（纯静态函数测试，无需 Provider）：

```java
package com.matburt.mobileorg.test.util;

import android.content.Intent;

import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.util.OrgUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class OrgUtilsTest {

	private static Intent sendIntent(String subject, String text) {
		Intent intent = new Intent();
		if (subject != null)
			intent.putExtra("android.intent.extra.SUBJECT", subject);
		if (text != null)
			intent.putExtra("android.intent.extra.TEXT", text);
		return intent;
	}

	private static String repeat(String s, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++)
			sb.append(s);
		return sb.toString();
	}

	@Test
	public void testSubjectAndTextBecomesOrgLink() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent("页面标题", "https://example.com"));
		assertEquals("[[https://example.com][页面标题]]", node.name);
		assertEquals("", node.getPayload());
	}

	@Test
	public void testTextOnlyGeneratesTitleFromFirstLine() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent(null, "首行做标题\n第二行内容"));
		assertEquals("首行做标题", node.name);
		assertEquals("首行做标题\n第二行内容", node.getPayload());
	}

	@Test
	public void testEmptySubjectGeneratesTitle() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent("", "首行做标题"));
		assertEquals("首行做标题", node.name);
	}

	@Test
	public void testLongFirstLineTruncatedTo40() {
		String longLine = repeat("字", 55);
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent(null, longLine));
		assertEquals(repeat("字", 40) + "…", node.name);
		assertEquals(longLine, node.getPayload());
	}

	@Test
	public void testLeadingBlankLinesSkipped() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent(null, "\n\n   \n实际标题\n内容"));
		assertEquals("实际标题", node.name);
	}

	@Test
	public void testBlankTextKeepsEmptyTitle() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent(null, "\n  \n"));
		assertEquals("", node.name);
		assertEquals("\n  \n", node.getPayload());
	}

	@Test
	public void testNullTextAndSubject() {
		OrgNode node = OrgUtils.getCaptureIntentContents(sendIntent(null, null));
		assertEquals("", node.name);
		assertEquals("", node.getPayload());
	}

	@Test
	public void testProcessTextFallback() {
		Intent intent = new Intent();
		intent.putExtra("android.intent.extra.PROCESS_TEXT", "选中的句子");
		OrgNode node = OrgUtils.getCaptureIntentContents(intent);
		assertEquals("选中的句子", node.name);
		assertEquals("选中的句子", node.getPayload());
	}
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.util.OrgUtilsTest`
预期：FAIL——标题生成与 PROCESS_TEXT 兜底尚未实现（`testTextOnlyGeneratesTitleFromFirstLine` 等断言标题为空）

- [ ] **步骤 3：实现**

`OrgUtils.getCaptureIntentContents` 整体替换为（保持 TAB 缩进；行为矩阵：SUBJECT 非空+TEXT → org 链接不变；SUBJECT null/空串+TEXT → 生成标题；TEXT null → 兜底读 PROCESS_TEXT；仅 SUBJECT → 原样保留）：

```java
	public static OrgNode getCaptureIntentContents(Intent intent) {
		String subject = intent
				.getStringExtra("android.intent.extra.SUBJECT");
		String text = intent.getStringExtra("android.intent.extra.TEXT");
		if (text == null)
			text = intent.getStringExtra("android.intent.extra.PROCESS_TEXT");

		if (text != null && subject != null && !subject.isEmpty()) {
			subject = "[[" + text + "][" + subject + "]]";
			text = "";
		} else if (text != null) {
			subject = generateTitle(text);
		}

		if (subject == null)
			subject = "";
		if (text == null)
			text = "";

		OrgNode node = new OrgNode();
		node.name = subject;
		node.setPayload(text);
		return node;
	}

	private static String generateTitle(String text) {
		for (String line : text.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty())
				continue;
			if (trimmed.length() <= 40)
				return trimmed;
			return trimmed.substring(0, 40) + "…";
		}
		return "";
	}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.util.OrgUtilsTest`
预期：PASS，8 个测试全部通过

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgUtilsTest.java
git commit -m "feat: generate capture title from text and add PROCESS_TEXT fallback"
```

### 任务 2：PROCESS_TEXT manifest 入口与真机验证

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/AndroidManifest.xml`
**导出/变更接口：** `AndroidManifest.xml::EditActivity`
**消费接口：** `OrgUtils.java::getCaptureIntentContents`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/AndroidManifest.xml:116-122`（`EditActivity` 首个 `ACTION_SEND` intent-filter 之后）

- [ ] **步骤 1：追加 intent-filter**

在 `EditActivity` 的 `ACTION_SEND`（`text/*`）intent-filter 结束标签后插入：

```xml
            <intent-filter>
                <action android:name="android.intent.action.PROCESS_TEXT" />

                <category android:name="android.intent.category.DEFAULT" />

                <data android:mimeType="text/plain" />
            </intent-filter>
```

（API 23+ 系统才在文本选择工具栏显示入口，低版本无副作用。）

- [ ] **步骤 2：编译验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：推送并触发 CI 全量回归**

```bash
git add MobileOrg/src/main/AndroidManifest.xml
git commit -m "feat: register PROCESS_TEXT handler for selection-toolbar capture"
git push origin share-capture
gh workflow run build.yml --ref share-capture
gh workflow run test.yml --ref share-capture
```

用 `gh run list --branch share-capture` 等待两 workflow 成功。

- [ ] **步骤 4：真机手测**

```bash
./gradlew assembleRelease
adb install -r MobileOrg/build/outputs/apk/release/MobileOrg-release.apk
```

手测清单：
1. 浏览器或任意 app 分享一段纯文本（无标题）给 MobileOrg → 表单标题自动填首个非空行，正文含全文 → 保存 → outline 列表显示标题
2. 分享超 40 字长文本 → 标题为前 40 字 + `…`，正文完整
3. 分享一个带标题的网页链接（SUBJECT 非空）→ 标题仍为 `[[URL][标题]]` org 链接（回归）
4. 在 ReadEra/浏览器中长按选中一段文字 → 文本选择工具栏出现 MobileOrg → 点击 → capture 表单打开且标题/正文已填
5. 分享面板里选择 MobileOrg（原 SEND 路径）→ 行为同 1

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2（依赖 1）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
