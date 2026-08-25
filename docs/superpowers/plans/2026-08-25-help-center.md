# 应用内帮助中心 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 用应用内帮助中心（原生列表 → WebView 详情，assets 打包双语 HTML）替换指向已死上游 wiki 的帮助入口。

**架构：** `HelpActivity`（RecyclerView 主题列表 + 关于块）→ `HelpDetailActivity`（WebView 加载 `assets/help/{zh|en}/{topic}.html`，按主题注入 dark class，外链交外部浏览器）。数据为静态 `HelpTopic[]`；入口改 `OutlineActivity.runHelp()` 与设置页 preference。

**技术栈：** Android（minSdk 17 / targetSdk 34）、AppCompat + RecyclerView（依赖已有）、WebView、无新增第三方依赖。

**规格：** `docs/superpowers/specs/2026-08-25-help-menu-design.md`（已批准）

**构建约定（重要）：** 本地无 Android SDK，不可构建。所有编译/测试验证通过 CI：push 后 `gh run list` / `gh run view <id>` 检查（`test.yml` 跑 androidTest，`build.yml` 构建）。serial 模式每任务 commit 后统一 push 验证；parallel 模式 wave 收口 push。TDD 红绿循环退化为"测试与实现同任务交付，CI 统一验证"。

**已知坑（CLAUDE.md 摘录，直接约束实现）：**
- preference intent 必须用 `android:targetPackage` + `android:targetClass`，不用隐式 action
- `OrgUtils.setTheme(this)` 必须在 `setContentView()` 之前调用
- 绝不用 `git add -A`，只 add 具体文件
- WebView 渲染用户内容要防 XSS——帮助 HTML 是自维护静态资产，无用户内容，不适用

---

## 文件结构

新增：

- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java` — 主题清单数据类 + 语言路由
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpActivity.java` — 列表页
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpDetailActivity.java` — WebView 详情页
- `MobileOrg/src/main/res/layout/activity_help.xml` — 列表页布局（RecyclerView + 关于块）
- `MobileOrg/src/main/res/layout/item_help_topic.xml` — 列表条目
- `MobileOrg/src/main/res/layout/activity_help_detail.xml` — WebView 容器
- `MobileOrg/src/main/assets/help/help.css` — 共享样式（深浅两套 CSS 变量）
- `MobileOrg/src/main/assets/help/zh/{quick-start,sync,outline,pomodoro,statistics,reminders,extras}.html` × 7
- `MobileOrg/src/main/assets/help/en/` 同名 × 7
- `MobileOrg/src/main/assets/help/images/{sync-wizard,outline-main,capture-editor,long-press-menu}.webp` × 4
- `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpTopicTest.java`
- `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpActivityTest.java`

修改：

- `MobileOrg/src/main/res/values/strings.xml` + `values-zh/strings.xml` — 帮助字符串
- `MobileOrg/src/main/AndroidManifest.xml` — 注册 2 个 Activity
- `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java` — runHelp 接线
- `MobileOrg/src/main/res/xml/preferences.xml` — 设置页帮助入口

---

### 任务 1：HelpTopic 数据层 + 双语 HTML 骨架 + 共享样式 + 完整性测试

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpTopicTest.java`, `MobileOrg/src/main/assets/help/help.css`, `MobileOrg/src/main/assets/help/zh/quick-start.html`, `MobileOrg/src/main/assets/help/zh/sync.html`, `MobileOrg/src/main/assets/help/zh/outline.html`, `MobileOrg/src/main/assets/help/zh/pomodoro.html`, `MobileOrg/src/main/assets/help/zh/statistics.html`, `MobileOrg/src/main/assets/help/zh/reminders.html`, `MobileOrg/src/main/assets/help/zh/extras.html`, `MobileOrg/src/main/assets/help/en/quick-start.html`, `MobileOrg/src/main/assets/help/en/sync.html`, `MobileOrg/src/main/assets/help/en/outline.html`, `MobileOrg/src/main/assets/help/en/pomodoro.html`, `MobileOrg/src/main/assets/help/en/statistics.html`, `MobileOrg/src/main/assets/help/en/reminders.html`, `MobileOrg/src/main/assets/help/en/extras.html`, `MobileOrg/src/main/res/values/strings.xml`, `MobileOrg/src/main/res/values-zh/strings.xml`
**导出/变更接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java::TOPICS`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java::getLangDir`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java::getAssetPath`
**消费接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/util/PreferenceUtils.java::getThemeName`
**复杂度：** standard

