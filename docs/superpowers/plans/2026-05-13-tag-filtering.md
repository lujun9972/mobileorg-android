# 标签过滤功能 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在大纲视图中添加基于标签的过滤功能，用户可以通过 Chip 选择标签来筛选节点。

**架构：** 水平 Chip 过滤条位于 ActionBar 和 ListView 之间，从 Tags 表加载标签，在内存中过滤节点。容器节点（有匹配后代的折叠父节点）以半透明显示。支持 AND/OR 切换。

**技术栈：** `HorizontalScrollView` + `ChipGroup` (Material Components), ContentProvider 查询标签。

**规格文件：** `docs/superpowers/specs/2026-05-13-tag-filtering-design.md`

**关键约束：**
- 过滤逻辑集中在 `OutlineTagFilter` 类中，adapter 只调用 `shouldShow(nodeId)` / `isContainer(nodeId)`
- `setFilter()` 必须在 `init()` 之前调用
- Chip 的 `OnCheckedChangeListener` 对编程式修改也会触发，需要 `programmaticChange` 守卫防止重入
- "All" chip 永远不能被取消选中——用户点它时是 no-op
- 后代扫描是全表扫描（`SELECT _id, parent_id, tags, tags_inherited FROM orgdata`），主线程同步执行
- Material Components 依赖和主题迁移是前置条件

---

## 文件结构

| 文件 | 职责 | 操作 |
|------|------|------|
| `MobileOrg/build.gradle` | 添加 Material 依赖 | 修改 |
| `MobileOrg/src/main/res/values/themes.xml` | 主题从 AppCompat 迁移到 MaterialComponents | 修改 |
| `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTagFilter.java` | 过滤状态 + 匹配逻辑 | 新建 |
| `MobileOrg/src/main/res/layout/tag_filter_bar.xml` | 过滤条布局（HorizontalScrollView + ChipGroup + ToggleButton） | 新建 |
| `MobileOrg/src/main/res/layout/outline.xml` | 添加 `<include>` 过滤条、空视图按钮容器 id、过滤空结果文本 | 修改 |
| `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineAdapter.java` | 添加 `setFilter()`、过滤 `init()`/`expand()`、容器节点 alpha | 修改 |
| `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java` | 过滤条初始化、chip 监听、状态保存/恢复、同步重载、跨级导航 | 修改 |

---

### 任务 1：添加 Material Components 依赖

**依赖：** 无
**文件集：** `MobileOrg/build.gradle`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：添加 Material 依赖**

在 `MobileOrg/build.gradle` 的 `dependencies` 块中，在 `implementation 'androidx.appcompat:appcompat:1.6.1'` 行后添加：

```groovy
implementation 'com.google.android.material:material:1.11.0'
```

- [ ] **步骤 2：验证构建**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/build.gradle
git commit -m "build: add Material Components dependency for tag filtering"
```

---

### 任务 2：迁移主题到 MaterialComponents

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/res/values/themes.xml`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：修改主题 parent**

`themes.xml` 当前内容：

```xml
<style name="Theme.MobileOrg.Dark" parent="Theme.AppCompat">
```

改为：

```xml
<style name="Theme.MobileOrg.Dark" parent="Theme.MaterialComponents">
```

```xml
<style name="Theme.MobileOrg.Light" parent="Theme.AppCompat.Light.DarkActionBar">
```

改为：

```xml
<style name="Theme.MobileOrg.Light" parent="Theme.MaterialComponents.Light.DarkActionBar">
```

ActionBar 的子样式 parent 也需要迁移：
- `Widget.Styled.ActionBar` 的 parent `Widget.AppCompat.ActionBar.Solid` → `Widget.MaterialComponents.ActionBar.Solid`
- `Widget.Styled.ActionBar.Light` 的 parent `Widget.AppCompat.Light.ActionBar.Solid.Inverse` → `Widget.MaterialComponents.Light.ActionBar.Solid.Inverse`

