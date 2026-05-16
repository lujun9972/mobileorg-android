# OrgRenderer 行级渲染引擎 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 用行级状态机渲染器替换 OrgNode2Html，实现 org-mode 富文本渲染（代码块高亮、表格、列表、链接导航、blockquote）

**架构：** 两阶段管线：原始 payload → 预清理（strip PROPERTIES/LOGBOOK/SCHEDULED/DEADLINE，保留 #+ 行）→ 状态机解析（NORMAL/TABLE/SRC_BLOCK/QUOTE/EXAMPLE）→ HTML → WebView。highlight.js 从本地 assets 加载，链接通过自定义 URL scheme 延迟到点击时解析。

**技术栈：** Java (Android)、WebView + HTML/CSS/JS、highlight.js (本地 assets)、ProviderTestCase2 测试

---

## 关键设计原则

- OrgRenderer 是纯字符串处理器（org text → HTML），不直接访问 DB。DB 依赖（主题、递归渲染）通过构造函数注入的 Context/ContentResolver 处理。
- 状态机在 NORMAL 状态应用 inline markup 和 link 转换；SRC_BLOCK/EXAMPLE 状态原样输出。
- 列表只做扁平 `<ul>/<ol>`，不处理缩进嵌套。
- 链接用自定义 URL scheme（`orgfile:`、`orgid:`、`orginternal:`），在 WebViewClient 中解析，找不到 Toast 提示。

## 文件职责

| 文件 | 职责 |
|------|------|
| `util/OrgRenderer.java` | 预清理 + 状态机 + HTML 生成。纯字符串处理，核心逻辑无 DB 依赖 |
| `util/OrgUtils.java` | 新增 `getNodeByHeading()` 和 `getNodeById()` — 链接点击时查 DB |
| `Gui/ViewFragment.java` | 调用 OrgRenderer + WebViewClient 处理自定义 URL scheme |
| `Gui/Capture/PayloadFragment.java` | 改为传 OrgNode 给渲染器 |
| `assets/highlight/*` | highlight.js 核心 + 语言包 + 主题 CSS |
| `res/xml/preferences.xml` | 删除 `viewWrapLines` 和 `viewApplyFormatting` |
| `util/OrgNode2Html.java` | 删除 |

---

### 任务 1：下载并打包 highlight.js 资源

**依赖：** 无
**文件集：** `MobileOrg/src/main/assets/highlight/highlight.pack.js`, `MobileOrg/src/main/assets/highlight/styles/atom-one-dark.css`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 创建：`MobileOrg/src/main/assets/highlight/highlight.pack.js`
- 创建：`MobileOrg/src/main/assets/highlight/styles/atom-one-dark.css`

- [ ] **步骤 1：创建 assets 目录**

```bash
mkdir -p MobileOrg/src/main/assets/highlight/styles
```

- [ ] **步骤 2：下载 highlight.js 定制包**

从 https://highlightjs.org/download/ 下载包含 elisp、python、shell、java、javascript、clojure 的定制包。或使用 CDN 获取预打包版本：

```bash
# 下载核心 + 目标语言的打包版本
curl -L "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js" -o MobileOrg/src/main/assets/highlight/highlight.pack.js

# 下载 atom-one-dark 主题
curl -L "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css" -o MobileOrg/src/main/assets/highlight/styles/atom-one-dark.css
```

注意：默认 CDN 包已包含常用语言。验证是否包含 elisp 和 clojure——如果不包含，需要单独下载语言包并合并。elisp 在 highlight.js 核心，clojure 可能需要额外注册。

- [ ] **步骤 3：验证文件大小**

