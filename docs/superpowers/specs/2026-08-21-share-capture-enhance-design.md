# Share-to-Capture 增强 设计

## 目标

让任意 app 分享/选中文字进 MobileOrg 的捕获体验完整：无标题分享自动生成标题，选中文本工具栏直达 capture。

## 背景（现状）

分享接收已存在：`EditActivity` 声明了 `ACTION_SEND`（`text/*`）、Google voice `SELF_NOTE`、Gmail `AUTO_SEND` intent-filter；无 `ACTIONMODE` extra 的 intent 走 `EditActivityControllerCreate(Intent)` → `OrgUtils.getCaptureIntentContents(intent)` 把 `EXTRA_SUBJECT`/`EXTRA_TEXT` 映射为 OrgNode。

两个缺口：

1. **标题空白**：纯文本分享（无 SUBJECT）时标题为空字符串，outline 列表出现无名条目。
2. **无 PROCESS_TEXT**：任何 app 内选中文本的系统工具栏没有 MobileOrg 入口，必须走分享菜单二跳。

## 设计

### 1. 标题生成（`OrgUtils.getCaptureIntentContents`）

现有逻辑保持：

- SUBJECT 与 TEXT 都非空 → 标题 = `"[[TEXT][SUBJECT]]"` org 链接，正文清空（不动）。

新增：SUBJECT 为 null 或空串时，标题由 TEXT 生成：

- 取 TEXT 的首个非空行（trim 后非空）。
- 该行 ≤ 40 个字符 → 整行作标题。
- > 40 个字符 → 前 40 个字符 + `"…"`。
- TEXT 为 null/空/全空白 → 标题保持空串。
- 正文保留全文（含首行，不因生成标题而删除）。

### 2. PROCESS_TEXT 入口

- `AndroidManifest.xml` 的 `EditActivity` 追加 intent-filter：`android.intent.action.PROCESS_TEXT` + `category DEFAULT` + `mimeType text/plain`。
- API 23+ 系统在文本选择工具栏显示入口；低版本系统不显示，无副作用。
- `getCaptureIntentContents`：`EXTRA_TEXT` 为空时读 `Intent.EXTRA_PROCESS_TEXT` 作为 TEXT 兜底，走同一标题生成逻辑。

### 3. 测试（新建 `MobileOrg/src/androidTest/.../util/OrgUtilsTest.java`）

- 回归：SUBJECT+TEXT → 标题为 `[[TEXT][SUBJECT]]`、正文空。
- 标题生成：纯 TEXT 单短行（整行）；超 40 字（截断 + `…`）；首行为空行（取下一非空行）；纯空白/空 TEXT（标题空串）；正文保留全文断言。
- PROCESS_TEXT：仅 `EXTRA_PROCESS_TEXT` 的 intent → 与纯 TEXT 相同结果。

## 范围外

- 有 SUBJECT 的 org 链接路径行为。
- `EditActivity` 表单 UI 与保存流程。
- 图片等非 `text/*` MIME 的分享。
- Direct Share（长按图标快捷方式）。
