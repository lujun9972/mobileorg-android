# 帮助内容扩充（工作原理 + 搜索 + 服务端配置）设计规格

日期：2026-08-28
状态：已批准

## 背景与目标

应用内帮助中心（v2.11.0）现有 7 主题 × 中英双语，内容为功能概述级。用户反馈「帮助信息不够」，经盘问确定补三个方向：

1. **搜索的语法**——现有帮助完全没讲搜索行为
2. **MobileOrg 基础知识**——本地副本模型、远端文件协议、编辑回传机制
3. **服务端要如何配置**——现有 sync.html 只讲客户端字段，不讲服务端准备什么

受众基调：Org-mode 老手（不解释 Org 概念本身）。范围：内容为主 + 少量结构改动（`HelpTopic.TOPICS` 加 2 条目）。**不修改任何功能代码**（搜索功能保持现状，帮助如实描述）。

## 代码事实依据（内容必须如实反映）

- 搜索实现 `OrgFileRepository.search()`：`NAME LIKE '%query%'`——仅匹配节点标题（name 列），子串匹配，SQLite `LIKE` 对 ASCII 大小写不敏感；不搜 payload 正文、标签、文件名；无运算符/正则/前缀语法。入口为主菜单搜索（`SearchActivity`）。
- 同步协议（`Synchronizer`）：
  - `INDEX_FILE = "index.org"`：文件清单。解析规则（`OrgIndexParser`）：`[file:文件名][别名]` 链接列出各 org 文件；`#+TODO:` 行定义 TODO 关键字序列（`|` 分隔未完成/已完成）；`#+TAGS:`、`#+PRIORITIES:` 行定义全局标签/优先级。
  - `checksums.dat`：每行 `校验和␣␣文件名`（两个空格分隔，校验和在前）。与本地比对决定增量下载哪些文件。
  - 编辑回传 `pushCaptures()`：本地所有 OrgEdit 序列化 + 本地 capture 内容，**追加**到远端 `mobileorg.org`（`FileUtils.CAPTURE_FILE`，别名 "Captures"）末尾并上传，随后清空本地 Edits 与本地 capture 文件。**不直接修改远端原 org 文件**——需 Emacs 端 `org-mobile-pull`（org-mobile.el）消化 mobileorg.org。
  - 远端删除清理：index.org 中不再列出的文件在同步后从本地 DB 移除（mobileorg.org 除外）。
  - app 只有下载/解析 index.org 的逻辑，**不生成 index.org**——服务端须预置（手写或 Emacs `org-mobile-push` 生成）。
- 同步触发：手动同步按钮、自动同步间隔（设置）、开机。

## 主题结构

`HelpTopic.TOPICS` 从 7 扩到 9，顺序：

```
quick-start → how-it-works → sync → outline → search → pomodoro → statistics → reminders → extras
```

- 「工作原理」置于 sync 之前（先懂原理再配服务端）。
- 「搜索」置于 outline 之后（浏览与检索相邻）。
- quick-start.html 的「接下来」链接列表补 2 条（工作原理、搜索），中英两份同步更新。

## 新主题 1：工作原理（how-it-works.html）

章节（中英同构）：

1. **本地副本模型** — 同步 = 拉取远端 → 解析进本地 SQLite；浏览/编辑完全离线；编辑不实时回传。
2. **远端三个约定文件** —
   - `index.org`：文件清单 + 全局配置。最小示例：

     ```org
     #+TODO: TODO DOING | DONE CANCELED
     #+TAGS: work home learning
     * 文件清单
     [[file:home.org][Home]]
     [[file:work.org][Work]]
     ```
   - `checksums.dat`：每行 `<校验和>  <文件名>`，增量同步依据。
   - `mobileorg.org`：本地修改的回传通道。
3. **编辑如何回传** — 本地编辑不改远端原文件；同步时全部序列化为 org 条目追加到远端 `mobileorg.org` 末尾，随后清空本地待同步项；在 Emacs 端执行 `org-mobile-pull` 消化这些修改。
4. **同步触发时机** — 手动按钮 / 自动同步间隔 / 开机。
5. **远端删除清理** — 从 index.org 移除的文件在下次同步后从本地删除；Captures（mobileorg.org）不受影响。

## 新主题 2：搜索（search.html）

章节：

1. **入口** — 主菜单放大镜图标 → 输入关键词即查。
2. **匹配规则（现状如实描述）** —
   - 只搜索节点标题（heading 文本）
   - 子串匹配：标题中任意位置包含关键词即命中
   - ASCII 大小写不敏感
   - 无运算符/正则/前缀语法：输入即按字面子串匹配
   - 正文（payload）、标签、文件名不参与搜索
3. **结果交互** — 点击结果进入该节点；无结果时标题栏显示无结果提示。
4. **相关功能** — 按标签筛选用主界面标签过滤（AND/OR）；按日期查事项用 Agenda。
5. 章节结构为将来搜索功能增强预留扩充位（本篇只描述现状）。

## sync.html 扩充：新增「服务端准备」节

插入位置：「同步方式」表之后、「配置向导」之前。内容（中英同构）：

1. **服务端目录必备** — 一个可读写目录，内含：`index.org`（必需）、各 org 文件；`checksums.dat`（增量同步比对用）。
2. **两种准备方式** —
   - Emacs 工作流（标准）：`org-mobile-directory` 指向该目录，`org-mobile-push` 自动生成 index.org 与 checksums.dat。
   - 手写最小 index.org：给出可运行的几行示例（同工作原理篇），适合无 Emacs 场景。
3. **三种同步方式的服务端要点** — WebDAV：任何支持读写（GET/PUT）的 WebDAV 服务（Nextcloud/ownCloud 或自建）；SSH：可 SFTP 读写即可（密钥或密码）；SDCard：本地目录即可（其他 app 放入文件）。
4. 首次同步行为衔接：配置完成后点同步 → 下载 index.org + 变更文件 → 列表出现。

## 技术约束

- 新 HTML 与现有主题同模板：同 head/meta/`<link rel="stylesheet" href="../help.css">`、`lang` 属性正确（zh/en）。
- 中英成对：`assets/help/zh/` 与 `assets/help/en/` 各新增 how-it-works.html、search.html；sync.html 中英两份同步扩充。
- 不新增截图。
- 代码改动仅两处：`HelpTopic.java` TOPICS 数组 +2 条目；`values/strings.xml` 与 `values-zh/strings.xml` 各 +2 标题资源（`help_topic_how_it_works`、`help_topic_search`）。
- 内容中的代码/文件名示例用 `<code>` 标签；遵循现有暗色主题（help.css 已处理）。
- 测试：新增 TOPICS 完整性检查（遍历 `HelpTopic.TOPICS`，断言 zh/en 两语言目录下对应 asset 文件均存在），随实现补充。

## 验收标准

- [ ] 帮助中心显示 9 个主题，顺序正确，中英标题正确
- [ ] 工作原理篇含本地副本模型、三文件说明（index.org 最小示例、checksums.dat、mobileorg.org）、编辑回传机制、触发时机、远端删除清理
- [ ] 搜索篇如实描述现状（仅标题子串、大小写不敏感、无语法），并指引标签过滤/Agenda 替代
- [ ] sync.html 含服务端准备节（Emacs org-mobile-push 标准工作流 + 手写最小示例 + 三方式服务端要点）
- [ ] quick-start「接下来」含新增 2 主题链接，内链跳转正常（沿用现有拦截逻辑）
- [ ] zh/en 内容同构，无缺份
- [ ] TOPICS 完整性测试通过
