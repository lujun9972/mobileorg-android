# 自定义主题实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新增"自定义"主题：应用内逐项调色（16 项）+ 实时预览 + 从预设复制 + SAF 导出/导入；XML 层走"基底 + 程序化覆盖"；顺手修复 Agenda 不跟随主题的既有 bug。设计见 `docs/superpowers/specs/2026-09-15-custom-theme-design.md` 与 `docs/adr/0004-custom-theme-base-plus-overlay.md`。

**架构：** preference 值 `"Custom"` 经 `OrgUtils.getEffectiveThemeName()` 解析为基底（Dark/Light/Mono）驱动 XML 层与图表配色；Java 层由 `CustomTheme`（`DefaultTheme` 子类，从 SharedPreferences 读 16 色）渲染；`CustomThemeStore` 管存储/预设复制/修订号；编辑器遵循"预览不是提交"，保存时整体落库并 bump 修订号。

**技术栈：** SharedPreferences + 自绘 View（HSV picker）+ 现有 MaterialComponents XML style；instrumentation 测试（AndroidJUnit4 + ApplicationProvider，无需 Provider）。

**关键约束（来自 CLAUDE.md 与设计规格，执行时必须遵守）：**
- `themes` 与 `themesEntries` 两数组必须同步追加，保持平行（`LanguageSwitchTest` 守护）
- preferences.xml 中 intent 必须用 `targetPackage`/`targetClass`（隐式 action 不可靠）
- 编辑器"预览不是提交"：picker 确定只更新内存工作集与预览，只有"保存"落库；保存无条件完成
- `Color.parseColor` 对垃圾输入抛 `IllegalArgumentException`——所有解析必须容错回落种子值
- `setStatusBarColor` 是 API 21+，守卫 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP`
- 测试操作 SharedPreferences 后必须在 @After 恢复（清理 `custom_theme_*` 与 `theme` key），避免污染其他测试
- 不用 `git add -A`，只 add 指定文件；提交信息用英文 conventional commits
- 本地不构建：跑单类 instrumentation 需先 `adb connect <设备>`（IP 见 memory），无设备则推送等 CI（`gh run list`）

---

### 任务 1：主题名解析统一 + Custom 选项打通

**依赖：** 无
**文件集：** `Gui/Theme/CustomThemeStore.java`（新建，最小版）, `util/OrgUtils.java`, `Gui/Statistics/ChartThemeConfig.java`, `res/values/arrays.xml`, `res/values-zh/arrays.xml`
**导出/变更接口：** `CustomThemeStore.java::getBase`, `CustomThemeStore.java::getRevision`, `OrgUtils.java::getEffectiveThemeName`, `OrgUtils.java::applyCustomThemeWindow`（本任务不实现，任务 5）
**消费接口：** 无
**复杂度：** standard

本任务完成后的中间态：选中"自定义"= 深色的完整等价物（`DefaultTheme.getTheme("Custom")` 落入既有 else 分支返回 `new DefaultTheme()`；`setTheme` 解析基底默认 `"Dark"`）。每一步都可用。

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/CustomThemeStore.java`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java:150-168`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/ChartThemeConfig.java:29-34`
- 修改：`MobileOrg/src/main/res/values/arrays.xml:60-64`（themes）与 `:201-206`（themesEntries）
- 修改：`MobileOrg/src/main/res/values-zh/arrays.xml:27-31`
- 测试（创建）：`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/Theme/CustomThemeTest.java`

- [ ] **步骤 1：编写失败的测试**

新建 `CustomThemeTest.java`（纯 prefs 测试，无需 Provider；@After 必须恢复现场）：

```java
package com.matburt.mobileorg.test.Gui.Theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.matburt.mobileorg.Gui.Statistics.ChartThemeConfig;
import com.matburt.mobileorg.Gui.Theme.CustomThemeStore;
import com.matburt.mobileorg.util.PreferenceUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class CustomThemeTest {
    private SharedPreferences prefs;
    private String origTheme;

    @Before
    public void setUp() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        origTheme = prefs.getString("theme", "Dark");
        prefs.edit().remove("custom_theme_base").commit();
    }

    @After
    public void tearDown() {
        prefs.edit().putString("theme", origTheme)
                .remove("custom_theme_base").commit();
    }

    @Test
    public void testBaseDefaultsToDark() {
        assertEquals("Dark", CustomThemeStore.getBase());
    }

    @Test
    public void testEffectiveNameResolvesCustomToBase() {
        prefs.edit().putString("theme", "Custom").commit();
        assertEquals("Dark", OrgUtils.getEffectiveThemeName());
        prefs.edit().putString("custom_theme_base", "Light").commit();
        assertEquals("Light", OrgUtils.getEffectiveThemeName());
    }

    @Test
    public void testIsDarkThemeFollowsBase() {
        prefs.edit().putString("theme", "Custom").commit();
        assertEquals(true, OrgUtils.isDarkTheme());          // Dark 基底
        prefs.edit().putString("custom_theme_base", "Light").commit();
        assertEquals(false, OrgUtils.isDarkTheme());
    }

    @Test
    public void testChartThemeConfigFollowsBase() {
        prefs.edit().putString("theme", "Custom").commit();
        ChartThemeConfig dark = ChartThemeConfig.current();
        prefs.edit().putString("custom_theme_base", "Light").commit();
        ChartThemeConfig light = ChartThemeConfig.current();
        assertEquals(false, dark.backgroundColor == light.backgroundColor);
    }
}
```