**文件：**
- 创建：上述文件集全部
- 修改：`strings.xml` / `values-zh/strings.xml`（追加）

- [ ] **步骤 1：编写失败的完整性测试**

`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpTopicTest.java`：

```java
package com.matburt.mobileorg.test.Gui;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.matburt.mobileorg.Gui.Help.HelpTopic;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class HelpTopicTest {

    private Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void topicsCoverSevenDocuments() {
        assertEquals(7, HelpTopic.TOPICS.length);
    }

    @Test
    public void fileNamesAreUnique() {
        assertEquals(7, java.util.Arrays.stream(HelpTopic.TOPICS)
                .map(t -> t.fileName).distinct().count());
    }

    @Test
    public void allTopicsExistInBothLanguages() throws IOException {
        for (HelpTopic topic : HelpTopic.TOPICS) {
            for (String lang : new String[]{"zh", "en"}) {
                InputStream in = targetContext().getAssets()
                        .open("help/" + lang + "/" + topic.fileName);
                in.close();
            }
        }
    }

    @Test
    public void sharedStylesheetExists() throws IOException {
        targetContext().getAssets().open("help/help.css").close();
    }
}
```

- [ ] **步骤 2：实现 HelpTopic**

`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java`：

```java
package com.matburt.mobileorg.Gui.Help;

import android.content.Context;

import com.matburt.mobileorg.R;

public class HelpTopic {
    public final int titleRes;
    public final String fileName;

    public HelpTopic(int titleRes, String fileName) {
        this.titleRes = titleRes;
        this.fileName = fileName;
    }

    public static final HelpTopic[] TOPICS = {
            new HelpTopic(R.string.help_topic_quick_start, "quick-start.html"),
            new HelpTopic(R.string.help_topic_sync, "sync.html"),
            new HelpTopic(R.string.help_topic_outline, "outline.html"),
            new HelpTopic(R.string.help_topic_pomodoro, "pomodoro.html"),
            new HelpTopic(R.string.help_topic_statistics, "statistics.html"),
            new HelpTopic(R.string.help_topic_reminders, "reminders.html"),
            new HelpTopic(R.string.help_topic_extras, "extras.html"),
    };

    public static String getLangDir(Context context) {
        String lang = context.getResources().getConfiguration().locale.getLanguage();
        return "zh".equals(lang) ? "zh" : "en";
    }

    public static String getAssetPath(Context context, HelpTopic topic) {
        return "help/" + getLangDir(context) + "/" + topic.fileName;
    }
}
```

- [ ] **步骤 3：添加字符串资源**

`values/strings.xml` 追加（`values-zh/strings.xml` 同步中文）：

```xml
<string name="help_title">Help</string>
<string name="help_topic_quick_start">Quick Start</string>
<string name="help_topic_sync">Synchronization</string>
<string name="help_topic_outline">Outline Basics</string>
<string name="help_topic_pomodoro">Pomodoro &amp; Timeclock</string>
<string name="help_topic_statistics">Statistics</string>
<string name="help_topic_reminders">Reminders</string>
<string name="help_topic_extras">More Features</string>
<string name="help_about_version">Version: %1$s</string>
<string name="help_about_repo">Fork: https://github.com/lujun9972/mobileorg-android</string>
<string name="help_about_license">Licensed under Apache License 2.0</string>
<string name="help_detail_error">Help document failed to load.</string>
```