highlight.pack.js 应 < 200KB，atom-one-dark.css 应 < 5KB。

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/assets/highlight/
git commit -m "feat: add highlight.js assets for code block rendering"
```

---

### 任务 2：实现 OrgRenderer 核心状态机

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgRenderer.java`
**导出/变更接口：** `OrgRenderer.java::OrgRenderer`, `OrgRenderer.java::preClean`, `OrgRenderer.java::render`, `OrgRenderer.java::toHTML`, `OrgRenderer.java::payloadToHTML`
**消费接口：** `OrgNode.java::getPayload`, `OrgNode.java::getCleanedPayload`, `OrgNode.java::getChildren`, `OrgNode.java::getFilename`, `DefaultTheme.java::getTheme`
**复杂度：** deep

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgRenderer.java`

OrgRenderer 是整个功能的主体。内部结构：

```
OrgRenderer
├── constructor(ContentResolver, Context) — 读主题颜色
├── preClean(String rawPayload) → String — strip PROPERTIES/LOGBOOK/SCHEDULED/DEADLINE
├── render(String cleanedPayload) → String — 状态机，返回 HTML body 内容
│   ├── parseNormal(List<String> lines) → String — 处理段落、列表
│   ├── parseTable(List<String> lines) → String — HTML table
│   ├── parseSrcBlock(List<String> lines, String lang) → String — <pre><code>
│   ├── parseQuote(List<String> lines) → String — <blockquote>
│   ├── parseExample(List<String> lines) → String — <pre>
│   ├── applyInlineMarkup(String text) → String — bold/italic/code/verbatim/underline/strike
│   └── convertLinks(String text) → String — [[link][desc]] → <a href="scheme:...">
├── wrapInTemplate(String htmlBody) → String — 完整 HTML 文档
├── toHTML(OrgNode, int levelOfRecursion) → String — 递归渲染节点
└── payloadToHTML(OrgNode) → String — 只渲染 payload
```

- [ ] **步骤 1：创建 OrgRenderer.java 骨架**

```java
package com.matburt.mobileorg.util;

import android.content.ContentResolver;
import android.content.Context;
import com.matburt.mobileorg.Gui.Theme.DefaultTheme;
import com.matburt.mobileorg.OrgData.OrgNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrgRenderer {
    private ContentResolver resolver;
    private String fontColor;
    private String bgColor;

    public OrgRenderer(ContentResolver resolver, Context context) {
        this.resolver = resolver;
        DefaultTheme theme = DefaultTheme.getTheme(context);
        this.fontColor = theme.defaultFontColor;
        // 将 int color 转为 #rrggbb 字符串
    }
    // ... 后续方法
}
```

- [ ] **步骤 2：实现 preClean()**

逻辑：
1. 逐行扫描原始 payload
2. 遇到 `:PROPERTIES:` 行 → 跳过直到 `:END:`
3. 遇到 `:LOGBOOK:` 行 → 跳过直到 `:END:`
4. 遇到 `SCHEDULED:` 或 `DEADLINE:` 行 → 跳过
5. 保留所有 `#+` 开头的行（`#+BEGIN_SRC` 等）
6. 保留所有其他行

```java
String preClean(String rawPayload) {
    String[] lines = rawPayload.split("\n");
    StringBuilder result = new StringBuilder();
    boolean skipping = false;
    for (String line : lines) {
        String trimmed = line.trim();
        if (skipping) {
            if (trimmed.equals(":END:")) skipping = false;
            continue;
        }
        if (trimmed.equals(":PROPERTIES:") || trimmed.equals(":LOGBOOK:")) {
            skipping = true;
            continue;
        }
        if (trimmed.startsWith("SCHEDULED:") || trimmed.startsWith("DEADLINE:")
                || trimmed.startsWith("CLOSED:")) {
            continue;
        }
        result.append(line).append("\n");
    }
    return result.toString().trim();
}
```

- [ ] **步骤 3：实现 applyInlineMarkup()**

沿用 OrgNode2Html 的 `getFormatingRegex()` 模式，新增 `~code~` 和 `=verbatim=`：

