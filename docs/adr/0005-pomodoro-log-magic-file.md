# ADR 0005: Pomodoro Log as an App-Owned Magic File (Out-of-Protocol)

## Status

Accepted

## Context

番茄钟记录（`pomodoro_sessions`）需要跨设备合并与重装恢复，载体是远端 `pomodoros.org`。同步协议的 pull 白名单要求文件**同时**出现在 `checksums.dat` 和 `index.org` 的 `[file:...]` 列表中才会被下载，而这两份索引由 Emacs 端 `org-mobile-push` 生成、只覆盖 `org-mobile-files` 注册的文件。`pomodoros.org` 由 app 全量重写（union 投影），若注册进 `org-mobile-files`，Emacs 会把它当普通 org 文件解析进本地大纲、并对其主张 checksum 管理权——与 app 的全量重写形成双重管理冲突。

### Alternatives considered

1. **注册进 Emacs `org-mobile-files`**（走标准白名单）— 文件进大纲列表、进 checksums.dat；Emacs 认为归它管，app 认为归它管，checksum 循环触发冗余下载，两边都会"修"这个文件。
2. **借道 `mobileorg.org` capture 条目** — capture 格式为节点编辑设计，时间记录塞进去语义别扭；且 Emacs pull 消费条目后 app 需防重推。
3. **非 org 文件（JSON/CSV）** — Emacs/org 工具链无法消费，破坏 org 目录单一模型。

## Decision

`pomodoros.org` 是继 `mobileorg.org`、`agendas.org` 之后的**第三个魔法文件**，完全在 org-mobile 协议之外由 app 管理：

- push：`Synchronizer` 直接 `putRemoteFile("pomodoros.org")`，内容 = 本地 `pomodoro_sessions` 全表投影（union 语义，见番茄钟同步设计规格）。
- pull/merge：直接 `getRemoteFile("pomodoros.org")` 取原始文本，由专用 `PomodoroLogParser` 解析——**不走 `OrgFileParser`**，不进 `files` 表、不进大纲。
- **明确不注册**进 Emacs 端 `org-mobile-files`；Emacs 侧只读消费（clocktable/脚本）。

## Consequences

- Emacs 端**零配置**即可工作；但也意味着 org-mobile 协议对它不可见——依赖 index.org 的备份/审计工具不会覆盖它。
- 格式演进由 app 端 self-contained 自担（parser + serializer 同仓演进，无跨端兼容负担）。
- SDCard 同步模式下 `putRemoteFile` 的 append 语义违反接口 "Replaces" 契约（`pushCaptures()` 已前置拼接远端内容，append 实际造成内容重复）——实现时恢复覆写契约，pomodoros.org 随之获得正确的覆写语义（见设计规格 §同步集成）。