values-zh 对应中文：帮助 / 快速上手 / 同步配置 / Outline 基本操作 / 番茄钟与计时 / 统计 / 提醒系统 / 更多功能 / 版本：%1$s / Fork：…（仓库 URL 原样）/ 基于 Apache 2.0 协议发布 / 帮助文档加载失败。

- [ ] **步骤 4：创建共享样式 help.css**

`MobileOrg/src/main/assets/help/help.css`：

```css
:root { --bg:#fafafa; --fg:#212121; --muted:#666; --accent:#1a4d8f; --code-bg:#eceff1; }
html.dark { --bg:#101010; --fg:#cccccc; --muted:#888; --accent:#7aa7d4; --code-bg:#1e1e1e; }
body { background:var(--bg); color:var(--fg); font-family:sans-serif;
       line-height:1.6; padding:16px; margin:0; }
h1 { font-size:1.4em; border-bottom:2px solid var(--accent); padding-bottom:8px; }
h2 { font-size:1.15em; margin-top:1.4em; color:var(--accent); }
img { max-width:100%; height:auto; border:1px solid var(--muted); border-radius:4px; }
code, pre { background:var(--code-bg); border-radius:4px; }
code { padding:1px 4px; }
pre { padding:8px; overflow-x:auto; }
kbd { border:1px solid var(--muted); border-radius:4px; padding:0 6px; font-family:monospace; }
a { color:var(--accent); }
.warn { border-left:4px solid var(--accent); padding-left:10px; color:var(--muted); }
```

- [ ] **步骤 5：创建 14 篇 HTML 骨架**

每篇骨架相同结构（标题各异，正文由任务 2-5 填充）。zh 模板（`lang="zh"`，en 用 `lang="en"` + 英文标题）：

```html
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>快速上手</title>
<link rel="stylesheet" href="help.css">
</head>
<body>
<h1>快速上手</h1>
</body>
</html>
```

标题对照（title/h1 一致）：

| 文件 | zh | en |
|---|---|---|
| quick-start.html | 快速上手 | Quick Start |
| sync.html | 同步配置 | Synchronization |
| outline.html | Outline 基本操作 | Outline Basics |
| pomodoro.html | 番茄钟与计时 | Pomodoro &amp; Timeclock |
| statistics.html | 统计 | Statistics |
| reminders.html | 提醒系统 | Reminders |
| extras.html | 更多功能 | More Features |

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java \
  MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpTopicTest.java \
  MobileOrg/src/main/assets/help MobileOrg/src/main/res/values/strings.xml \
  MobileOrg/src/main/res/values-zh/strings.xml
git commit -m "feat(help): HelpTopic 数据层 + 双语 HTML 骨架 + 完整性测试"
```

---

### 任务 2：quick-start 双语内容（含截图引用）

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/assets/help/zh/quick-start.html`, `MobileOrg/src/main/assets/help/en/quick-start.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

**文件：**
- 修改：任务 1 创建的两份骨架（`<h1>` 后追加正文）

- [ ] **步骤 1：编写中文正文**

结构（4 节，图片路径相对 `help/` 目录——`loadDataWithBaseURL("file:///android_asset/help/", ...)` 的基准；图片在 `images/`，双语共用）：

1. `<h2>1. 配置同步</h2>` — 三种方式一句话（WebDAV / SSH / SDCard）；入口路径"设置 → Actions → Setup Wizard"；`<img src="images/sync-wizard.webp" alt="同步向导">`；详细步骤链接到 `<a href="sync.html">`
2. `<h2>2. 首次同步</h2>` — 主界面右上角同步图标点击；下拉同步后文件出现在列表；`<img src="images/outline-main.webp" alt="主界面">`
3. `<h2>3. 日常使用</h2>` — 点 + capture 新想法；`<img src="images/capture-editor.webp" alt="capture">`；长按节点弹出操作菜单（编辑/删除/番茄钟/分享）；`<img src="images/long-press-menu.webp" alt="长按菜单">`；勾选 checkbox；点节点查看内容
4. `<h2>接下来</h2>` — 其余 6 篇的相对链接清单

- [ ] **步骤 2：编写英文正文**

与中文同结构同图片，英文表述。面向陌生 MobileOrg 用户，术语与 app 英文 UI 一致（Capture、Sync、Outline）。

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/assets/help/zh/quick-start.html \
  MobileOrg/src/main/assets/help/en/quick-start.html
git commit -m "docs(help): 快速上手篇（双语，含 4 张截图引用）"
```

