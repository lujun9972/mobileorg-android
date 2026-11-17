# 番茄钟统计可视化 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新增独立统计页面，记录番茄钟完成事件并展示每日/每周/每月数量、总时长、连续天数(streak)和趋势折线图。

**架构：** 新建 `pomodoro_sessions` SQLite 表，`TimeclockService` 在番茄钟超时时写入记录。新 `PomodoroStatisticsRepository` 直接通过 `SQLiteDatabase` 查询。新 `StatisticsActivity` 用 TabLayout+ViewPager2 承载两个 Fragment（概览/趋势），通过共享 `StatisticsViewModel` (LiveData) 传递时间范围和粒度状态，用 MPAndroidChart 渲染图表。

**技术栈：** MPAndroidChart v3.1.0 (JitPack), Material Components 1.11.0 (已有), ViewPager2 + TabLayout (Material 传递依赖), AndroidX ViewModel + LiveData

**规格文档：** `docs/superpowers/specs/2026-06-06-pomodoro-statistics-design.md`

---

## 编码原则

- **Activity 主题模式**：onCreate() 首行读 `PreferenceUtils.getThemeName()`，然后 `OrgUtils.setTheme(this)`，再调 `super.onCreate()`。参考 `OutlineActivity:82-85`。
- **DB 访问**：`new OrgDatabase(context).getWritableDatabase()`，不走 ContentProvider。参考 `SyncService:152`, `AgendaFragment:32`。
- **菜单 XML**：使用 `app:showAsAction`（不是 `android:showAsAction`），参考 `outline_menu.xml`。
- **Service 中的 Context**：`TimeclockService` 是 Service，直接用 `this` 作 Context。
- **资源文件**：所有用户可见字符串放入 `strings.xml`，新数组放入 `arrays.xml`。
- **minSdk 17**：ViewPager2 需要使用 AndroidX 的 `ViewPager2`（通过 Material 传递依赖），不需要额外依赖。
- **Java 8**：可用 lambda、方法引用、stream API（compileOptions 已设 1.8）。
- **Fragment 通信**：通过共享 `StatisticsViewModel`（AndroidX ViewModel），Fragment 用 `ViewModelProvider` 获取 Activity scope 的同一个实例。ViewModel 持有 `MutableLiveData<TimeRange>` 通知 Fragment 刷新。

## 粒度系统设计

三种粒度影响顶部选择器、概览柱状图和趋势折线图：

| 粒度 | 选择器导航 | 概览柱状图 | 摘要格式 | 趋势折线图 |
|------|-----------|-----------|---------|-----------|
| 日 | < 2026-06-06 >（按天切换） | 选定日+前6天 = 7 bar | "今天: N个 | Xh" | 最近 7 天（7 点） |
| 周 | < 6/2 ~ 6/8 >（按周切换） | 7 bar（周一~周日） | "本周: N个 | Xh | 日均X" | 最近 4 周（4 点） |
| 月 | < 2026年6月 >（按月切换） | 当月每天（28-31 bar，横向滚动，`setVisibleXRangeMaximum(14)`） | "本月: N个 | Xh | 日均X" | 最近 12 月（12 点） |

- **月模式柱状图**：设置 `barChart.setVisibleXRangeMaximum(14)` 允许横向滚动，避免 28-31 bar 挤在小屏上
- **三种粒度下点击 bar 都弹出 DayDetailBottomSheet**，显示该天的番茄列表
- **Streak 计算独立于粒度**，始终从今天往前数连续天数（今天有记录则包含今天）

## 文件结构

