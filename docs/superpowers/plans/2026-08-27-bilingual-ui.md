# 中英文双语界面 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 补全中文翻译消除界面中英混杂（双向：默认资源 53 条中文硬编码英文化 + zh 侧 129 条补全），并新增应用内语言切换（跟随系统/中文/English），采用 AppCompat 1.6 per-app language 标准方案。

**架构：** `AppCompatDelegate.setApplicationLocales()` 为核心机制（API 33+ 接系统面板，API <33 由 AppCompat 兼容层持久化 + 自动 recreate AppCompatActivity）。5 个非 AppCompat 的 framework Activity（SettingsActivity、WizardActivity、3 个同步设置页）通过轻量 `attachBaseContext` wrap 读同一状态（仅 4 行代码，非规格否决的全局 wrap 方案），保证规格手测验收"设置页无英文残留"在 API <33 设备（MI PAD 4 / API 27）上可过。

**技术栈：** AppCompat 1.6.1（已满足）、Android 资源限定符 `values-zh/`、`res/xml/locale-config`。

---

## 共享约定（所有任务适用）

### 术语表（zh 翻译必须对齐）

capture=捕获、outline=大纲、sync=同步、pomodoro=番茄钟、agenda=日程、clock in/out=开始/结束计时、deadline=截止日期、scheduled=日程、undo=撤销、share=分享、archive=归档、wizard=向导、statistics=统计。

### 关键决策（实现时不可偏离）

1. **默认资源里的 53 条中文硬编码必须对调**（任务 2 ↔ 任务 3 配对）：`values/strings.xml` 与 `values/arrays.xml` 中 sync_config/提醒/番茄钟/统计组的中文改回英文，中文值搬入 `values-zh/`。英文用户当前看到中文，同样是"中英混杂"bug。
2. **`themes` 数组 entries=vals 共用**（`preferences.xml:57-58` 两处都引用 `@array/themes`，存储值 Dark/Light/Monochrome 被 `OrgUtils.setTheme()` 按值匹配）——绝不能直接翻译。必须拆出 `themesEntries` 数组：entries 用新数组（可翻译），vals 保持 `themes` 不动。
3. **`agendaSpanValues` 的显示文本参与逻辑判断**（`AgendaEntrySetting.java:88` `selected.equalsIgnoreCase("Custom")`）——翻译前必须把判断改为 position（Custom 固定为数组末尾项）。
4. **`storageModes` 数组零引用**（死数组），不翻译。`*Vals`/`*_values` 值数组一律不翻译。
5. 跳过不翻：`key_*` preference 常量 13 条（不显示）、Ubuntu One/Dropbox 死服务文案 8 条（`log_in_to_ubuntuone`、`dropbox_login_info`、`preference_ubuntuone_*` 4 条、`wizard_ubuntu_email_hint`、`wizard_ubuntuone`）、`example_webURL`（URL 值无语言差异）。
6. **帮助中心无需改动**：`HelpTopic.getAssetPath` 读 `Configuration.locale`，语言切换后自动选 `help/{zh,en}/`。
7. **XML 转义**：含 `%s`/`%1$s` 的保持原样；`\n` 保持转义；引号用中文引号「」或全角，避免 `\"` 转义麻烦。
8. **验证方法**（本地无构建环境，CI 验证）：资源任务用 grep/comm 脚本核对条目数与 name 集合；Java/XML 良构由脚本粗查 + CI `assembleDebug` 最终把关。
9. **commit 规范**：`type(scope): 描述`，只 `git add` 具体文件路径（绝不用 `git add -A`，仓库有 .superpowers/ 等未跟踪杂物）。

### 本计划不包含（收尾阶段做）

README changelog、版本 bump/tag、CLAUDE.md 坑记录。手测走查在 Wave FINAL F3。

---

### 任务 1：locales_config + manifest 接入

**依赖：** 无
**文件集：** `MobileOrg/src/main/res/xml/locales_config.xml`, `MobileOrg/src/main/AndroidManifest.xml`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 创建：`MobileOrg/src/main/res/xml/locales_config.xml`
- 修改：`MobileOrg/src/main/AndroidManifest.xml:17`（application 标签）

- [ ] **步骤 1：创建 locales_config.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en"/>
    <locale android:name="zh"/>
</locale-config>
```

- [ ] **步骤 2：manifest application 标签加 android:localeConfig**

`MobileOrg/src/main/AndroidManifest.xml` line 17 的 `<application` 标签属性中加一行（保持既有属性不动）：

```xml
android:localeConfig="@xml/locales_config"
```

- [ ] **步骤 3：机械校验**

运行：
```bash
python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('MobileOrg/src/main/res/xml/locales_config.xml'); print('OK')"
grep -c 'android:localeConfig="@xml/locales_config"' MobileOrg/src/main/AndroidManifest.xml
```
预期：`OK`；`1`。

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/res/xml/locales_config.xml MobileOrg/src/main/AndroidManifest.xml
git commit -m "feat(i18n): locales_config 声明 en/zh，manifest 接入 per-app language"
```

---

### 任务 2：默认资源英文化 + 显示/逻辑耦合修复