---

### 任务 3：sync 双语内容（吸收上游 wiki 同步知识）

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/assets/help/zh/sync.html`, `MobileOrg/src/main/assets/help/en/sync.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

- [ ] **步骤 1：编写中文正文**

结构（5 节，纯文字无图）：

1. `<h2>同步方式</h2>` — 三种方式对比表（WebDAV: URL+用户名+密码；SSH: 主机+端口+用户+密码/密钥+远程路径；SDCard: 本地目录路径），各自适合场景一句话
2. `<h2>配置向导</h2>` — "设置 → Actions → Setup Wizard"，逐方式说明向导各字段含义（URL 需含 index.org 所在目录等）
3. `<h2>同步的工作原理</h2>` — 拉取 index.org → 对比校验和下载变更文件 → 解析入本地库；本地编辑进入 edit 队列，下次同步回传服务器；从服务器删除的文件同步后本地也移除
4. `<h2>配置导入导出</h2>` — "设置 → Actions → Export/Import Sync Configuration"，系统文件选择器（SAF）选位置；重装后恢复配置的场景
5. `<h2>常见问题</h2>` — 自签名证书提示（CertificateConflict 屏幕）；同步失败看通知错误信息

- [ ] **步骤 2：编写英文正文**

同结构英文表述。

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/assets/help/zh/sync.html \
  MobileOrg/src/main/assets/help/en/sync.html
git commit -m "docs(help): 同步配置篇（双语，吸收上游 wiki 有效内容）"
```

---

### 任务 4：outline 双语内容

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/assets/help/zh/outline.html`, `MobileOrg/src/main/assets/help/en/outline.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

- [ ] **步骤 1：编写中文正文**

结构（6 节，纯文字）：

1. `<h2>主界面</h2>` — 列表顶部 TODO / Agenda 两个固定项（点开看任务汇总/日程）；文件列表；层级导航（点节点进入子树，返回键回上层）
2. `<h2>长按菜单</h2>` — 长按节点按类型弹出不同菜单：可编辑节点（编辑/添加子节点/删除/番茄钟/计时/分享等）；只读节点（查看/分享）；文件节点与 agenda 节点各有菜单
3. `<h2>记录想法（Capture）</h2>` — 主菜单 + 快速建条目，进编辑器
4. `<h2>编辑器</h2>` — Heading（标题/TODO 状态/优先级）、Dates（DEADLINE/SCHEDULED）、Tags、Payload（正文）分页；正文支持 org 语法（列表/checkbox/链接）
5. `<h2>Agenda 与搜索</h2>` — Agenda 项自定义查询（设置 agenda 规则）；搜索入口主菜单放大镜
6. `<h2>桌面小部件</h2>` — 桌面 widget 显示 outline，长按进入 capture widget 快速记录

- [ ] **步骤 2：编写英文正文** — 同结构。

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/assets/help/zh/outline.html \
  MobileOrg/src/main/assets/help/en/outline.html
git commit -m "docs(help): Outline 基本操作篇（双语）"
```

---