```java
String applyInlineMarkup(String text) {
    text = markupRegex("*", "b", text);
    text = markupRegex("/", "i", text);
    text = markupRegex("_", "u", text);
    text = markupRegex("+", "strike", text);
    text = markupRegex("~", "code", text);   // new
    text = markupRegex("=", "code", text);    // new
    return text;
}

private String markupRegex(String ch, String tag, String text) {
    return text.replaceAll(
        "(^|\\s)\\" + ch + "(\\S[\\S\\s]*?\\S)\\" + ch + "(\\s|$)",
        "$1<" + tag + ">$2</" + tag + ">$3");
}
```

- [ ] **步骤 4：实现 convertLinks()**

```java
String convertLinks(String text) {
    // [[link][desc]] 格式
    Pattern linkPattern = Pattern.compile("\\[\\[([^\\]]*)\\]\\[([^\\]]*)\\]\\]");
    Matcher m = linkPattern.matcher(text);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
        String target = m.group(1);
        String desc = m.group(2);
        String href = resolveLinkTarget(target);
        m.appendReplacement(sb, "<a href=\"" + href + "\">" + desc + "</a>");
    }
    m.appendTail(sb);
    // 裸 URL
    return sb.toString().replaceAll("(?<!href=\")(https?://\\S+)",
        "<a href=\"$1\">$1</a>");
}

private String resolveLinkTarget(String target) {
    if (target.startsWith("file:")) return "orgfile:" + target.substring(5);
    if (target.startsWith("id:")) return "orgid:" + target.substring(3);
    if (target.startsWith("*")) return "orginternal:" + target;
    return target; // http(s) etc.
}
```

- [ ] **步骤 5：实现状态机 render()**

逐行扫描 cleaned payload，根据行特征进入不同状态，收集行后一次性输出 HTML。核心循环：

```java
String render(String cleanedPayload) {
    String[] lines = cleanedPayload.split("\n");
    StringBuilder html = new StringBuilder();
    int i = 0;
    while (i < lines.length) {
        String line = lines[i];
        if (line.trim().startsWith("#+BEGIN_SRC")) {
            // 收集直到 #+END_SRC
            i = collectAndRenderSrcBlock(lines, i, html);
        } else if (line.trim().startsWith("#+BEGIN_QUOTE")) {
            i = collectAndRenderQuote(lines, i, html);
        } else if (line.trim().startsWith("#+BEGIN_EXAMPLE")) {
            i = collectAndRenderExample(lines, i, html);
        } else if (isTableLine(line)) {
            i = collectAndRenderTable(lines, i, html);
        } else {
            i = renderNormalLines(lines, i, html);
        }
    }
    return html.toString();
}
```

每个 `collectAndRender*` 方法从当前行开始收集属于同一块的行，返回下一个未处理行索引。

`renderNormalLines()` 处理 NORMAL 状态——逐行累积，检测列表（连续 `- ` 或 `1.` 行包裹 `<ul>/<ol>/<li>`），空行分段落 `<p>`，对文本行应用 `applyInlineMarkup()` 和 `convertLinks()`。

`isTableLine()` 检测 `|...|` 模式。

`collectAndRenderTable()`：第一行 → `<thead><tr><th>`，`|---+---|` 分隔行跳过，其余 → `<tbody><tr><td>`。

`collectAndRenderSrcBlock()`：提取语言名（从 `#+BEGIN_SRC python` 中解析 `python`），内容原样输出到 `<pre><code class="language-python">`。

`collectAndRenderQuote()`：内容包裹在 `<blockquote>` 中，内部文本应用 inline markup。

`collectAndRenderExample()`：内容原样输出到 `<pre>`。

`: ` 缩进行处理：在 NORMAL 状态检测连续 `: ` 开头行，包裹为 `<pre>`。

- [ ] **步骤 6：实现 wrapInTemplate()**