- [ ] **步骤 2：验证构建和基本 UI**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/res/values/themes.xml
git commit -m "chore: migrate theme from AppCompat to MaterialComponents"
```

---

### 任务 3：创建 OutlineTagFilter 类

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTagFilter.java`
**导出/变更接口：** `OutlineTagFilter.java::OutlineTagFilter`, `OutlineTagFilter.java::matchesTags`, `OutlineTagFilter.java::setSelectedTags`, `OutlineTagFilter.java::setAndMode`, `OutlineTagFilter.java::setTagSelected`, `OutlineTagFilter.java::clearAll`, `OutlineTagFilter.java::isActive`, `OutlineTagFilter.java::matches`, `OutlineTagFilter.java::isContainer`, `OutlineTagFilter.java::shouldShow`, `OutlineTagFilter.java::rebuild`, `OutlineTagFilter.java::getSelectedTagsArray`, `OutlineTagFilter.java::isAndMode`
**消费接口：** 无
**复杂度：** standard

- [ ] **步骤 1：创建 OutlineTagFilter.java**

在 `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/` 目录创建 `OutlineTagFilter.java`：

```java
package com.matburt.mobileorg.Gui.Outline;

import android.content.ContentResolver;
import android.database.Cursor;

import com.matburt.mobileorg.OrgData.OrgContract.OrgData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OutlineTagFilter {

    private Set<String> selectedTags = new HashSet<>();
    private boolean andMode = false;
    private Set<Long> matchingNodeIds = new HashSet<>();
    private Set<Long> containerIds = new HashSet<>();

    public OutlineTagFilter() {}

    public void setSelectedTags(String[] tags) {
        selectedTags.clear();
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isEmpty()) {
                    selectedTags.add(tag);
                }
            }
        }
    }

    public void setAndMode(boolean andMode) {
        this.andMode = andMode;
    }

    public void setTagSelected(String tag, boolean selected) {
        if (selected) {
            selectedTags.add(tag);
        } else {
            selectedTags.remove(tag);
        }
    }

    public void clearAll() {
        selectedTags.clear();
        matchingNodeIds.clear();
        containerIds.clear();
    }

    public boolean isActive() {
        return !selectedTags.isEmpty();
    }

    public boolean matches(long nodeId) {
        return matchingNodeIds.contains(nodeId);
    }

    public boolean isContainer(long nodeId) {
        return containerIds.contains(nodeId);
    }

    public boolean shouldShow(long nodeId) {
        return matches(nodeId) || isContainer(nodeId);
    }

    public void rebuild(ContentResolver resolver) {
        matchingNodeIds.clear();
        containerIds.clear();

        if (!isActive()) {
            return;
        }

        // Build parent map: childId -> parentId
        HashMap<Long, Long> parentMap = new HashMap<>();
        List<Long> allNodeIds = new ArrayList<>();

        Cursor cursor = resolver.query(
                OrgData.CONTENT_URI,
                new String[]{OrgData.ID, OrgData.PARENT_ID, OrgData.TAGS, OrgData.TAGS_INHERITED},
                null, null, null);

        if (cursor == null) {
            return;
        }

        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long parentId = cursor.getLong(1);
                String tags = cursor.getString(2);
                String tagsInherited = cursor.getString(3);

                allNodeIds.add(id);
                parentMap.put(id, parentId);

                if (matchesTags(tags, tagsInherited, selectedTags, andMode)) {
                    matchingNodeIds.add(id);
                }
            }
        } finally {
            cursor.close();
        }

        // Build containerIds: walk ancestor chains of matching nodes
        for (Long matchId : matchingNodeIds) {
            Long ancestorId = parentMap.get(matchId);
            while (ancestorId != null && ancestorId != -1 && !matchingNodeIds.contains(ancestorId)) {
                containerIds.add(ancestorId);
                ancestorId = parentMap.get(ancestorId);
            }
        }
    }

    public String[] getSelectedTagsArray() {
        return selectedTags.toArray(new String[0]);
    }

    public boolean isAndMode() {
        return andMode;
    }

    /** Pure function: testable without ContentResolver. */
    static boolean matchesTags(String tags, String tagsInherited,
                               Set<String> selectedTags, boolean andMode) {
        Set<String> nodeTags = new HashSet<>();
        if (tags != null) {
            for (String t : tags.split(":")) {
                if (!t.isEmpty()) nodeTags.add(t);
            }
        }
        if (tagsInherited != null) {
            for (String t : tagsInherited.split(":")) {
                if (!t.isEmpty()) nodeTags.add(t);
            }
        }
        if (andMode) {
            return nodeTags.containsAll(selectedTags);
        } else {
            for (String t : selectedTags) {
                if (nodeTags.contains(t)) return true;
            }
            return false;
        }
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew compileDebugJavaWithJavac`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineTagFilter.java
git commit -m "feat: add OutlineTagFilter with tag matching and descendant scan"
```

---

### 任务 4：创建 tag_filter_bar.xml 布局

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/res/layout/tag_filter_bar.xml`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：创建布局文件**