### 任务 5：pomodoro / statistics / reminders / extras 双语内容（fork 功能）

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/assets/help/zh/pomodoro.html`, `MobileOrg/src/main/assets/help/en/pomodoro.html`, `MobileOrg/src/main/assets/help/zh/statistics.html`, `MobileOrg/src/main/assets/help/en/statistics.html`, `MobileOrg/src/main/assets/help/zh/reminders.html`, `MobileOrg/src/main/assets/help/en/reminders.html`, `MobileOrg/src/main/assets/help/zh/extras.html`, `MobileOrg/src/main/assets/help/en/extras.html`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

- [ ] **步骤 1：pomodoro（双语）**

章节：主菜单 🍅 入口启动（选时长）；单次模式；连续模式（自动进入下一轮 + 休息阶段）；超时闹钟（静音模式也响铃；通知上"关闭闹铃"按钮只停闹钟不停会话）；timeclock 计时（clock in/out 记录到 LOGBOOK）；编辑时长（小时+分钟 NumberPicker，确定只是预览，保存才生效）。命令行不可用——全部描述 UI 操作。

- [ ] **步骤 2：statistics（双语）**

章节：入口（番茄钟菜单 → Statistics）；Overview（今日/本周番茄数与时长）；Trend 趋势图；点某天看日详情（各会话列表）。

- [ ] **步骤 3：reminders（双语）**

章节：自动扫描节点的 DEADLINE/SCHEDULED 日期并在到期前提醒；"设置 → Reminders"配置（开关、提前量、每日总览时间）；每日总览通知汇总当天到期事项；开机后提醒自动重新注册；Android 12+ 精确闹钟权限受限时提醒可能延迟的说明。

- [ ] **步骤 4：extras（双语）**

章节：Undo（主菜单撤销上一次同步前批次的本地修改，LIFO）；分享子树（长按菜单或查看页菜单 → 分享，导出子树文本）；主题（设置 → Theme：Dark/Light/Monochrome）。

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/assets/help/zh/pomodoro.html MobileOrg/src/main/assets/help/en/pomodoro.html \
  MobileOrg/src/main/assets/help/zh/statistics.html MobileOrg/src/main/assets/help/en/statistics.html \
  MobileOrg/src/main/assets/help/zh/reminders.html MobileOrg/src/main/assets/help/en/reminders.html \
  MobileOrg/src/main/assets/help/zh/extras.html MobileOrg/src/main/assets/help/en/extras.html
git commit -m "docs(help): fork 功能四篇（番茄钟/统计/提醒/更多，双语）"
```

---

### 任务 6：HelpActivity + HelpDetailActivity + 入口接线 + Activity 测试

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpActivity.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpDetailActivity.java`, `MobileOrg/src/main/res/layout/activity_help.xml`, `MobileOrg/src/main/res/layout/item_help_topic.xml`, `MobileOrg/src/main/res/layout/activity_help_detail.xml`, `MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpActivityTest.java`, `MobileOrg/src/main/AndroidManifest.xml`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`, `MobileOrg/src/main/res/xml/preferences.xml`
**导出/变更接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpDetailActivity.java::EXTRA_ASSET_PATH`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java::runHelp`
**消费接口：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java::TOPICS`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpTopic.java::getAssetPath`
**复杂度：** deep

- [ ] **步骤 1：编写失败的 Activity 测试**

`MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpActivityTest.java`：

```java
package com.matburt.mobileorg.test.Gui;

import android.content.Intent;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.matburt.mobileorg.Gui.Help.HelpActivity;
import com.matburt.mobileorg.Gui.Help.HelpDetailActivity;
import com.matburt.mobileorg.Gui.Help.HelpTopic;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class HelpActivityTest {

    @Rule
    public ActivityTestRule<HelpActivity> activityRule =
            new ActivityTestRule<>(HelpActivity.class);

    @Test
    public void listShowsAllTopics() {
        RecyclerView rv = activityRule.getActivity()
                .findViewById(R.id.help_recycler);
        assertEquals(HelpTopic.TOPICS.length, rv.getAdapter().getItemCount());
    }

    @Test
    public void aboutBlockShowsVersion() throws Exception {
        HelpActivity activity = activityRule.getActivity();
        TextView version = activity.findViewById(R.id.help_about_version);
        String versionName = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0).versionName;
        assertNotNull(versionName);
        assertEquals(activity.getString(R.string.help_about_version, versionName),
                version.getText().toString());
    }

    @Test
    public void clickTopicOpensDetail() throws Throwable {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                HelpDetailActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);

        RecyclerView rv = activityRule.getActivity().findViewById(R.id.help_recycler);
        instrumentation.runOnMainSync(() -> rv.getChildAt(0).performClick());

        android.app.Activity detail = monitor.waitForActivityWithTimeout(5000);
        assertNotNull(detail);
        String expectedPath = HelpTopic.getAssetPath(
                activityRule.getActivity(), HelpTopic.TOPICS[0]);
        assertEquals(expectedPath,
                detail.getIntent().getStringExtra(HelpDetailActivity.EXTRA_ASSET_PATH));
    }
}
```