**依赖：** 无
**文件集：** `MobileOrg/src/main/res/values/strings.xml`, `MobileOrg/src/main/res/values/arrays.xml`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Agenda/AgendaEntrySetting.java`
**导出/变更接口：** `strings.xml::menu_save`, `strings.xml::preference_app_language`, `strings.xml::app_language_system`, `strings.xml::app_language_chinese`, `strings.xml::app_language_english`, `arrays.xml::themesEntries`, `arrays.xml::app_language_entries`, `arrays.xml::app_language_values`
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/res/values/strings.xml`（53 条改英文 + 新增 5 条）
- 修改：`MobileOrg/src/main/res/values/arrays.xml`（5 组改英文 + 新增 3 组）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Agenda/AgendaEntrySetting.java:86-92`（Custom 判断改 position）

- [ ] **步骤 1：strings.xml 新增 5 条（menu_save + 语言切换）**

在 `<!-- others -->` 注释块（`undo_menu` 之前）插入：

```xml
<string name="menu_save">Save</string>
```

在文件末尾 `<!-- help center -->` 块之前插入：

```xml
<!-- in-app language switch -->
<string name="preference_app_language">Language</string>
<string name="app_language_system">Follow system</string>
<string name="app_language_chinese">Chinese</string>
<string name="app_language_english">English</string>
```

- [ ] **步骤 2：strings.xml 53 条中文硬编码改英文**

逐条**原地替换值**（name 不动，位置不动）。对照表（name → 新英文值）：

sync_config 组：
| name | 新英文值 |
|---|---|
| preference_export_sync_config | Export sync config |
| preference_export_sync_config_summary | Export sync settings to a file |
| preference_import_sync_config | Import sync config |
| preference_import_sync_config_summary | Restore sync settings from a file |
| sync_config_export_success | Sync config exported |
| sync_config_export_failed | Export failed:\n%s |
| sync_config_import_success | Sync config imported; re-enter settings to verify |
| sync_config_import_failed | Import failed:\n%s |
| sync_config_no_data | No sync config to export |

提醒设置组：
| name | 新英文值 |
|---|---|
| preference_reminder | Reminders |
| preference_reminder_enabled | Enable reminders |
| preference_reminder_enabled_summary | Notify when DEADLINE and SCHEDULED items are due |
| preference_reminder_deadline_advance | DEADLINE advance |
| preference_reminder_scheduled_advance | SCHEDULED advance |
| preference_reminder_daily_overview_time | Daily overview time |

提醒通知组：
| name | 新英文值 |
|---|---|
| reminder_notification_channel | Task reminders |
| reminder_notification_channel_desc | DEADLINE and SCHEDULED due reminders |
| reminder_daily_overview_title | Today's tasks |
| reminder_daily_overview_empty | Nothing scheduled today |
| reminder_scheduled_today | Scheduled today |
| reminder_deadline_upcoming | Upcoming deadlines |
| reminder_days_later | In %d days |
| reminder_tomorrow | Tomorrow |

番茄钟设置组：
| name | 新英文值 |
|---|---|
| preference_pomodoro_duration | Pomodoro duration |
| preference_pomodoro_duration_summary | Default countdown duration in minutes |
| pomodoro_duration_picker_title | Set pomodoro duration (minutes) |
| preference_pomodoro_count_default | Default consecutive pomodoros |
| preference_pomodoro_count_default_summary | Default count when starting consecutive mode |
| preference_pomodoro_short_break | Short break duration |
| preference_pomodoro_short_break_summary | Break between pomodoros in minutes |
| preference_pomodoro_long_break | Long break duration |
| preference_pomodoro_long_break_summary | Long break after every N pomodoros, in minutes |
| preference_pomodoro_long_break_interval | Long break interval |
| preference_pomodoro_long_break_interval_summary | Number of pomodoros before a long break |
| pomodoro_picker_title | Set pomodoro |
| pomodoro_count_picker_title | Consecutive pomodoros |
| pomodoro_active_toast | A pomodoro is already running |

统计组：
| name | 新英文值 |
|---|---|
| menu_statistics | Pomodoro statistics |
| statistics_title | Pomodoro statistics |
| statistics_tab_overview | Overview |
| statistics_tab_trend | Trend |
| statistics_streak_format | 🔥 %d-day streak |
| statistics_streak_empty | Start your first pomodoro! |
| statistics_streak_subtitle | At least 1 pomodoro per day |
| statistics_summary_day_format | Today: %d \| %s |
| statistics_summary_week_format | Week: %1$d \| %2$s \| %3$.1f/day |
| statistics_summary_month_format | Month: %1$d \| %2$s \| %3$.1f/day |
| statistics_no_data | No data yet |
| preference_week_start_day | Week starts on |
| preference_week_start_day_summary | First day of week on the statistics screen |
| monday | Monday |
| sunday | Sunday |
| duration_minutes_format | %d min |

- [ ] **步骤 3：arrays.xml 5 组中文改英文 + 新增 3 组**

5 组原地替换（name 与 item 数不动）：

```xml
<string-array name="reminder_advance_labels">
    <item>Same day</item>
    <item>1 hour ahead</item>
    <item>3 hours ahead</item>
    <item>1 day ahead</item>
    <item>3 days ahead</item>
