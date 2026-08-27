# 帮助内容扩充（工作原理/搜索/服务端配置）实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 帮助中心从 7 主题扩到 9：新增「工作原理」与「搜索」主题，sync.html 补「服务端准备」节，quick-start 补链接。

**架构：** 纯内容扩充为主——4 个新 HTML（zh/en 成对）+ 2 个现有 HTML 扩充 + `HelpTopic.TOPICS` 注册与 strings 资源 + 既有 `HelpTopicTest` 数量断言更新。不改任何功能代码。

**技术栈：** 静态 HTML（WebView 渲染，现有 help.css），Android strings 资源，instrumentation 测试。

**规格：** `docs/superpowers/specs/2026-08-28-help-content-design.md`（内容大纲与验收标准的唯一依据）

**编码约束（所有 HTML 任务适用）：**

- 新文件模板（zh 版；en 版 `lang="en"`，标题正文英文）：

```html
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>【主题标题】</title>
<link rel="stylesheet" href="../help.css">
</head>
<body>
<h1>【主题标题】</h1>
<!-- 各节 h2/p/ul，文件名与代码用 <code> -->
</body>
</html>
```

- 主题内互链用相对文件名（如 `<a href="how-it-works.html">`），共享资源用 `../` 上跳（`../help.css`）。不新增截图。
- 受众基调：Org-mode 老手，不解释 Org 概念本身（DEADLINE/capture 等直接使用）。
- en 版与 zh 版章节严格同构，术语对照：工作原理=How It Works，服务端准备=Preparing the Server Side，本地副本=local copy，编辑回传=uploading edits，搜索=Search。

---

### 任务 1：新增「工作原理」主题（how-it-works.html，zh/en）

**依赖：** 无
**文件集：** `MobileOrg/src/main/assets/help/zh/how-it-works.html`, `MobileOrg/src/main/assets/help/en/how-it-works.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 创建：`MobileOrg/src/main/assets/help/zh/how-it-works.html`
- 创建：`MobileOrg/src/main/assets/help/en/how-it-works.html`

内容章节（zh 版要点，en 同构）：

1. `<h2>本地副本模型</h2>` — 同步时从服务器拉取 org 文件解析进本地数据库；浏览与编辑完全离线进行，编辑不实时回传，在下次同步时上传。
2. `<h2>远端三个约定文件</h2>` —
   - `index.org`：文件清单与全局配置。含最小示例代码块：

     ```org
     #+TODO: TODO DOING | DONE CANCELED
     #+TAGS: work home learning
     * 文件清单
     [[file:home.org][Home]]
     [[file:work.org][Work]]
     ```

     说明：`[file:文件名][别名]` 链接列出参与同步的文件；`#+TODO:` 行（`|` 前是未完成态、后是完成态）、`#+TAGS:` 行定义 app 内可用的关键字与标签。
   - `checksums.dat`：每行 `<校验和>␣␣<文件名>`（两个空格分隔，校验和在前），app 据此判断哪些文件需要重新下载。
   - `mobileorg.org`：本地修改的回传通道（见下节）。
3. `<h2>编辑如何回传</h2>` — 本地编辑**不直接修改**远端 org 文件。同步时，所有待上传的编辑与 capture 被序列化为 org 条目**追加**到远端 `mobileorg.org`（别名 "Captures"）末尾并上传，随后本地队列清空。在 Emacs 端执行 `org-mobile-pull` 消化这些修改并合入原文件。
4. `<h2>同步触发时机</h2>` — ul 列表：主界面手动同步按钮；设置的自动同步间隔；设备开机。
5. `<h2>远端删除清理</h2>` — 从 index.org 移除的文件在下次同步后从本地删除；`mobileorg.org`（Captures）不受影响。
6. 文末加 `<p>` 引导：配置服务端见 `<a href="sync.html">同步配置</a>`。

- [ ] **步骤 1：创建 zh 版 how-it-works.html**（按上述章节与模板）

- [ ] **步骤 2：创建 en 版 how-it-works.html**（同构翻译，`lang="en"`，标题 "How It Works"）

- [ ] **步骤 3：验证结构并 commit**

验证：两文件均以 `<!DOCTYPE html>` 开头、`</html>` 结尾，`<h2>` 数量一致（5）。

```bash
grep -c '<h2>' MobileOrg/src/main/assets/help/zh/how-it-works.html MobileOrg/src/main/assets/help/en/how-it-works.html
# 预期：两文件均输出 5
git add MobileOrg/src/main/assets/help/zh/how-it-works.html MobileOrg/src/main/assets/help/en/how-it-works.html
git commit -m "feat(help): 新增「工作原理」主题（本地副本模型/远端文件协议/编辑回传）"
```

### 任务 2：新增「搜索」主题（search.html，zh/en）