（import 需补 `android.app.Instrumentation`。）

- [ ] **步骤 2：创建布局**

`activity_help.xml`（垂直 LinearLayout：RecyclerView 占满 + 底部关于块）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/help_recycler"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"/>
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">
        <TextView
            android:id="@+id/help_about_version"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="?android:attr/textColorSecondary"/>
        <TextView
            android:id="@+id/help_about_repo"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="?android:attr/textColorSecondary"/>
        <TextView
            android:id="@+id/help_about_license"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="?android:attr/textColorSecondary"/>
    </LinearLayout>
</LinearLayout>
```

`item_help_topic.xml`（单行标题，MaterialComponents 主题下必须显式两个维度）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/help_topic_title"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp"
    android:textSize="16sp"
    android:textColor="?android:attr/textColorPrimary"
    android:background="?android:attr/selectableItemBackground"
    android:clickable="true"/>
```

`activity_help_detail.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<WebView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/help_webview"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

- [ ] **步骤 3：实现 HelpActivity**

```java
package com.matburt.mobileorg.Gui.Help;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OrgUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        RecyclerView recycler = findViewById(R.id.help_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new HelpAdapter());

        TextView version = findViewById(R.id.help_about_version);
        TextView repo = findViewById(R.id.help_about_repo);
        TextView license = findViewById(R.id.help_about_license);
        repo.setText(R.string.help_about_repo);
        license.setText(R.string.help_about_license);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText(getString(R.string.help_about_version, info.versionName));
        } catch (Exception e) {
            version.setText(""); // setText(int) 不做 %1$s 格式化，不能直接用资源 id
        }
    }

    private class HelpAdapter extends RecyclerView.Adapter<HelpAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            ViewHolder(TextView itemView) {
                super(itemView);
                title = itemView;
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView view = (TextView) LayoutInflater.from(parent)
                    .inflate(R.layout.item_help_topic, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final HelpTopic topic = HelpTopic.TOPICS[position];
            holder.title.setText(topic.titleRes);
            holder.title.setOnClickListener(v ->
                    startActivity(new Intent(HelpActivity.this,
                            HelpDetailActivity.class)
                            .putExtra(HelpDetailActivity.EXTRA_ASSET_PATH,
                                    HelpTopic.getAssetPath(HelpActivity.this, topic))));
        }

        @Override
        public int getItemCount() {
            return HelpTopic.TOPICS.length;
        }
    }
}
```

- [ ] **步骤 4：实现 HelpDetailActivity**

```java
package com.matburt.mobileorg.Gui.Help;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.matburt.mobileorg.Gui.Theme.DefaultTheme;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;
import com.matburt.mobileorg.util.PreferenceUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HelpDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ASSET_PATH = "asset_path";

    private WebView webView;
    public boolean pageFinished; // 测试轮询用（测试在 test.Gui 包，须 public）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OrgUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_detail);

        webView = findViewById(R.id.help_webview);
        webView.setWebViewClient(new HelpWebViewClient());
        webView.getSettings().setBuiltInZoomControls(true);
        webView.setBackgroundColor(
                DefaultTheme.getTheme(this).defaultBackground);

        String assetPath = getIntent().getStringExtra(EXTRA_ASSET_PATH);
        if (assetPath == null) {
            displayError();
            return;
        }
        loadAsset(assetPath);
    }

    private void loadAsset(String assetPath) {
        try {
            String html = readAsset(assetPath);
            if (isDarkTheme())
                html = html.replace("<html", "<html class=\"dark\"");
            webView.loadDataWithBaseURL(
                    "file:///android_asset/help/", html, "text/html", "UTF-8", null);
        } catch (IOException e) {
            displayError();
        }
    }

    private String readAsset(String path) throws IOException {
        InputStream in = getAssets().open(path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        in.close();
        return sb.toString();
    }

    private boolean isDarkTheme() {
        return !"Light".equals(PreferenceUtils.getThemeName());
    }

    private void displayError() {
        webView.loadDataWithBaseURL(null,
                "<html><body style='background:#101010;color:#ccc'>"
                        + getString(R.string.help_detail_error)
                        + "</body></html>",
                "text/html", "UTF-8", null);
    }

    private class HelpWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("file://"))
                return false; // 相对资源（css/img）与篇间互链由 WebView 正常加载
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
            }
            return true; // 外链一律外部浏览器
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageFinished = true;
        }
    }
}
```

另加一个详情加载测试（同一文件 `HelpActivityTest.java` 追加，import 补 `android.os.SystemClock`、`static org.junit.Assert.assertTrue`、`android.content.Intent`）：

```java
@Rule
public ActivityTestRule<HelpDetailActivity> detailRule = new ActivityTestRule<>(
        HelpDetailActivity.class, true, false);