</string-array>
```

```xml
<string-array name="pomodoro_duration_entries">
    <item>15 min</item>
    <item>20 min</item>
    <item>25 min</item>
    <item>30 min</item>
    <item>45 min</item>
    <item>60 min</item>
</string-array>
```

```xml
<string-array name="pomodoro_count_entries">
    <item>1</item>
    <item>2</item>
    <item>3</item>
    <item>4</item>
    <item>5</item>
    <item>6</item>
    <item>7</item>
    <item>8</item>
    <item>9</item>
    <item>10</item>
</string-array>
```

```xml
<string-array name="pomodoro_break_entries">
    <item>0 min (no countdown)</item>
    <item>3 min</item>
    <item>5 min</item>
    <item>10 min</item>
    <item>15 min</item>
    <item>20 min</item>
    <item>30 min</item>
</string-array>
```

```xml
<string-array name="pomodoro_interval_entries">
    <item>Every 3</item>
    <item>Every 4</item>
    <item>Every 5</item>
    <item>Every 6</item>
    <item>Every 8</item>
    <item>Every 10</item>
    <item>Every 12</item>
</string-array>
```

文件末尾 `</resources>` 前新增 3 组：

```xml
<!-- Display-only copy of themes; entryValues must stay @array/themes (stored values) -->
<string-array name="themesEntries">
    <item>Light</item>
    <item>Dark</item>
    <item>Monochrome</item>
</string-array>

<!-- In-app language switch; entries resolve @string so values-zh only translates the 3 strings -->
<string-array name="app_language_entries">
    <item>@string/app_language_system</item>
    <item>@string/app_language_chinese</item>
    <item>@string/app_language_english</item>
</string-array>
<string-array name="app_language_values">
    <item>system</item>
    <item>zh</item>
    <item>en</item>
</string-array>
```

- [ ] **步骤 4：AgendaEntrySetting.java Custom 判断改 position**

`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Agenda/AgendaEntrySetting.java` 的 `spanView.setOnItemSelectedListener` 内 `onItemSelected`，将：

```java
String selected =
    parentView.getItemAtPosition(position).toString();