```java
String wrapInTemplate(String htmlBody) {
    return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
        + "<link rel=\"stylesheet\" href=\"file:///android_asset/highlight/styles/atom-one-dark.css\">"
        + "<script src=\"file:///android_asset/highlight/highlight.pack.js\"></script>"
        + "<style>"
        + "body { color: #" + colorToHex(fontColor) + "; margin: 8px; }"
        + "table { border-collapse: collapse; margin: 8px 0; }"
        + "th, td { border: 1px solid #444; padding: 4px 8px; text-align: left; }"
        + "th { font-weight: bold; }"
        + "pre.src-block { background: #282c34; padding: 12px; border-radius: 4px; overflow-x: auto; }"
        + "blockquote { border-left: 3px solid #666; margin: 8px 0; padding: 4px 12px; }"
        + "ul, ol { padding-left: 20px; }"
        + "li { margin: 2px 0; }"
        + "code { background: #333; padding: 1px 4px; border-radius: 2px; }"
        + "</style></head><body>"
        + htmlBody
        + "<script>hljs.highlightAll();</script>"
        + "</body></html>";
}
```

- [ ] **步骤 7：实现 toHTML() 和 payloadToHTML()**

```java
public String toHTML(OrgNode node, int levelOfRecursion) {
    String html = nodeToHTMLRecursive(node, levelOfRecursion);
    return wrapInTemplate(html);
}

public String payloadToHTML(OrgNode node) {
    String cleaned = preClean(node.getPayload());
    String html = render(cleaned);
    return wrapInTemplate(html);
}

private String nodeToHTMLRecursive(OrgNode node, int level) {
    StringBuilder result = new StringBuilder();
    int fontSize = 3 + level;
    result.append("<font size=\"").append(fontSize).append("\"><b>")
          .append(escapeHtml(node.name)).append("</b></font><hr/>");
    String cleaned = preClean(node.getPayload());
    if (!cleaned.isEmpty()) {
        result.append(render(cleaned));
    }
    result.append("<br/>");
    if (level > 0) {
        for (OrgNode child : node.getChildren(resolver)) {
            result.append(nodeToHTMLRecursive(child, level - 1));
        }
    }
    return result.toString();
}
```

注意：`node.name` 需要 HTML 转义（`escapeHtml`），避免名称中的 `<>&` 破坏 HTML 结构。

- [ ] **步骤 8：编译验证**

```bash
./gradlew compileDebugJavaWithJavac
```

预期：编译通过（OrgRenderer 无外部调用者，不影响现有代码）

- [ ] **步骤 9：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgRenderer.java
git commit -m "feat: add OrgRenderer line-level state machine renderer"
```

---

### 任务 3：实现 OrgUtils 链接解析辅助方法

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`
**导出/变更接口：** `OrgUtils.java::getNodeByHeading`, `OrgUtils.java::getNodeById`
**消费接口：** `OrgFile.java::OrgFile(String, ContentResolver)`, `OrgData.java::CONTENT_URI`, `OrgData.java::DEFAULT_COLUMNS`, `OrgData.java::NAME`, `OrgData.java::FILE_ID`, `OrgData.java::PAYLOAD`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`

- [ ] **步骤 1：实现 getNodeByHeading()**

在文件内按 name 查找 heading 节点。从现有 `getNodeFromPath()`（line 88）附近添加：

```java
public static long getNodeByHeading(String filename, String heading, ContentResolver resolver)
        throws OrgNodeNotFoundException {
    OrgFile file = new OrgFile(filename, resolver);
    // 在该文件的所有节点中查找 name 匹配的
    Cursor cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS,
            OrgData.FILE_ID + "=? AND " + OrgData.NAME + "=?",
            new String[] { String.valueOf(file.nodeId), heading }, null);
    if (cursor != null && cursor.moveToFirst()) {
        OrgNode node = new OrgNode(cursor);
        cursor.close();
        return node.id;
    }
    if (cursor != null) cursor.close();
    throw new OrgNodeNotFoundException("Heading \"" + heading + "\" not found in " + filename);
}
```

- [ ] **步骤 2：实现 getNodeById()**

在所有节点的 payload 中搜索 `:ID: xxx` 模式：

```java
public static long getNodeById(String id, ContentResolver resolver)
        throws OrgNodeNotFoundException {
    Cursor cursor = resolver.query(OrgData.CONTENT_URI, OrgData.DEFAULT_COLUMNS,
            OrgData.PAYLOAD + " LIKE ?",
            new String[] { "%:ID:%" + id + "%" }, null);
    if (cursor != null && cursor.moveToFirst()) {
        OrgNode node = new OrgNode(cursor);
        cursor.close();
        return node.id;
    }
    if (cursor != null) cursor.close();
    throw new OrgNodeNotFoundException("Node with ID \"" + id + "\" not found");
}
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileDebugJavaWithJavac
```

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java
git commit -m "feat: add getNodeByHeading and getNodeById for link navigation"
```

