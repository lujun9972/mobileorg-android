# ADR 0004: Custom Theme as Preset Base + Programmatic Overlay

## Status

Accepted

## Context

主题系统是两层独立体系：XML 主题（`themes.xml`，控制 ActionBar/对话框/preference 的结构性外观）与 Java 主题（`DefaultTheme` 及子类，控制 outline 渲染颜色）。内置三个主题（Default/Mono/White）全部硬编码——构造器字面量 + 三路字符串分发，用户无法逐项调色。

两个平台约束排除了直觉方案：

1. **style 资源是编译期产物**——运行时无法为任意用户颜色生成 XML style，"给 Custom 主题写一个 themes.xml 条目"不存在。
2. **MaterialComponents 主题属性在 inflate 时解析**——`setContentView()` 后修改主题属性不影响已加载控件；逐 widget 程序化染色要侵入所有布局、对话框、preference 页，遗漏任何一处都是亮暗打架，不可维护。

此外存在第三套平行色彩系统 `ChartThemeConfig`（统计图表，按主题名分发 LIGHT/DARK/MONO），与 `DefaultTheme` 互不复用。

### Alternatives considered

1. **运行时生成 style** — 平台不支持。
2. **逐 widget 程序化染色实现全量 XML 层自定义** — 覆盖面是无底洞（对话框、preference、菜单、chip），任何遗漏即视觉割裂；放弃。
3. **仅基底选择、不做颜色覆盖** — 最简单，但深色 outline 配亮色 ActionBar 的割裂正是本功能要消灭的；放弃。
4. **基底 + 程序化覆盖 windowBackground/ActionBar** (chosen) — 基底 style 决定结构性外观，运行时 API（`setBackgroundDrawable`/`setStatusBarColor`）覆盖主色调；outline 层全量自由。
5. **图表配色进编辑器** — 逐色编辑图表收益极低；按基底映射（Dark 基底→DARK 配色）已消除亮暗冲突。
6. **主题随 org 同步漫游** — 主题是设备外观偏好，塞进同步协议复杂度远大于收益；纯本地 + 复用 `SyncConfigHelper` 的 SAF 导出/导入（白名单加 key）即可备份。

## Decision

- 新增 preference 值 `"Custom"`。选中后：XML 层使用自定义主题存储的**基底**（Dark/Light/Mono 三 style 之一）；Java 层使用 `CustomTheme`（从 SharedPreferences 加载 16 个被消费的颜色）；统计图表配色按基底映射。
- **暴露的颜色集合 = outline 渲染实际消费的 16 项**（gray、c1Red、caLGreen、c3Yellow、c4Blue、defaultForeground、defaultBackground、levelColors×10）。未消费的 ANSI 字段不暴露，留给 HTML 预览阶段。`defaultFontColor` 不做 UI，由背景亮度推导。
- 系统程序化覆盖：windowBackground = 自定义背景色；statusBar/ActionBar 背景 = 背景色按固定公式加深；ActionBar 标题色 = defaultForeground。
- 内置三主题保持只读预设；编辑器提供"从预设复制"起步。**验收标准：Custom 能按可见渲染结果复刻任一预设。**
- 刷新机制：颜色修改不改变 themeName，OutlineActivity 比对 themeName + 自定义主题修订号决定 recreate。

## Consequences

- 颜色修改后，非 Outline 的 Activity 需重进才生效（与既有主题变更行为一致，接受）。
- Help 页暗色注入跟随基底而非实际背景亮度：Custom + Light 基底 + 深背景时 help 仍是浅色 CSS。已知边界，接受。
- `SyncConfigHelper` 导出文件从此含主题 key；旧版本导入新文件时忽略未知 key，双向兼容。
- 层级色以 CSV 单 key 存储（10 个 hex 逗号分隔）而非 10 个独立 key——编辑器整体读写，导出白名单只加一个 key。
- 后续给 outline 添加新颜色消费点时，必须同步扩展 CustomTheme 的 16 项集合，否则自定义主题在新的消费点回落到 Dark 种子值（复刻验收会当场抓出）。
