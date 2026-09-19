# 自定义主题设计

## 背景

主题系统是两层独立体系：

1. **XML 主题层**（`themes.xml` + `OrgUtils.setTheme()`）——ActionBar、对话框、preference 页的结构性外观。现有 3 个 style：`Theme.MobileOrg.Dark` / `.Light` / `.Monochrome`，全部手调硬编码。
2. **Java 主题层**（`Gui/Theme/DefaultTheme` + `WhiteTheme`/`MonoTheme`）——outline 渲染颜色。public 字段在构造器里写死，工厂方法 `DefaultTheme.getTheme(Context)` 按主题名字符串分发。

用户无法调色。本设计新增**自定义主题（Custom Theme）**：应用内逐项调色，选中后两层作为一个单元一致应用。

## 目标

- 设置中主题选项新增"自定义"；提供编辑器：逐项调色 + 实时预览 + 从预设复制起步 + 导出/导入备份
- 自定义主题对 outline 渲染**全量可控**（暴露实际被消费的颜色项）
- 系统UI（windowBackground、ActionBar、statusBar）跟随自定义主色调，不出现亮暗割裂

## 非目标（本期）

- HTML 预览（`OrgRenderer`）逐色自定义——第二阶段；届时开放全部 ANSI 色
- Widget、`TimeclockDialog`、录制条等硬编码颜色——维持现状
- 主题随 org 同步漫游——纯本地
- DayNight / 跟随系统深色模式

## 约束

- **运行时无法生成 style 资源**：XML 层自定义只能"基底 style + 程序化覆盖"（见 ADR 0004）
- 两层必须一致应用（领域规则：主题从不部分应用）
- `themes` 与 `themesEntries` 两数组必须保持平行（`LanguageSwitchTest` 守护）
-Preference 入口 intent 必须用 `targetPackage`/`targetClass`（CLAUDE.md 坑）
- 编辑器遵循"预览不是提交"原则：调色只改内存工作集，点保存才落库

## 领域模型

见 `CONTEXT.md`：**Theme / Preset Theme / Custom Theme / Theme Base**。

不变式：
1. 主题作为单元整体应用——任何 UI 表面不得绕过主题系统（本期顺手修复 Agenda 违约，见 §9）
2. 复刻规则——Custom 复制任一 Preset 的全部暴露色值后，可见渲染结果与该 Preset 等价

## 设计

### 1. 数据模型（SharedPreferences，默认实例）

| key | 类型 | 默认 | 说明 |
|-----|------|------|------|
| `custom_theme_base` | String | `"Dark"` | 基底：`Dark` / `Light` / `Monochrome` |
| `custom_theme_revision` | String | `""` | 修订号（保存时间戳 millis 的字符串），刷新检测用 |
| `custom_theme_gray` | String(hex) | Dark 种子 | tags、COMMENT/Archive |
| `custom_theme_c1` | String(hex) | Dark 种子 | active TODO |
| `custom_theme_ca` | String(hex) | Dark 种子 | inactive TODO |
| `custom_theme_c3` | String(hex) | Dark 种子 | 优先级 |
| `custom_theme_c4` | String(hex) | Dark 种子 | 链接 |
| `custom_theme_fg` | String(hex) | Dark 种子 | 前景（子节点指示、agenda 块） |
| `custom_theme_bg` | String(hex) | Dark 种子 | 背景（outline/window/WebView） |
| `custom_theme_levelColors` | String(CSV) | Dark 种子 | 10 个层级色，`#rrggbb` 逗号分隔 |

**暴露集合的确定依据**（outline 实际消费，`OutlineItem` 逐处核实）：
`gray`（setupTags/setupTitle）、`c1Red`/`caLGreen`（setupTodo）、`c3Yellow`（setupPriority）、`c4Blue`（formatLinks）、`defaultForeground`（setupChildrenIndicator/setupAgendaBlock）、`levelColors[level % len]`（applyLevelFormating，len=10）、`defaultBackground`（ViewFragment/PayloadFragment WebView 背景）。其余 ANSI 字段（c0/c2/c5/c6/c7/c9/cb/cc/cd/ce）无消费点，不暴露。

`defaultFontColor`（`"white"`/`"black"` 二值，`OrgRenderer.wrapInTemplate` 消费）**不做 UI**，由背景亮度推导：`0.299R + 0.587G + 0.114B > 0.5×255 → "black"`，否则 `"white"`。对三个预设的推导结果与现值一致（Dark→white、White/Mono→black）。

