package com.matburt.mobileorg.test.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.test.mock.MockContentResolver;
import android.test.ProviderTestCase2;

import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgFile;
import com.matburt.mobileorg.OrgData.OrgNode;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.OrgData.OrgContract.Edits;
import com.matburt.mobileorg.OrgData.OrgContract.Files;
import com.matburt.mobileorg.OrgData.OrgContract.OrgData;
import com.matburt.mobileorg.util.OrgRenderer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OrgRendererTest extends ProviderTestCase2<OrgProvider> {

    private MockContentResolver resolver;
    private OrgRenderer renderer;
    private OrgDatabase db;

    public OrgRendererTest() {
        super(OrgProvider.class, OrgProvider.class.getName());
    }

    @Before
    public void setUp() throws Exception {
        setContext(ApplicationProvider.getApplicationContext());
        super.setUp();
        this.resolver = getMockContentResolver();
        this.db = new OrgDatabase(getMockContext());

        // Wrap context for renderer (it needs getTheme)
        final ContentResolver testResolver = this.resolver;
        Context rendererContext = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public ContentResolver getContentResolver() {
                return testResolver;
            }
        };
        this.renderer = new OrgRenderer(testResolver, rendererContext);

        // Clean DB
        resolver.delete(Edits.CONTENT_URI, null, null);
        resolver.delete(OrgData.CONTENT_URI, null, null);
        resolver.delete(Files.CONTENT_URI, null, null);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        super.tearDown();
    }

    private OrgNode createTestNode(String payload) throws Exception {
        OrgFile file = new OrgFile("test.org", "Test", "checksum");
        file.write(resolver);

        OrgNode node = new OrgNode();
        node.setFilename("test.org", resolver);
        node.setPayload(payload);
        node.write(resolver);

        return node;
    }

    @Test
    public void testPreCleanStripsProperties() throws Exception {
        OrgNode node = createTestNode(":PROPERTIES:\n:ID: abc123\n:END:\nSome text here");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain the text", html.contains("Some text here"));
        assertFalse("Should NOT contain :PROPERTIES:", html.contains(":PROPERTIES:"));
        assertFalse("Should NOT contain :ID:", html.contains(":ID:"));
    }

    @Test
    public void testPreCleanStripsLogbook() throws Exception {
        OrgNode node = createTestNode(":LOGBOOK:\nCLOCK: [2025-01-15 Wed 09:00]--[2025-01-15 Wed 10:28] => 1:28\n:END:\nSome text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain the text", html.contains("Some text"));
        assertFalse("Should NOT contain :LOGBOOK:", html.contains(":LOGBOOK:"));
        assertFalse("Should NOT contain CLOCK:", html.contains("CLOCK:"));
    }

    @Test
    public void testPreCleanStripsScheduled() throws Exception {
        OrgNode node = createTestNode("SCHEDULED: <2025-01-15 Wed>\nSome text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain the text", html.contains("Some text"));
        assertFalse("Should NOT contain SCHEDULED:", html.contains("SCHEDULED:"));
    }

    @Test
    public void testPreCleanStripsDeadline() throws Exception {
        OrgNode node = createTestNode("DEADLINE: <2025-01-16 Thu>\nSome text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain the text", html.contains("Some text"));
        assertFalse("Should NOT contain DEADLINE:", html.contains("DEADLINE:"));
    }

    @Test
    public void testPreCleanStripsClosed() throws Exception {
        OrgNode node = createTestNode("CLOSED: [2025-01-15 Wed]\nSome text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain the text", html.contains("Some text"));
        assertFalse("Should NOT contain CLOSED:", html.contains("CLOSED:"));
    }

    @Test
    public void testPreCleanPreservesBeginSrc() throws Exception {
        OrgNode node = createTestNode("#+BEGIN_SRC python\nprint('hello')\n#+END_SRC");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <code class=\"language-python\">", html.contains("language-python"));
        assertTrue("Should contain the code content", html.contains("print('hello')"));
    }

    @Test
    public void testInlineMarkupBold() throws Exception {
        OrgNode node = createTestNode("This is *bold* text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <b>bold</b>", html.contains("<b>bold</b>"));
    }

    @Test
    public void testInlineMarkupItalic() throws Exception {
        OrgNode node = createTestNode("This is /italic/ text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <i>italic</i>", html.contains("<i>italic</i>"));
    }

    @Test
    public void testInlineMarkupCode() throws Exception {
        OrgNode node = createTestNode("This is ~code~ text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <code>code</code>", html.contains("<code>code</code>"));
    }

    @Test
    public void testInlineMarkupVerbatim() throws Exception {
        OrgNode node = createTestNode("This is =verbatim= text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <code>verbatim</code>", html.contains("<code>verbatim</code>"));
    }

    @Test
    public void testInlineMarkupUnderline() throws Exception {
        OrgNode node = createTestNode("This is _underline_ text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <u>underline</u>", html.contains("<u>underline</u>"));
    }

    @Test
    public void testInlineMarkupStrike() throws Exception {
        OrgNode node = createTestNode("This is +strike+ text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <strike>strike</strike>", html.contains("<strike>strike</strike>"));
    }

    @Test
    public void testLinkFile() throws Exception {
        OrgNode node = createTestNode("Check [[file:other.org::*Heading][this link]]");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain href=\"orgfile:other.org::*Heading\"", html.contains("href=\"orgfile:other.org::*Heading\""));
        assertTrue("Should contain link text", html.contains("this link"));
    }

    @Test
    public void testLinkId() throws Exception {
        OrgNode node = createTestNode("See [[id:abc123][description]]");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain href=\"orgid:abc123\"", html.contains("href=\"orgid:abc123\""));
        assertTrue("Should contain description", html.contains("description"));
    }

    @Test
    public void testLinkInternal() throws Exception {
        OrgNode node = createTestNode("Go to [[*My Heading][the heading]]");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain href=\"orginternal:*My Heading\"", html.contains("href=\"orginternal:*My Heading\""));
        assertTrue("Should contain link text", html.contains("the heading"));
    }

    @Test
    public void testLinkExternalUrl() throws Exception {
        OrgNode node = createTestNode("Visit [[https://example.com][example]]");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain href=\"https://example.com\"", html.contains("href=\"https://example.com\""));
        assertTrue("Should contain link text", html.contains("example"));
    }

    @Test
    public void testBareUrl() throws Exception {
        OrgNode node = createTestNode("Visit https://example.com for more");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain href=\"https://example.com\"", html.contains("href=\"https://example.com\""));
    }

    @Test
    public void testTableRendering() throws Exception {
        OrgNode node = createTestNode("| Name | Age |\n|---+---|\n| Alice | 30 |");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <thead>", html.contains("<thead>"));
        assertTrue("Should contain <th>Name</th>", html.contains("<th>Name</th>"));
        assertTrue("Should contain <td>Alice</td>", html.contains("<td>Alice</td>"));
    }

    @Test
    public void testSrcBlockWithLanguage() throws Exception {
        OrgNode node = createTestNode("#+BEGIN_SRC python\nprint('hello')\n#+END_SRC");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <code class=\"language-python\">", html.contains("language-python"));
        assertTrue("Should contain print('hello')", html.contains("print('hello')"));
    }

    @Test
    public void testBlockQuote() throws Exception {
        OrgNode node = createTestNode("#+BEGIN_QUOTE\nSome quote\n#+END_QUOTE");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <blockquote>", html.contains("<blockquote>"));
        assertTrue("Should contain quote text", html.contains("Some quote"));
    }

    @Test
    public void testExampleBlock() throws Exception {
        OrgNode node = createTestNode("#+BEGIN_EXAMPLE\nSome example\n#+END_EXAMPLE");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <pre>", html.contains("<pre>"));
        assertTrue("Should contain example text", html.contains("Some example"));
    }

    @Test
    public void testColonExampleBlock() throws Exception {
        OrgNode node = createTestNode(": Line 1\n: Line 2\n\nNormal text");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <pre>", html.contains("<pre>"));
        assertTrue("Should contain Line 1", html.contains("Line 1"));
        assertTrue("Should contain Line 2", html.contains("Line 2"));
    }

    @Test
    public void testUnorderedList() throws Exception {
        OrgNode node = createTestNode("- item one\n- item two\n- item three");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <ul>", html.contains("<ul>"));
        assertTrue("Should contain <li>item one</li>", html.contains("<li>item one</li>"));
        assertTrue("Should contain <li>item two</li>", html.contains("<li>item two</li>"));
        assertTrue("Should contain </ul>", html.contains("</ul>"));
    }

    @Test
    public void testOrderedList() throws Exception {
        OrgNode node = createTestNode("1. first\n2. second\n3. third");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <ol>", html.contains("<ol>"));
        assertTrue("Should contain <li>first</li>", html.contains("<li>first</li>"));
        assertTrue("Should contain <li>second</li>", html.contains("<li>second</li>"));
        assertTrue("Should contain </ol>", html.contains("</ol>"));
    }

    @Test
    public void testEmptyPayload() throws Exception {
        OrgNode node = createTestNode("");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should return valid HTML structure", html.contains("<html>"));
        assertTrue("Should contain <body>", html.contains("<body>"));
    }

    @Test
    public void testMultipleInlineMarkup() throws Exception {
        OrgNode node = createTestNode("This has *bold*, /italic/, and ~code~");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain bold", html.contains("<b>bold</b>"));
        assertTrue("Should contain italic", html.contains("<i>italic</i>"));
        assertTrue("Should contain code", html.contains("<code>code</code>"));
    }

    @Test
    public void testNestedListsReset() throws Exception {
        OrgNode node = createTestNode("- unordered item 1\n- unordered item 2\n\n1. ordered item 1\n2. ordered item 2");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain <ul>", html.contains("<ul>"));
        assertTrue("Should contain </ul>", html.contains("</ul>"));
        assertTrue("Should contain <ol>", html.contains("<ol>"));
        assertTrue("Should contain </ol>", html.contains("</ol>"));
    }

    @Test
    public void testSrcBlockWithInlineMarkup() throws Exception {
        OrgNode node = createTestNode("#+BEGIN_SRC python\n# This is *not* bold\nprint('test')\n#+END_SRC");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should contain the comment", html.contains("# This is *not* bold"));
        // In source blocks, markup should NOT be processed (it's inside <pre>)
        assertTrue("Should contain print statement", html.contains("print('test')"));
    }

    @Test
    public void testHtmlEncoding() throws Exception {
        OrgNode node = createTestNode("Text with <html> & \"quotes\"");
        String html = renderer.payloadToHTML(node);

        assertTrue("Should escape <", html.contains("&lt;html&gt;"));
        assertTrue("Should escape &", html.contains("&amp;"));
        assertTrue("Should escape quotes", html.contains("&quot;"));
    }
}
