# 搜索主题帮助扩充（场景案例为主）实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 重写 search.html（zh/en）为场景案例为主的结构——5 个使用场景 + 匹配规则与边界（含通配符坑），替换现有 4 节结构。

**架构：** 纯内容变更，仅重写 `MobileOrg/src/main/assets/help/{zh,en}/search.html` 两个文件。不动任何代码、不动 HelpTopic.TOPICS、不改测试（文件名与数量不变，`HelpTopicTest` 现有断言继续有效）。

**技术栈：** 静态 HTML（WebView 渲染，现有 help.css）。

**规格：** `docs/superpowers/specs/2026-08-28-search-help-design.md`（本计划任务文本已自包含规格全部内容要点）

**编码约束：**

- 沿用现有模板：`<!DOCTYPE html>`、`lang="zh"/"en"`、`<meta charset/viewport>`、`<title>`=`<h1>`（zh「搜索」/ en "Search"）、`<link rel="stylesheet" href="../help.css">`。
- 关键词与标题示例用 `<code>`；不新增截图。
- 受众 Org-mode 老手；如实描述现状，不虚构能力。
- en 版与 zh 版章节严格同构；场景示例词可本地化（如 `预算`/`budget`、`会议`/`meeting`）。

---

### 任务 1：重写 search.html 为场景案例为主结构（zh/en）

**依赖：** 无
**文件集：** `MobileOrg/src/main/assets/help/zh/search.html`, `MobileOrg/src/main/assets/help/en/search.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/assets/help/zh/search.html`（整文件重写）
- 修改：`MobileOrg/src/main/assets/help/en/search.html`（同构翻译）

- [ ] **步骤 1：重写 zh 版 search.html**

结构（4 个 `<h2>` + 尾引导，替换现有全部 body 内容）：

1. `<h2>入口</h2>` — `<p>` 通过主菜单的放大镜图标进入搜索，输入关键词即可查询。搜索范围是**所有已同步 org 文件**的节点标题。
2. `<h2>场景案例</h2>` — `<ul>` 5 个场景，每项 `<li><strong>场景名</strong>：描述</li>`，叙述式「意图→输入→命中」：
   - **记不清完整标题时输入片段**：想找 <code>2026 年度预算评审</code> 但只记得「预算」？输入 <code>预算</code> 即可命中所有标题含「预算」的节点——不需要输入完整标题。
   - **中文关键词直接搜**：输入 <code>会议</code>，命中 <code>周会会议纪要</code>、<code>项目启动会议</code> 等所有标题含该关键词的节点，点选进入。
   - **英文关键词不区分大小写**：输入 <code>email</code> 可命中 <code>Email</code>、<code>EmailServer</code>。
   - **想找所有待办任务（常见误解）**：输入 <code>todo</code> 命中的是**标题里含 TODO 字样**的节点，不是未完成任务。查看待办请用主界面顶部的 TODO 分类项，或按状态用 Agenda。
   - **搜不到正文/标签（常见误解）**：搜索只匹配节点标题，正文内容与标签不参与。按标签找请用主界面标签过滤（支持 AND/OR 组合），按日期找请用 Agenda。
3. `<h2>匹配规则与边界</h2>` — `<ul>` 6 条：
   - 只搜索节点**标题**（heading 文本）；正文（payload）、标签、文件名均不参与搜索
   - 子串匹配：标题任意位置包含关键词即命中，例如输入 <code>会议</code> 可命中 <code>周会会议纪要</code>
   - 英文关键词大小写不敏感：<code>todo</code> 与 <code>TODO</code> 等价
   - 没有运算符、正则或前缀语法——输入什么就按字面子串匹配
   - 前后空格自动忽略：输入 <code> 会议 </code> 按 <code>会议</code> 搜索
   - <code>%</code> 与 <code>_</code> 被当作通配符：输入 <code>100%</code> 实际按 <code>100</code> 匹配；单独输入 <code>%</code> 会命中所有节点；无法转义按字面搜索这两个字符
4. `<h2>结果交互</h2>` — `<p>` 搜索结果以列表形式展示，点击任一结果进入对应节点。无结果时标题栏会显示无结果提示。
5. 尾引导 `<p>`：节点的基本浏览与操作见 `<a href="outline.html">Outline 基本操作</a>`。

**删除**现「相关功能」节（`<h2>相关功能</h2>` 及其 `<p>`）——其内容已并入场景 4/5。

- [ ] **步骤 2：重写 en 版 search.html**

`lang="en"`，title/h1 = "Search"，4 个 h2：Entry / Usage Examples / Matching Rules & Edge Cases / Interacting with Results。与 zh 严格同构：5 场景（示例词本地化：budget/meeting/email/todo/%）、6 条规则边界（trailing spaces are stripped automatically; `%` and `_` act as wildcards — typing `100%` effectively searches for `100`, typing `%` alone matches every node, and there is no way to escape these two characters literally）、结果交互、尾引导链接文字 "Outline Basics"。

- [ ] **步骤 3：验证结构并 commit**

```bash
grep -c '<h2>' MobileOrg/src/main/assets/help/zh/search.html MobileOrg/src/main/assets/help/en/search.html
# 预期：两文件均输出 4
grep -c '相关功能\|Related Features' MobileOrg/src/main/assets/help/zh/search.html MobileOrg/src/main/assets/help/en/search.html
# 预期：两文件均输出 0（旧节已删）
grep -c '通配符\|wildcard' MobileOrg/src/main/assets/help/zh/search.html MobileOrg/src/main/assets/help/en/search.html
# 预期：两文件均输出 ≥1（边缘行为在）
git add MobileOrg/src/main/assets/help/zh/search.html MobileOrg/src/main/assets/help/en/search.html
git commit -m "feat(help): 搜索主题重写为场景案例为主（5 场景+规则边界含通配符坑）"
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1

- Wave 1（无依赖）：任务 1
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测（真机渲染抽查：4 章节显示、`<code>` 渲染、outline 内链跳转）、F4 范围保真