### 新建文件
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/PomodoroStatisticsRepository.java` — 查询统计数据的 Repository
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsViewModel.java` — 共享 ViewModel，持有粒度+时间范围状态
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsActivity.java` — 统计页面 Activity
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/OverviewFragment.java` — 概览 Tab Fragment
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/TrendFragment.java` — 趋势 Tab Fragment
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/DayDetailBottomSheet.java` — 点击柱子弹出的当天明细
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/ChartThemeConfig.java` — 三套主题的图表配色
- `MobileOrg/src/main/res/layout/activity_statistics.xml` — StatisticsActivity 布局
- `MobileOrg/src/main/res/layout/fragment_overview.xml` — 概览 Fragment 布局
- `MobileOrg/src/main/res/layout/fragment_trend.xml` — 趋势 Fragment 布局
- `MobileOrg/src/main/res/layout/bottom_sheet_day_detail.xml` — 当天明细布局

### 修改文件
- `build.gradle` (root) — 添加 JitPack 仓库到 allprojects
- `MobileOrg/build.gradle` — 添加 MPAndroidChart 依赖
- `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java` — 升级版本号，onCreate/onUpgrade 建表+索引，Tables 加常量
- `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java` — handlePomodoroTimeout() 写入记录
- `MobileOrg/src/main/res/menu/outline_menu.xml` — 添加统计菜单项
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java` — onOptionsItemSelected 处理统计菜单
- `MobileOrg/src/main/res/xml/preferences.xml` — 添加周起始日设置
- `MobileOrg/src/main/res/values/arrays.xml` — 添加周起始日选项
- `MobileOrg/src/main/res/values/strings.xml` — 添加字符串资源
- `MobileOrg/src/main/AndroidManifest.xml` — 注册 StatisticsActivity

---

### 任务 1：添加 MPAndroidChart 依赖并验证构建

**依赖：** 无
**文件集：** `build.gradle`, `MobileOrg/build.gradle`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`build.gradle:16-21` (allprojects.repositories 块)
- 修改：`MobileOrg/build.gradle:54-68` (dependencies 块)

- [ ] **步骤 1：在 root build.gradle 的 allprojects.repositories 中添加 JitPack**

当前 `build.gradle` 的 `allprojects { repositories { ... } }` 在第 16-21 行。在 `jcenter()` 后面添加 JitPack：

```groovy
allprojects {
    repositories {
        google()
        jcenter()
        maven { url 'https://jitpack.io' }
    }
}
```

- [ ] **步骤 2：在 MobileOrg/build.gradle 的 dependencies 中添加 MPAndroidChart**

在 dependencies 块末尾（`implementation project(':libraries:locale')` 之后）添加：

```groovy
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
```

- [ ] **步骤 3：推送并检查 CI 构建通过**

```bash
git add build.gradle MobileOrg/build.gradle
git commit -m "build: add MPAndroidChart dependency via JitPack"
git push
gh run list --limit 1
```

等待 CI 通过（用 `gh run watch` 或 `gh run list` 确认绿色）。构建失败则修复后重新推送。

---

### 任务 2：创建 pomodoro_sessions 表

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java`
**导出/变更接口：** `OrgDatabase.java::Tables.POMODORO_SESSIONS`
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java`

- [ ] **步骤 1：升级 DATABASE_VERSION，添加建表和索引**

修改点：

1. `DATABASE_VERSION` 从 5 改为 6
2. `Tables` 接口添加 `String POMODORO_SESSIONS = "pomodoro_sessions";`
3. `onCreate()` 末尾添加建表和索引：
```java
db.execSQL("CREATE TABLE IF NOT EXISTS pomodoro_sessions ("
        + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
        + "started_at INTEGER NOT NULL,"
        + "duration_min INTEGER NOT NULL,"
        + "completed_at INTEGER NOT NULL)");
db.execSQL("CREATE INDEX IF NOT EXISTS idx_pomodoro_completed "
        + "ON pomodoro_sessions(completed_at)");
```
4. `onUpgrade()` switch 中添加 `case 6:`，内容同上两行 `execSQL`，加 `break;`

- [ ] **步骤 2：推送并检查 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/OrgData/OrgDatabase.java
git commit -m "feat(stats): add pomodoro_sessions table with index"
git push
gh run list --limit 1
```

---

### 任务 3：在番茄钟超时时写入记录

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`
**导出/变更接口：** 无
**消费接口：** `OrgDatabase.java::Tables.POMODORO_SESSIONS`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java`

- [ ] **步骤 1：在 handlePomodoroTimeout() 中添加写入逻辑**

在 `handlePomodoroTimeout()` 方法中，`this.pomodoroTimedOut = true;` 之后，添加：

```java
// 记录番茄钟完成到统计表
writePomodoroSession();
```

在类中添加新方法：

```java
private void writePomodoroSession() {
    SQLiteDatabase db = new OrgDatabase(this).getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put("started_at", pomodoroStartTime);
    values.put("duration_min", pomodoroDurationMins);
    values.put("completed_at", System.currentTimeMillis());
    db.insert(OrgDatabase.Tables.POMODORO_SESSIONS, null, values);
    db.close();
}
```

需要添加 import：
```java
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.matburt.mobileorg.OrgData.OrgDatabase;
```

- [ ] **步骤 2：推送并检查 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Services/TimeclockService.java
git commit -m "feat(stats): write pomodoro session record on timeout"
git push
gh run list --limit 1
```