在 `MobileOrg/src/main/res/layout/` 目录创建 `tag_filter_bar.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/tag_filter_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="8dp"
    android:paddingEnd="4dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp"
    android:visibility="gone">

    <HorizontalScrollView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:scrollbars="none">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/tag_filter_chips"
            app:singleSelection="false"
            app:chipSpacingHorizontal="4dp" />

    </HorizontalScrollView>

    <ToggleButton
        android:id="@+id/tag_filter_mode"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:textOn="AND"
        android:textOff="OR"
        android:textSize="12sp"
        android:minWidth="48dp"
        android:minHeight="32dp" />

</LinearLayout>
```

- [ ] **步骤 2：验证构建**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add MobileOrg/src/main/res/layout/tag_filter_bar.xml
git commit -m "feat: add tag_filter_bar layout with ChipGroup and AND/OR toggle"
```

---

### 任务 5：修改 outline.xml 集成过滤条

**依赖：** 任务 4
**文件集：** `MobileOrg/src/main/res/layout/outline.xml`
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** standard

- [ ] **步骤 1：在 OutlineListView 上方添加过滤条 include**

在 `outline.xml` 中，在 `<com.matburt.mobileorg.Gui.Outline.OutlineListView` 之前插入过滤条：

```xml
<include layout="@layout/tag_filter_bar" />
```

**注意：** 录音栏是动态通过 `addView(recordingBar, 0)` 插入到 root layout 的 position 0 的，所以过滤条 include 在 XML 中在 ListView 之前即可——录音栏会在运行时被插入到最上面。

- [ ] **步骤 2：给空视图按钮容器添加 id**

在 `outline.xml` 中，找到空视图 `RelativeLayout` 内部的按钮容器 `LinearLayout`（包含四个按钮的那个，位于 `layout_centerInParent="true"`），给它添加 `android:id="@+id/outline_list_empty_buttons"`。

该 `LinearLayout` 当前没有 id。找到 `<LinearLayout` 标签（紧接在 logo ImageView 之后），添加 id 属性：

```xml
<LinearLayout
    android:id="@+id/outline_list_empty_buttons"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_centerInParent="true"
    ...>
```

- [ ] **步骤 3：在空视图中添加过滤空结果 TextView**

在 `outline_list_empty` RelativeLayout 中，在 `outline_list_empty_buttons` LinearLayout 之后添加：

```xml
<TextView
    android:id="@+id/outline_list_filter_empty"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_centerInParent="true"
    android:text="No matching nodes"
    android:textSize="18sp"
    android:visibility="gone" />
```

- [ ] **步骤 4：验证构建**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/main/res/layout/outline.xml
git commit -m "feat: integrate tag filter bar and filter-empty view into outline layout"
```

---

### 任务 6：修改 OutlineAdapter 支持过滤

**依赖：** 任务 3
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineAdapter.java`
**导出/变更接口：** `OutlineAdapter.java::setFilter`, `OutlineAdapter.java::refresh`
**消费接口：** `OutlineTagFilter.java::shouldShow`, `OutlineTagFilter.java::isContainer`, `OutlineTagFilter.java::matches`, `OutlineTagFilter.java::isActive`
**复杂度：** standard

- [ ] **步骤 1：添加 filter 字段和 setFilter 方法**

在 `OutlineAdapter` 类中，在 `private boolean levelIndentation = true;` 之后添加：

```java
private OutlineTagFilter filter = null;