---

### 任务 4：更新 ViewFragment 集成 OrgRenderer

**依赖：** 任务 2, 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java`
**导出/变更接口：** `ViewFragment.java::display(OrgNode, int, ContentResolver)`, `ViewFragment.java::displayPayload(OrgNode)`, `ViewFragment.java::display(String)` (removed)
**消费接口：** `OrgRenderer.java::OrgRenderer`, `OrgRenderer.java::toHTML`, `OrgRenderer.java::payloadToHTML`, `OrgUtils.java::getNodeByHeading`, `OrgUtils.java::getNodeById`, `OrgUtils.java::getNodeFromPath`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java`

- [ ] **步骤 1：替换 OrgNode2Html 为 OrgRenderer**

在 `display()` 和 `displayPayload()` 方法中：

```java
// 旧代码
OrgNode2Html htmlNode = new OrgNode2Html(resolver, getActivity());
htmlNode.wrapLines = true;
String html = htmlNode.toHTML(payload);

// 新代码
OrgRenderer renderer = new OrgRenderer(resolver, getActivity());
String html = renderer.toHTML(payload);
```

三个 display 方法更新为：
- `display(String payload)` → 删除（不再需要，PayloadFragment 将改为调用 payloadToHTML(OrgNode)）
- `displayPayload(OrgNode node)` → 调用 `renderer.payloadToHTML(node)`
- `display(OrgNode node, int levelOfRecursion, ContentResolver resolver)` → 调用 `renderer.toHTML(node, levelOfRecursion)`

- [ ] **步骤 2：扩展 WebViewClient 处理自定义 URL scheme**

重写 `InternalWebViewClient.shouldOverrideUrlLoading()`：

```java
@Override
public boolean shouldOverrideUrlLoading(WebView view, String url) {
    if (url.startsWith("orgfile:")) {
        handleFileLink(url.substring("orgfile:".length()));
        return true;
    }
    if (url.startsWith("orgid:")) {
        handleIdLink(url.substring("orgid:".length()));
        return true;
    }
    if (url.startsWith("orginternal:")) {
        handleInternalLink(url.substring("orginternal:".length()));
        return true;
    }
    // 原有的 file:// 处理（向后兼容）
    try {
        URL urlObj = new URL(url);
        if (urlObj.getProtocol().equals("file")) {
            handleInternalOrgUrl(url);
            return true;
        }
    } catch (MalformedURLException e) {}
    // 外部链接
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    try { startActivity(intent); } catch (ActivityNotFoundException e) {}
    return true;
}
```

- [ ] **步骤 3：实现链接处理方法**