**依赖：** 无
**文件集：** `MobileOrg/src/main/assets/help/zh/search.html`, `MobileOrg/src/main/assets/help/en/search.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 创建：`MobileOrg/src/main/assets/help/zh/search.html`
- 创建：`MobileOrg/src/main/assets/help/en/search.html`

内容章节（zh 版要点，en 同构）——**如实描述现状，不虚构不存在的语法**：

1. `<h2>入口</h2>` — 主菜单的放大镜图标进入搜索，输入关键词即查。
2. `<h2>匹配规则</h2>` — ul 列表：
   - 只搜索节点**标题**（heading 文本）；正文（payload）、标签、文件名均不参与
   - 子串匹配：标题任意位置包含关键词即命中（如输入 `会议` 可命中 `周会会议纪要`）
   - 英文关键词大小写不敏感（`todo` 与 `TODO` 等价）
   - 没有运算符、正则或前缀语法——输入什么就按字面子串匹配
3. `<h2>结果交互</h2>` — 结果列表点击进入对应节点；无结果时标题栏显示无结果提示。
4. `<h2>相关功能</h2>` — 按标签筛选用主界面标签过滤（支持 AND/OR）；按日期查看事项用 Agenda。
5. 文末加 `<p>` 引导：节点浏览见 `<a href="outline.html">Outline 基本操作</a>`。

- [ ] **步骤 1：创建 zh 版 search.html**（按上述章节与模板）

- [ ] **步骤 2：创建 en 版 search.html**（同构翻译，标题 "Search"）

- [ ] **步骤 3：验证结构并 commit**

```bash
grep -c '<h2>' MobileOrg/src/main/assets/help/zh/search.html MobileOrg/src/main/assets/help/en/search.html
# 预期：两文件均输出 4
git add MobileOrg/src/main/assets/help/zh/search.html MobileOrg/src/main/assets/help/en/search.html
git commit -m "feat(help): 新增「搜索」主题（如实描述标题子串匹配规则）"
```

### 任务 3：sync.html 扩充「服务端准备」节并衔接工作原理篇（zh/en）

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/assets/help/zh/sync.html`, `MobileOrg/src/main/assets/help/en/sync.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/assets/help/zh/sync.html`（在「同步方式」节 `<h2>同步方式</h2>` 与 `<h2>配置向导</h2>` 之间插入新节）
- 修改：`MobileOrg/src/main/assets/help/en/sync.html`（同位置：`<h2>Sync Methods</h2>` 与 `<h2>Setup Wizard</h2>` 之间）

修改内容（zh 版要点，en 同构）：

1. 插入 `<h2>服务端准备</h2>` 节：
   - `<p>` 服务端只需一个可读写的目录，内含：`index.org`（必需）、参与同步的 org 文件、`checksums.dat`（增量比对用）。
   - `<p><strong>Emacs 工作流（标准）</strong>`：将 `org-mobile-directory` 指向该目录，执行 `org-mobile-push` 自动生成 index.org 与 checksums.dat。
   - `<p><strong>手写最小 index.org</strong>`：无 Emacs 时手写即可，给出示例代码块（与工作原理篇相同的四行示例）：

     ```org
     #+TODO: TODO DOING | DONE CANCELED
     * 文件清单
     [[file:home.org][Home]]
     [[file:work.org][Work]]
     ```

   - `<ul>` 三种方式的服务端要点：WebDAV——任何支持读写（GET/PUT）的 WebDAV 服务（Nextcloud/ownCloud 或自建）；SSH——服务器开启 SFTP 可读写即可（密码或密钥认证）；SD 卡——本地目录即可（其他 app 或文件管理器放入文件）。
2. 在既有 `<h2>同步的工作原理</h2>` 节末尾（`</ol>` 之后）追加一句：完整机制与文件格式详见 `<a href="how-it-works.html">工作原理</a>`。
3. en 版对应位置同构修改：`<h2>Preparing the Server Side</h2>`，内链文字 "How It Works"。

- [ ] **步骤 1：修改 zh 版 sync.html**（插入服务端准备节 + 工作原理节补内链）

- [ ] **步骤 2：修改 en 版 sync.html**（同构）

- [ ] **步骤 3：验证结构并 commit**

```bash
grep -c '<h2>' MobileOrg/src/main/assets/help/zh/sync.html MobileOrg/src/main/assets/help/en/sync.html
# 预期：两文件均输出 6（原 5 节 + 新增 1 节）
git add MobileOrg/src/main/assets/help/zh/sync.html MobileOrg/src/main/assets/help/en/sync.html
git commit -m "feat(help): sync 主题补「服务端准备」节并内链工作原理篇"
```

### 任务 4：quick-start.html「接下来」列表补 2 条链接（zh/en）

**依赖：** 任务 1, 任务 2
**文件集：** `MobileOrg/src/main/assets/help/zh/quick-start.html`, `MobileOrg/src/main/assets/help/en/quick-start.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/assets/help/zh/quick-start.html`（「接下来」`<h2>` 下的 `<ul>`）
- 修改：`MobileOrg/src/main/assets/help/en/quick-start.html`（"Next Steps" 的 `<ul>`）