（import 按需补：`android.preference.PreferenceManager`、`com.matburt.mobileorg.util.OrgUtils`。`ChartThemeConfig` 的单例字段是 private——断言用 backgroundColor 不等即可，不必比较实例。）

- [ ] **步骤 2：运行测试验证失败**

```bash
adb connect 192.168.31.198:<port>   # 无设备则跳过本地，推送等 CI
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.Gui.Theme.CustomThemeTest
```

预期：编译失败（`CustomThemeStore`/`getEffectiveThemeName` 不存在）。

- [ ] **步骤 3：实现**

`CustomThemeStore.java`（最小版，key 常量一次定齐，后续任务复用）：

```java
package com.matburt.mobileorg.Gui.Theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.matburt.mobileorg.OrgData.MobileOrgApplication;

public class CustomThemeStore {
    public static final String KEY_BASE = "custom_theme_base";
    public static final String KEY_REVISION = "custom_theme_revision";
    public static final String KEY_GRAY = "custom_theme_gray";
    public static final String KEY_C1 = "custom_theme_c1";
    public static final String KEY_CA = "custom_theme_ca";
    public static final String KEY_C3 = "custom_theme_c3";
    public static final String KEY_C4 = "custom_theme_c4";
    public static final String KEY_FG = "custom_theme_fg";
    public static final String KEY_BG = "custom_theme_bg";
    public static final String KEY_LEVEL_COLORS = "custom_theme_levelColors";
    public static final String BASE_DARK = "Dark";
    public static final String BASE_LIGHT = "Light";
    public static final String BASE_MONO = "Monochrome";

    private static SharedPreferences prefs() {
        Context ctx = MobileOrgApplication.getContext();
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    public static String getBase() {
        return prefs().getString(KEY_BASE, BASE_DARK);
    }

    public static String getRevision() {
        return prefs().getString(KEY_REVISION, "");
    }

    /** All custom-theme pref keys (for config export). */
    public static String[] prefKeys() {
        return new String[] { KEY_BASE, KEY_REVISION, KEY_GRAY, KEY_C1, KEY_CA,
                KEY_C3, KEY_C4, KEY_FG, KEY_BG, KEY_LEVEL_COLORS };
    }
}
```

`OrgUtils.java`——`setTheme`/`isDarkTheme` 改为统一解析（保持分支语义不变）：

```java
/** Resolve "Custom" to its base (Dark/Light/Monochrome); other names pass through. */
public static String getEffectiveThemeName() {
    String name = PreferenceUtils.getThemeName();
    return "Custom".equals(name)
            ? com.matburt.mobileorg.Gui.Theme.CustomThemeStore.getBase() : name;
}

public static void setTheme(Activity activity) {
    String themeName = getEffectiveThemeName();
    if (themeName.equals("Dark"))
        activity.setTheme(R.style.Theme_MobileOrg_Dark);
    else if (themeName.equals("Monochrome"))
        activity.setTheme(R.style.Theme_MobileOrg_Monochrome);
    else
        activity.setTheme(R.style.Theme_MobileOrg_Light);
}

public static boolean isDarkTheme() {
    String themeName = getEffectiveThemeName();
    return themeName.equals("Dark") || themeName.equals("Monochrome");
}
```

（注意 `getThemeName()` 返回值永不为 null 且有默认 "Dark"；`getEffectiveThemeName` 用常量在前的 equals。）

`ChartThemeConfig.current()`：

```java
public static ChartThemeConfig current() {
    String theme = OrgUtils.getEffectiveThemeName();
    if ("Light".equals(theme)) return LIGHT;
    if ("Monochrome".equals(theme)) return MONO;
    return DARK;
}
```

arrays：`themes` 追加 `<item>Custom</item>`；`themesEntries`（values）追加 `<item>Custom</item>`；`values-zh/themesEntries` 追加 `<item>自定义</item>`。三个数组各加一项，顺序一致。

- [ ] **步骤 4：运行测试验证通过**