---

### 任务 4：创建 PomodoroStatisticsRepository

**依赖：** 任务 2
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/PomodoroStatisticsRepository.java`
**导出/变更接口：** `PomodoroStatisticsRepository.java::PomodoroStatisticsRepository`, `PomodoroStatisticsRepository.java::getDailyCounts`, `PomodoroStatisticsRepository.java::getTrendData`, `PomodoroStatisticsRepository.java::getStreak`, `PomodoroStatisticsRepository.java::getSessionsForDate`, `PomodoroStatisticsRepository.java::getSummary`
**消费接口：** `OrgDatabase.java::Tables.POMODORO_SESSIONS`
**复杂度：** deep

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/PomodoroStatisticsRepository.java`

- [ ] **步骤 1：创建 Repository 类**

包名：`com.matburt.mobileorg.Gui.Statistics`

构造函数接收 `Context`，保存引用用于创建 `OrgDatabase`。

```java
public class PomodoroStatisticsRepository {
    private final Context context;

    public PomodoroStatisticsRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    private SQLiteDatabase getDb() {
        return new OrgDatabase(context).getReadableDatabase();
    }
```

核心查询方法（所有日期计算使用 `Calendar.getInstance()` 本地时区）：

**getDailyCounts(long rangeStart, long rangeEnd)** → `List<DailyCount>`
- 查询指定时间范围内的每日番茄数
- SQL: `SELECT date(completed_at / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS cnt FROM pomodoro_sessions WHERE completed_at >= ? AND completed_at < ? GROUP BY day ORDER BY day`
- 调用方负责补零空天：日粒度补 7 天，周粒度补 7 天，月粒度补当月天数

**getTrendData(int count, String granularity)** → `List<TrendPoint>`
- `granularity` = "day" → 最近 `count` 天的每日数据
- `granularity` = "week" → 最近 `count` 周的每周数据
- `granularity` = "month" → 最近 `count` 月的每月数据
- 每个时间段的起止用 `Calendar` 计算，查询 COUNT(*)
- 返回 `List<TrendPoint>`（label: "6/2", count: 5）

**getStreak()** → `int`
- 从今天开始往前逐天检查
- 先检查今天（today 0:00 到明天 0:00）是否有记录，有则计入 streak
- 然后从昨天（或今天无记录则从昨天开始）往前逐天检查
- 遇到 0 就停止，返回连续天数

**getSessionsForDate(long dayStart)** → `List<PomodoroSession>`
- 查询某天 0:00 到次日 0:00 的所有记录，按 completed_at 排序
- 返回每个番茄的 id, started_at, duration_min

**getSummary(long rangeStart, long rangeEnd, int dayCount)** → `Summary`
- 总番茄数: COUNT(*)
- 总时长: SUM(duration_min)
- 日均: 总番茄数 / dayCount（日粒度时 dayCount=1 不显示日均）

内部数据类（static inner class）：
```java
public static class DailyCount { public final String date; public final int count; }
public static class TrendPoint { public final String label; public final int count; }
public static class PomodoroSession { public final long id; public final long startedAt; public final int durationMin; }
public static class Summary { public final int totalCount; public final int totalMinutes; public final float dailyAvg; }
```

所有方法关闭 Cursor 和 Database。

- [ ] **步骤 2：推送并检查 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/PomodoroStatisticsRepository.java
git commit -m "feat(stats): add PomodoroStatisticsRepository with query methods"
git push
gh run list --limit 1
```

---

### 任务 5：创建 ChartThemeConfig

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/ChartThemeConfig.java`
**导出/变更接口：** `ChartThemeConfig.java::ChartThemeConfig`, `ChartThemeConfig.java::current`
**消费接口：** `PreferenceUtils.java::getThemeName`
**复杂度：** quick

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/ChartThemeConfig.java`

- [ ] **步骤 1：创建配色配置类**

```java
package com.matburt.mobileorg.Gui.Statistics;