修改内容：在列表的「同步设置」条目之后插入两条：

```html
<li><a href="how-it-works.html">工作原理</a> — 本地副本、index.org 协议、编辑如何回传</li>
<li><a href="search.html">搜索</a> — 标题子串匹配规则与相关功能</li>
```

en 版：

```html
<li><a href="how-it-works.html">How It Works</a> — local copy model, the index.org protocol, how edits travel back</li>
<li><a href="search.html">Search</a> — title substring matching and related features</li>
```

- [ ] **步骤 1：修改 zh/en 两版 quick-start.html 并 commit**

```bash
grep -c '<li><a href' MobileOrg/src/main/assets/help/zh/quick-start.html MobileOrg/src/main/assets/help/en/quick-start.html
# 预期：两文件均输出 8（原 6 条 + 新 2 条）
git add MobileOrg/src/main/assets/help/zh/quick-start.html MobileOrg/src/main/assets/help/en/quick-start.html
git commit -m "feat(help): 快速上手「接下来」补工作原理与搜索链接"
```

### 任务 5：注册新主题到 TOPICS + strings 资源 + 更新数量断言（TDD）

**依赖：** 任务 1, 任务 2
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java`, `MobileOrg/src/main/res/values/strings.xml`, `MobileOrg/src/main/res/values-zh/strings.xml`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpTopicTest.java`
**导出/变更接口：** `HelpTopic.java::TOPICS`
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpTopicTest.java`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java`
- 修改：`MobileOrg/src/main/res/values/strings.xml`（381-387 行 help_topic_* 资源块）
- 修改：`MobileOrg/src/main/res/values-zh/strings.xml`（165-171 行 help_topic_* 资源块）

- [ ] **步骤 1：更新测试断言（RED）**

`HelpTopicTest.java` 两处 `assertEquals(7, ...)` 改为 `assertEquals(9, ...)`：

```java
@Test
public void topicsCoverSevenDocuments() {
    assertEquals(9, HelpTopic.TOPICS.length);
}

@Test
public void fileNamesAreUnique() {
    assertEquals(9, java.util.Arrays.stream(HelpTopic.TOPICS)
            .map(t -> t.fileName).distinct().count());
}
```

- [ ] **步骤 2：运行测试验证失败**

前提：`adb devices` 有设备（无线调试需先 `adb connect <ip:port>`）。

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.Gui.HelpTopicTest
```

预期：FAILED，`topicsCoverSevenDocuments` 报 expected:9 actual:7。

- [ ] **步骤 3：注册 TOPICS 与字符串资源（GREEN）**

`values/strings.xml` help_topic 块内追加（英文默认）：

```xml
<string name="help_topic_how_it_works">How It Works</string>
<string name="help_topic_search">Search</string>
```

`values-zh/strings.xml` 对应追加：

```xml
<string name="help_topic_how_it_works">工作原理</string>
<string name="help_topic_search">搜索</string>
```

`HelpTopic.java` 的 `TOPICS` 数组改为（how-it-works 在 sync 前，search 在 outline 后）：

```java
public static final HelpTopic[] TOPICS = {
        new HelpTopic(R.string.help_topic_quick_start, "quick-start.html"),
        new HelpTopic(R.string.help_topic_how_it_works, "how-it-works.html"),
        new HelpTopic(R.string.help_topic_sync, "sync.html"),
        new HelpTopic(R.string.help_topic_outline, "outline.html"),
        new HelpTopic(R.string.help_topic_search, "search.html"),
        new HelpTopic(R.string.help_topic_pomodoro, "pomodoro.html"),
        new HelpTopic(R.string.help_topic_statistics, "statistics.html"),
        new HelpTopic(R.string.help_topic_reminders, "reminders.html"),
        new HelpTopic(R.string.help_topic_extras, "extras.html"),
};
```

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.Gui.HelpTopicTest
```

预期：4/4 PASS（`allTopicsExistInBothLanguages` 会验证任务 1/2 创建的 4 个新 asset 存在）。

- [ ] **步骤 5：跑全量回归并 commit**

```bash
./gradlew connectedDebugAndroidTest
# 预期：全部通过（既有 HelpActivityTest 不受影响：主题列表变长）
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java MobileOrg/src/main/res/values/strings.xml MobileOrg/src/main/res/values-zh/strings.xml MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpTopicTest.java
git commit -m "feat(help): 帮助中心注册「工作原理」「搜索」两个新主题（7→9）"
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 5

- Wave 1（无依赖）：任务 1, 任务 2
- Wave 2（依赖 Wave 1）：任务 3（依赖 1）, 任务 4（依赖 1, 2）, 任务 5（依赖 1, 2）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