if (selected.equalsIgnoreCase("Custom")) {
```

替换为（Custom 固定为 `agendaSpanValues` 末尾项，翻译显示文本后判断仍成立）：

```java
if (position == parentView.getAdapter().getCount() - 1) {
```

删除不再使用的 `String selected` 局部变量（若无其他引用）。

- [ ] **步骤 5：机械校验**

运行：
```bash
grep -c 'name="menu_save"\|name="preference_app_language"' MobileOrg/src/main/res/values/strings.xml
grep -c '分钟\|当天\|每 [0-9]' MobileOrg/src/main/res/values/arrays.xml
grep -c 'name="themesEntries"\|name="app_language_entries"\|name="app_language_values"' MobileOrg/src/main/res/values/arrays.xml
grep -n 'equalsIgnoreCase("Custom")' MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Agenda/AgendaEntrySetting.java
python3 -c "import xml.dom.minidom; [xml.dom.minidom.parse(p) for p in ['MobileOrg/src/main/res/values/strings.xml','MobileOrg/src/main/res/values/arrays.xml']]; print('XML OK')"
```
预期：`2`；`0`（默认 arrays 无中文残留）；`3`；无输出（Custom 文本判断已移除）；`XML OK`。

strings.xml 中文残留检查（`storageModes` 死数组不在 strings）：
```bash
grep -n '[\x{4e00}-\x{9fff}]' MobileOrg/src/main/res/values/strings.xml -P || echo "无中文残留"
```
预期：`无中文残留`。

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/res/values/strings.xml MobileOrg/src/main/res/values/arrays.xml MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Agenda/AgendaEntrySetting.java
git commit -m "fix(i18n): 默认资源 53 条中文硬编码英文化，拆分 themesEntries，agenda span 判断改 position"
```

---

### 任务 3：values-zh 翻译补全（129 条 strings + 12 组 arrays）

**依赖：** 无
**文件集：** `MobileOrg/src/main/res/values-zh/strings.xml`, `MobileOrg/src/main/res/values-zh/arrays.xml`
**导出/变更接口：** `values-zh/strings.xml::menu_save`, `values-zh/strings.xml::preference_app_language`（及 129 条新增条目）, `values-zh/arrays.xml::themesEntries`（及 12 组数组）
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/res/values-zh/strings.xml`（追加 129 条：53 条从默认资源照搬 + 76 条新译）
- 创建：`MobileOrg/src/main/res/values-zh/arrays.xml`

与任务 2 配对：任务 2 把默认值改英文的同时，这里必须落中文副本（语义配对靠本对照表，两任务可并行）。

- [ ] **步骤 1：values-zh/strings.xml 追加 129 条**

在文件末尾 `</resources>` 前追加（分组注释可保留）。

**A 组：53 条照搬**（当前在 `values/strings.xml` 中的中文值，照抄不改；任务 2 正在把默认侧改英文）：

```xml
<!-- sync config import/export -->
<string name="preference_export_sync_config">导出同步配置</string>
<string name="preference_export_sync_config_summary">将同步设置导出到文件</string>
<string name="preference_import_sync_config">导入同步配置</string>
<string name="preference_import_sync_config_summary">从文件恢复同步设置</string>
<string name="sync_config_export_success">同步配置导出成功</string>
<string name="sync_config_export_failed">导出失败:\n%s</string>
<string name="sync_config_import_success">同步配置导入成功，请重新进入设置页确认</string>
<string name="sync_config_import_failed">导入失败:\n%s</string>
<string name="sync_config_no_data">没有可导出的同步配置</string>

<!-- reminder settings -->
<string name="preference_reminder">任务提醒</string>
<string name="preference_reminder_enabled">启用提醒</string>
<string name="preference_reminder_enabled_summary">DEADLINE 和 SCHEDULED 到期时发送通知</string>
<string name="preference_reminder_deadline_advance">DEADLINE 提前提醒</string>
<string name="preference_reminder_scheduled_advance">SCHEDULED 提前提醒</string>
<string name="preference_reminder_daily_overview_time">每日概览时间</string>

<!-- reminder notifications -->
<string name="reminder_notification_channel">任务提醒</string>
<string name="reminder_notification_channel_desc">DEADLINE 和 SCHEDULED 任务到期提醒</string>
<string name="reminder_daily_overview_title">今日待办</string>
<string name="reminder_daily_overview_empty">今日无待办</string>
<string name="reminder_scheduled_today">今日 SCHEDULED</string>
<string name="reminder_deadline_upcoming">即将到期 DEADLINE</string>
<string name="reminder_days_later">%d 天后</string>
<string name="reminder_tomorrow">明天</string>

<!-- pomodoro settings -->
<string name="preference_pomodoro_duration">番茄钟时长</string>
<string name="preference_pomodoro_duration_summary">默认倒计时时长（分钟）</string>
<string name="pomodoro_duration_picker_title">设置番茄钟时长（分钟）</string>
<string name="preference_pomodoro_count_default">默认连续番茄数</string>
<string name="preference_pomodoro_count_default_summary">启动连续番茄时的默认数量</string>
<string name="preference_pomodoro_short_break">短休息时长</string>
<string name="preference_pomodoro_short_break_summary">番茄之间的休息时长（分钟）</string>
<string name="preference_pomodoro_long_break">长休息时长</string>
<string name="preference_pomodoro_long_break_summary">每 N 个番茄后的长休息时长（分钟）</string>
<string name="preference_pomodoro_long_break_interval">长休息间隔</string>
<string name="preference_pomodoro_long_break_interval_summary">每完成多少个番茄后触发长休息</string>
<string name="pomodoro_picker_title">设置番茄钟</string>
<string name="pomodoro_count_picker_title">连续番茄数</string>
<string name="pomodoro_active_toast">番茄钟正在进行中</string>

<!-- statistics -->
<string name="menu_statistics">番茄统计</string>
<string name="statistics_title">番茄统计</string>
<string name="statistics_tab_overview">概览</string>
<string name="statistics_tab_trend">趋势</string>
<string name="statistics_streak_format">🔥 连续 %d 天</string>
<string name="statistics_streak_empty">开始你的第一个番茄吧！</string>
<string name="statistics_streak_subtitle">每天至少完成 1 个番茄</string>
<string name="statistics_summary_day_format">今天: %d 个 | %s</string>
<string name="statistics_summary_week_format">本周: %1$d 个 | %2$s | 日均 %3$.1f</string>
<string name="statistics_summary_month_format">本月: %1$d 个 | %2$s | 日均 %3$.1f</string>
<string name="statistics_no_data">暂无数据</string>
<string name="preference_week_start_day">一周起始日</string>
<string name="preference_week_start_day_summary">选择统计页面的一周起始日</string>
<string name="monday">周一</string>
<string name="sunday">周日</string>
<string name="duration_minutes_format">%d 分钟</string>
```

**B 组：76 条新译**：

```xml
<!-- menus & actions -->
<string name="menu_save">保存</string>
<string name="menu_advanced">高级</string>
<string name="menu_agenda">日程</string>
<string name="menu_archive">归档</string>
<string name="menu_archive_tosibling">归档到同级</string>
<string name="menu_capture_simple">快速捕获</string>
<string name="menu_clockin">开始计时</string>
<string name="menu_delete">删除</string>
<string name="menu_delete_file">删除文件</string>
<string name="menu_pomodoro">番茄钟</string>
<string name="menu_pomodoro_stop">停止 🍅</string>
<string name="menu_record">录音</string>
<string name="menu_recover">恢复</string>
<string name="menu_share">分享节点</string>
<string name="menu_view">查看</string>
<string name="node_not_found">未找到节点</string>
<string name="search_results_for">搜索结果：</string>
<string name="share_node_not_found">未找到节点</string>
<string name="share_truncated">内容过长，已截断至 400000 字符</string>

<!-- preferences -->
<string name="preferences">设置</string>
<string name="preference_app_language">语言</string>
<string name="app_language_system">跟随系统</string>
<string name="app_language_chinese">中文</string>
<string name="app_language_english">English</string>
<string name="preference_actions">操作</string>
<string name="preference_advanced">高级</string>
<string name="preference_edit">编辑</string>
<string name="preference_interface">界面</string>
<string name="preference_general">常规</string>
<string name="preference_fontsize">字体大小</string>
<string name="preference_fontsize_summary">大纲中使用的字体大小。</string>
<string name="preference_theme">主题</string>
<string name="preference_setup_wizard">配置向导</string>
<string name="preference_sync_wifi_only">仅在 Wi-Fi 下同步</string>
<string name="preference_sync_wifi_only_summary">仅在无线网络可用时同步</string>
<string name="preference_capture_advanced">高级捕获</string>
<string name="preference_capture_advanced_summary">高级捕获机制，允许将内容捕获到任意标题之下。</string>
<string name="preference_exclude_tags">标签继承排除</string>
<string name="preference_exclude_tags_summary">用 \":\" 分隔的标签列表，这些标签不参与继承。</string>
<string name="preference_selected_todos">快速 TODO 关键词</string>
<string name="preference_selected_todos_summary">选择大纲中可快速切换的 TODO 关键词，用空格分隔。</string>
<string name="preference_view_on_click">点击时查看</string>
<string name="preference_view_on_click_summary">将大纲条目的默认操作从编辑改为查看</string>
<string name="preference_view_apply_formatting">视图应用格式化</string>
<string name="preference_view_apply_formatting_summary">查看节点时应用 orgmode 的粗体、下划线、删除线等强调格式。</string>
<string name="preference_properties_drawer">属性抽屉</string>
<string name="preference_view_node_properties_enabled">显示节点属性</string>
<string name="preference_view_node_properties_enabled_summary">显示节点属性抽屉的内容。</string>
<string name="preference_properties_drawer_fields">字段</string>
<string name="preference_properties_drawer_fields_summary">限制显示特定字段。字段列表用空格分隔，留空表示不限制。</string>
<string name="preference_properties_drawer_files">文件</string>
<string name="preference_properties_drawer_files_summary">限制显示特定文件。文件名列表用 \";\" 分隔，留空表示不限制。</string>
<string name="preference_pomodoro">番茄钟</string>

<!-- calendar -->
<string name="preference_calendar_display">显示</string>
<string name="preference_calendar_show_past">显示过去事件</string>
<string name="preference_calendar_show_past_summary">在日历中显示已过去的条目</string>
<string name="preference_calendar_pull">吸收日历条目</string>
<string name="preference_calendar_pull_summary">吸收非 MobileOrg 写入的日历条目并加入捕获文件。实验性功能！</string>
<string name="preference_calendar_pull_delete">吸收时删除</string>
<string name="preference_calendar_pull_delete_summary">删除已从日历吸收的条目</string>

<!-- prompts -->
<string name="prompt_delete_file">您确定要删除此文件吗？</string>
<string name="prompt_node_archive">您确定要归档此节点吗？</string>

<!-- recording -->
<string name="recording_notification_channel">MobileOrg 录音</string>
<string name="recording_notification_title">录音中：%s</string>
<string name="recording_stop">停止</string>
<string name="recording_pause">暂停</string>
<string name="recording_resume">继续</string>

<!-- synchronizer settings -->
<string name="configure_synchronizer_settings">同步设置</string>
<string name="storage_location">存储位置</string>
<string name="storage_card">存储卡</string>
<string name="internal">内部存储</string>
<string name="title_scp_host">主机</string>
<string name="title_scp_port">端口</string>
<string name="title_scp_path">路径</string>
<string name="summary_scp_path">服务器上 index.org 的完整路径</string>
<string name="title_index_file_path">index.org 的本地完整路径</string>
<string name="summary_web_url">您的 index.org 文件的完整 URL</string>
<string name="ssh_inform_choose_pub_file">或者选择包含*私钥*的文件（需要已安装文件管理器）</string>
<string name="ssh_choose_file_button">选择您的*私钥*文件</string>

<!-- edit -->
<string name="edit_create_new_entry">新建条目</string>
<string name="edit_create_new_entry_body">输入要创建的文件名</string>
```

- [ ] **步骤 2：创建 values-zh/arrays.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="fileSources">
        <item>WebDAV</item>
        <item>存储卡</item>
        <item>SSH/SCP</item>
        <item>仅捕获</item>
    </string-array>
    <string-array name="syncIntervals">
        <item>5 分钟</item>
        <item>10 分钟</item>
        <item>15 分钟</item>
        <item>30 分钟</item>
        <item>45 分钟</item>
        <item>1 小时</item>
        <item>3 小时</item>
        <item>6 小时</item>
        <item>12 小时</item>
    </string-array>
    <string-array name="viewRecursionLevels">
        <item>仅当前节点</item>
        <item>节点及其直接子节点</item>
        <item>节点、子节点及孙节点</item>
        <item>3 层嵌套</item>
        <item>4 层嵌套</item>
    </string-array>
    <string-array name="themesEntries">
        <item>浅色</item>
        <item>深色</item>
        <item>暖纸护眼</item>
    </string-array>
    <string-array name="agendaQueryTypes">
        <item>全部条目</item>
        <item>日程</item>
    </string-array>
    <string-array name="agendaSpanValues">
        <item>日</item>
        <item>周</item>
        <item>月</item>
        <item>年</item>
        <item>自定义</item>
    </string-array>
    <string-array name="reminder_advance_labels">
        <item>当天</item>
        <item>1 小时前</item>
        <item>3 小时前</item>
        <item>1 天前</item>
        <item>3 天前</item>
    </string-array>
    <string-array name="pomodoro_duration_entries">
        <item>15 分钟</item>
        <item>20 分钟</item>
        <item>25 分钟</item>
        <item>30 分钟</item>
        <item>45 分钟</item>
        <item>60 分钟</item>
    </string-array>
    <string-array name="pomodoro_count_entries">
        <item>1 个</item>
        <item>2 个</item>
        <item>3 个</item>
        <item>4 个</item>
        <item>5 个</item>
        <item>6 个</item>
        <item>7 个</item>
        <item>8 个</item>
        <item>9 个</item>
        <item>10 个</item>
    </string-array>
    <string-array name="pomodoro_break_entries">
        <item>0 分钟（无倒计时）</item>
        <item>3 分钟</item>
        <item>5 分钟</item>
        <item>10 分钟</item>
        <item>15 分钟</item>
        <item>20 分钟</item>
        <item>30 分钟</item>
    </string-array>
    <string-array name="pomodoro_interval_entries">
        <item>每 3 个</item>
        <item>每 4 个</item>
        <item>每 5 个</item>
        <item>每 6 个</item>
        <item>每 8 个</item>
        <item>每 10 个</item>
        <item>每 12 个</item>
    </string-array>
</resources>
```

注意：`app_language_entries`、`app_language_values`、`week_start_entries` 不在 zh 重复定义——前者的 item 是 `@string` 引用自动本地化，后者同理；`themes`（存储值数组）绝不出现在 zh。

- [ ] **步骤 3：机械校验**

运行：
```bash
python3 - <<'EOF'
import re
en = set(re.findall(r'name="([^"]+)"', open('MobileOrg/src/main/res/values/strings.xml').read()))
zh = set(re.findall(r'name="([^"]+)"', open('MobileOrg/src/main/res/values-zh/strings.xml').read()))
skip = {'example_webURL','dropbox_login_info','log_in_to_ubuntuone','wizard_ubuntuone','wizard_ubuntu_email_hint',
        'preference_ubuntuone_login','preference_ubuntuone_login_summary','preference_ubuntuone_path','preference_ubuntuone_path_summary'}
skip |= {n for n in en if n.startswith('key_')}
missing = (en - zh) - skip
extra = zh - en
print(f"zh 覆盖 {len(zh & en)}/{len(en)}；缺失 {sorted(missing)}；多余 {sorted(extra)}")
assert not missing and not extra, "覆盖不完整"
print("OK")
EOF
python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('MobileOrg/src/main/res/values-zh/strings.xml'); xml.dom.minidom.parse('MobileOrg/src/main/res/values-zh/arrays.xml'); print('XML OK')"
grep -c '<item>' MobileOrg/src/main/res/values-zh/arrays.xml
```
预期：`zh 覆盖 292/314`（314 - 13 条 key_* - 9 条死文案/URL；缺失与多余均为空集）；`OK`；`XML OK`；arrays item 总数 `63`（4+9+5+3+2+5+5+6+10+7+7）。

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/res/values-zh/strings.xml MobileOrg/src/main/res/values-zh/arrays.xml
git commit -m "feat(i18n): 补全 values-zh 翻译（129 条 strings + 12 组 arrays）"
```

---

### 任务 4：语言切换接入（设置页 + 非 AppCompat Activity wrap）

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/res/xml/preferences.xml`, `MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Wizard/WizardActivity.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Settings/Synchronizers/ScpSettingsActivity.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Settings/Synchronizers/SDCardSettingsActivity.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Settings/Synchronizers/WebDAVSettingsActivity.java`
**导出/变更接口：** `OrgUtils.java::wrapForAppLocales`, `SettingsActivity.java::KEY_APP_LANGUAGE`
**消费接口：** `strings.xml::preference_app_language`, `arrays.xml::app_language_entries`, `arrays.xml::app_language_values`, `arrays.xml::themesEntries`
**复杂度：** deep

**文件：**
- 修改：`MobileOrg/src/main/res/xml/preferences.xml`（顶部加 ListPreference + themes entries 引用）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`（新增静态方法）
- 修改：`WizardActivity.java`、`ScpSettingsActivity.java`、`SDCardSettingsActivity.java`、`WebDAVSettingsActivity.java`（各加 attachBaseContext）

- [ ] **步骤 1：OrgUtils 新增 wrapForAppLocales**

`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java` 新增 import 与静态方法（放类末尾）：

```java
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
```

```java
/**
 * Wrap base context with the per-app locale chosen via AppCompatDelegate.
 * Needed by Activities that do not extend AppCompatActivity (framework
 * PreferenceActivity / Activity): on API < 33 AppCompat only applies
 * app locales in its own delegates, so these activities must read the
 * same state here. No-op when no app locale is set (follow system).
 */
public static Context wrapForAppLocales(Context base) {
    LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
    if (locales.isEmpty())
        return base;
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocale(locales.get(0));
    return base.createConfigurationContext(config);
}
```

- [ ] **步骤 2：preferences.xml 顶部加语言 ListPreference + themes entries 换引用**

在 `<PreferenceCategory android:title="@string/preferences" >`（line 4）开标签之后、第一个 `<PreferenceScreen android:title="@string/preference_synchronization"` 之前插入：

```xml
<ListPreference
    android:defaultValue="system"
    android:entries="@array/app_language_entries"
    android:entryValues="@array/app_language_values"
    android:key="app_language"
    android:title="@string/preference_app_language" />
```

同时把主题 ListPreference（line 55-60）的 `android:entries="@array/themes"` 改为 `android:entries="@array/themesEntries"`（entryValues 保持 `@array/themes` 不动）。

- [ ] **步骤 3：SettingsActivity 切换逻辑**

`MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java`：

新增 import：
```java
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
```

新增常量（`KEY_SYNC_SOURCE` 旁）：
```java
public static final String KEY_APP_LANGUAGE = "app_language";
```

`onCreate()` 中 `init()` 调用后加 `syncLanguagePreference();`，并新增方法（回显，防状态漂移——AppCompat 持久化的 locale 与 SharedPreferences 值可能被 API 33+ 系统面板单方面改变）：

```java
private void syncLanguagePreference() {
    ListPreference langPref = (ListPreference) findPreference(KEY_APP_LANGUAGE);
    if (langPref == null) return;
    LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
    String value = "system";
    if (!current.isEmpty()) {
        String tag = current.toLanguageTags();
        if (tag.startsWith("zh")) value = "zh";
        else if (tag.startsWith("en")) value = "en";
    }
    langPref.setValue(value);
}
```

`onSharedPreferenceChanged()` 的 `updatePreferenceSummary(key)` 之前加分支：

```java
if (key.equals(KEY_APP_LANGUAGE)) {
    applyAppLanguage(appSettings.getString(key, "system"));
    return; // 语言切换触发 recreate，无需更新 summary
}
```

新增方法：

```java
private void applyAppLanguage(String value) {
    LocaleListCompat locales;
    if ("zh".equals(value))
        locales = LocaleListCompat.forLanguageTags("zh");
    else if ("en".equals(value))
        locales = LocaleListCompat.forLanguageTags("en");
    else
        locales = LocaleListCompat.getEmptyLocaleList();
    AppCompatDelegate.setApplicationLocales(locales);
    // AppCompat only auto-recreates AppCompatActivity; this activity is a
    // framework PreferenceActivity, so recreate it ourselves on API < 33.
    // On API 33+ the framework recreates every activity and our manual
    // call is a harmless no-op after that.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        recreate();
}
```

`updatePreferenceSummary()` 的 `KEY_THEME` 分支改为用 display 数组查 summary（否则中文界面 summary 残留英文值）：

```java
} else if (key.equals(KEY_THEME)) {
    String value = appSettings.getString(key, "");
    summary = OrgUtils.lookUpValueFromArray(this, R.array.themesEntries,
            R.array.themes, value);
}
```

类中加 attachBaseContext（配合 OrgUtils.wrapForAppLocales，自身也吃语言覆盖）：

```java
@Override
protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(OrgUtils.wrapForAppLocales(newBase));
}
```

- [ ] **步骤 4：4 个非 AppCompat Activity 加 attachBaseContext**

`WizardActivity.java`、`ScpSettingsActivity.java`、`SDCardSettingsActivity.java`、`WebDAVSettingsActivity.java` 各加（import `android.content.Context` 与 `com.matburt.mobileorg.util.OrgUtils` 按需）：

```java
@Override
protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(OrgUtils.wrapForAppLocales(newBase));
}
```

不处理（规格接受的已知限制）：`TimeclockDialog`（FragmentActivity，对话框生命周期极短）、`CertificateConflictActivity`/`FileDecryptionActivity`/`SyncEditActivity`（低频边缘）、Application context（通知标题）。

- [ ] **步骤 5：机械校验**

```bash
grep -c 'attachBaseContext' MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Wizard/WizardActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Settings/Synchronizers/*.java
grep -n 'entries="@array/themesEntries"' MobileOrg/src/main/res/xml/preferences.xml
grep -n 'entryValues="@array/themes"' MobileOrg/src/main/res/xml/preferences.xml
grep -c 'app_language' MobileOrg/src/main/res/xml/preferences.xml
python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('MobileOrg/src/main/res/xml/preferences.xml'); print('XML OK')"
```
预期：5 个 java 文件各 `1`；两个 themes 引用各 1 处；preferences.xml 中 `app_language` 出现 `1` 次；`XML OK`。

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/res/xml/preferences.xml MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Wizard/WizardActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Settings/Synchronizers/ScpSettingsActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Settings/Synchronizers/SDCardSettingsActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Settings/Synchronizers/WebDAVSettingsActivity.java
git commit -m "feat(i18n): 设置页应用内语言切换与非 AppCompat Activity locale wrap"
```

---

### 任务 5：菜单 XML 硬编码 Save 修复

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/res/menu/edit.xml`, `MobileOrg/src/main/res/menu/agenda_entry.xml`, `MobileOrg/src/main/res/menu/agenda_block.xml`
**导出/变更接口：** 无
**消费接口：** `strings.xml::menu_save`
**复杂度：** quick

**文件：**
- 修改：3 个 menu XML 的 line 14

- [ ] **步骤 1：3 处 android:title="Save" 替换**

`edit.xml:14`、`agenda_entry.xml:14`、`agenda_block.xml:14` 中：

```xml
android:title="Save"/>
```

替换为：

```xml
android:title="@string/menu_save"/>
```

- [ ] **步骤 2：机械校验**

```bash
grep -rn 'android:title="Save"' MobileOrg/src/main/res/menu/ || echo "无硬编码残留"
grep -c '@string/menu_save' MobileOrg/src/main/res/menu/edit.xml MobileOrg/src/main/res/menu/agenda_entry.xml MobileOrg/src/main/res/menu/agenda_block.xml
```
预期：`无硬编码残留`；3 个文件各 `1`。

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/res/menu/edit.xml MobileOrg/src/main/res/menu/agenda_entry.xml MobileOrg/src/main/res/menu/agenda_block.xml
git commit -m "fix(i18n): 菜单 XML 硬编码 Save 改 @string/menu_save"
```

---

### 任务 6：语言切换 instrumentation 回归测试

**依赖：** 任务 2, 任务 3, 任务 4
**文件集：** `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/LanguageSwitchTest.java`
**导出/变更接口：** 无
**消费接口：** `SettingsActivity.java::KEY_APP_LANGUAGE`, `OrgUtils.java::wrapForAppLocales`, `strings.xml::menu_save`
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/LanguageSwitchTest.java`

测试设备 MI PAD 4 是 API 27（<33）且系统语言中文——正好覆盖兼容层路径；断言必须用「app locale 覆盖系统 locale」的方向（先设 en 断言英文，再设 zh 断言中文），否则测不出 wrap 效果。

- [ ] **步骤 1：编写测试**

```java
package com.matburt.mobileorg.test.Gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Settings.SettingsActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LanguageSwitchTest {

    private Context targetContext;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        targetContext = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(targetContext);
    }

    @After
    public void tearDown() {
        // Restore follow-system state so other tests are not polluted.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        prefs.edit().putString(SettingsActivity.KEY_APP_LANGUAGE, "system").commit();
    }

    @Test
    public void appLanguagePreferenceAppliesApplicationLocales() {
        prefs.edit().putString(SettingsActivity.KEY_APP_LANGUAGE, "zh").commit();
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                activity.onSharedPreferenceChanged(prefs, SettingsActivity.KEY_APP_LANGUAGE);
                LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
                assertTrue("app_language=zh 应设置应用 locale 为 zh，实际: " + locales.toLanguageTags(),
                        locales.toLanguageTags().startsWith("zh"));
            });
        }
    }

    @Test
    public void systemLanguageValueClearsApplicationLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
        prefs.edit().putString(SettingsActivity.KEY_APP_LANGUAGE, "system").commit();
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                activity.onSharedPreferenceChanged(prefs, SettingsActivity.KEY_APP_LANGUAGE);
                assertTrue("app_language=system 应清空应用 locale",
                        AppCompatDelegate.getApplicationLocales().isEmpty());
            });
        }
    }

    @Test
    public void appLocaleOverridesSystemLanguageOnNonAppCompatActivity() {
        // Device system locale is zh; force en and verify the PreferenceActivity
        // (framework, not AppCompatActivity) renders English via wrapForAppLocales.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("en",
                        activity.getResources().getConfiguration().locale.getLanguage());
                assertEquals("Settings", activity.getString(R.string.menu_settings));
            });
        }
    }

    @Test
    public void zhLocaleResolvesChineseResourcesOnNonAppCompatActivity() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"));
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("zh",
                        activity.getResources().getConfiguration().locale.getLanguage());
                assertEquals("设置", activity.getString(R.string.menu_settings));
                assertEquals("保存", activity.getString(R.string.menu_save));
            });
        }
    }

    @Test
    public void themeEntriesLocalizedButStoredValuesUntouched() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"));
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                String[] entries = activity.getResources().getStringArray(R.array.themesEntries);
                String[] values = activity.getResources().getStringArray(R.array.themes);
                assertEquals("深色", entries[1]);
                assertEquals("Dark", values[1]);
            });
        }
    }
}
```

- [ ] **步骤 2：连接设备并运行**

设备：MI PAD 4（API 27）。先 `adb connect 192.168.31.198:<port>`（端口见无线调试面板），然后：

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.Gui.LanguageSwitchTest
```

预期：`LanguageSwitchTest` 5/5 PASS。若 `appLanguagePreferenceAppliesApplicationLocales` 在 API 27 上因 recreate 时序 flaky，可将断言移至 `scenario.onActivity` 后轮询 `getApplicationLocales()`（500ms 超时）。

- [ ] **步骤 3：全量回归**

```bash
./gradlew connectedDebugAndroidTest
```
预期：原有 94+ 测试与本测试类全绿（签名冲突时 UTP 会自动清包重装，属已知行为）。

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/LanguageSwitchTest.java
git commit -m "test(i18n): 语言切换与资源解析回归测试"
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 2 → 任务 4 → 任务 6

- Wave 1（无依赖）：任务 1, 任务 2, 任务 3
  - 文件集互不相交；任务 2 与任务 3 是"同一批文案两侧配对"（靠各自对照表保证语义一致，无符号级依赖）
- Wave 2（依赖 Wave 1）：任务 4（依赖 2）, 任务 5（依赖 2）
- Wave 3（依赖 Wave 2）：任务 6（依赖 2, 3, 4）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