import com.matburt.mobileorg.util.PreferenceUtils;

public class ChartThemeConfig {
    public final int barColor;
    public final int lineColor;
    public final int textColor;
    public final int gridColor;
    public final int backgroundColor;
    public final int streakCardColor;

    private static final ChartThemeConfig LIGHT = new ChartThemeConfig(
        0xFF4CAF50, 0xFF2196F3, 0xFF212121, 0xFFE0E0E0, 0xFFFFFFFF, 0xFFFFF3E0);
    private static final ChartThemeConfig DARK = new ChartThemeConfig(
        0xFF81C784, 0xFF64B5F6, 0xFFFFFFFF, 0xFF424242, 0xFF1E1E1E, 0xFF3E2723);
    private static final ChartThemeConfig MONO = new ChartThemeConfig(
        0xFF795548, 0xFF607D8B, 0xFF3E2723, 0xFFD7CCC8, 0xFFF5F0E8, 0xFFEFEBE9);

    private ChartThemeConfig(int bar, int line, int text, int grid, int bg, int streak) {
        this.barColor = bar; this.lineColor = line; this.textColor = text;
        this.gridColor = grid; this.backgroundColor = bg; this.streakCardColor = streak;
    }

    public static ChartThemeConfig current() {
        String theme = PreferenceUtils.getThemeName();
        if ("Light".equals(theme)) return LIGHT;
        if ("Monochrome".equals(theme)) return MONO;
        return DARK;
    }
}
```

- [ ] **步骤 2：推送**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/ChartThemeConfig.java
git commit -m "feat(stats): add ChartThemeConfig with 3 theme color schemes"
git push
```

---

### 任务 6：添加字符串资源和数组

**依赖：** 无
**文件集：** `MobileOrg/src/main/res/values/strings.xml`, `MobileOrg/src/main/res/values/arrays.xml`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/res/values/strings.xml`
- 修改：`MobileOrg/src/main/res/values/arrays.xml`

- [ ] **步骤 1：在 strings.xml 中添加字符串**

在末尾 `</resources>` 前添加：

```xml
<!-- Pomodoro Statistics -->
<string name="menu_statistics">番茄统计</string>
<string name="statistics_title">番茄统计</string>
<string name="statistics_tab_overview">概览</string>
<string name="statistics_tab_trend">趋势</string>
<string name="statistics_streak_format">🔥 连续 %d 天</string>
<string name="statistics_streak_empty">开始你的第一个番茄吧！</string>
<string name="statistics_streak_subtitle">每天至少完成 1 个番茄</string>
<string name="statistics_summary_day_format">今天: %d 个 | %s</string>
<string name="statistics_summary_week_format">本周: %d 个 | %s | 日均 %.1f</string>
<string name="statistics_summary_month_format">本月: %d 个 | %s | 日均 %.1f</string>
<string name="statistics_no_data">暂无数据</string>
<string name="preference_week_start_day">一周起始日</string>
<string name="preference_week_start_day_summary">选择统计页面的一周起始日</string>
<string name="monday">周一</string>
<string name="sunday">周日</string>
<string name="duration_minutes_format">%d 分钟</string>
```

- [ ] **步骤 2：在 arrays.xml 中添加周起始日选项**

在末尾 `</resources>` 前添加：

```xml
<string-array name="week_start_entries">
    <item>@string/monday</item>
    <item>@string/sunday</item>
</string-array>
<string-array name="week_start_values">
    <item>monday</item>
    <item>sunday</item>
</string-array>
```

- [ ] **步骤 3：推送**

```bash
git add MobileOrg/src/main/res/values/strings.xml MobileOrg/src/main/res/values/arrays.xml
git commit -m "feat(stats): add string resources and week start day arrays"
git push
```

---

### 任务 7：在设置中添加周起始日选项

**依赖：** 任务 6
**文件集：** `MobileOrg/src/main/res/xml/preferences.xml`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/res/xml/preferences.xml`