```java
private void handleFileLink(String target) {
    try {
        long nodeId;
        int headingIdx = target.indexOf("::*");
        if (headingIdx > -1) {
            String filename = target.substring(0, headingIdx);
            String heading = target.substring(headingIdx + 2); // skip "::*"
            nodeId = OrgUtils.getNodeByHeading(filename, heading, resolver);
        } else {
            nodeId = OrgUtils.getNodeFromPath("file://" + target, resolver);
        }
        Intent intent = new Intent(getActivity(), ViewActivity.class);
        intent.putExtra(ViewActivity.NODE_ID, nodeId);
        startActivity(intent);
    } catch (OrgNodeNotFoundException e) {
        Toast.makeText(getActivity(), R.string.node_not_found, Toast.LENGTH_SHORT).show();
    }
}

private void handleIdLink(String id) {
    try {
        long nodeId = OrgUtils.getNodeById(id, resolver);
        Intent intent = new Intent(getActivity(), ViewActivity.class);
        intent.putExtra(ViewActivity.NODE_ID, nodeId);
        startActivity(intent);
    } catch (OrgNodeNotFoundException e) {
        Toast.makeText(getActivity(), R.string.node_not_found, Toast.LENGTH_SHORT).show();
    }
}
```

`handleInternalLink()` 需要 ViewFragment 知道当前文件名。添加一个 `currentFilename` 字段，在 `display(OrgNode)` 时从 `node.getFilename(resolver)` 获取。

- [ ] **步骤 4：添加字符串资源**

在 `res/values/strings.xml` 中添加：

```xml
<string name="node_not_found">节点未找到</string>
```

- [ ] **步骤 5：编译验证**

```bash
./gradlew compileDebugJavaWithJavac
```

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java MobileOrg/src/main/res/values/strings.xml
git commit -m "feat: integrate OrgRenderer into ViewFragment with link navigation"
```

---

### 任务 5：更新 PayloadFragment 统一渲染

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/PayloadFragment.java`
**导出/变更接口：** `PayloadFragment.java::switchToView`
**消费接口：** `OrgRenderer.java::payloadToHTML`, `OrgNode.java::OrgNode`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/PayloadFragment.java`

- [ ] **步骤 1：修改 switchToView()**

当前代码（line 156-164）：
```java
public void switchToView() {
    payloadEdit.setVisibility(View.GONE);
    cancelButton.setVisibility(View.GONE);
    saveButton.setVisibility(View.GONE);
    display(this.payload.getCleanedPayload());  // ← 旧：传字符串
    webView.setVisibility(View.VISIBLE);
    editButton.setVisibility(View.VISIBLE);
}
```

改为通过 `EditHost.getController()` 获取 OrgNode，调用新渲染器：

```java
public void switchToView() {
    payloadEdit.setVisibility(View.GONE);
    cancelButton.setVisibility(View.GONE);
    saveButton.setVisibility(View.GONE);

    EditHost editActivity = (EditHost) getActivity();
    OrgNode node = editActivity.getController().getOrgNode();
    OrgRenderer renderer = new OrgRenderer(resolver, getActivity());
    String html = renderer.payloadToHTML(node);
    displayHtml(html);

    webView.setVisibility(View.VISIBLE);
    editButton.setVisibility(View.VISIBLE);
}
```

注意：需要确认 `getController().getOrgNode()` 返回的是当前编辑中的节点。查看 `EditActivityController` 的 API，可能需要用不同的方法获取。如果 controller 没有直接的 `getOrgNode()`，可以从 controller 获取 node ID 再查询。

- [ ] **步骤 2：检查 EditActivityController 的 OrgNode 访问方式**

读取 `EditActivityController.java` 找到获取当前 OrgNode 的方法。可能的路径：
- `controller.getOrgNode()`
- `controller.getNodeId()` → 然后 `new OrgNode(id, resolver)`

- [ ] **步骤 3：编译验证 + Commit**

```bash
./gradlew compileDebugJavaWithJavac
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/PayloadFragment.java
git commit -m "feat: PayloadFragment uses OrgRenderer for unified preview"
```

---

### 任务 6：清理旧代码和偏好设置

**依赖：** 任务 4, 任务 5
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgNode2Html.java`, `MobileOrg/src/main/res/xml/preferences.xml`
**导出/变更接口：** `OrgNode2Html.java` (deleted)
**消费接口：** 无
**复杂度：** quick

