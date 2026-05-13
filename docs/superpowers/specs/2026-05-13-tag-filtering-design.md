# Tag Filtering Feature Design

**Goal**: Add tag-based filtering to the outline view so users can quickly find nodes by tags.

**Architecture**: Horizontal Chip filter bar above the outline list, loaded from Tags table, filtering nodes in-memory. AND/OR toggle for combining selected tags. Descendant-aware: collapsed parents with matching children are shown dimmed so matches are never hidden.

**Tech Stack**: `HorizontalScrollView` + `ChipGroup` (Material), ContentProvider query for tags.

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Filter scope | All levels (file list + subtrees) | Consistent filtering experience |
| Filter trigger | Immediate on chip click / toggle switch | No "apply" button needed; chip clicks are low-frequency |
| Collapsed parent with matching descendants | Show parent node (dimmed, alpha=0.5) | User can discover matches by expanding, without forcing auto-expand |
| Click dimmed container node | Normal expand, showing filtered children | Dimmed is visual hint only, interaction unchanged |
| Expand in filtered view | Only show matching children + container nodes | Consistent filtered view; clear filter to see all |
| AND mode tag source | Merge `tags` + `tags_inherited` into one set, then check AND | User thinks of "tags this node has" as one combined set |
| Empty tags | Hide filter bar | No visual noise when irrelevant |
| Tag source | Tags table (global list) | Consistent across navigation; discoverable even when current view has none |
| Clear filter | "All" chip clears all selections; last chip unchecked auto-selects "All" | One-tap reset; chip bar always has one selected item |
| Cross-level state | Preserve filter state across navigation (Intent extras) | Don't lose user's filter when entering/leaving subtrees |
| Data load order | `setFilter()` before `init()` | Adapter loads filtered data from the start, no re-filter |
| Filter on sync | Reload tags, keep existing selections for still-existing tags, drop stale ones | Tags table may change after sync |
| Return (back) | Filter preserved by parent Activity in memory | Natural Activity back-stack behavior |
| Descendant scan scope | Full table scan (all files) | Simpler code; <5000 nodes completes in ms; negligible overhead for unused file_ids |
| Descendant scan thread | Main thread (synchronous) | Fast enough for typical org files; async adds concurrency complexity |
| containerIds ownership | `OutlineTagFilter` — `matches(nodeId)`, `isContainer(nodeId)`, `shouldShow(nodeId)` | Filter is the single source of truth; testable without adapter |

## UI Layout

The filter bar sits between the ActionBar and the ListView in `OutlineActivity`:

```
┌──────────────────────────────┐
│  ActionBar (MobileOrg [...]) │
├──────────────────────────────┤
│ [All] [work] [urgent] [home] │  ← Chip filter bar (horizontal scroll)
│                        [OR▼] │  ← AND/OR toggle button
├──────────────────────────────┤
│  Outline ListView            │
│  ...                         │
└──────────────────────────────┘
```

- **Container**: `HorizontalScrollView` wrapping a `ChipGroup`. Material's `ChipGroup` handles selection state, single/multi-select, and chip styling. `HorizontalScrollView` handles overflow when tags exceed screen width.
- **"All" chip**: Always first in the group. When selected, clears all other selections (no filter). When the last tag chip is unchecked, "All" auto-selects, ensuring exactly one item is always checked.
- **Tag chips**: One per unique tag from the `Tags` table. Checked = active filter.
- **AND/OR toggle**: A small `ToggleButton` at the right end of the bar. Displays "AND" / "OR". Defaults to "OR". Switches take effect immediately.
- **Visibility**: Determined in `onResume()`. Shown when Tags table has entries; `View.GONE` otherwise.
- **Integration**: `<include layout="@layout/tag_filter_bar" />` in `outline.xml`, placed above `OutlineListView`.

## OutlineTagFilter API