- [ ] **步骤 1：在 Pomodoro PreferenceScreen 中添加 ListPreference**

在 `preferences.xml` 的 Pomodoro `PreferenceCategory` 内（第 98-108 行的 `</PreferenceScreen>` 前），在 pomodoro_duration 的 ListPreference 之后添加：

```xml
<ListPreference
    android:key="week_start_day"
    android:title="@string/preference_week_start_day"
    android:entries="@array/week_start_entries"
    android:entryValues="@array/week_start_values"
    android:defaultValue="monday"
    android:summary="@string/preference_week_start_day_summary" />
```

- [ ] **步骤 2：推送**

```bash
git add MobileOrg/src/main/res/xml/preferences.xml
git commit -m "feat(stats): add week start day preference to Pomodoro settings"
git push
```

---

### 任务 8：创建 StatisticsViewModel 和 StatisticsActivity

**依赖：** 任务 4, 任务 5, 任务 6
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsViewModel.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsActivity.java`, `MobileOrg/src/main/res/layout/activity_statistics.xml`, `MobileOrg/src/main/AndroidManifest.xml`
**导出/变更接口：** `StatisticsViewModel.java::StatisticsViewModel`, `StatisticsViewModel.java::TimeRange`, `StatisticsActivity.java::StatisticsActivity`
**消费接口：** `PomodoroStatisticsRepository.java::PomodoroStatisticsRepository`, `ChartThemeConfig.java::ChartThemeConfig`
**复杂度：** deep

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsViewModel.java`
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsActivity.java`
- 创建：`MobileOrg/src/main/res/layout/activity_statistics.xml`
- 修改：`MobileOrg/src/main/AndroidManifest.xml`

- [ ] **步骤 1：创建 StatisticsViewModel**

```java
package com.matburt.mobileorg.Gui.Statistics;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class StatisticsViewModel extends AndroidViewModel {

    public enum Granularity { DAY, WEEK, MONTH }

    public static class TimeRange {
        public final Granularity granularity;
        public final long rangeStart; // 当前区间的起始 millis
        public final long rangeEnd;   // 当前区间的结束 millis
        public TimeRange(Granularity g, long start, long end) {
            this.granularity = g; this.rangeStart = start; this.rangeEnd = end;
        }
    }

    private final MutableLiveData<TimeRange> timeRange = new MutableLiveData<>();
    public LiveData<TimeRange> getTimeRange() { return timeRange; }

    public StatisticsViewModel(@NonNull Application application) {
        super(application);
        // 默认：本周
        setTimeRange(Granularity.WEEK);
    }

    public void setTimeRange(Granularity granularity) {
        setTimeRange(granularity, null);
    }

    /** 重载：传入 anchorMillis 则以此为选定日期/周/月的锚点，否则用当前时间 */
    public void setTimeRange(Granularity granularity, Long anchorMillis) {
        long anchor = anchorMillis != null ? anchorMillis : System.currentTimeMillis();
        long start, end;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(anchor);

        switch (granularity) {
            case DAY:
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, 1);
                end = cal.getTimeInMillis();
                break;
            case WEEK:
                // 读取 week_start_day preference 计算周起始
                start = weekStart(cal);
                cal.add(Calendar.DAY_OF_MONTH, 7);
                end = cal.getTimeInMillis();
                break;
            case MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                start = cal.getTimeInMillis();
                cal.add(Calendar.MONTH, 1);
                end = cal.getTimeInMillis();
                break;
        }
        timeRange.setValue(new TimeRange(granularity, start, end));
    }

    /** 导航：前一个区间 */
    public void navigatePrevious() { /* 根据 granularity 减一天/一周/一月 */ }

    /** 导航：后一个区间 */
    public void navigateNext() { /* 根据 granularity 加一天/一周/一月 */ }

    private long weekStart(Calendar cal) {
        // 读取 week_start_day preference，调整 cal 到本周起始日
        // 返回起始 millis
    }
}
```

- [ ] **步骤 2：创建 activity_statistics.xml 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="8dp">

        <ImageButton
            android:id="@+id/btn_prev"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@android:drawable/ic_media_previous"
            android:contentDescription="Previous" />

        <TextView
            android:id="@+id/range_label"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:gravity="center"
            android:textAppearance="?android:attr/textAppearanceMedium" />

        <ImageButton
            android:id="@+id/btn_next"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@android:drawable/ic_media_next"
            android:contentDescription="Next" />

        <com.google.android.material.button.MaterialButtonToggleGroup
            android:id="@+id/granularity_toggle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            app:singleSelection="true">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_day"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="日"
                android:minWidth="40dp" />
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_week"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="周"
                android:minWidth="40dp" />
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_month"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="月"
                android:minWidth="40dp" />
        </com.google.android.material.button.MaterialButtonToggleGroup>
    </LinearLayout>

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tab_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:tabMode="fixed" />

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/view_pager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

- [ ] **步骤 3：创建 StatisticsActivity.java**

```java
public class StatisticsActivity extends AppCompatActivity {
    private StatisticsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OrgUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.statistics_title);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(StatisticsViewModel.class);

        // 观察 TimeRange 变化 → 更新 range_label 文本
        viewModel.getTimeRange().observe(this, range -> {
            TextView label = findViewById(R.id.range_label);
            label.setText(formatRangeLabel(range));
        });

        // ViewPager + TabLayout
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        viewPager.setAdapter(new StatsPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager,
            (tab, pos) -> tab.setText(pos == 0 ? R.string.statistics_tab_overview : R.string.statistics_tab_trend)
        ).attach();

        // 导航按钮
        findViewById(R.id.btn_prev).setOnClickListener(v -> viewModel.navigatePrevious());
        findViewById(R.id.btn_next).setOnClickListener(v -> viewModel.navigateNext());

        // 粒度切换
        MaterialButtonToggleGroup toggle = findViewById(R.id.granularity_toggle);
        toggle.check(R.id.btn_week); // 默认周
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_day) viewModel.setTimeRange(Granularity.DAY);
            else if (checkedId == R.id.btn_week) viewModel.setTimeRange(Granularity.WEEK);
            else if (checkedId == R.id.btn_month) viewModel.setTimeRange(Granularity.MONTH);
        });
    }

    private String formatRangeLabel(TimeRange range) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        switch (range.granularity) {
            case DAY: return sdf.format(new Date(range.rangeStart));
            case WEEK: return sdf.format(new Date(range.rangeStart)) + " ~ " + sdf.format(new Date(range.rangeEnd - 1));
            case MONTH: return new SimpleDateFormat("yyyy年M月").format(new Date(range.rangeStart));
        }
        return "";
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    private static class StatsPagerAdapter extends FragmentStateAdapter {
        public StatsPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }
        @Override public int getItemCount() { return 2; }
        @NonNull @Override public Fragment createFragment(int position) {
            return position == 0 ? new OverviewFragment() : new TrendFragment();
        }
    }
}
```

- [ ] **步骤 4：在 AndroidManifest.xml 中注册 Activity**

在合适位置添加：

```xml
<activity
    android:name=".Gui.Statistics.StatisticsActivity"
    android:parentActivityName=".Gui.Outline.OutlineActivity" />
