# Checkbox 勾选 设计

## 目标

渲染视图中的 org checkbox（`- [ ]` / `- [X]`）可点击切换勾选状态，并自动刷新统计 cookie（`[1/3]`、`[33%]`）。与桌面 org 的 `C-c C-c` 体验一致。

## 背景（现状）

- `OrgRenderer.render()` 逐行状态机渲染 payload；无序列表项（`^[-+]\s+.*`）输出 `<li>`，checkbox 标记 `[ ]` 被当普通文本。
- `OrgRenderer.preClean()` 删除 PROPERTIES/LOGBOOK/SCHEDULED/DEADLINE/CLOSED 行 → cleaned 行号与原始 payload 行号错位。
- `ViewFragment.InternalWebViewClient.shouldOverrideUrlLoading` 已有自定义 scheme 拦截模式（`orgfile:` / `orgid:` / `orginternal:`）。
- `OrgNodeRepository.generateApplyWriteEdits(old, new, olp)` 生成并应用 BODY edit（EditActivity 保存路径），undo/同步天然兼容。

## 设计

### 1. 渲染层（OrgRenderer）

- `preClean` 附加输出行号映射（cleaned 行号 → 原始行号），供 checkbox 链接携带原始行号。
- 无序列表分支识别 checkbox 行：`^\s*[-+]\s+\[( |X|x)\]\s+`：
  - 输出 `<a href="orgcheckbox:<nodeId>:<原始行号>">☐</a> 内容`（`[X]`/`[x]` → `☑`），checkbox 标记从渲染内容中剥离。
  - `☐`/`☑` 作为文字渲染，跟随主题颜色，零 JS 依赖。
- 接入点：`payloadToHTML(node)`（详情页）与 `toHTML(node, level)` 递归渲染（子节点 checkbox 用子节点自己的 nodeId）。
- 渲染核心函数组织：checkbox 识别/链接生成为静态纯函数（可测），`OrgRenderer` 只做接线。

### 2. 状态翻转与 cookie 刷新（OrgUtils 静态纯函数）

- `toggleCheckboxLine(String payload, int rawLineIdx)`：
  - 定位原始 payload 第 rawLineIdx 行（0 基），匹配 checkbox 行则翻转 `[ ]`↔`[X]`，返回新 payload。
  - `[X]`/`[x]` 统一翻转为 `[ ]`，`[ ]` 翻转为 `[X]`。
  - 非 checkbox 行或行号越界 → 返回原 payload。
- `refreshCookies(String payload)`：
  - cookie 行：列表项含 `[n/m]` 或 `[p%]` 标记。
  - 统计范围：cookie 项之后、下一个缩进 ≤ cookie 项缩进的行之前的全部后代 checkbox（含嵌套）。
  - 重算后代中 `[X]`（含 `[x]`）数 / 总数；按 cookie 原格式写回（分数格式 → `[X数/总数]`，百分比格式 → `[p%]`）。
  - 无 cookie 的 checkbox 块不改动。

### 3. 拦截与写回（ViewFragment）

- `shouldOverrideUrlLoading` 新增 `orgcheckbox:` 分支：解析 `<nodeId>:<rawLineIdx>`。
- 流程：`repo.getById(nodeId)` 取 DB 最新 node → `toggleCheckboxLine` → `refreshCookies` → 构造仅 payload 不同的 newNode → `repo.generateApplyWriteEdits(oldNode, newNode, "")`（BODY edit，undo batch/同步自动兼容）→ 通知宿主重渲染。
- `olpPath` 传 `""`（BODY edit 不消费该参数，REFILE 才用）。

### 4. 刷新（ViewActivity / ViewFragment）

ViewFragment 新增回调接口（宿主实现），点击写回后回调；ViewActivity 重读 node 并重新 `display()`（复用现有渲染调用点）。

### 5. 测试（instrumentation，纯静态函数）

- `toggleCheckboxLine`：`[ ]`→`[X]`、`[X]`/`[x]`→`[ ]`、非 checkbox 行不变、行号越界不变、`+ [ ]` 形式、行首空白。
- `refreshCookies`：简单块计数更新、百分比格式保持、嵌套后代统计、无 cookie 不动。
- 渲染：checkbox 行输出 `☐`/`☑` 与链接格式；preClean 删除 PROPERTIES 行后映射行号正确（不错位）。

### 6. 编辑窗口预览点击（EditActivity / PayloadFragment）

**背景**：`viewOnClick` 配置默认 false 时，outline 点击叶子节点进入的是 EditActivity（而非 ViewActivity）——编辑窗口预览是用户的主入口，必须支持点击。

**数据流（与 ViewActivity 路径的关键差异——写工作副本而非 DB）**：

- `PayloadFragment.switchToView()` 渲染预览时不再设 `previewNode.id = -1`，改用 controller 的真实 node.id，渲染可点击链接（id=-1 时 render() 自然降级为纯符号，覆盖 Create 模式新节点）。
- 点击回调流程：`toggleCheckboxLine(payload.get(), rawLine)` 作用于**内存工作副本**（含未点 ActionBar 保存的全部修改）→ `refreshCookies` → `setPayload()` 更新工作副本 → `editActivity.saveEdits()` 整体落库（不 finish）→ 重新 `switchToView()` 刷新预览。
- **单一真相源**：toggle 与渲染都作用于工作副本，无双写冲突；点击即保存 = 所有 tab 未保存修改（标题/tags）连带落库。
- **取消语义自洽**：点击落库后 `hasEdits()` 为 false，返回不再弹"放弃修改？"。
- 行号映射天然正确：rawLineMap 基于 switchToView 时传入的工作副本生成，toggle 也作用于同一字符串。
- undo/同步兼容：saveEdits 生成 BODY edit（非 capture 节点），与 ViewActivity 路径一致。
- `saveEdits()` 内的 `announceSyncDone` 保留（后台 Outline 收到是 no-op：停止不存在的动画）。

**ViewActivity 路径保留**：两处语义一致（点击即生效），不冲突。

## 范围外

- 有序列表 checkbox（`1. [ ]`）。
- heading 级 cookie（node.name 的 `[2/3]` 统计）。
- 子节点 TODO 状态 → 父 cookie 联动。
