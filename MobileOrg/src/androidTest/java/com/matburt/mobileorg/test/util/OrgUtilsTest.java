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
	public void testMailtoSchemeStillBecomesOrgLink() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent("地址", "mailto:someone@example.com"));
		assertEquals("[[mailto:someone@example.com][地址]]", node.name);
		assertEquals("", node.getPayload());
	}

	@Test
	public void testSubjectWithNonUrlTextKeepsSubjectAndFullBody() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent("从书中引用 天才基本法", "第一行内容\n第二行内容\n第三行内容"));
		assertEquals("从书中引用 天才基本法", node.name);
		assertEquals("第一行内容\n第二行内容\n第三行内容", node.getPayload());
	}

	@Test
	public void testSubjectWithSingleLineNonUrlText() {
		OrgNode node = OrgUtils.getCaptureIntentContents(
				sendIntent("书名", "单行选中文字"));
		assertEquals("书名", node.name);
		assertEquals("单行选中文字", node.getPayload());
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
}
