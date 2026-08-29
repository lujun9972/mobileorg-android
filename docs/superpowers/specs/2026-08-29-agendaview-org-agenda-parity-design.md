# AgendaView 实现 org-agenda 视图（org-mobile 原生协议）设计规格

日期：2026-08-29
状态：已批准（用户 2026-08-29 盘问确认）

## 背景与目标

用户在 PC（init-GTD-org.org）定义了 10 个 org-agenda custom commands，希望手机 MobileOrg 的 Agenda 视图呈现同样的内容与过滤语义。

探索结论：**org-mobile 协议原生支持此需求，且机制已在工作**（手机已见「Agenda Views」文件）。`org-mobile-push` 内置 `org-mobile-create-sumo-agenda` 把 `org-agenda-custom-commands` 合成 SUMO 视图写出 `agendas.org`（块标记 `<after>KEYS=n TITLE:..</after>`、日期分节、`:ORIGINAL_ID:` 回流属性）；MobileOrg Android 的 `OrgFileParser.combineBlockAgendas()` 原生解析分块分节，节点编辑经 `findOriginalNode()` 按 `:ORIGINAL_ID:` 回流源文件。**Android 端零改动。**

问题根源是配置漂移：云上 sync 脚本自带一份缩水视图定义（5 个且 `t` 键重复定义两次），与 PC 的 init-GTD-org.org（10 个）不一致；且 NOAGENDA 全局 skip 只在 PC 配置存在，云上生成的 agendas.org 未排除 NOAGENDA 项。

## 用户决策记录

| 决策点 | 选择 |
|---|---|
| 视图范围 | 全部 10 个 + org-mobile 默认「Agenda 周视图」（`org-mobile-agendas 'all`；custom 已含 `t` 键故不重复补 ALL TODO，共 11 个视图） |
| 核心诉求 | 过滤正确性（`org/schedule-until`、NOAGENDA）优先 |
| 方案 | org-mobile 原生协议，改 PC/云侧 elisp |
| 部署事实 | sync 在腾讯云跑，手机 SSH 连云，agendas.org 生成在云上 `~/mobileorg` |
| 视图定义源 | init-GTD-org.org 的 10 个为准 |
| 单一真相机制 | 共享 el 文件放 mobileorg-sync 仓库，两端 load |
| 排序 | n/t/T/w/o 补 `scheduled-up priority-down`（现仅 sync 脚本版有） |

## 架构与数据流

```
GTD 文件（云 ~/我的GTD git checkout）
  ↓ org-mobile-push → SUMO 批处理（org-agenda 引擎跑全部视图，
    全局 skip NOAGENDA、各视图 skip 函数、排序全部原生生效）
~/mobileorg/agendas.org（#+READONLY；11 个视图；:ORIGINAL_ID:）
  ↓ 手机 SSH 同步（现有流程零改动）
MobileOrg「Agenda Views」→ combineBlockAgendas 分块 → #HEAD# 分节
  ↓ 手机端编辑（todo/标题/正文）
Edits 按 :ORIGINAL_ID: 生成 → org-mobile-pull 应用回原 GTD 文件 → git push
```

## 改动清单

### A. 新建 `mobileorg-sync/agenda-views.el`（单一真相）

内容（按序）：
1. `org/schedule-until` 函数（两处现存定义合一，init-GTD-org.org 版为准）
2. `my/org-skip-noagenda` 函数 + `(add-to-list 'org-agenda-skip-function-global #'my/org-skip-noagenda)`
3. `(setq org-agenda-custom-commands '(...))`：init-GTD-org.org 的 10 个视图原样迁移（n 下一步行动 / t 今日事项 / T 明日事项 / w 分配给他人 / s 超市 / m 会议 / o 工作任务 / h 习惯 / r 季度报告 / ␣ 块 agenda+REFILE），修改点：
   - n/t/T/w/o 各补 `(org-agenda-sorting-strategy '(scheduled-up priority-down))`
   - 不携带 mobileorg-sync.el 旧版重复的第二个 `t` 定义（lambda 型，SUMO 本就跳过）
4. 文件仅依赖 org 变量/函数，不依赖 spacemacs 环境（云侧 `emacs --quick` 兼容）

### B. 修改 `mobileorg-sync.el`

1. 删除自带 `org-agenda-custom-commands` 与 `org/schedule-until` 定义
2. 在 `(require 'org-mobile)` 之后加载共享文件（按脚本自身路径定位，不依赖工作目录）：
   ```elisp
   (unless (load (expand-file-name "agenda-views.el"
                                   (file-name-directory load-file-name)) nil t)
     (error "mobileorg-sync: failed to load agenda-views.el"))
   ```
3. 显式 `(setq org-mobile-agendas 'all)`
4. `org-mobile-force-id-on-agenda-items` 保持默认 `t`（agendas.org 条目自动带 ID，编辑回流依赖它）

### C. 修改 `~/.spacemacs.d/layers/my-GTD/init-GTD-org.org`

1. 「配置agenda view」相关 src block：删除 `org-agenda-custom-commands`、`org/schedule-until`、`my/org-skip-noagenda` 的内联定义，改为 load 共享文件（失败显式 warning，不静默、不保留 fallback 拷贝——显式失败优于静默漂移）：
   ```elisp
   (unless (load "~/github/mobileorg-sync/agenda-views.el" nil t)
     (message "WARN: agenda-views.el not found; org-agenda views unavailable"))
   ```
2. 重新 org-babel-tangle 生成 `init-GTD-org.el`

### 部署

1. 本机修改 → push mobileorg-sync 仓库（GitHub，origin）
2. 腾讯云上 mobileorg-sync 仓库 git pull
3. 下一次 sync（5 分钟间隔）自动生成新版 agendas.org

## 已知限制（明确预期）

- **快照性**：视图随 sync 周期（`mobileorg-sync-interval` 5 分钟）刷新；手机端改 todo 后，该节点在视图内不会即时消失/更新
- **正文预览**：每个条目仅 10 行正文 + planning 日期（SCHEDULED/DEADLINE），非完整子树
- **时间前缀丢失**：agenda 行的时间列（如 8:30..）经 `<before>` 标记被 Android 解析器丢弃，具体时间看正文 planning 行
- **默认视图附加**：`'all` 会附带「Agenda 周视图」默认视图（custom 已含 `t` 键，ALL TODO 不重复附加；用户已接受）

## 验收标准

1. **云侧产物**：sync 后 `~/mobileorg/agendas.org` 存在，含 11 个视图标题（10 custom + 默认 a 周视图）；NOAGENDA tag 的条目不出现；n 视图无 scheduled 在未来的条目
2. **手机呈现**：同步后「Agenda Views」按块分节显示全部视图，块标题干净（无 `<after` 残留）
3. **端到端回流**：手机上对 Agenda Views 中某节点切换 todo → 等一个 sync 周期 → PC 端 `git -C ~/我的GTD log` 显示原文件对应节点状态变更
4. **PC 回归**：Emacs 中 `M-x org-agenda` 各视图（n/t/T/w/s/m/o/h/r/␣）行为与改动前一致（视图定义等价迁移 + 排序补强）

## 涉及文件

| 文件 | 操作 | 仓库 |
|---|---|---|
| `agenda-views.el` | 新建 | mobileorg-sync |
| `mobileorg-sync.el` | 修改 | mobileorg-sync |
| `~/.spacemacs.d/layers/my-GTD/init-GTD-org.org` | 修改（含重新 tangle） | spacemacs.d |
| MobileOrg Android | 无改动 | — |
