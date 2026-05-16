# OrgRenderer: Line-Level Org-mode Rendering Engine

**Date**: 2026-05-16
**Status**: Approved
**Replaces**: `OrgNode2Html` (deleted)

## Context

MobileOrg's `ViewFragment` displays org node content via WebView + HTML. The current `OrgNode2Html` uses global regex replacements to convert org markup to HTML. This approach cannot handle structural elements (code blocks, tables, blockquotes) because regex has no state awareness across lines.

The primary use case is **viewing** org content on mobile (not editing), so a read-only HTML rendering approach is sufficient.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Parser architecture | Line-level state machine | Structural parsing needed for code blocks, tables |
| Rendering target | WebView + HTML (existing) | Already in place, supports CSS/JS for highlight.js |
| Link resolution timing | Deferred to click (WebViewClient) | Decouples rendering from DB queries, consistent with existing pattern |
| Code block highlighting | highlight.js from local assets | WebView environment suits JS; offline support required |
| Existing preferences | Remove `wrapLines` and `viewApplyFormating` | No longer meaningful with proper HTML rendering |
| Migration | Replace OrgNode2Html directly | New renderer is a strict superset; no value in keeping both |
| Input payload | Raw payload (`getPayload()`), renderer cleans internally | `getCleanedPayload()` strips ALL `#+` lines including `#+BEGIN_SRC` |
| Pre-processing | Clean first (strip PROPERTIES/LOGBOOK/SCHEDULED/DEADLINE), then state machine | Separation of concerns: "what to show" vs "how to show" |
| List rendering | Flat `<ul>/<ol>/<li>`, no nested list support | First pass covers 90% of cases; nested indentation parsing deferred |
| Checkbox rendering | None — keep `[X]` `[ ]` as plain text | No misleading interactivity hints |
| PayloadFragment | Unified rendering — also uses OrgRenderer with OrgNode | Consistent view between ViewActivity and edit preview |
| Link not found | Toast "node not found" | Simple, no performance impact on rendering |

## Architecture

### Two-Phase Pipeline

```
Raw Payload → [Pre-clean] → Cleaned text → [State Machine] → HTML → WebView
                  │                                │
     Strip: PROPERTIES drawer           5 rendering states
            :LOGBOOK:...:END:           inline markup
            SCHEDULED: / DEADLINE:      link conversion
            (preserve all #+ lines)     list/table/block detection
```

### State Machine

The parser processes cleaned payload line-by-line, maintaining one of five states:

```
         ┌─────────────────────────────────────────┐
         │              NORMAL                       │
         │  (default state, inline markup applied)   │
         └──────┬──────┬──────┬──────┬──────────────┘
                │      │      │      │
      |...|     │      │      │      │  :text
      lines     │      │      │      │  lines
                ▼      │      │      ▼
           ┌────────┐  │      │  ┌──────────┐
           │ TABLE  │  │      │  │ EXAMPLE  │
           └────────┘  │      │  └──────────┘
      #+BEGIN_SRC     │      │   #+END_EXAMPLE
                       ▼      │   or non-: line
                  ┌──────────┐│
                  │SRC_BLOCK ││
                  └──────────┘│
              #+BEGIN_QUOTE  │
                       ▼     │
                  ┌──────────┐
                  │  QUOTE   │
                  └──────────┘
```

All states return to NORMAL on their exit condition.

### State Definitions

| State | Entry Condition | Exit Condition | HTML Output |
|-------|----------------|----------------|-------------|
| NORMAL | Default | Special line detected | `<p>` with inline markup, `<ul>/<ol>` for list items |
| TABLE | Consecutive `\|...\|` lines | Blank line or non-table line | `<table>` with `<thead>`/`<tbody>` from separator row |
| SRC_BLOCK | `#+BEGIN_SRC [lang]` | `#+END_SRC` | `<pre><code class="language-{lang}">` + highlight.js |
| QUOTE | `#+BEGIN_QUOTE` | `#+END_QUOTE` | `<blockquote>` |
| EXAMPLE | `#+BEGIN_EXAMPLE` or `: ` indented lines | `#+END_EXAMPLE` or non-`:` line | `<pre>` |

### Inline Markup (NORMAL state only)

Applied via regex within text lines, NOT inside SRC_BLOCK or EXAMPLE:

| Org Syntax | HTML | Status |
|------------|------|--------|
| `*bold*` | `<b>bold</b>` | Existing |
| `/italic/` | `<i>italic</i>` | Existing |
| `~code~` | `<code>code</code>` | **New** |
| `=verbatim=` | `<code>verbatim</code>` | **New** |
| `_underline_` | `<u>underline</u>` | Existing |
| `+strike+` | `<strike>strike</strike>` | Existing |

### List Rendering

Flat lists only (no nested indentation). Detection within NORMAL state:

| Pattern | HTML |
|---------|------|
| Consecutive `- ` or `+ ` lines | `<ul><li>...</li></ul>` |
| Consecutive `1.` `2.` etc. lines | `<ol><li>...</li></ol>` |

Inline markup is applied inside `<li>` content. Lists end at a blank line or non-list line.

### Link Rendering