```

- [ ] **步骤 5：推送并检查 CI**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsViewModel.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsActivity.java MobileOrg/src/main/res/layout/activity_statistics.xml MobileOrg/src/main/AndroidManifest.xml
git commit -m "feat(stats): add StatisticsViewModel and StatisticsActivity"
git push
gh run list --limit 1
```

---

### 任务 9：创建 OverviewFragment

**依赖：** 任务 8
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/OverviewFragment.java`, `MobileOrg/src/main/res/layout/fragment_overview.xml`
**导出/变更接口：** `OverviewFragment.java::OverviewFragment`
**消费接口：** `StatisticsViewModel.java::getTimeRange`, `PomodoroStatisticsRepository.java::getDailyCounts`, `PomodoroStatisticsRepository.java::getStreak`, `PomodoroStatisticsRepository.java::getSummary`, `PomodoroStatisticsRepository.java::getSessionsForDate`, `ChartThemeConfig.java::ChartThemeConfig`
**复杂度：** deep

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/OverviewFragment.java`
- 创建：`MobileOrg/src/main/res/layout/fragment_overview.xml`

- [ ] **步骤 1：创建 fragment_overview.xml 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Streak Card -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/streak_card"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp">
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp"
                android:gravity="center">
                <TextView
                    android:id="@+id/streak_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textAppearance="?android:attr/textAppearanceLarge" />
                <TextView
                    android:id="@+id/streak_subtitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textAppearance="?android:attr/textAppearanceSmall" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Bar Chart -->
        <com.github.mikephil.charting.charts.BarChart
            android:id="@+id/bar_chart"
            android:layout_width="match_parent"
            android:layout_height="250dp"
            android:layout_marginBottom="16dp" />

        <!-- Summary Card -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content">
            <TextView
                android:id="@+id/summary_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="16dp"
                android:gravity="center"
                android:textAppearance="?android:attr/textAppearanceMedium" />
        </com.google.android.material.card.MaterialCardView>

    </LinearLayout>