同步骤 2 命令。预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/CustomThemeStore.java MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/ChartThemeConfig.java MobileOrg/src/main/res/values/arrays.xml MobileOrg/src/main/res/values-zh/arrays.xml MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/Theme/CustomThemeTest.java
git commit -m "feat: resolve Custom theme to its base and add Custom option"
```

---

### 任务 2：CustomTheme + 预设复制（复刻核心）

**依赖：** 任务 1
**文件集：** `Gui/Theme/CustomTheme.java`, `Gui/Theme/DefaultTheme.java`, `Gui/Theme/CustomThemeStore.java`
**导出/变更接口：** `CustomTheme.java::CustomTheme`, `DefaultTheme.java::getTheme`（新增 Custom 分支）, `CustomThemeStore.java::loadAll`, `CustomThemeStore.java::saveAll`, `CustomThemeStore.java::presetValues`
**消费接口：** `CustomThemeStore.java` 的 key 常量
**复杂度：** deep

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/CustomTheme.java`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/DefaultTheme.java:42-50`（工厂）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/CustomThemeStore.java`
- 测试：`CustomThemeTest.java`

- [ ] **步骤 1：编写失败的测试**

`CustomThemeTest.java` 追加（setUp 中额外清理全部 `custom_theme_*` key；@After 同步恢复）：

```java
private void clearCustomKeys() {
    SharedPreferences.Editor e = prefs.edit();
    for (String k : CustomThemeStore.prefKeys()) e.remove(k);
    e.commit();
}

@Test
public void testCustomThemeSeedsFromDark() {
    clearCustomKeys();
    prefs.edit().putString("theme", "Custom").commit();
    DefaultTheme custom = DefaultTheme.getTheme(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
    DefaultTheme dark = new DefaultTheme();
    assertEquals(dark.gray, custom.gray);
    assertEquals(dark.c1Red, custom.c1Red);
    assertEquals(dark.caLGreen, custom.caLGreen);
    assertEquals(dark.c3Yellow, custom.c3Yellow);
    assertEquals(dark.c4Blue, custom.c4Blue);
    assertEquals(dark.defaultForeground, custom.defaultForeground);
    assertEquals(dark.defaultBackground, custom.defaultBackground);
    assertEquals("white", custom.defaultFontColor);
    assertEquals(10, custom.levelColors.length);
}

private void assertReplicates(DefaultTheme preset) {
    DefaultTheme custom = DefaultTheme.getTheme(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
    assertEquals(preset.gray, custom.gray);
    assertEquals(preset.c1Red, custom.c1Red);
    assertEquals(preset.caLGreen, custom.caLGreen);
    assertEquals(preset.c3Yellow, custom.c3Yellow);
    assertEquals(preset.c4Blue, custom.c4Blue);
    assertEquals(preset.defaultForeground, custom.defaultForeground);
    assertEquals(preset.defaultBackground, custom.defaultBackground);
    assertEquals(preset.defaultFontColor, custom.defaultFontColor);
    for (int i = 0; i < 10; i++)
        assertEquals("level " + i,
                preset.levelColors[i % preset.levelColors.length],
                custom.levelColors[i]);
}

@Test
public void testReplicatesWhitePreset() {
    clearCustomKeys();
    prefs.edit().putString("theme", "Custom").commit();
    CustomThemeStore.saveAll(CustomThemeStore.presetValues("Light"), null);
    assertReplicates(new WhiteTheme());
}

@Test
public void testReplicatesMonoPreset() {
    clearCustomKeys();
    prefs.edit().putString("theme", "Custom").commit();
    CustomThemeStore.saveAll(CustomThemeStore.presetValues("Monochrome"), null);
    assertReplicates(new MonoTheme());
}

@Test
public void testGarbageHexFallsBackToSeed() {
    clearCustomKeys();
    prefs.edit().putString("theme", "Custom")
            .putString(CustomThemeStore.KEY_BG, "not-a-color").commit();
    DefaultTheme custom = DefaultTheme.getTheme(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
    assertEquals(new DefaultTheme().defaultBackground, custom.defaultBackground);
}

@Test
public void testSaveAllBumpsRevision() {
    clearCustomKeys();
    String before = CustomThemeStore.getRevision();
    CustomThemeStore.saveAll(CustomThemeStore.presetValues("Dark"), null);
    String after = CustomThemeStore.getRevision();
    assertEquals(false, after.equals(before));
    assertEquals(false, after.isEmpty());
}
```

（import 补 `DefaultTheme`/`WhiteTheme`/`MonoTheme`。`saveAll(values, editor)` 第二参预留任务 4 的"工作集整体落库"复用，传 null 表示自行 apply。）

- [ ] **步骤 2：运行测试验证失败**

同任务 1 命令。预期：编译失败（`loadAll`/`saveAll`/`presetValues`/`CustomTheme` 不存在）。

- [ ] **步骤 3：实现**

`CustomTheme.java`：