Links are converted to `<a>` tags with custom URL schemes at render time. Resolution happens at click time in `WebViewClient`.

| Link Type | Org Syntax | HTML href | Click Handler |
|-----------|-----------|-----------|---------------|
| File | `[[file:xxx.org::*heading][desc]]` | `orgfile:xxx.org::*heading` | Find file, optionally navigate to heading |
| ID | `[[id:xxx][desc]]` | `orgid:xxx` | Query DB PAYLOAD column for `:ID: xxx` |
| Internal | `[[*heading][desc]]` | `orginternal:*heading` | Find heading in current file |
| External | `[[https://url][desc]]` | `https://url` | System browser (existing behavior) |

File link supports: file-level (`[[file:xxx.org]]`), heading (`[[file:xxx.org::*heading]]`). CUSTOM_ID deferred to future iteration. Line number targeting excluded.

**Not-found behavior**: Toast "node not found", no navigation.

### Table Rendering

- Consecutive `|...|` lines collected into a table block
- First row → `<thead>`, separator row `|---+---|` skipped, remaining → `<tbody>`
- Column widths estimated by character count
- CSS: compact borders + cell padding

### Code Block Rendering

- `#+BEGIN_SRC lang` → `<pre><code class="language-{lang}">`
- Content output verbatim (no inline markup)
- `#+END_SRC` → `</code></pre>`
- highlight.js loaded from `assets/highlight/`:
  - `highlight.min.js` (core)
  - Language packs: elisp, python, shell, java, javascript, clojure
  - Theme: `atom-one-dark.min.css`
- WebView calls `hljs.highlightAll()` after loading HTML

### HTML Template

All rendered content is wrapped in an HTML template:

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>/* CSS for tables, code blocks, blockquotes, lists, body colors */</style>
  <link rel="stylesheet" href="file:///android_asset/highlight/styles/atom-one-dark.min.css">
  <script src="file:///android_asset/highlight/highlight.min.js"></script>
</head>
<body>
  {rendered content}
  <script>hljs.highlightAll();</script>
</body>
</html>
```

Body colors from `DefaultTheme` (existing theme system).

## API

### OrgRenderer

```java
public class OrgRenderer {
    public OrgRenderer(ContentResolver resolver, Context context);

    /** Render a full node with recursive children */
    public String toHTML(OrgNode node, int levelOfRecursion);

    /** Render just the payload of a node */
    public String payloadToHTML(OrgNode node);
}
```

Note: `toHTML(String text)` is removed. Both callers (ViewActivity and PayloadFragment) have access to the OrgNode.

### ViewFragment changes

```java
// Replace OrgNode2Html with OrgRenderer
OrgRenderer renderer = new OrgRenderer(resolver, getActivity());

// Extend WebViewClient to handle custom URL schemes
@Override
public boolean shouldOverrideUrlLoading(WebView view, String url) {
    if (url starts with "orgfile:") → resolve file link
    if (url starts with "orgid:") → resolve ID link
    if (url starts with "orginternal:") → resolve heading link
    if (url starts with "http(s):") → system browser
}
```

### PayloadFragment changes

`switchToView()` currently calls `display(this.payload.getCleanedPayload())`. Changed to pass the OrgNode instead, so links work in edit preview too. The renderer receives the raw payload via `node.getPayload()`.

### OrgUtils additions

```java
/** Find node by heading name within a specific file */
public static long getNodeByHeading(String filename, String heading, ContentResolver resolver)
    throws OrgNodeNotFoundException;

/** Find node by :ID: property across all files */
public static long getNodeById(String id, ContentResolver resolver)
    throws OrgNodeNotFoundException;
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `util/OrgRenderer.java` | **New** | State machine parser + HTML generator + pre-cleaner |
| `Gui/ViewFragment.java` | **Modify** | Use OrgRenderer; extend WebViewClient for orgfile/orgid/orginternal schemes |
| `Gui/Capture/PayloadFragment.java` | **Modify** | Pass OrgNode to renderer instead of cleaned payload string |
| `util/OrgUtils.java` | **Modify** | Add `getNodeByHeading()`, `getNodeById()` |
| `assets/highlight/*` | **New** | highlight.js core + 6 language packs + atom-one-dark theme |
| `res/xml/preferences.xml` | **Modify** | Remove `viewWrapLines` and `viewApplyFormating` settings |
| `util/OrgNode2Html.java` | **Delete** | Replaced by OrgRenderer |

## Tests

New file: `OrgRendererTest` (instrumentation test using ProviderTestCase2)

Test cases:
- Pre-cleaning: PROPERTIES drawer stripped, `#+BEGIN_SRC` preserved, SCHEDULED/DEADLINE stripped
- State transitions: NORMAL→TABLE→NORMAL, NORMAL→SRC_BLOCK→NORMAL, etc.
- Inline markup: bold, italic, code, verbatim, underline, strike
- Links: file, id, internal, external URL schemes
- Lists: unordered (`-`), ordered (`1.`), mixed with paragraphs
- Tables: single-row, multi-row with separator, empty cells
- Code blocks: with/without language, multi-line content
- Edge cases: empty payload, nested markup (`*bold /italic/*`), link inside table cell
