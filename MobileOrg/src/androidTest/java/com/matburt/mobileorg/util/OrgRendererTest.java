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
		assertEquals("- [ ] a\n", cleaned);
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