```java
public class OutlineTagFilter {
    private Set<String> selectedTags;   // empty = no filter active
    private boolean andMode;            // false = OR, true = AND
    private Set<Long> matchingNodeIds;  // nodes whose own tags match
    private Set<Long> containerIds;     // nodes with matching descendants (ancestors)

    /** No-arg constructor. Filter is inactive until tags are selected and rebuild() is called. */
    public OutlineTagFilter();

    /** Replace entire selection with the given tags (used to restore from Intent extras). Call rebuild() afterward. */
    public void setSelectedTags(String[] tags);
    public void setAndMode(boolean andMode);

    /** Add/remove a single tag from selection. Caller must call rebuild() afterward. */
    public void setTagSelected(String tag, boolean selected);

    /** Deselect all tags. isActive() becomes false. Does NOT call rebuild(). */
    public void clearAll();

    /** True if any tag is selected. */
    public boolean isActive();

    /** Cached lookup: node's tags matched during last rebuild(). */
    public boolean matches(long nodeId);

    /** Cached lookup: node is an ancestor of a matching node. */
    public boolean isContainer(long nodeId);

    /** Combined: show this node in filtered view? */
    public boolean shouldShow(long nodeId);

    /** Rebuild internal matchingNodeIds and containerIds sets. Called on filter change or sync. */
    public void rebuild(ContentResolver resolver);

    /** Get selected tags as array (for Intent extras). */
    public String[] getSelectedTagsArray();

    /** Current AND/OR mode. */
    public boolean isAndMode();
}
```

- No-arg constructor: `selectedTags` empty, `isActive()` = false. `rebuild()` is a no-op when `!isActive()`.
- `setSelectedTags(String[])` + `setAndMode()` support restoring filter from Intent extras for cross-level navigation.
- `matches()` and `isContainer()` are `Set<Long>` lookups against cached results from `rebuild()`. No tag parsing at query time.
- `rebuild()` executes `SELECT _id, parent_id, tags, tags_inherited FROM orgdata`, applies `matchesTags()` (the pure function listed below) to each row to build `matchingNodeIds`, builds a parent map, then walks ancestor chains to compute `containerIds`.
- All operations are on the main thread. Typical org files (<5000 nodes) complete in a few ms.

## Data Flow

### 1. Initialization (Activity)

`OutlineActivity.onCreate()`:
1. `setContentView(R.layout.outline)` — filter bar included via `<include>`, initially `View.GONE`
2. Read `node_id` from Intent
3. If Intent contains filter extras (selectedTags, andMode), restore `OutlineTagFilter` via `setSelectedTags()` + `setAndMode()`
4. If `onSaveInstanceState` has saved filter state, restore it (taking precedence over Intent extras — the saved state is more recent)
5. If filter `isActive()`: call `filter.rebuild(resolver)` to populate `matchingNodeIds` / `containerIds`
6. `setupList()` — create adapter, call `adapter.setFilter(filter)` **before** `adapter.init()`

`OutlineActivity.onResume()`:
1. Query `Tags` table via `OrgProviderUtils.getTags()`
2. If tags exist: populate `ChipGroup`, apply existing `OutlineTagFilter` selections to chips, set bar visible
3. If tags empty: hide bar

### 2. Chip interaction (Activity → Adapter)

```
User taps chip
  → ChipGroup.OnCheckedChangeListener
  → (guard against re-entrancy: skip if programmatic change in progress)
    → if "All" chip checked:
        → filter.clearAll()
        → uncheck all tag chips programmatically
    → else if tag chip checked:
        → filter.setTagSelected(tag, true)
        → uncheck "All" chip programmatically
    → else if tag chip unchecked:
        → filter.setTagSelected(tag, false)
        → if no tag chips remain checked: check "All" chip programmatically
    → filter.rebuild(resolver)  // re-scan descendant matches
    → adapter.setFilter(filter) // update adapter reference
    → adapter.refresh()         // re-init with filter applied
```

The guard flag prevents infinite recursion: `OnCheckedChangeListener` fires for programmatic `setChecked()` calls too. Use a boolean `programmaticChange` — set it true before modifying chips, set it false after, and skip the listener body when true.

Toggle switch: same flow — call `filter.setAndMode(checked)`, `filter.rebuild()`, `adapter.refresh()`.

### 3. Adapter filtering (init / expand)

Both `init()` and `expand(position)` follow the same pattern:

```java
ArrayList<OrgNode> children = OrgProviderUtils.getOrgNodeChildren(parentId, resolver);
for (OrgNode node : children) {
    if (filter == null || !filter.isActive() || filter.shouldShow(node.id)) {
        add(node);
        expanded.add(false);
    }
}
```

- When no filter is active (`filter == null` or `!filter.isActive()`), all nodes are added (existing behavior).
- `shouldShow(nodeId)` returns true if `matches(nodeId)` OR `isContainer(nodeId)`.