```java
package com.matburt.mobileorg.Gui.Theme;

import android.graphics.Color;

public class CustomTheme extends DefaultTheme {

    public CustomTheme() {
        super();
        java.util.Map<String, String> v = CustomThemeStore.loadAll();
        gray              = parse(v, CustomThemeStore.KEY_GRAY, gray);
        c1Red             = parse(v, CustomThemeStore.KEY_C1, c1Red);
        caLGreen          = parse(v, CustomThemeStore.KEY_CA, caLGreen);
        c3Yellow          = parse(v, CustomThemeStore.KEY_C3, c3Yellow);
        c4Blue            = parse(v, CustomThemeStore.KEY_C4, c4Blue);
        defaultForeground = parse(v, CustomThemeStore.KEY_FG, defaultForeground);
        defaultBackground = parse(v, CustomThemeStore.KEY_BG, defaultBackground);
        levelColors       = parseLevels(v.get(CustomThemeStore.KEY_LEVEL_COLORS), levelColors);
        // defaultFontColor 由背景亮度推导（不暴露 UI）：
        defaultFontColor  = isLightColor(defaultBackground) ? "black" : "white";
    }

    private static int parse(java.util.Map<String, String> v, String key, int fallback) {
        String s = v.get(key);
        if (s == null) return fallback;
        try { return Color.parseColor(s); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    private static int[] parseLevels(String csv, int[] fallback) {
        if (csv == null || csv.isEmpty()) return fallback;
        try {
            String[] parts = csv.split(",");
            int[] out = new int[fallback.length];
            for (int i = 0; i < out.length; i++)
                out[i] = Color.parseColor(parts[i % parts.length].trim());
            return out;
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return fallback;
        }
    }

    /** ITU-R 601 luma; > 0.5 means a light background needs dark text. */
    public static boolean isLightColor(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return (0.299 * r + 0.587 * g + 0.114 * b) > 127.5;
    }
}
```

`DefaultTheme.getTheme()` 工厂加分支（放在 Light 判断之前或之后均可，值互斥）：

```java
public static DefaultTheme getTheme(Context context) {
    final String themeName = PreferenceUtils.getThemeName();
    if (themeName.equals("Custom"))
        return new CustomTheme();
    if (themeName.equals("Light"))
        return new WhiteTheme();
    else if (themeName.equals("Monochrome"))
        return new MonoTheme();
    else
        return new DefaultTheme();
}
```

`CustomThemeStore.java` 追加：

```java
public static java.util.Map<String, String> loadAll() {
    SharedPreferences p = prefs();
    java.util.Map<String, String> out = new java.util.HashMap<>();
    for (String k : prefKeys())
        out.put(k, p.getString(k, null));
    return out;
}

/** Persist values and bump the revision. editor=null → apply here. */
public static void saveAll(java.util.Map<String, String> values,
        SharedPreferences.Editor editor) {
    SharedPreferences p = prefs();
    boolean own = (editor == null);
    if (own) editor = p.edit();
    if (values != null)
        for (java.util.Map.Entry<String, String> e : values.entrySet())
            if (e.getValue() != null) editor.putString(e.getKey(), e.getValue());
    editor.putString(KEY_REVISION, String.valueOf(System.currentTimeMillis()));
    if (own) editor.apply();
}

/** The 16 exposed values of a built-in preset, levelColors modulo-filled to 10. */
public static java.util.Map<String, String> presetValues(String presetName) {
    DefaultTheme t;
    if (BASE_LIGHT.equals(presetName))      t = new WhiteTheme();
    else if (BASE_MONO.equals(presetName))  t = new MonoTheme();
    else                                    t = new DefaultTheme();

    java.util.Map<String, String> m = new java.util.HashMap<>();
    m.put(KEY_GRAY, hex(t.gray));
    m.put(KEY_C1,   hex(t.c1Red));
    m.put(KEY_CA,   hex(t.caLGreen));
    m.put(KEY_C3,   hex(t.c3Yellow));
    m.put(KEY_C4,   hex(t.c4Blue));
    m.put(KEY_FG,   hex(t.defaultForeground));
    m.put(KEY_BG,   hex(t.defaultBackground));
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10; i++) {
        if (i > 0) sb.append(',');
        sb.append(hex(t.levelColors[i % t.levelColors.length]));
    }
    m.put(KEY_LEVEL_COLORS, sb.toString());
    return m;
}

private static String hex(int color) {
    return String.format("#%06x", 0xFFFFFF & color);
}
```

- [ ] **步骤 4：运行测试验证通过**