**文件：**
- 删除：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgNode2Html.java`
- 修改：`MobileOrg/src/main/res/xml/preferences.xml`

- [ ] **步骤 1：删除 OrgNode2Html.java**

```bash
git rm MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgNode2Html.java
```

- [ ] **步骤 2：删除偏好设置项**

在 `preferences.xml` 中删除这两个 `CheckBoxPreference`（约 line 70-80 区域）：
- `android:key="viewWrapLines"` 
- `android:key="viewApplyFormatting"`

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileDebugJavaWithJavac
```

确认无编译错误（OrgNode2Html 已无引用，ViewFragment 和 PayloadFragment 都已迁移到 OrgRenderer）。

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/res/xml/preferences.xml
git commit -m "chore: remove OrgNode2Html and deprecated view preferences"
```

---

### 任务 7：编写 OrgRendererTest 测试套件

**依赖：** 任务 2
**文件集：** `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgRendererTest.java`
**导出/变更接口：** 无
**消费接口：** `OrgRenderer.java::OrgRenderer`, `OrgRenderer.java::preClean`, `OrgRenderer.java::render`, `OrgRenderer.java::applyInlineMarkup`, `OrgRenderer.java::convertLinks`
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgRendererTest.java`

**注意**：OrgRenderer 的核心渲染逻辑（preClean、applyInlineMarkup、convertLinks、render）是纯字符串处理。为方便测试，将这些方法设为 package-private（无修饰符）而非 private，这样同包的测试类可以直接调用。

对于需要 ContentResolver 和 Context 的集成测试（toHTML with recursion），使用 ProviderTestCase2 + MockContentResolver（参考现有 SynchronizerTest 的 setup 模式）。

- [ ] **步骤 1：创建测试文件骨架**

```java
package com.matburt.mobileorg.test.util;

import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;
import com.matburt.mobileorg.OrgData.OrgDatabase;
import com.matburt.mobileorg.OrgData.OrgProvider;
import com.matburt.mobileorg.util.OrgRenderer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OrgRendererTest extends ProviderTestCase2<OrgProvider> {
    private OrgRenderer renderer;
    // ...
}
```

- [ ] **步骤 2：编写 preClean 测试**

```java
@Test public void preClean_stripsPropertiesDrawer()
@Test public void preClean_stripsLogbook()
@Test public void preClean_stripsScheduledAndDeadline()
@Test public void preClean_preservesBeginSrc()
@Test public void preClean_preservesRegularText()
```

- [ ] **步骤 3：编写 inline markup 测试**

```java
@Test public void markup_bold()
@Test public void markup_italic()
@Test public void markup_code_tilde()       // ~code~
@Test public void markup_verbatim_equals()  // =verbatim=
@Test public void markup_underline()
@Test public void markup_strike()
@Test public void markup_nested()
```

- [ ] **步骤 4：编写链接转换测试**

```java
@Test public void link_fileWithHeading()
@Test public void link_id()
@Test public void link_internalHeading()
@Test public void link_externalUrl()
@Test public void link_bareUrl()
```

- [ ] **步骤 5：编写结构化渲染测试**

```java
@Test public void render_tableWithSeparator()
@Test public void render_srcBlockWithLanguage()
@Test public void render_srcBlockNoLanguage()
@Test public void render_blockQuote()
@Test public void render_exampleBlock()
@Test public void render_unorderedList()
@Test public void render_orderedList()
@Test public void render_mixedContent()
```

- [ ] **步骤 6：编译 + 运行测试**

```bash
./gradlew compileDebugAndroidTestJavaWithJavac
```

（实际运行需要 emulator，CI 执行）

- [ ] **步骤 7：Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/util/OrgRendererTest.java
git commit -m "test: add OrgRenderer test suite for state machine and markup"
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 4 → 任务 6

- Wave 1（无依赖）：任务 1, 任务 2, 任务 3
- Wave 2（依赖 Wave 1）：任务 4（依赖 2, 3）, 任务 5（依赖 2）, 任务 7（依赖 2）
- Wave 3（依赖 Wave 2）：任务 6（依赖 4, 5）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
