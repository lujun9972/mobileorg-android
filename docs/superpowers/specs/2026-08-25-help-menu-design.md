# 应用内帮助中心设计

日期：2026-08-25
状态：已批准（盘问式头脑风暴定稿）

## 背景与动机

现状：主菜单"帮助"项（`OutlineActivity.runHelp()`）直接用浏览器打开上游
`matburt/mobileorg-android` 的 wiki——该仓库已停止维护，链接内容永远不会更新，
且本 fork 新增的功能（pomodoro 连续模式、统计、undo、分享、DEADLINE/SCHEDULED 提醒、
每日总览、同步配置导入导出）在那边完全没有文档。

动机（盘问确认，全选）：

1. fork 新功能无使用说明，用户（包括未来的维护者自己）发现不了或不会用
2. 死链接指向不再维护的 wiki，内容过时
3. 跳浏览器体验差，需要应用内阅读
4. 新用户上手难（同步、capture、edit、agenda 概念多）

## 目标与非目标

**目标**

- 应用内帮助页：原生列表 → WebView 详情，完全离线，不依赖外部站点
- 覆盖 4 大主题域：快速上手 / 同步配置 / Outline 基本操作 / fork 新功能全集
- 中英双语同时交付（跟随系统语言）
- 上游 wiki 中仍有效的通用知识吸收重写进来，帮助页完全自包含

**非目标**

- 交互式新手引导（coach mark / onboarding 浮层）
- FAQ、排错指南等完整帮助中心形态
- 在线文档拉取与热更新（内容全部打包 APK，随版本发布）

**受众**：其他 MobileOrg 用户（发布 APK 的实际使用者），以陌生用户能看懂为标准。

## 决策总览

| 决策点 | 选定 | 主要理由 |
|---|---|---|
| 技术形态 | WebView + 打包 HTML | 复用现有 WebView 基础设施，富文档排版容易，零新增依赖 |
| 内容分发 | 全部打包 APK assets | 离线可用，内容与功能版本严格同步 |
| 信息架构 | 两级导航（列表 → 详情） | 每主题独立文件，双语好维护，可扩展 |
| 列表页实现 | 原生 RecyclerView | 自动跟随 app 两层主题体系，返回栈自然 |
| 新手方案 | 帮助页内"快速上手"章节 | 纯静态文档，不写引导代码 |
| 语言 | 中英双语同时交付 | assets 按 zh/en 目录分放 |
| 入口 | 主菜单 + 设置页 | 两处都改 |
| 上游内容 | 吸收重写，不外链 wiki | 自包含 |
| 配图 | 关键步骤 4 张截图 | 平衡直观性与维护成本 |
| 截图来源 | MI PAD 4 真机（Android 8.1） | 本地无模拟器，真机零新基础设施 |

## 架构

```
OutlineActivity 主菜单 menu_help ──┐
                                    ├──→ HelpActivity（原生列表页）
SettingsActivity "帮助" 项 ─────────┘         │ 点击主题
                                              ↓
                                   HelpDetailActivity（WebView 详情）
                                              │ loadUrl
                                              ↓
                                   assets/help/{zh|en}/{topic}.html
```

## 组件设计

### HelpActivity

- RecyclerView 列表页，展示主题清单 + 底部"关于"块
- 数据为代码内静态定义的 `HelpTopic[]`：每项含标题字符串资源、asset 文件名
- 关于块：版本号（`BuildConfig.VERSION_NAME`）、fork 仓库地址、Apache 2.0 协议

### HelpDetailActivity

- 加载 `file:///android_asset/help/{lang}/{topic}.html`
- `WebViewClient.shouldOverrideUrlLoading`：`file://` 之外的链接交给外部浏览器打开，
  WebView 内不导航离开帮助内容
- 按当前主题向 HTML 注入 `class="dark"`（见"主题适配"）

### 语言路由

- 系统中文 locale → `assets/help/zh/`，否则 → `assets/help/en/`
- 完整性由测试保证（zh/en 文件一一对应，见"测试策略"）

## 内容结构

7 篇 × 双语（`zh/` 与 `en/` 目录文件名一一对应）：