空存储 = Dark 种子（`CustomTheme` 未命中 key 时保留 `super()` 的 Dark 默认值），天然实现"从 Dark 复制起步"。

### 2. Java 主题层

新类 `Gui/Theme/CustomTheme extends DefaultTheme`：构造器从 SharedPreferences 读 16 项，hex 解析失败（`Color.parseColor` 抛 `IllegalArgumentException`）时保留超类默认值——损坏数据只会回落种子，不会崩溃。

`DefaultTheme.getTheme()` 工厂增加分支：`"Custom"` → `new CustomTheme()`。

新类 `Gui/Theme/CustomThemeStore`：key 常量 + 静态方法
- `loadAll()` / `saveAll(Map)`（save 内部写修订号）
- `presetValues(String presetName)` — 实例化 Default/Mono/White，输出 16 项 map；levelColors 按源长度取模填充到 10 槽（Mono 5 色循环展开，保证复刻）
- `getBase()` / `getRevision()`

### 3. 主题名解析与 XML 层

`OrgUtils` 新增统一解析：

```java
public static String getEffectiveThemeName() {
    String name = PreferenceUtils.getThemeName();
    return "Custom".equals(name) ? CustomThemeStore.getBase() : name;
}
```

- `setTheme(Activity)`：先 `getEffectiveThemeName()`，再走既有三分支（Dark→Dark style、Monochrome→Mono style、其余→Light style）
- `isDarkTheme()`：改用 effective name（语义不变：Dark/Monochrome 为深）
- `ChartThemeConfig.current()`：改用 effective name，映射关系自然成立（Dark 基底→DARK、Monochrome→MONO、Light→LIGHT）

数据侧：`themes` 数组（`values/arrays.xml:60-64`）追加 `"Custom"`；`themesEntries`（英文 + `values-zh`）追加显示名（`Custom` / `自定义`）。`preferences.xml` 的 ListPreference 引用数组，无需改动。

### 4. 程序化覆盖（XML 层主色调）

`OrgUtils.applyCustomThemeWindow(Activity)`，非 Custom 直接 return：

```java
window.setBackgroundDrawable(new ColorDrawable(bg));
window.setStatusBarColor(darken(bg, 0.6f));
ActionBar ab = activity.getSupportActionBar();
if (ab != null) {
    ab.setBackgroundDrawable(new ColorDrawable(darken(bg, 0.75f)));
    ab.setTitle(colored(ab.getTitle(), fg));   // ForegroundColorSpan
}
```

`darken(color, factor)` = RGB 各通道乘系数。固定公式、零额外 UI。在各 Activity `setContentView()` 之后调用（10 处一行）；framework `PreferenceActivity`（SettingsActivity）无 supportActionBar，只覆盖 window/statusBar。

### 5. 编辑器 UI

`Gui/Theme/ThemeEditorActivity`（AppCompatActivity，manifest 注册，`OrgUtils.setTheme()` + overlay 照常）。设置入口：`preferences.xml` 主题项下方 `<Preference>` + intent（`targetPackage`/`targetClass`，仿 wizard/help 写法），常驻可见。

```
┌──────────────────────────────┐
│ [预览块]                      │ ← 模拟 outline 条目，改色即刷
│  █ #A TODO 学日语 :work:      │    优先级=c3、TODO=c1、tags=gray、标题=fg
│     带链接的 [[org][org mode]]│    层级缩进=levelColors[2]、链接=c4
│  DONE 已完成条目              │    inactive TODO=ca；背景=bg
├──────────────────────────────┤
│ 基底  (•)深色 ( )浅色 ( )暖纸  │ ← RadioGroup
├──────────────────────────────┤
│ 基础色                        │
│  背景    [██]  前景    [██]   │
│  灰(tags) [██]                │
│ 语义色                        │
│  活跃TODO [██] 完成TODO [██]  │
│  优先级   [██] 链接     [██]  │
│ 层级色                        │
│  1[██] 2[██] 3[██] 4[██] 5[██]│
│  6[██] 7[██] 8[██] 9[██] 10[██]│
├──────────────────────────────┤
│ [从预设复制]  [重置]  [保存]   │
└──────────────────────────────┘
```