@Test
public void detailLoadsAssetHtml() throws Throwable {
    Intent intent = new Intent();
    intent.putExtra(HelpDetailActivity.EXTRA_ASSET_PATH, "help/en/quick-start.html");
    HelpDetailActivity detail = detailRule.launchActivity(intent);
    long deadline = System.currentTimeMillis() + 5000;
    while (!detail.pageFinished && System.currentTimeMillis() < deadline)
        SystemClock.sleep(100);
    assertTrue(detail.pageFinished);
}
```

- [ ] **步骤 5：注册 Activity + 接线入口**

`AndroidManifest.xml` `<application>` 内追加（紧跟其他 activity 声明格式）：

```xml
<activity android:name=".Gui.Help.HelpActivity" />
<activity android:name=".Gui.Help.HelpDetailActivity" />
```

`OutlineActivity.runHelp()`（约 433 行）整体替换为：

```java
public void runHelp(View view) {
    startActivity(new Intent(this, HelpActivity.class));
}
```

并添加 import `com.matburt.mobileorg.Gui.Help.HelpActivity`（删除不再使用的 `Uri.parse` 死链接相关 import 仅当无其他引用——先 grep `Uri` 确认）。

`preferences.xml` 的 `preference_actions` 类目内、Setup Wizard 项之后追加：

```xml
<Preference android:title="@string/help_title">
    <intent
        android:targetPackage="com.matburt.mobileorg"
        android:targetClass="com.matburt.mobileorg.Gui.Help.HelpActivity" />
</Preference>
```

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpActivity.java \
  MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Help/HelpDetailActivity.java \
  MobileOrg/src/main/res/layout/activity_help.xml \
  MobileOrg/src/main/res/layout/item_help_topic.xml \
  MobileOrg/src/main/res/layout/activity_help_detail.xml \
  MobileOrg/src/androidTest/java/com/matburt/mobileorg/test/Gui/HelpActivityTest.java \
  MobileOrg/src/main/AndroidManifest.xml \
  MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java \
  MobileOrg/src/main/res/xml/preferences.xml
git commit -m "feat(help): 应用内帮助中心页面与入口接线，替换死 wiki 链接"
```

---

### 任务 7：真机截图 4 张入 assets

**依赖：** 无
**文件集：** `MobileOrg/src/main/assets/help/images/sync-wizard.webp`, `MobileOrg/src/main/assets/help/images/outline-main.webp`, `MobileOrg/src/main/assets/help/images/capture-editor.webp`, `MobileOrg/src/main/assets/help/images/long-press-menu.webp`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

