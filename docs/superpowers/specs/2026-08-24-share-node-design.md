# 分享节点功能设计

- 日期：2026-08-24
- 状态：已批准
- 范围：将 org 节点子树以纯文本形式通过 Android 分享面板发出

## 需求

**场景**：用户把节点内容发到自己的其他工具（备忘录、草稿箱、其他设备），后续自行处理。

**已确认的决策**：

| 决策点 | 选择 |
|---|---|
| 分享目的 | 自用工具间转移（用户自己后续处理） |
| 分享范围 | 整棵子树（当前节点 + 所有后代） |
| 分享载体 | `ACTION_SEND` + `text/plain`，系统分享面板 |
| 文本格式 | org 原文（保真，可粘贴回任何 org 工具） |
| 入口位置 | Outline 长按上下文菜单 + ViewActivity 查看页菜单 |
| 标题层级 | 归一化到 level 1（根节点 `*` 开始，后代递增） |

## 架构

```
UI 入口（3 处接线）               逻辑层                        数据层
OutlineActionMode  ─┐
ViewActivity       ─┼→ OrgUtils.shareNode(ctx, nodeId)  →  OrgNodeRepository
outline_node*.xml  ─┘        │                                  ├─ getById(nodeId)
（菜单资源项）               └─ ACTION_SEND text/plain          └─ getSubtreeText(nodeId)【新增】
                                + EXTRA_SUBJECT
                                + createChooser
```

方案取舍：不新建 ShareUtils 类（功能仅一个静态方法，OrgUtils 即项目工具方法聚集地）；
不做分享前预览对话框（自用场景无需确认环节，想预览可用查看页）。

## 组件改动

| 文件 | 改动 |
|---|---|
| `OrgData/OrgNode.java` | `toString()` 抽出 `toString(int level)` 重载，原方法委托 `toString(this.level)`，行为不变 |
| `OrgData/OrgNodeRepository.java` | 新增 `getSubtreeText(long nodeId)`：递归序列化子树，星号数 = `node.level − root.level + 1` |
| `util/OrgUtils.java` | 新增 `public static shareNode(Context, long nodeId)`：查节点 → 序列化 → 截断防御 → 分享面板 |
| `res/menu/outline_node.xml`、`res/menu/outline_node_uneditable.xml` | 加 `menu_share` 项（分享只读，uneditable 菜单也加） |
| `Gui/Outline/OutlineActionMode.java` | `onActionItemClicked` 加 `menu_share` case |
| `Gui/ViewActivity.java` | `onCreateOptionsMenu` 加分享项（overflow）；`onOptionsItemSelected` 用 **itemId** 判断——现有代码用 `order` 0-4 判断递归层级（ViewActivity.java:103），新项必须避开 |
| `res/values/strings.xml` | `menu_share` = "Share node"、`share_node_not_found` = "Node not found"、`share_truncated` = "Content too long, truncated to 400000 chars" |
| 新文件 `res/drawable/ic_menu_share.xml` | Material share vector；`<vector>` 必须用 `xmlns:android`（用 `res-auto` 会 AAPT 构建失败，见 CLAUDE.md） |

### 关键方法规格

```java
// OrgNodeRepository
/**
 * 序列化节点及其整棵子树为 org 格式文本，标题层级归一化：
 * 根节点为 level 1（*），后代依次递增。
 * @throws OrgNodeNotFoundException nodeId 无效
 */
public String getSubtreeText(long nodeId)

// OrgUtils
/**
 * 查找节点并把其子树文本送入系统分享面板。
 * 节点不存在：Toast 提示并返回；文本超长：截断 + Toast 提示。
 */
public static void shareNode(Context context, long nodeId)
```

`getSubtreeText` 不复用现有 `nodesToString(nodeId, level)`：该方法 `level == 0`
时跳过根节点本身（OrgNodeRepository.java:547），且星号数取自 `node.level` 字段
无法归一化；它另有调用者，不动它。

## 序列化规格

- 标题行：`*`×(相对层级+1) + 空格 + [`TODO `] + [`[#A] `] + name + [` :tags:`]
- payload 原样保留（SCHEDULED / DEADLINE / CLOCK / logbook 不清理——org 保真）
- tags 仅节点自身，不含 inherited（org 语义：继承标签不落盘）
- 节点之间空行分隔（与现有 `nodesToString` 的拼接行为一致）
- `EXTRA_SUBJECT` = 节点名（邮件等 app 显示为主题）
- `EXTRA_TEXT` = 子树完整文本

## 错误处理

| 场景 | 处理 |
|---|---|
| 节点不存在 | `shareNode` 捕获 `OrgNodeNotFoundException`，Toast 提示，不崩溃 |
| 子树文本超大 | Intent extras 走 Binder 事务（~1MB 硬限，实际 ~200-500KB 即可能 `TransactionTooLargeException`）。超长时截断至 400,000 字符 + Toast "内容过长已截断"。截断优于拒绝（自用场景） |
| 序列化中途子节点消失 | `getChildren` 返回空列表，递归自然终止 |
| 无 app 处理 `text/plain` | `createChooser` 保证始终有系统分享面板 |

从 Activity 上下文调用 `startActivity`，无需 `FLAG_ACTIVITY_NEW_TASK`（两个入口都在 Activity 内）。

## 测试

instrumentation 测试（`androidTest`），沿用 `test/OrgData/` 的 `OrgDatabaseStub` 模式，新增用例：

1. **归一化**：level=2 的 3 层树，`getSubtreeText` 输出根 `*`、子 `**`、孙 `***`
2. **完整性**：TODO / priority / tags / payload（含 SCHEDULED 行）均出现
3. **不含 inherited tags**：父有 tag、子无 tag，子行不带 tag
4. **不存在节点**：`getSubtreeText(-1)` 抛 `OrgNodeNotFoundException`

不测：分享面板弹起（系统 UI，instrumentation 难断言，收益低）；intent 构建逻辑
简单，靠 CI + 真机手动验证。

## 不做的事（YAGNI）

- 不支持 Markdown / 剥离语法纯文本输出（未来需要再加格式选择）
- 不分享为 .org 文件附件
- 不加分享预览对话框
- 不在 EditActivity 加入口（编辑保存后从查看页分享）