</ScrollView>
```

- [ ] **步骤 2：创建 OverviewFragment.java**

关键逻辑：
- `onViewCreated` 中获取共享 ViewModel：`ViewModelProvider(requireActivity()).get(StatisticsViewModel.class)`
- 观察 `viewModel.getTimeRange()`，收到变化时调用 `refreshData(range)`
- `refreshData(TimeRange range)` 根据 `range.granularity` 执行不同查询：
  - **DAY**：`repo.getDailyCounts(rangeStart - 6天, rangeEnd)` → 7 bar，X 轴显示 "6/1 6/2 ... 6/7"，摘要用 `statistics_summary_day_format`
  - **WEEK**：`repo.getDailyCounts(rangeStart, rangeEnd)` → 7 bar，X 轴显示 "一 二 三 四 五 六 日"（根据 week_start_day 调整），摘要用 `statistics_summary_week_format`
  - **MONTH**：`repo.getDailyCounts(rangeStart, rangeEnd)` → 28-31 bar，X 轴显示日期号 "1 2 3 ... 31"，设置 `barChart.setVisibleXRangeMaximum(14)` 允许横向滚动，摘要用 `statistics_summary_month_format`
- 所有粒度都调用 `repo.getStreak()` 更新 Streak 卡片
- 所有粒度的 BarChart 点击 bar 都弹 `DayDetailBottomSheet`

BarChart 通用配置：
```java
barChart.getDescription().setEnabled(false);
barChart.setFitBars(true);
barChart.getXAxis().setGranularity(1f);
barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
barChart.getAxisRight().setEnabled(false);
barChart.getAxisLeft().setGranularity(1f);
barChart.getAxisLeft().setAxisMinimum(0f);
ChartThemeConfig config = ChartThemeConfig.current();
barChart.getXAxis().setTextColor(config.textColor);
barChart.getAxisLeft().setTextColor(config.textColor);
```

- [ ] **步骤 3：推送**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/OverviewFragment.java MobileOrg/src/main/res/layout/fragment_overview.xml
git commit -m "feat(stats): add OverviewFragment with streak, bar chart, summary"
git push
```

---

### 任务 10：创建 TrendFragment

**依赖：** 任务 8
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/TrendFragment.java`, `MobileOrg/src/main/res/layout/fragment_trend.xml`
**导出/变更接口：** `TrendFragment.java::TrendFragment`
**消费接口：** `StatisticsViewModel.java::getTimeRange`, `PomodoroStatisticsRepository.java::getTrendData`, `ChartThemeConfig.java::ChartThemeConfig`
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/TrendFragment.java`
- 创建：`MobileOrg/src/main/res/layout/fragment_trend.xml`

- [ ] **步骤 1：创建 fragment_trend.xml 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/line_chart"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

- [ ] **步骤 2：创建 TrendFragment.java**

关键逻辑：
- `onViewCreated` 中获取共享 ViewModel，观察 `getTimeRange()`
- `refreshData(TimeRange range)` 根据 granularity 调用 `repo.getTrendData(count, granularity)`：
  - **DAY** → `getTrendData(7, "day")` → 最近 7 天每日数据，7 个点
  - **WEEK** → `getTrendData(4, "week")` → 最近 4 周每周数据，4 个点
  - **MONTH** → `getTrendData(12, "month")` → 最近 12 月每月数据，12 个点