public void setFilter(OutlineTagFilter filter) {
    this.filter = filter;
}
```

- [ ] **步骤 2：修改 init() 支持过滤**

将 `init()` 方法改为：

```java
public void init() {
    clear();

    for (OrgNode node : OrgProviderUtils.getOrgNodeChildren(-1, resolver)) {
        if (filter == null || !filter.isActive() || filter.shouldShow(node.id)) {
            add(node);
        }
    }

    MobileOrgApplication.log("OutlineAdapter.init() count=" + getCount());
    notifyDataSetInvalidated();
}
```

- [ ] **步骤 3：修改 expand() 支持过滤**

将 `expand()` 方法改为：

```java
public void expand(int position) {
    OrgNode node = getItem(position);
    ArrayList<OrgNode> children = node.getChildren(resolver);
    ArrayList<OrgNode> filteredChildren = new ArrayList<>();
    for (OrgNode child : children) {
        if (filter == null || !filter.isActive() || filter.shouldShow(child.id)) {
            filteredChildren.add(child);
        }
    }
    insertAll(filteredChildren, position + 1);
    this.expanded.set(position, true);
}
```

- [ ] **步骤 4：修改 getView() 添加容器节点 alpha**

将 `getView()` 方法改为：

```java
@Override
public View getView(int position, View convertView, ViewGroup parent) {
    OutlineItem outlineItem = (OutlineItem) convertView;
    if (convertView == null)
        outlineItem = new OutlineItem(getContext());

    outlineItem.setLevelFormating(levelIndentation);
    outlineItem.setup(getItem(position), this.expanded.get(position), theme, resolver);

    if (filter != null && filter.isActive() && filter.isContainer(getItemId(position)) && !filter.matches(getItemId(position))) {
        outlineItem.setAlpha(0.5f);
    } else {
        outlineItem.setAlpha(1.0f);
    }

    return outlineItem;
}
```

**注意：** `getItemId(position)` 返回 `OrgNode.id`（long），而 `matches(long)` / `isContainer(long)` 接受 long，类型一致。

- [ ] **步骤 5：验证编译**

运行：`./gradlew compileDebugJavaWithJavac`
预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineAdapter.java
git commit -m "feat: add filter support to OutlineAdapter for init/expand/getView"
```

---

### 任务 7：修改 OutlineActivity 集成过滤条