### 4. Visual distinction

In `OutlineAdapter.getView()` (the adapter holds the filter reference):

```java
// In getView(), after binding the item:
if (filter != null && filter.isActive() && filter.isContainer(node.id) && !filter.matches(node.id)) {
    itemView.setAlpha(0.5f);  // container node — dimmed
} else {
    itemView.setAlpha(1.0f);  // direct match or no filter
}
```

Entire row at 50% alpha gives a clear "this is a container, not a direct match" signal. This logic lives in the adapter because the adapter owns the filter reference; `OutlineItem` does not.

### 5. Expand in filtered view

When user expands a node (container or direct match), `expand()` loads children, filters each through `filter.shouldShow()`. Only matching children + sub-containers are added. Non-matching children are omitted.

### 6. Refresh and expand state preservation

`OutlineAdapter.refresh()` saves expanded node IDs, calls `init()` (which reloads with filter), then re-expands saved IDs via `expandNodes()`. If a saved ID no longer exists in the adapter (filter changed, node no longer matches and is not a container), it is silently skipped — no error, no log. This is normal behavior.

Clearing the filter (clicking "All") triggers `refresh()` — previously expanded nodes remain expanded because their IDs are in the new unfiltered dataset.

### 7. Empty filter result

`outline_list_empty` is a `RelativeLayout` containing a logo and four action buttons (Setup Wizard, Settings, Synchronize, Website). For the filter-empty state, add a simple `TextView` inside this RelativeLayout:

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