LineChart 配置：
```java
lineChart.getDescription().setEnabled(false);
lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
lineChart.getAxisRight().setEnabled(false);
lineChart.getAxisLeft().setGranularity(1f);
lineChart.getAxisLeft().setAxisMinimum(0f);
ChartThemeConfig config = ChartThemeConfig.current();
lineChart.getXAxis().setTextColor(config.textColor);
lineChart.getAxisLeft().setTextColor(config.textColor);
```

- [ ] **步骤 3：推送**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/TrendFragment.java MobileOrg/src/main/res/layout/fragment_trend.xml
git commit -m "feat(stats): add TrendFragment with granularity-aware line chart"
git push
```

---

### 任务 11：创建 DayDetailBottomSheet

**依赖：** 任务 9
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/DayDetailBottomSheet.java`, `MobileOrg/src/main/res/layout/bottom_sheet_day_detail.xml`
**导出/变更接口：** `DayDetailBottomSheet.java::DayDetailBottomSheet`
**消费接口：** `PomodoroStatisticsRepository.java::getSessionsForDate`
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/DayDetailBottomSheet.java`
- 创建：`MobileOrg/src/main/res/layout/bottom_sheet_day_detail.xml`

- [ ] **步骤 1：创建 bottom_sheet_day_detail.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/day_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="?android:attr/textAppearanceMedium"
        android:gravity="center"
        android:paddingBottom="8dp" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/session_list"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

</LinearLayout>
```

- [ ] **步骤 2：创建 DayDetailBottomSheet.java**

继承 `BottomSheetDialogFragment`。通过 `newInstance(long dayStartMillis)` 工厂方法创建。

`onCreateView` 中：
1. 格式化 `dayStartMillis` 为日期标题（如 "2026年6月6日 周五"）
2. `repo.getSessionsForDate(dayStartMillis)` 获取当天所有番茄
3. 用 RecyclerView 展示每个番茄的开始时间（如 "09:00"）和时长（如 "25 分钟"）
4. 无记录时显示"当天无番茄钟记录"

- [ ] **步骤 3：推送**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/DayDetailBottomSheet.java MobileOrg/src/main/res/layout/bottom_sheet_day_detail.xml
git commit -m "feat(stats): add DayDetailBottomSheet for daily session details"
git push
```

---

### 任务 12：添加导航入口

**依赖：** 任务 6, 任务 8
**文件集：** `MobileOrg/src/main/res/menu/outline_menu.xml`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`
**导出/变更接口：** 无
**消费接口：** `StatisticsActivity.java::StatisticsActivity`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/res/menu/outline_menu.xml`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`

- [ ] **步骤 1：在 outline_menu.xml 中添加统计菜单项**

在 `menu_settings` 项之前添加：

```xml
<item
    android:id="@+id/menu_statistics"
    android:icon="@android:drawable/ic_menu_today"
    android:title="@string/menu_statistics" />
```

- [ ] **步骤 2：在 OutlineActivity.onOptionsItemSelected() 中处理点击**

在 `menu_pomodoro` 的 `else if` 分支之前添加：

```java
} else if (id == R.id.menu_statistics) {
    startActivity(new Intent(this, StatisticsActivity.class));
    return true;
```

需要添加 import：
```java
import com.matburt.mobileorg.Gui.Statistics.StatisticsActivity;
```

- [ ] **步骤 3：推送并检查 CI**

```bash
git add MobileOrg/src/main/res/menu/outline_menu.xml MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java
git commit -m "feat(stats): add navigation entry in OutlineActivity menu"
git push
gh run list --limit 1
```

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 2 → 任务 4 → 任务 8 → 任务 9 → 任务 11

- Wave 1（无依赖）：任务 1, 任务 2, 任务 5, 任务 6
- Wave 2（依赖 Wave 1）：任务 3（依赖 2）, 任务 4（依赖 2）, 任务 7（依赖 6）
- Wave 3（依赖 Wave 2）：任务 8（依赖 4, 5, 6）
- Wave 4（依赖 Wave 3）：任务 9（依赖 8）, 任务 10（依赖 8）, 任务 12（依赖 6, 8）
- Wave 5（依赖 Wave 4）：任务 11（依赖 9）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