| 文件 | 主题 | 来源要点 |
|---|---|---|
| `quick-start.html` | 快速上手：配置同步 → 首次同步 → 日常 capture/edit 循环 | 新写，配 4 张截图 |
| `sync.html` | 同步配置：WebDAV/SSH/SDCard、向导、配置导入导出（SAF） | 吸收上游 wiki 同步章节 |
| `outline.html` | Outline 基本操作：长按菜单（按节点类型 4 套）、capture、编辑器、agenda、搜索、tag 过滤、widget | 吸收上游 wiki + 本 fork 行为 |
| `pomodoro.html` | Pomodoro 与计时：连续模式、休息、闹钟、timeclock | 新写（fork 功能） |
| `statistics.html` | 统计图表 | 新写（fork 功能） |
| `reminders.html` | 提醒系统：DEADLINE/SCHEDULED 通知、每日总览 | 新写（fork 功能） |
| `extras.html` | 更多功能：undo、分享子树、主题切换 | 新写（fork 功能） |

## 主题适配

- 原生列表页：自动跟随现有两层主题体系（XML 主题 + Java 主题），无需额外工作
- WebView 详情页：加载时按当前主题（dark/light/monochrome→dark 基底）向 `<html>` 注入
  `class="dark"`；HTML 内 CSS 用变量定义深浅两套配色
- 截图统一浅色主题拍摄，不随详情页主题切换（两套主题 × 双语截图组合维护成本失控）

## 截图策略

4 张图，全部服务于 quick-start 关键路径：

| 文件（最终产物 .webp） | 内容 | 对应步骤 |
|---|---|---|
| `sync-wizard.webp` | 同步向导 WebDAV 配置页 | 第 1 步"配置同步" |
| `outline-main.webp` | Outline 主界面（同步按钮、文件列表） | 第 2 步"首次同步" |
| `capture-editor.webp` | capture 编辑页 | 第 3 步"日常使用" |
| `long-press-menu.webp` | 长按节点 ActionMode 菜单 | 第 3 步，预告 Outline 操作篇 |

拍摄时中间产物为 PNG，`cwebp` 转换后入 assets 的是 WebP，HTML 引用 `.webp`。

不做箭头/圆圈标注：文字路径指引（如"右上角同步图标"）。如需高亮，用 CSS 在图上
叠加半透明圈（`position:absolute`），随文档改而不动图。

**执行流程（MI PAD 4，Android 8.1，USB adb）**

1. 前置（数据安全）：确认设备上 MobileOrg 无未同步数据 →
   `adb shell pm clear com.matburt.mobileorg` → 导入虚构 demo org 文件 →
   系统语言切英文
2. 拍摄：浅色主题，操作到目标界面后 `adb exec-out screencap -p > x.png`
3. 后处理：`cwebp -resize 1080 0` 压到每张 <100KB，放入 `assets/help/images/`，
   中英文档共用同一套图（英文界面）
4. 已知取舍（接受）：平板比例 + Android 8.1 系统状态栏样式，app 自身 UI 主题
   不受系统版本影响

**维护契约**：仅 UI 布局变化需重截（文字改动不用）；4 张清单即重截清单。

## 错误处理

- asset 缺失（打包内容，理论不发生）：WebView 显示内置错误提示页
- 外链一律外部浏览器打开
- 语言路由只分 zh / en 两档，无第三档回退链

## 测试策略

- Instrumentation（`src/androidTest/`）：
  - `HelpActivity` 列表渲染：条目数与 `HelpTopic[]` 一致
  - 点击主题 → 跳转 `HelpDetailActivity` 并携带正确 asset 路径
  - `HelpDetailActivity` WebView 成功加载对应 asset（`onPageFinished` 且无错误）
- **完整性测试**（双语交付最大回归风险）：遍历 `HelpTopic[]`，断言 `zh/` 与 `en/`
  下对应文件均存在，防止只写了一侧语言
- 手测：深浅色主题切换下详情页配色正确；外链跳浏览器

## 入口改动

- `OutlineActivity.runHelp()`：删除死 wiki URL，改为启动 `HelpActivity`
- `SettingsActivity`：新增帮助项。XML preference 用 `android:targetPackage` +
  `android:targetClass`（遵循项目已知坑，不用隐式 action）
- `strings.xml` + `values-zh/`：补帮助相关字符串条目

## 关联文件（实现时涉及）

- 新增：`Gui/Help/HelpActivity.java`、`Gui/Help/HelpDetailActivity.java`、
  `res/layout/help*.xml`、`assets/help/{zh,en}/*.html`、`assets/help/images/*`
- 修改：`Gui/Outline/OutlineActivity.java`（runHelp）、`Settings/SettingsActivity.java`
  + 对应 preference XML、`res/menu/outline_menu.xml`（如需）、strings 资源
- 参考实现：`Gui/ViewActivity.java` / `ViewFragment.java`（WebView 用法）