同任务 1 命令。预期：全部 PASS（含任务 1 测试）。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/CustomTheme.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/DefaultTheme.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/CustomThemeStore.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/Theme/CustomThemeTest.java
git commit -m "feat: add CustomTheme loading colors from preferences with preset copy"
```

---

### 任务 3：HSV 颜色选择器

**依赖：** 无（可与任务 2 并行）
**文件集：** `Gui/Theme/HsvColorPickerView.java`, `Gui/Theme/HsvColorPickerDialog.java`, `res/layout/view_hsv_color_picker.xml`
**导出/变更接口：** `HsvColorPickerView.java::setColor`, `HsvColorPickerView.java::getColor`, `HsvColorPickerView.java::setOnColorChangedListener`, `HsvColorPickerDialog.java::HsvColorPickerDialog`
**消费接口：** 无
**复杂度：** standard

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/HsvColorPickerView.java`
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/HsvColorPickerDialog.java`
- 创建：`MobileOrg/src/main/res/layout/view_hsv_color_picker.xml`
- 测试：`CustomThemeTest.java`

- [ ] **步骤 1：编写失败的测试**

`CustomThemeTest.java` 追加（View 需在 UI 线程 inflate，用 `InstrumentationRegistry.getInstrumentation().runOnMainSync` 或直接在 @Test 上用主线程 runner——最简单：`@Test` 内 `runOnMainSync` 包裹）：

```java
@Test
public void testPickerRoundTrip() throws Throwable {
    final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
    final HsvColorPickerView[] holder = new HsvColorPickerView[1];
    Runnable inflate = () -> {
        HsvColorPickerView v = new HsvColorPickerView(ctx);
        v.setColor(0xFF112233);
        holder[0] = v;
    };
    InstrumentationRegistry.getInstrumentation().runOnMainSync(inflate);
    assertEquals(0xFF112233, holder[0].getColor());
}
```

（`getColor()` 经 HSV 往返可能有 ±1 量化误差——实现时 `getColor()` 直接返回保存的原始 int，不重算，保证精确相等。）

- [ ] **步骤 2：运行测试验证失败**

预期：编译失败（`HsvColorPickerView` 不存在）。

- [ ] **步骤 3：实现**

`HsvColorPickerView`：自绘 View，~150 行。
- 布局：上半 SV 渐变方块（当前 hue 的白→纯色横向渐变 ⊕ 黑色纵向渐变，`LinearGradient` 两层 compose 到 `Bitmap`/`Shader`），下半 hue 竖条（`SweepGradient` 或 7 段 `LinearGradient` 拼接），各带选中游标（圆圈描边）
- 触摸：`onTouchEvent` 按 y 分区拖动，`invalidate()` 重绘 + 回调 listener
- 状态：`float[] hsv = new float[3]` + `int originalColor`（往返精度用）
- `setColor(int)`：`Color.colorToHSV` 填 hsv，记录 original；`getColor()`：返回 original（未拖动时）或 `Color.HSVToColor(hsv)`（拖动后置 original = 计算值）
- 尺寸：方块宽=测量宽，高=宽×0.6；hue 条高 32dp
- 不支持 alpha（主题色不透明）

`HsvColorPickerDialog`：

```java
public class HsvColorPickerDialog {
    public interface OnColorPicked { void onColor(int color); }

