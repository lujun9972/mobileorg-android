# 番茄钟统计可视化设计

## 背景

MobileOrg 的番茄钟功能（`TimeclockService`）只有运行时状态，完成后不持久化任何记录。用户无法回顾自己的番茄钟使用情况——完成了多少、什么时候做的、是否保持习惯。

## 目标

新增独立统计页面，记录并可视化番茄钟完成历史。聚焦纯番茄钟数据，不涉及 Clock-in/out 功能。

## 约束

- 仅记录超时自动完成的番茄钟，手动提前停止的不记录
- 不迁移现有 CLOCK 条目数据
- 番茄钟和 Clock-in/out 是独立功能，统计数据互不干扰
- 图表需适配三套主题（Light / Dark / Monochrome）
- 所有日期计算（Streak、日视图分组、周范围）使用用户本地时区（`Calendar.getInstance()`），不用 UTC

## 设计

### 1. 数据层

#### 新表 `pomodoro_sessions`

```sql
CREATE TABLE pomodoro_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at INTEGER NOT NULL,    -- 番茄开始时间 (epoch millis)
    duration_min INTEGER NOT NULL,  -- 设定时长（25/30/45 等）
    completed_at INTEGER NOT NULL   -- 超时完成时间 (epoch millis)
);
```

字段说明：
- 不需要 `node_id` / `node_name`——番茄钟经常不绑定任务
- 不需要 `type` 字段——纯番茄钟，只有一种类型
- `duration_min` 是用户设定的时长（如 25），不是实际耗时（实际耗时 = 设定时长，因为是超时才记录）

#### DB 迁移

升级 `OrgDatabase` 版本号（5 → 6），需要同时修改两处：

1. **`onCreate()`**（覆盖新用户直接安装）：添加建表语句
2. **`onUpgrade()` 的 `case 6`**（覆盖老用户升级）：添加建表语句

注意：现有 `onUpgrade` 使用 `switch (newVersion)` 模式且末尾总调 `onCreate(db)`，所以 `case 6` 必须显式添加，不能依赖 `onCreate` 重跑（老用户的旧表已存在，`IF NOT EXISTS` 会跳过）。

```java
// onCreate() 中追加：
db.execSQL("CREATE TABLE IF NOT EXISTS pomodoro_sessions (...)");
db.execSQL("CREATE INDEX IF NOT EXISTS idx_pomodoro_completed ON pomodoro_sessions(completed_at)");

// onUpgrade() 中追加 case 6：
case 6:
    db.execSQL("CREATE TABLE IF NOT EXISTS pomodoro_sessions (...)");
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_pomodoro_completed ON pomodoro_sessions(completed_at)");
    break;
```

#### 写入时机

在 `TimeclockService.handlePomodoroTimeout()` 中，设置 `pomodoroTimedOut = true` 之后，直接通过 `SQLiteDatabase` 写入一条记录（不走 ContentProvider）：

```java
// 已有代码之后
this.pomodoroTimedOut = true;
// 新增：写入统计记录
writePomodoroSession();
```

写入需要的数据全部在 Service 内存中：
- `started_at` = `pomodoroStartTime`
- `duration_min` = `pomodoroDurationMins`
- `completed_at` = `System.currentTimeMillis()`

#### 数据访问

`pomodoro_sessions` 是纯本地统计数据，不走 ContentProvider，不需要同步或暴露给其他 app。

- **写入**：`TimeclockService` 通过 `OrgDatabase.getWritableDatabase()` 直接执行 SQL
- **查询**：`PomodoroStatisticsRepository` 持有 `OrgDatabase` 引用，直接查询，不走 `ContentResolver`

不需要在 `OrgProvider` 中注册新的 URI 或添加 CRUD 方法。

#### 查询层 `PomodoroStatisticsRepository`

新类，提供以下查询方法：

| 方法 | 参数 | 返回 | 用途 |
|------|------|------|------|
| `getDailyCounts(weekStart, weekEnd)` | 周起始/结束 millis | `List<DailyCount>` (date, count) | 概览柱状图 |
| `getDailyDurations(weekStart, weekEnd)` | 同上 | `List<DailyDuration>` (date, totalMin) | 概览柱状图（时长模式） |
| `getWeeklyTrends()` | 无（固定最近 4 周） | `List<WeeklyTotal>` (weekStart, count) | 趋势折线图 |
| `getStreak()` | 无 | `int`（当前连续天数） | Streak 卡片 |
| `getSessionsForDate(date)` | 某天 0 点 millis | `List<PomodoroSession>` | BottomSheet 明细 |
| `getWeekSummary(weekStart, weekEnd)` | 同上 | `WeekSummary` (count, totalMin, dailyAvg) | 概览摘要卡片 |

### 2. UI 层

#### StatisticsActivity

新 Activity，标题"番茄统计"。

布局结构：
```
[Toolbar: 番茄统计]
[周选择器: < 2026-06-02 ~ 2026-06-08 >]     [粒度: 日 | 周 | 月]
[TabLayout: 概览 | 趋势]
[ViewPager2]
```

周选择器：
- 左右箭头切换周
- 中间显示当前周的日期范围
- 切换时刷新概览 Tab 的数据
- 周起始日可配置（默认周一）

粒度切换：
- 三个按钮组（日 | 周 | 月）
- 影响柱状图的 X 轴分组方式
- 不影响趋势 Tab（趋势固定 4 周）

#### 概览 Fragment