**依赖：** 任务 3, 任务 5, 任务 6
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java`
**导出/变更接口：** 无
**消费接口：** `OutlineTagFilter.java::OutlineTagFilter`, `OutlineTagFilter.java::setSelectedTags`, `OutlineTagFilter.java::setAndMode`, `OutlineTagFilter.java::isActive`, `OutlineTagFilter.java::rebuild`, `OutlineTagFilter.java::clearAll`, `OutlineTagFilter.java::setTagSelected`, `OutlineTagFilter.java::getSelectedTagsArray`, `OutlineTagFilter.java::isAndMode`, `OutlineTagFilter.java::shouldShow`, `OutlineAdapter.java::setFilter`
**复杂度：** deep

这是最核心的任务，涉及多个集成点。代码变更集中在一个文件中。

- [ ] **步骤 1：添加字段**

在 `OutlineActivity` 类中，在 `private long pendingRecordNodeId = -1;` 之后添加：

```java
private OutlineTagFilter tagFilter = new OutlineTagFilter();
private boolean programmaticChipChange = false;
private static final String STATE_FILTER_TAGS = "filter_tags";
private static final String STATE_FILTER_AND_MODE = "filter_and_mode";
```

- [ ] **步骤 2：添加 import**

在文件顶部 import 区域添加：

```java
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.widget.ToggleButton;
import java.util.ArrayList;
import java.util.Set;
```

- [ ] **步骤 3：修改 onCreate() 恢复过滤状态**

在 `onCreate()` 中，`setupList()` 调用之前（即 `MobileOrgApplication.log("OutlineActivity.onCreate() displayNewUserDialogs done");` 之后），插入：

```java
// Restore filter state
if (savedInstanceState != null) {
    String[] savedTags = savedInstanceState.getStringArray(STATE_FILTER_TAGS);
    boolean savedAndMode = savedInstanceState.getBoolean(STATE_FILTER_AND_MODE, false);
    if (savedTags != null && savedTags.length > 0) {
        tagFilter.setSelectedTags(savedTags);
        tagFilter.setAndMode(savedAndMode);
        tagFilter.rebuild(getContentResolver());
    }
} else {
    Intent intent = getIntent();
    String[] intentTags = intent.getStringArrayExtra("filter_tags");
    boolean intentAndMode = intent.getBooleanExtra("filter_and_mode", false);
    if (intentTags != null && intentTags.length > 0) {
        tagFilter.setSelectedTags(intentTags);
        tagFilter.setAndMode(intentAndMode);
        tagFilter.rebuild(getContentResolver());
    }
}
```

同时修改 `onCreate()` 方法签名——当前是 `protected void onCreate(Bundle savedInstanceState)`，已经是正确的。但 `onCreate` 中没有调用 `super.onCreate()` 中的 savedInstanceState 恢复逻辑。需要确认 `savedInstanceState` 参数在 `onCreate` 中可用——当前代码确实有 `savedInstanceState` 参数但未使用它。上面的代码会正确使用它。

- [ ] **步骤 4：修改 setupList() 传入 filter**

将 `setupList()` 改为：

```java
private void setupList() {
    listView = (OutlineListView) findViewById(R.id.outline_list);
    listView.setActivity(this);
    listView.setEmptyView(findViewById(R.id.outline_list_empty));
    OutlineAdapter adapter = (OutlineAdapter) listView.getAdapter();
    adapter.setFilter(tagFilter);
}
```

**注意：** `OutlineListView` 构造函数中已经 `setAdapter(new OutlineAdapter(context))`，所以这里可以通过 `getAdapter()` 获取。但 `getAdapter()` 返回 `ListAdapter`，需要强转为 `OutlineAdapter`。由于 `OutlineAdapter` 已经有 filter 字段默认为 null，这里的 `setFilter` 会在 adapter 已有数据之后调用。这没关系，因为 `refreshDisplay()` 会在 `onResume` 后重新加载数据。

- [ ] **步骤 5：添加 onResume 中的过滤条初始化**

在 `onResume()` 中，`refreshTitle()` 之后添加：

```java
setupFilterBar();
```

然后添加 `setupFilterBar()` 方法：

```java
private void setupFilterBar() {
    ArrayList<String> tags = OrgProviderUtils.getTags(getContentResolver());
    View filterBar = findViewById(R.id.tag_filter_bar);
    
    if (tags.isEmpty()) {
        filterBar.setVisibility(View.GONE);
        return;
    }
    
    ChipGroup chipGroup = findViewById(R.id.tag_filter_chips);
    ToggleButton modeToggle = findViewById(R.id.tag_filter_mode);
    
    programmaticChipChange = true;
    chipGroup.removeAllViews();
    
    // Create "All" chip
    Chip allChip = new Chip(this);
    allChip.setId(android.R.id.text1);  // stable ID for "All"
    allChip.setText("All");
    allChip.setCheckable(true);
    allChip.setChecked(!tagFilter.isActive());
    allChip.setChipBackgroundColorResource(android.R.color.white);
    chipGroup.addView(allChip);
    
    // Create tag chips
    for (String tag : tags) {
        Chip chip = new Chip(this);
        chip.setText(tag);
        chip.setCheckable(true);
        chip.setChecked(tagFilter.isActive() && containsTag(tagFilter.getSelectedTagsArray(), tag));
        chip.setChipBackgroundColorResource(android.R.color.white);
        chip.setTag(tag);  // store tag name for listener lookup
        chipGroup.addView(chip);
    }
    
    // Set AND/OR toggle state
    modeToggle.setChecked(tagFilter.isAndMode());
    
    programmaticChipChange = false;
    
    // Set listeners (after programmatic setup)
    chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
        if (programmaticChipChange) return;
        handleChipChange(checkedIds);
    });
    
    modeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
        if (programmaticChipChange) return;
        tagFilter.setAndMode(isChecked);
        applyFilter();
    });
    
    filterBar.setVisibility(View.VISIBLE);
    updateEmptyView();
}

private boolean containsTag(String[] tags, String tag) {
    for (String t : tags) {
        if (t.equals(tag)) return true;
    }
    return false;
}
```

**注意：** `ChipGroup.setOnCheckedStateChangeListener` 是 Material 1.11.0 中可用的 API（替代了已废弃的 `setOnCheckedChangeListener`）。如果编译不通过，改用 `setOnCheckedChangeListener`（接受 `ChipGroup, int` checkedId 的单参数版本）。

- [ ] **步骤 6：添加 handleChipChange() 方法**

```java
private void handleChipChange(java.util.List<Integer> checkedIds) {
    ChipGroup chipGroup = findViewById(R.id.tag_filter_chips);
    Chip allChip = findViewById(android.R.id.text1);
    
    boolean allChecked = checkedIds.contains(android.R.id.text1);
    
    programmaticChipChange = true;
    
    if (allChecked) {
        // "All" was checked — clear filter, uncheck all tag chips
        tagFilter.clearAll();
        for (int i = 1; i < chipGroup.getChildCount(); i++) {
            ((Chip) chipGroup.getChildAt(i)).setChecked(false);
        }
    } else {
        // A tag chip changed
        if (allChip.isChecked()) {
            allChip.setChecked(false);
        }
        
        // Rebuild selected tags from checked chips
        tagFilter.clearAll();
        for (int i = 1; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            if (chip.isChecked()) {
                tagFilter.setTagSelected((String) chip.getTag(), true);
            }
        }
        
        // If no tag chips are checked, auto-check "All"
        if (!tagFilter.isActive()) {
            allChip.setChecked(true);
        }
    }
    
    programmaticChipChange = false;
    applyFilter();
}
```

- [ ] **步骤 7：添加 applyFilter() 和 updateEmptyView() 方法**

```java
private void applyFilter() {
    tagFilter.rebuild(getContentResolver());
    OutlineAdapter adapter = (OutlineAdapter) listView.getAdapter();
    adapter.setFilter(tagFilter);
    adapter.refresh();
    updateEmptyView();
}