设备：MI PAD 4（Android 8.1），USB 已连（`adb devices` 确认 `ae0a6a51`）。源 APK：`release/MobileOrg-release.apk`（2.10.0，被截界面均为现有功能）。

- [ ] **步骤 1：危险操作前确认（阻塞点）**

`adb shell pm clear com.matburt.mobileorg` 会清空设备上 app 全部本地数据。执行前必须向用户确认设备上 MobileOrg 无未同步数据；用户未确认前跳到步骤 2 先拍无数据依赖的界面。若设备未装该 app 则直接装。

- [ ] **步骤 2：准备环境**

```bash
adb install -r release/MobileOrg-release.apk
adb shell pm clear com.matburt.mobileorg        # 确认后执行
adb shell settings put system system_locales en-US   # 切英文，失败则请用户在设置中手切
```

app 内操作（手动或 `adb shell input`）：设置 → Theme → Light；主界面点 + capture 3 条虚构条目（Buy milk / Read paper / Team meeting）。

- [ ] **步骤 3：依次截 4 张**

```bash
mkdir -p tmp/help-shots
# 1) 同步向导：设置 → Actions → Setup Wizard，停在 WebDAV 首屏
adb exec-out screencap -p > tmp/help-shots/sync-wizard.png
# 2) 主界面：回到 Outline 列表（同步按钮可见）
adb exec-out screencap -p > tmp/help-shots/outline-main.png
# 3) capture 编辑页：点 + 打开编辑器
adb exec-out screencap -p > tmp/help-shots/capture-editor.png
# 4) 长按菜单：长按任一节点展开 ActionMode
adb exec-out screencap -p > tmp/help-shots/long-press-menu.png
```

- [ ] **步骤 4：压缩入 assets**

```bash
mkdir -p MobileOrg/src/main/assets/help/images
for f in sync-wizard outline-main capture-editor long-press-menu; do
  cwebp -resize 1080 0 tmp/help-shots/$f.png \
    -o MobileOrg/src/main/assets/help/images/$f.webp
done
ls -la MobileOrg/src/main/assets/help/images/   # 逐张 <100KB，超了加 -q 75 重压
```

无 cwebp 则先 `sudo pacman -S libwebp`。

- [ ] **步骤 5：恢复设备（可选，问用户）**

设备语言/数据按用户意愿恢复；提醒截图后 app 内 demo 数据可随下次同步覆盖。

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/assets/help/images
git commit -m "docs(help): 快速上手 4 张截图（MI PAD 4 实拍，WebP 压缩）"
```

---

### 任务 8：推送 CI 验证与收尾

**依赖：** 任务 1, 任务 2, 任务 3, 任务 4, 任务 5, 任务 6, 任务 7
**文件集：** 无
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：本地静态自查**

`grep -rn "quick-start.html" MobileOrg/src/main/assets/help/zh/quick-start.html` 确认图片引用路径均为 `images/*.webp`（与任务 7 产物名一致）；`git status` 无计划外文件。

- [ ] **步骤 2：推送并等 CI**

```bash
git push
gh run list --limit 3        # 等待 test.yml 与 build.yml 完成
gh run watch <run-id>
```

预期：androidTest 全绿（含 `HelpTopicTest` 完整性、`HelpActivityTest` 三用例）、build 绿。失败则读日志修复后重复本步骤。

- [ ] **步骤 3：手测检查项（转交用户，CI 绿后）**

设备装新 APK：主菜单 Help → 列表 7 项 + 关于块版本号；点主题 → WebView 渲染（图/样式/篇间链接）；深色主题下重新进入无白闪；文内任意外链跳浏览器；设置页 Actions → Help 同样进入。

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 6 → 任务 8

- Wave 1（无依赖）：任务 1, 任务 7
- Wave 2（依赖 Wave 1）：任务 2（依赖 1）, 任务 3（依赖 1）, 任务 4（依赖 1）, 任务 5（依赖 1）, 任务 6（依赖 1）
- Wave 3（依赖 Wave 2）：任务 8（依赖 1-7）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