```
┌─────────────────────────────┐
│  🔥 连续 7 天               │  ← Streak 卡片
│  每天至少完成 1 个番茄       │
├─────────────────────────────┤
│  ████ ████ ██ █████ ████ ██ │  ← 每日柱状图 (BarChart)
│   周一  周二 ...         周日 │
├─────────────────────────────┤
│  本周: 18 个 | 7.5h | 日均 2.6│  ← 摘要卡片
└─────────────────────────────┘
```

- Streak 卡片：大号数字 + 火焰 emoji，0 天时显示"开始你的第一个番茄吧！"
- 柱状图：点击某天的柱子弹出 BottomSheet，显示该天每个番茄的开始时间和时长
- 摘要卡片：本周总番茄数、总时长、日均时长

#### 趋势 Fragment

```
┌─────────────────────────────┐
│      ╱\    ╱\               │  ← 最近 4 周折线图 (LineChart)
│  ──╱  ──╱  \──             │
│  W1   W2   W3   W4         │
└─────────────────────────────┘
```

- 固定显示最近 4 周
- 每个数据点 = 一周的番茄总数
- X 轴标签为周起始日期

#### 空状态

无数据时（新用户或本周无记录）：
- 概览 Tab：Streak 卡片显示"开始你的第一个番茄吧！"，柱状图显示空状态占位，摘要显示全零
- 趋势 Tab：折线图显示全零

### 3. 导航

在 OutlineActivity 的 options menu（`outline_menu.xml`）中添加：

```xml
<item
    android:id="@+id/menu_statistics"
    android:icon="@drawable/ic_menu_statistics"
    android:orderInCategory="90"
    app:showAsAction="never"
    android:title="番茄统计" />
```

点击后启动 `StatisticsActivity`。

### 4. 主题适配

三套主题各自一套图表配色：

| 元素 | Light | Dark | Monochrome |
|------|-------|------|------------|
| 柱状图填充 | #4CAF50 (绿) | #81C784 (浅绿) | #795548 (棕) |
| 折线颜色 | #2196F3 (蓝) | #64B5F6 (浅蓝) | #607D8B (灰蓝) |
| 文字/标签 | #212121 | #FFFFFF | #3E2723 |
| 网格线 | #E0E0E0 | #424242 | #D7CCC8 |
| 背景 | #FFFFFF | #1E1E1E | #F5F0E8 |
| Streak 卡片背景 | #FFF3E0 | #3E2723 | #EFEBE9 |

读取方式：在 Fragment 中通过 `PreferenceUtils.getThemeName()` 获取当前主题名（静态方法，无参数），选择对应配色常量。

### 5. 设置

在现有 Pomodoro PreferenceScreen（`preferences.xml`）中添加：

```xml
<ListPreference
    android:key="week_start_day"
    android:title="一周起始日"
    android:entries="@array/week_start_entries"
    android:entryValues="@array/week_start_values"
    android:summary="选择统计页面的一周起始日" />
```

arrays.xml 新增：
```xml
<string-array name="week_start_entries">
    <item>周一</item>
    <item>周日</item>
</string-array>
<string-array name="week_start_values">
    <item>monday</item>
    <item>sunday</item>
</string-array>
```

### 6. 依赖

`settings.gradle` 添加 JitPack 仓库（现代 Gradle 必须在 settings 中声明）：

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

`MobileOrg/build.gradle` 添加依赖：

```groovy
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
```

兼容性确认：MPAndroidChart v3.1.0 已迁移到 AndroidX，支持 API 16+，与 AGP 8.x / Gradle 8.5 兼容。

### 7. 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `OrgDatabase.java` | 修改 | 升级版本号，onCreate/onUpgrade 建表+索引，Tables 接口加 `POMODORO_SESSIONS` 常量 |
| `TimeclockService.java` | 修改 | handlePomodoroTimeout() 中写入记录 |
| `PomodoroStatisticsRepository.java` | 新增 | 查询统计数据的 Repository |
| `StatisticsActivity.java` | 新增 | 统计页面 Activity |
| `OverviewFragment.java` | 新增 | 概览 Tab Fragment |
| `TrendFragment.java` | 新增 | 趋势 Tab Fragment |
| `DayDetailBottomSheet.java` | 新增 | 点击柱子弹出的当天明细 |
| `ChartThemeConfig.java` | 新增 | 三套主题的图表配色常量 |
| `activity_statistics.xml` | 新增 | StatisticsActivity 布局 |
| `fragment_overview.xml` | 新增 | 概览 Fragment 布局 |
| `fragment_trend.xml` | 新增 | 趋势 Fragment 布局 |
| `bottom_sheet_day_detail.xml` | 新增 | 当天明细 BottomSheet 布局 |
| `outline_menu.xml` | 修改 | 添加统计菜单项 |
| `preferences.xml` | 修改 | 添加周起始日设置 |
| `arrays.xml` | 修改 | 添加周起始日选项 |
| `strings.xml` | 修改 | 添加相关字符串资源 |
| `settings.gradle` | 修改 | 添加 JitPack 仓库 |
| `MobileOrg/build.gradle` | 修改 | 添加 MPAndroidChart 依赖 |
| `AndroidManifest.xml` | 修改 | 注册 StatisticsActivity |

## 不做的事

- 不记录手动提前停止的番茄钟
- 不记录 Clock-in/out 会话
- 不导入现有 CLOCK 条目历史数据
- 不支持导出统计数据（CSV 等）
- 不支持编辑/删除已记录的番茄钟
- 不在番茄钟和 OrgNode 任务之间建立关联