private void updateEmptyView() {
    View emptyButtons = findViewById(R.id.outline_list_empty_buttons);
    TextView filterEmpty = findViewById(R.id.outline_list_filter_empty);
    
    if (tagFilter.isActive() && listView.getAdapter().getCount() == 0) {
        if (emptyButtons != null) emptyButtons.setVisibility(View.GONE);
        if (filterEmpty != null) filterEmpty.setVisibility(View.VISIBLE);
    } else {
        if (emptyButtons != null) emptyButtons.setVisibility(View.VISIBLE);
        if (filterEmpty != null) filterEmpty.setVisibility(View.GONE);
    }
}
```

- [ ] **步骤 8：添加 onSaveInstanceState**

在 `onDestroy()` 之前添加：

```java
@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (tagFilter.isActive()) {
        outState.putStringArray(STATE_FILTER_TAGS, tagFilter.getSelectedTagsArray());
        outState.putBoolean(STATE_FILTER_AND_MODE, tagFilter.isAndMode());
    }
}
```

- [ ] **步骤 9：修改 SynchServiceReceiver 支持过滤条重载**

在 `SynchServiceReceiver.onReceive()` 中，`syncDone` 分支内，在 `refreshDisplay()` 之后添加过滤条重载：

将：
```java
} else if (syncDone) {
    android.view.View actionView = synchronizerMenuItem.getActionView();
    if (actionView != null) {
        actionView.clearAnimation();
    }
    synchronizerMenuItem.setActionView(null);
    refreshDisplay();
    ...
```

改为：
```java
} else if (syncDone) {
    android.view.View actionView = synchronizerMenuItem.getActionView();
    if (actionView != null) {
        actionView.clearAnimation();
    }
    synchronizerMenuItem.setActionView(null);
    refreshDisplay();
    setupFilterBar();
    ...
```

- [ ] **步骤 10：修改跨级导航传递过滤状态**

将 `runExpandableOutline()` 改为：

```java
private void runExpandableOutline(long id) {
    Intent intent = new Intent(this, OutlineActivity.class);
    intent.putExtra(OutlineActivity.NODE_ID, id);
    if (tagFilter.isActive()) {
        intent.putExtra("filter_tags", tagFilter.getSelectedTagsArray());
        intent.putExtra("filter_and_mode", tagFilter.isAndMode());
    }
    startActivity(intent);
}
```

- [ ] **步骤 11：验证编译**

运行：`./gradlew compileDebugJavaWithJavac`
预期：BUILD SUCCESSFUL

- [ ] **步骤 12：Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Outline/OutlineActivity.java
git commit -m "feat: integrate tag filtering into OutlineActivity with chip bar and state management"
```

---

### 任务 8：编写 matchesTags 单元测试

**依赖：** 任务 3
**文件集：** `MobileOrg/src/test/java/com/matburt/mobileorg/Gui/Outline/OutlineTagFilterTest.java`
**导出/变更接口：** 无
**消费接口：** `OutlineTagFilter.java::matchesTags`
**复杂度：** standard

- [ ] **步骤 1：确认 test source set 存在**

运行：`ls MobileOrg/src/test/java/com/matburt/mobileorg/ 2>/dev/null || echo "NOT_FOUND"`
如果不存在，创建目录结构：`mkdir -p MobileOrg/src/test/java/com/matburt/mobileorg/Gui/Outline/`

- [ ] **步骤 2：在 build.gradle 中确认有 JUnit test 依赖**

确认 `testImplementation 'junit:junit:4.12'` 存在于 `MobileOrg/build.gradle`。已确认存在。

- [ ] **步骤 3：创建测试类**

在 `MobileOrg/src/test/java/com/matburt/mobileorg/Gui/Outline/OutlineTagFilterTest.java` 中：

```java
package com.matburt.mobileorg.Gui.Outline;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class OutlineTagFilterTest {

    private Set<String> tags(String... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }

    // === matchesTags pure function tests ===

    @Test
    public void orMode_matchesAny() {
        assertTrue(OutlineTagFilter.matchesTags("work:urgent", null, tags("work"), false));
    }

    @Test
    public void orMode_noMatch() {
        assertFalse(OutlineTagFilter.matchesTags("home:personal", null, tags("work"), false));
    }

    @Test
    public void orMode_inheritedTagsMatch() {
        assertTrue(OutlineTagFilter.matchesTags(null, "project:work", tags("work"), false));
    }

    @Test
    public void andMode_matchesAll() {
        assertTrue(OutlineTagFilter.matchesTags("work:urgent", null, tags("work", "urgent"), true));
    }

    @Test
    public void andMode_missingOne() {
        assertFalse(OutlineTagFilter.matchesTags("work:home", null, tags("work", "urgent"), true));
    }

    @Test
    public void nullTags() {
        assertFalse(OutlineTagFilter.matchesTags(null, null, tags("work"), false));
    }

    @Test
    public void emptyTags() {
        assertFalse(OutlineTagFilter.matchesTags("", "", tags("work"), false));
    }

    @Test
    public void mixedOwnAndInherited() {
        assertTrue(OutlineTagFilter.matchesTags("home", "work", tags("work"), false));
    }

    @Test
    public void emptySelectedTags() {
        assertFalse(OutlineTagFilter.matchesTags("work", null, new HashSet<String>(), false));
    }

    // === isActive / clearAll tests ===

    @Test
    public void isActive_falseWhenEmpty() {
        OutlineTagFilter filter = new OutlineTagFilter();
        assertFalse(filter.isActive());
    }

    @Test
    public void isActive_trueAfterSelection() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        assertTrue(filter.isActive());
    }

    @Test
    public void clearAll_deactivates() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setTagSelected("work", true);
        filter.clearAll();
        assertFalse(filter.isActive());
    }

    @Test
    public void setSelectedTags_roundTrip() {
        OutlineTagFilter filter = new OutlineTagFilter();
        filter.setSelectedTags(new String[]{"work", "urgent"});
        String[] result = filter.getSelectedTagsArray();
        assertEquals(2, result.length);
        assertTrue(Arrays.asList(result).contains("work"));
        assertTrue(Arrays.asList(result).contains("urgent"));
    }
}
```

- [ ] **步骤 4：运行测试**

运行：`./gradlew test --tests "com.matburt.mobileorg.Gui.Outline.OutlineTagFilterTest"`
预期：所有测试通过

- [ ] **步骤 5：Commit**

```bash
git add MobileOrg/src/test/java/com/matburt/mobileorg/Gui/Outline/OutlineTagFilterTest.java
git commit -m "test: add unit tests for OutlineTagFilter.matchesTags and state management"
```

---

### 任务 9：构建验证和最终集成测试

**依赖：** 任务 1, 任务 2, 任务 3, 任务 4, 任务 5, 任务 6, 任务 7, 任务 8
**文件集：** 无
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：完整构建**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：运行所有单元测试**

运行：`./gradlew test`
预期：所有测试通过

- [ ] **步骤 3：运行所有 instrumentation 测试（如果设备可用）**

运行：`./gradlew connectedDebugAndroidTest`
预期：所有测试通过

- [ ] **步骤 4：推送并等待 CI 验证**

```bash
git push
```

然后通过 `gh run list --limit 1` 检查 CI 状态。

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 4 → 任务 5 → 任务 6 → 任务 7 → 任务 9

- Wave 1（无依赖）：任务 1, 任务 3, 任务 8
- Wave 2（依赖 Wave 1）：任务 2（依赖 1）, 任务 4（依赖 1）
- Wave 3（依赖 Wave 2）：任务 5（依赖 4）, 任务 6（依赖 3）
- Wave 4（依赖 Wave 3）：任务 7（依赖 3, 5, 6）
- Wave 5（依赖 Wave 4）：任务 9（依赖 1, 2, 3, 4, 5, 6, 7, 8）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