When `filter.isActive()` and `adapter.getCount() == 0`: hide the button container, show the filter-empty TextView. When filter is cleared or inactive: show the button container, hide the filter-empty TextView. When the adapter has items: the empty view is not shown at all (ListView's standard empty-view behavior).

### 8. Sync Integration

`OutlineActivity`'s `SynchServiceReceiver` (already registered) handles sync-complete broadcasts:

1. Reload tags from Tags table
2. Re-populate ChipGroup with new tag list
3. For each previously selected tag: if it exists in new tags, keep it checked; if not, remove from `OutlineTagFilter`
4. If all selected tags were removed: auto-select "All" chip, clear filter
5. Call `filter.rebuild(resolver)` + `adapter.refresh()`

### 9. Cross-level navigation

When navigating into a file (new `OutlineActivity` with different `node_id`):

```java
Intent intent = new Intent(this, OutlineActivity.class);
intent.putExtra("node_id", node.id);
intent.putExtra("filter_tags", filter.getSelectedTagsArray());
intent.putExtra("filter_and_mode", filter.isAndMode());
startActivity(intent);
```

New Activity reads extras in `onCreate()`, builds `OutlineTagFilter`, calls `adapter.setFilter(filter)` before `adapter.init()`.

Each `OutlineActivity` instance independently runs descendant scans — no sharing of `containerIds` between Activities (simpler, and scans are cheap).

When user presses back: previous Activity is still in memory with its filter state intact — no extra handling needed.

## State Management

- `OutlineTagFilter` instance stored in `OutlineActivity` field, passed to adapter via `setFilter()`
- Selected tags (`Set<String>`) and AND/OR mode (`boolean`) saved in `onSaveInstanceState` / restored in `onCreate`
- On `onResume`, chips are populated from Tags table. Existing `OutlineTagFilter` selections are re-applied to the chip bar
- Cross-level: filter state passed via Intent extras

## Files to Create/Modify

### New Files
- `OutlineTagFilter.java` — Filter state + matching logic: selected tags, andMode, `matches(long)`, `isContainer(long)`, `shouldShow(long)`, `getSelectedTagsArray()`, `isAndMode()`, `rebuild(ContentResolver)`, `isActive()`
- `layout/tag_filter_bar.xml` — `HorizontalScrollView` > `ChipGroup` + AND/OR `ToggleButton`

### Modified Files
- `OutlineActivity.java` — Filter bar init in `onCreate`, tag loading in `onResume`, chip listeners, sync-reload, state save/restore, Intent extras for cross-level state
- `OutlineAdapter.java` — `setFilter(OutlineTagFilter)`, filter in `init()` and `expand()`, alpha handling for container nodes
- `outline.xml` — `<include layout="@layout/tag_filter_bar" />` above the OutlineListView; add filter-empty `TextView` inside `outline_list_empty`

## Error Handling

| Condition | Behavior |
|-----------|----------|
| Tags table empty | Hide filter bar |
| Query fails | Log error, hide filter bar, show all nodes |
| Tags removed by sync while selected | Silently drop removed tags; if none remain, auto-select "All" |
| No matching nodes with active filter | Hide button container, show "No matching nodes" TextView in empty view |
| Saved expanded node ID not in filtered dataset | Silently skip during re-expand (normal) |
| Tag string is null | Treat as empty set for matching |

## Implementation Details

### Filter bar layout structure

```xml
<!-- tag_filter_bar.xml -->
<LinearLayout orientation="horizontal">
    <HorizontalScrollView android:layout_weight="1"
                          android:scrollbars="none">
        <ChipGroup android:id="@+id/tag_filter_chips"
                   app:singleSelection="false" />
    </HorizontalScrollView>
    <ToggleButton android:id="@+id/tag_filter_mode"
                  android:textOn="AND"
                  android:textOff="OR"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content" />
</LinearLayout>
```

- AND/OR toggle is fixed on the right, does not scroll with chips.
- Chip style: `@style/Widget.MaterialComponents.Chip.Filter` (checkmark animation, checked/unchecked states).

### Layout ordering in outline.xml

```
ActionBar (provided by AppCompat)
── recording bar (dynamic, inserted at position 0 via addView)  ← temporary
── tag_filter_bar (static include)                               ← persistent
── OutlineListView (fills remaining space)
── empty view RelativeLayout (hidden)
```

Recording bar is always above filter bar. Both coexist when recording + filtering simultaneously.

### Chip behavior

- "All" chip: checked by default (no filter active). When checked, unchecks all tag chips.
- Tag chips: checking a tag chip unchecks "All". Unchecking the last tag chip auto-checks "All".
- No tag count limit. Tags table typically has < 50 entries.

### Interaction with search

Tag filtering and search are independent. Search is handled by a separate `SearchActivity` (Android's built-in search). The filter bar state is preserved when the user returns from search.

## Performance Considerations

- Descendant scan: one query, one pass through all rows. Typical org files <5000 nodes → <10ms on main thread.
- Parent chain walk: O(m × d) where m = matching nodes, d = average depth. Acceptable.
- Cache invalidation: `rebuild()` called on filter change or sync complete.
- Tag chip list: typically <50 tags. No pagination needed.
- All operations synchronous on main thread. No AsyncTask complexity unless profiling shows a need.

## Tag matching — pure function

To make tag matching testable without Android dependencies, extract the matching logic as a static method:

```java
// In OutlineTagFilter.java
/** Pure function: testable without ContentResolver. */
static boolean matchesTags(String tags, String tagsInherited,
                           Set<String> selectedTags, boolean andMode) {
    Set<String> nodeTags = new HashSet<>();
    if (tags != null) {
        for (String t : tags.split(":"))
            if (!t.isEmpty()) nodeTags.add(t);
    }
    if (tagsInherited != null) {
        for (String t : tagsInherited.split(":"))
            if (!t.isEmpty()) nodeTags.add(t);
    }
    if (andMode) {
        return nodeTags.containsAll(selectedTags);
    } else {
        for (String t : selectedTags)
            if (nodeTags.contains(t)) return true;
        return false;
    }
}
```

## Testing

- **Unit** (pure Java, no Android): `matchesTags()` — OR match, OR no match, AND match, AND no match, inherited tags, empty tags, null tags, mixed tags+tags_inherited
- **Unit** (pure Java): `isActive()` — false when no tags selected, true when tags selected
- **Unit** (pure Java): `clearAll()` — clears selections, `isActive()` returns false
- **Unit** (pure Java): `setSelectedTags()` / `getSelectedTagsArray()` round-trip
- **Instrumentation**: `rebuild()` — `matchingNodeIds` correct, `containerIds` includes ancestors, `isContainer()` vs `matches()` distinction, empty tags table
- **Instrumentation**: Filter bar hidden when Tags table empty, visible when tags exist
- **Instrumentation**: State restoration after rotation
- **Instrumentation**: Cross-level filter preservation via Intent extras
- **Instrumentation**: Sync removes stale tag selections; all tags removed → "All" auto-selected
- **Instrumentation**: "All" chip clears all selections; last tag unchecked → "All" auto-selected
- **UI**: Container nodes rendered at alpha=0.5; direct matches at alpha=1.0