    public static void show(AppCompatActivity activity, int currentColor,
                            CharSequence title, final OnColorPicked cb) {
        HsvColorPickerView view = new HsvColorPickerView(activity);
        view.setColor(currentColor);
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        cb.onColor(view.getColor()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
```

`view_hsv_color_picker.xml` 可省（纯代码构建），若建则只包一个自定义 View。

- [ ] **步骤 4：运行测试验证通过**

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/HsvColorPickerView.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/HsvColorPickerDialog.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/Theme/CustomThemeTest.java
git commit -m "feat: add HSV color picker view and dialog"
```

---

### 任务 4：主题编辑器 Activity

**依赖：** 任务 2、任务 3
**文件集：** `Gui/Theme/ThemeEditorActivity.java`, `res/layout/activity_theme_editor.xml`, `res/values/strings.xml`, `res/values-zh/strings.xml`, `AndroidManifest.xml`, `res/xml/preferences.xml`
**导出/变更接口：** `ThemeEditorActivity`（入口）
**消费接口：** `CustomThemeStore.java::loadAll/saveAll/presetValues/getBase`, `HsvColorPickerDialog`, `OrgUtils.java::setTheme`
**复杂度：** standard

UI 任务，验证 = 编译 + 手测（步骤 5），无自动化断言（复刻逻辑已被任务 2 测试覆盖）。

**文件：**
- 创建：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/ThemeEditorActivity.java`
- 创建：`MobileOrg/src/main/res/layout/activity_theme_editor.xml`
- 修改：`MobileOrg/src/main/res/values/strings.xml`、`values-zh/strings.xml`
- 修改：`MobileOrg/src/main/AndroidManifest.xml`（注册 Activity）
- 修改：`MobileOrg/src/main/res/xml/preferences.xml:66`（主题项之后）

- [ ] **步骤 1：strings 资源（en + zh 成对）**

```xml
<!-- values/strings.xml -->
<string name="preference_theme_editor">Custom theme editor</string>
<string name="preference_theme_editor_summary">Adjust colors of the custom theme</string>
<string name="theme_editor_base">Base</string>
<string name="theme_editor_section_basic">Basic colors</string>
<string name="theme_editor_section_semantic">Semantic colors</string>
<string name="theme_editor_section_levels">Level colors</string>
<string name="theme_editor_bg">Background</string>
<string name="theme_editor_fg">Foreground</string>
<string name="theme_editor_gray">Gray (tags)</string>
<string name="theme_editor_c1">Active TODO</string>
<string name="theme_editor_ca">Done TODO</string>
<string name="theme_editor_c3">Priority</string>
<string name="theme_editor_c4">Links</string>
<string name="theme_editor_level">Level %1$d</string>
<string name="theme_editor_copy">Copy from preset</string>
<string name="theme_editor_reset">Reset</string>
<string name="theme_editor_save">Save</string>
<string name="theme_editor_saved">Saved</string>
<string name="theme_editor_dirty_confirm">Discard unsaved changes?</string>
<string name="theme_editor_discard">Discard</string>
<string name="theme_editor_cancel">Cancel</string>
```

zh 侧：`自定义主题编辑器 / 调整自定义主题的颜色 / 基底 / 基础色 / 语义色 / 层级色 / 背景 / 前景 / 灰（标签）/ 活跃 TODO / 已完成 TODO / 优先级 / 链接 / 层级 %1$d / 从预设复制 / 重置 / 保存 / 已保存 / 丢弃未保存的修改？/ 丢弃 / 取消`。

- [ ] **步骤 2：布局与注册**

`activity_theme_editor.xml`：`ScrollView` > 竖直 `LinearLayout`（MaterialComponents widget 必须显式声明 `layout_width`/`layout_height`——CLAUDE.md 坑）：

```
previewContainer   → LinearLayout（代码填充预览行）
RadioGroup baseRadio（水平，3 个 RadioButton：深色/浅色/暖纸）
basicContainer     → LinearLayout（代码填充颜色行）
semanticContainer  → LinearLayout
levelsContainer    → LinearLayout（两行GridLayout 或 5 列流）
按钮行：Button copy / Button reset / Button save
```

颜色行与预览行全部代码构建（16 行来自 `{key, labelRes}` 描述数组，布局零重复）。色块按钮用 `Button` + `getBackground().setColorFilter(color, SRC)` 或 `BackgroundColorSpan` 的圆角 `GradientDrawable`。

`AndroidManifest.xml`：

```xml
<activity
    android:name=".Gui.Theme.ThemeEditorActivity"
    android:label="@string/preference_theme_editor" />
```

`preferences.xml` 主题 ListPreference（:61-66）之后：

```xml
<Preference
    android:summary="@string/preference_theme_editor_summary"
    android:title="@string/preference_theme_editor" >
    <intent
        android:targetPackage="com.matburt.mobileorg"
        android:targetClass="com.matburt.mobileorg.Gui.Theme.ThemeEditorActivity" />
</Preference>
```

- [ ] **步骤 3：实现 Activity**

骨架（约 250 行）：

```java
public class ThemeEditorActivity extends AppCompatActivity {
    private final Map<String, Integer> working = new LinkedHashMap<>(); // 工作集
    private int[] workingLevels = new int[10];
    private String workingBase;
    private boolean dirty = false;

    protected void onCreate(Bundle b) {
        OrgUtils.setTheme(this);
        super.onCreate(b);
        setContentView(R.layout.activity_theme_editor);
        OrgUtils.applyCustomThemeWindow(this);   // 任务 5 提供；先注释占位
        loadWorkingSet();
        buildPreview(); buildBaseSelector(); buildColorRows(); wireButtons();
    }
```

行为要点：
- `loadWorkingSet()`：`CustomThemeStore.loadAll()` + `DefaultTheme.getTheme(this)` 双源——有 key 用 key，无 key 用当前渲染主题种子值（保证首次打开即为 Dark 种子的完整 16 色而非空白）
- 色块点击 → `HsvColorPickerDialog.show(...)` → 确定：写 `working` + 刷色块 + 刷预览 + `dirty = true`（**不落库**——预览不是提交）
- 基底 RadioButton → `workingBase`（同样只进工作集）
- 从预设复制：`AlertDialog` 三选（深色/浅色/暖纸）→ `CustomThemeStore.presetValues(name)` 解析进工作集（base 不动）→ 全量刷新
- 重置：工作集 = Dark 种子（`presetValues("Dark")`）→ 刷新
- 保存：组装 map（16 色 + levelColors CSV + base）→ `SharedPreferences.Editor` 一次批处理：`CustomThemeStore.saveAll(colorValues, editor)` + `editor.putString(KEY_BASE, workingBase)` → `editor.apply()` → toast → `finish()`（无条件完成）
- 返回键：`dirty` 时弹确认对话框（丢弃/取消），否则直接 `finish()`
- 预览块：3 行代码构建——`[#A] TODO 学日语 :work:`（c3/c1/fg/gray）、缩进 2 层 + `[[https://orgmode.org][org mode]]` 链接（levelColors[2]/c4）、`DONE 已完成`（ca）；背景色 = working bg
- `onOptionsItemSelected` 或标题栏返回 = 同返回键逻辑

- [ ] **步骤 4：编译验证**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.matburt.mobileorg.test.Gui.Theme.CustomThemeTest
```

（编译整个 app + 回归主题测试。）

- [ ] **步骤 5：手测**

安装到设备：设置 → 界面 → 出现"自定义主题编辑器" → 打开改背景色 → 预览即时变化 → 不保存返回 → 弹确认 → 保存后 outline 变色。

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Theme/ThemeEditorActivity.java MobileOrg/src/main/res/layout/activity_theme_editor.xml MobileOrg/src/main/res/values/strings.xml MobileOrg/src/main/res/values-zh/strings.xml MobileOrg/src/main/AndroidManifest.xml MobileOrg/src/main/res/xml/preferences.xml
git commit -m "feat: add custom theme editor activity"
```

---

### 任务 5：XML 层程序化覆盖 + Agenda 主题修复

**依赖：** 任务 2
**文件集：** `util/OrgUtils.java` + 12 个 Activity
**导出/变更接口：** `OrgUtils.java::applyCustomThemeWindow`
**消费接口：** `DefaultTheme.java::getTheme`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java`
- 修改（overlay 一行）：`CertificateConflictActivity`、`SearchActivity`、`ViewActivity`、`OutlineActivity`、`EditActivity`、`WizardActivity`、`HelpDetailActivity`、`HelpActivity`、`StatisticsActivity`、`SettingsActivity`（setContentView 之后）
- 修改（setTheme + overlay）：`Gui/Agenda/AgendaActivity.java`、`AgendasActivity.java`（既有 bug：不调 setTheme 恒为 Dark）

- [ ] **步骤 1：实现 applyCustomThemeWindow**

```java
/** Overlay window/action-bar colors with the custom theme's tones. No-op unless Custom. */
public static void applyCustomThemeWindow(Activity activity) {
    if (!"Custom".equals(PreferenceUtils.getThemeName()))
        return;
    DefaultTheme theme = DefaultTheme.getTheme(activity);
    activity.getWindow().setBackgroundDrawable(
            new ColorDrawable(theme.defaultBackground));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        activity.getWindow().setStatusBarColor(
                darken(theme.defaultBackground, 0.6f));
    androidx.appcompat.app.ActionBar ab =
            (activity instanceof androidx.appcompat.app.AppCompatActivity)
                    ? ((androidx.appcompat.app.AppCompatActivity) activity)
                            .getSupportActionBar() : null;
    if (ab != null) {
        ab.setBackgroundDrawable(new ColorDrawable(
                darken(theme.defaultBackground, 0.75f)));
        CharSequence title = ab.getTitle();
        if (title != null) {
            SpannableString s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(theme.defaultForeground), 0,
                    s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ab.setTitle(s);
        }
    }
}

private static int darken(int color, float factor) {
    return Color.rgb((int) (Color.red(color) * factor),
            (int) (Color.green(color) * factor),
            (int) (Color.blue(color) * factor));
}
```

（SettingsActivity 是 framework `PreferenceActivity`——instanceof 分支自然跳过 ActionBar，只覆盖 window/statusBar。）

- [ ] **步骤 2：接线 12 个 Activity**

每个调 `setContentView(...)` 之后加一行 `OrgUtils.applyCustomThemeWindow(this);`。Agenda 两个 Activity 另在 `super.onCreate` 之前加 `OrgUtils.setTheme(this);`。

- [ ] **步骤 3：编译 + 手测**

单类测试命令编译；手测：选自定义主题 → outline/设置/搜索/统计页 window 与 ActionBar 跟随主色调、标题可读；Agenda 页跟随主题（修复验证）；切回内置主题行为与之前一致。

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/OrgUtils.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/CertificateConflictActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/SearchActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/EditActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Wizard/WizardActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpDetailActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Statistics/StatisticsActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Agenda/AgendaActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Agenda/AgendasActivity.java
git commit -m "feat: tint window/action bar from custom theme; fix Agenda ignoring theme"
```

（文件路径以实际为准——各 Activity 所在包先 `find` 确认再 add。）

---

### 任务 6：修订号刷新 + 配置导出/导入

**依赖：** 任务 2
**文件集：** `Gui/Outline/OutlineActivity.java`, `util/SyncConfigHelper.java`
**导出/变更接口：** 无新增
**消费接口：** `CustomThemeStore.java::getRevision/prefKeys`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java:60,64,105-118`
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/util/SyncConfigHelper.java:32-48`
- 测试：`CustomThemeTest.java`

- [ ] **步骤 1：编写失败的测试**

`CustomThemeTest.java` 追加（导出到缓存文件 `file://` uri，`ContentResolver.openOutputStream` 支持 file scheme）：

```java
@Test
public void testExportContainsThemeKeys() throws Exception {
    Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
    CustomThemeStore.saveAll(CustomThemeStore.presetValues("Dark"), null);
    java.io.File f = new java.io.File(ctx.getCacheDir(), "cfg-test.json");
    java.net.Uri uri = java.net.Uri.fromFile(f);
    String err = SyncConfigHelper.exportConfig(ctx, uri);
    assertEquals(null, err);
    String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
    for (String k : CustomThemeStore.prefKeys())
        assertTrue("missing " + k, json.contains("\"" + k + "\""));
    f.delete();
}

@Test
public void testImportRestoresThemeKeys() throws Exception {
    Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
    clearCustomKeys();
    java.io.File f = new java.io.File(ctx.getCacheDir(), "cfg-test2.json");
    java.nio.file.Files.write(f.toPath(),
            ("{\"version\":1,\"" + CustomThemeStore.KEY_BG + "\":\"#101010\"}")
                    .getBytes("UTF-8"));
    String err = SyncConfigHelper.importConfig(ctx, java.net.Uri.fromFile(f));
    assertEquals(null, err);
    assertEquals("#101010",
            CustomThemeStore.loadAll().get(CustomThemeStore.KEY_BG));
    f.delete();
}
```

（import 补 `SyncConfigHelper`、`assertTrue`。）

- [ ] **步骤 2：运行测试验证失败**

预期：`testExportContainsThemeKeys` 失败（JSON 无主题 key）。

- [ ] **步骤 3：实现**

`SyncConfigHelper.java`——`SYNC_KEYS` 初始化后追加主题 key（保持单一事实来源）：

```java
static {
    SYNC_KEYS.addAll(java.util.Arrays.asList(
            com.matburt.mobileorg.Gui.Theme.CustomThemeStore.prefKeys()));
}
```

（`SYNC_KEYS` 声明处由 `HashSet<String>(Arrays.asList(...))` 改为非 final 引用即可；或直接把 `prefKeys()` 并入 asList——选改动最小者。）

`OutlineActivity.java`：

```java
private String currentThemeRev;   // 与 currentThemeName 并列
// onCreate（:64 之后）：
currentThemeRev = CustomThemeStore.getRevision();
// onResume（:109-113 替换）：
String newTheme = PreferenceUtils.getThemeName();
String newRev = CustomThemeStore.getRevision();
if (newTheme != null && (!newTheme.equals(currentThemeName)
        || !newRev.equals(currentThemeRev))) {
    currentThemeName = newTheme;
    currentThemeRev = newRev;
    recreate();
    return;
}
```

- [ ] **步骤 4：运行测试验证通过 + 全量回归**

```bash
./gradlew connectedDebugAndroidTest
```

预期：全部 PASS——特别是 `LanguageSwitchTest`（数组平行）与既有 94 测试。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java MobileOrg/src/main/java/com/matburt/mobileorg/util/SyncConfigHelper.java MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/Theme/CustomThemeTest.java
git commit -m "feat: recreate outline on custom theme revision; export theme with sync config"
```

---

### 任务 7：CI 验证 + 手测清单

**依赖：** 任务 4、5、6
**文件集：** 无
**复杂度：** quick

- [ ] **步骤 1：推送并监控 CI**

```bash
git push
gh run list --limit 3
gh run view <run-id>
```

预期：构建 + API 30 模拟器全量 instrumentation 通过。

- [ ] **步骤 2：手测清单（CI 出包后自测）**

1. 设置 → 界面 → 主题选"自定义" → outline 外观与深色主题一致（Dark 种子）
2. 编辑器改背景为白色 → 预览即时变 → 保存 → 返回 outline 自动 recreate，window/ActionBar/状态栏变浅且标题可读（深色文字）
3. 从预设复制"暖纸" → 保存 → outline 与内置"暖纸护眼"肉眼一致；"浅色"同理（复刻验收）
4. 基底切"浅色" + 深背景 → 对话框/preference 呈浅色结构（已知边界：help 页 CSS 跟基底不跟背景）
5. 统计页配色跟随基底切换
6. 导出配置文件 → JSON 含 `custom_theme_*`；清掉 app 数据后导入 → 主题恢复
7. Agenda 页跟随所选主题（原 bug 修复）
8. 主题切回"深色/浅色/暖纸护眼" → 行为与改动前一致（无回归）
9. 中英文切换 → 编辑器与主题选项文案正确、数组平行（LanguageSwitchTest CI 已守护）

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 4 → 任务 5 → 任务 6 → 任务 7

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2、任务 3（互不依赖，可并行）
- Wave 3（依赖 Wave 2）：任务 4（需 2+3）、任务 5（需 2）、任务 6（需 2）
- Wave 4（全部完成后）：任务 7

**并行警告**：任务 1 与任务 5 都改 `OrgUtils.java`；任务 2 与任务 4 都改 `CustomThemeStore` 相关文件——并行执行时这两个文件对必须串行。建议整体串行，仅任务 3 可安全穿插。
