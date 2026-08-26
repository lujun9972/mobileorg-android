# 中英文双语界面 设计规格

**日期**：2026-08-27
**状态**：已批准

## 背景与目标

`values-zh/strings.xml` 仅覆盖 166/314 条（缺 151 条，48%），缺失集中在 fork 新功能（番茄钟、计时、归档、撤销、分享、提醒、菜单项）；`values-zh/arrays.xml` 不存在（135 个 array item 全显英文）；菜单 XML 有 3 处硬编码 `"Save"`；无应用内语言切换（纯跟随系统）。

目标：
1. 补全中文翻译，消除界面中英混杂
2. 增加应用内语言切换（跟随系统 / 中文 / English，默认跟随系统）

非目标：
- 不动 de / es 上游遗留翻译（保留资源，不出现在系统 per-app 语言面板）
- 不翻译 org 文件内容本身（节点名、文件名）
- 不做中文输入法、字体等无关国际化

## 方案

采用 **AppCompat 1.6 per-app language 标准方案**：`AppCompatDelegate.setApplicationLocales()`。API 33+ 接入系统「应用语言」设置；API <33 由 AppCompat 兼容层自动持久化并 recreate 所有 AppCompatActivity。项目 AppCompat 1.6.1，满足前提。

已否决的替代方案：
- 手动 `attachBaseContext` wrap：代码多、Service/Notification context 覆盖不到，坑多
- 仅引导系统设置：API <33 无解

## 详细设计

### 1. 语言切换

- `SettingsActivity`（设置主页）**顶部**新增 ListPreference：
  - key：`app_language`
  - 标题：`@string/preference_app_language`（en "Language" / zh "语言"）
  - 选项与值：`跟随系统` = `system`（默认）｜`中文` = `zh`｜`English` = `en`
  - 选项文字随当前语言本地化显示（存于 strings/arrays，值存 SharedPreferences 由 AppCompat 管理）
- 值变化处理：`system` → `setApplicationLocales(LocaleListCompat.getEmptyLocaleList())`；`zh` → `setApplicationLocales(forLanguageTags("zh"))`；`en` → 同理。切换后 AppCompat 自动 recreate，立即生效，无需手动重启
- 初始化：SettingsActivity 创建时以 `getApplicationLocales()` 回显当前状态（AppCompat 已持久化，防状态漂移）
- manifest：`<application android:localeConfig="@xml/locales_config">`；`res/xml/locales_config.xml` 声明 `en`、`zh`
- 帮助中心联动：`HelpTopic.getAssetPath` 读 `Configuration.locale`，随切换自动选 `help/{zh,en}/`，无需改动

**已知限制**（接受，不修）：
- API <33 上非 AppCompatActivity 的对话框式 Activity（`TimeclockDialog`）与 Application context（通知标题）可能仍按系统语言——对话框生命周期极短、通知场景少
- API 33+ 系统 per-app 面板与应用内设置双向同步由框架保证

### 2. 翻译补全

- **values-zh/strings.xml**：补 151 条缺失中真正显示给用户的 UI 文案；跳过 `key_*` 纯 preference 常量（不显示）与 Ubuntu One 死服务文案（`log_in_to_ubuntuone` 等）
- **新建 values-zh/arrays.xml**：翻译用户可见 display 数组（`syncIntervals`、`themeNames`、`storageModes`、`fileSources`、提醒提前量等约 10 组）；`*Vals` 值数组不翻
- **硬编码修复**：3 处菜单 XML `android:title="Save"` → `@string/menu_save`（en "Save" / zh "保存"）
- **术语表**（对齐现有 166 条风格）：capture=捕获、outline=大纲、sync=同步、pomodoro=番茄钟、agenda=日程、clock in/out=开始/结束计时、deadline=截止日期、scheduled=日程、undo=撤销、share=分享
- 翻译由 AI 起草直接定稿，不逐条人工审校

### 3. 测试与验收

- **instrumentation 回归**：
  - 设置语言后 `AppCompatDelegate.getApplicationLocales()` 返回目标 locale
  - 切换后 `Resources.getString` / `Configuration.locale` 反映目标语言
  - `menu_save` 抽取后菜单功能不回归
- **手测走查**：系统英文 + 应用内选中文 → 主界面、设置页、各级菜单、帮助中心、对话框无英文残留（org 文件内容除外）；切回跟随系统恢复正常
- CI 全量测试双绿

## 组件与文件清单（实现计划的输入）

| 文件 | 动作 |
|---|---|
| `MobileOrg/src/main/res/xml/locales_config.xml` | 新建（en/zh） |
| `MobileOrg/src/main/AndroidManifest.xml` | 加 `android:localeConfig` |
| `MobileOrg/src/main/res/values/strings.xml` | 加 `preference_app_language`、`menu_save` 及语言选项 strings |
| `MobileOrg/src/main/res/values/arrays.xml` | 加语言选项 display 数组 |
| `MobileOrg/src/main/res/values-zh/strings.xml` | 补全缺失翻译 |
| `MobileOrg/src/main/res/values-zh/arrays.xml` | 新建（display 数组中文） |
| `MobileOrg/src/main/res/xml/settings_main.xml`（设置主页） | 顶部加 ListPreference |
| `SettingsActivity.java` | 值变化监听调 setApplicationLocales + 回显 |
| 3 个 menu XML（agenda_block/agenda_entry/edit） | `"Save"` → `@string/menu_save` |
| `MobileOrg/src/androidTest/...` | 新增语言切换回归测试 |