行为：
- 颜色行 label + 色块按钮，点击弹 `HsvColorPickerDialog`；确定后只更新**内存工作集** + 色块 + 预览块（"预览不是提交"）
- **从预设复制**：AlertDialog 三选（深色/浅色/暖纸）→ `CustomThemeStore.presetValues()` 合入工作集 → 刷新
- **重置**：工作集清空（= Dark 种子）→ 刷新
- **保存**：`saveAll()`（内部写 revision）→ toast → `finish()`。无条件完成（用户主动点击必须有确定性结果）
- 返回键：有未保存修改时弹确认对话框，确认后丢弃退出
- 16 行颜色行由代码按 `{key, labelRes}` 描述数组构建，布局只放容器

### 6. 颜色选择器

`Gui/Theme/HsvColorPickerView`（自绘 View，~150 行）：SV 二维渐变方块 + Hue 竖条，十字游标，无 alpha（主题色全部不透明）。`HsvColorPickerDialog` = `AlertDialog` 包该 View + 确定/取消，构造时传入当前颜色，确定回调 `onColorChanged(int)`。

### 7. 统计图表

`ChartThemeConfig.current()` 用 effective name 分发（§3），零新增 UI。

### 8. 导出/导入

`SyncConfigHelper.SYNC_KEYS` 追加全部 `custom_theme_*` key（含 base/revision/levelColors CSV，均为 String 类型，现有 boolean/string 分支直接可用）。`FORMAT_VERSION` 不动——加 key 对旧版本只是被忽略的未知字段，双向兼容。

### 9. 顺手修复：Agenda 主题失联（既有 bug）

`AgendaActivity`/`AgendasActivity` 不调 `setTheme()`，永远显示 manifest 默认 Dark——违反"主题整体应用"不变式。修复：两处 onCreate 加 `OrgUtils.setTheme(this)` + overlay 调用。

### 10. 刷新机制（修订号）

选了 Custom 后再改色，`themeName` 恒为 `"Custom"`，`OutlineActivity.onResume()` 现有的"themeName 变了才 recreate"不会触发。修复：`OutlineActivity` onCreate 同时缓存 `themeName + revision`，onResume 两者任一变化即 `recreate()`。编辑器保存必然 bump revision，outline 返回前台即刷新。

## 验收标准

1. **复刻**（自动化）：`CustomThemeStore.presetValues("Light"/"Monochrome"/"Dark")` 写入后，`CustomTheme` 的 16 项字段与对应预设实例逐项相等；levelColors 按取模展开等价；`defaultFontColor` 推导值与预设现值一致
2. **解析**（自动化）：`getEffectiveThemeName()` 对 Custom→base 的映射；`isDarkTheme()`、`ChartThemeConfig.current()` 跟随基底
3. **容错**（自动化）：垃圾 hex 只回落种子值
4. 全量 `connectedDebugAndroidTest` 通过（含 `LanguageSwitchTest` 数组平行）
5. 手测清单见实施计划

## 文件清单

| 文件 | 操作 |
|------|------|
| `Gui/Theme/CustomTheme.java` | 新增 |
| `Gui/Theme/CustomThemeStore.java` | 新增 |
| `Gui/Theme/HsvColorPickerView.java` | 新增 |
| `Gui/Theme/HsvColorPickerDialog.java` | 新增 |
| `Gui/Theme/ThemeEditorActivity.java` | 新增 |
| `res/layout/activity_theme_editor.xml` | 新增 |
| `res/layout/view_hsv_color_picker.xml` | 新增 |
| `Gui/Theme/DefaultTheme.java` | 修改（工厂分支） |
| `util/OrgUtils.java` | 修改（effective name + applyCustomThemeWindow） |
| `Gui/Statistics/ChartThemeConfig.java` | 修改（effective name） |
| `util/SyncConfigHelper.java` | 修改（key 白名单） |
| `Gui/Outline/OutlineActivity.java` | 修改（revision 比对 + overlay） |
| `Gui/Agenda/AgendaActivity.java`、`AgendasActivity.java` | 修改（setTheme + overlay） |
| 其余 8 个调 `setTheme` 的 Activity | 修改（overlay 一行） |
| `res/values/arrays.xml`、`values-zh/arrays.xml` | 修改（Custom 项） |
| `res/xml/preferences.xml` | 修改（编辑器入口） |
| `res/values/strings.xml`、`values-zh/strings.xml` | 修改 |
| `AndroidManifest.xml` | 修改（注册编辑器） |
| `androidTest/.../test/Gui/Theme/CustomThemeTest.java` | 新增 |

## 第二阶段（HTML 预览，另立设计）

开放全部 ANSI 色；`OrgRenderer` 代码块/table CSS 按自定义色生成（当前恒暗，Light 主题下已是既有缺陷）；帮助页暗色注入从"基底"升级为"实际背景亮度"。
