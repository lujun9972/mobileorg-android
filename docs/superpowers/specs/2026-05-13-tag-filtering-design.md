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

    public OutlineTagFilter(ContentResolver resolver);

    /** Add/remove a tag from selection. Caller must call rebuild() afterward. */
    public void setTagSelected(String tag, boolean selected);
    public void setAndMode(boolean andMode);
    public void clearAll();  // deselect all tags (selects "All")

    /** True if any tag is selected. */
    public boolean isActive();

    /** Cached lookup: node's tags matched during last rebuild(). */
    public boolean matches(long nodeId);

    /** Cached lookup: node is an ancestor of a matching node. */
    public boolean isContainer(long nodeId);

    /** Combined: show this node in filtered view? */
    public boolean shouldShow(long nodeId);

    /** Rebuild internal sets. Called on filter change or sync. */
    public void rebuild(ContentResolver resolver);

    /** Get selected tags as array (for Intent extras). */
    public String[] getSelectedTagsArray();

    /** Current AND/OR mode. */
    public boolean isAndMode();
}
```

- `matches()` and `isContainer()` are pure `Set<Long>` lookups against cached results from `rebuild()`. No tag parsing at query time.
- `rebuild()` executes `SELECT _id, parent_id, tags, tags_inherited FROM orgdata`, does tag matching in Java (colon-split + HashSet), builds parent map, walks ancestor chains. **Note**: parent chains may cross file boundaries — a matching node's ancestors in other files could appear as containers. Acceptable for simplicity; add `file_id` filtering if this becomes an issue.
- All operations are on the main thread. Typical org files (<5000 nodes) complete in a few ms.

## Data Flow

### 1. Initialization (Activity)

`OutlineActivity.onCreate()`:
1. `setContentView(R.layout.outline)` — filter bar included via `<include>`, initially `View.GONE`
2. Read `node_id` from Intent
3. If Intent contains filter extras (selectedTags, andMode), restore `OutlineTagFilter`
4. `setupList()` — create adapter, call `adapter.setFilter(filter)` **before** `adapter.init()`

`OutlineActivity.onResume()`:
1. Query `Tags` table via `OrgProviderUtils.getTags()`
2. If tags exist: populate `ChipGroup`, apply existing `OutlineTagFilter` selections to chips, set bar visible
3. If tags empty: hide bar

### 2. Chip interaction (Activity → Adapter)

```
User taps chip
  → ChipGroup.OnCheckedChangeListener
    → if "All" chip: uncheck all tag chips, deselect all tags in OutlineTagFilter
    → else if tag chip:
        → add to OutlineTagFilter.selectedTags
        → uncheck "All" chip
        → if no tag chips remain checked: auto-check "All" chip, clear filter
    → filter.rebuild(resolver)  // re-scan descendant matches
    → adapter.setFilter(filter) // update adapter reference
    → adapter.refresh()         // re-init with filter applied
```

Toggle switch: same flow — update `filter.andMode`, `filter.rebuild()`, `adapter.refresh()`.

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

In `OutlineItem` (or the adapter's `getView()`):

```java
if (filter != null && filter.isActive() && filter.isContainer(node.id) && !filter.matches(node.id)) {
    itemView.setAlpha(0.5f);  // container node — dimmed
} else {
    itemView.setAlpha(1.0f);  // direct match or no filter
}
```

Entire row at 50% alpha gives a clear "this is a container, not a direct match" signal.

### 5. Expand in filtered view

When user expands a node (container or direct match), `expand()` loads children, filters each through `filter.shouldShow()`. Only matching children + sub-containers are added. Non-matching children are omitted.

### 6. Refresh and expand state preservation

`OutlineAdapter.refresh()` saves expanded node IDs, calls `init()` (which reloads with filter), then re-expands saved IDs via `expandNodes()`. If a saved ID no longer exists in the adapter (filter changed, node no longer matches and is not a container), it is silently skipped — no error, no log. This is normal behavior.

Clearing the filter (clicking "All") triggers `refresh()` — previously expanded nodes remain expanded because their IDs are in the new unfiltered dataset.

### 7. Empty filter result

When `filter.isActive()` and `adapter.getCount() == 0`: set the existing `outline_list_empty` TextView text to "No matching nodes". When filter is cleared or inactive, restore original empty view text. This avoids creating extra views — just swap the text on the existing empty view.

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
- `outline.xml` — `<include layout="@layout/tag_filter_bar" />` above the OutlineListView

## Error Handling

| Condition | Behavior |
|-----------|----------|
| Tags table empty | Hide filter bar |
| Query fails | Log error, hide filter bar, show all nodes |
| Tags removed by sync while selected | Silently drop removed tags; if none remain, auto-select "All" |
| No matching nodes with active filter | Show "No matching nodes" text |
| Saved expanded node ID not in filtered dataset | Silently skip during re-expand (normal) |
| Tag string is null | Treat as empty set for matching |

## Tag Matching Specification

Tags in the database are colon-delimited strings (e.g. `"work:urgent:home"`). Leading/trailing colons are stripped by the parser.

### Java-side matching (used in OutlineTagFilter.rebuild() only)

```java
// Merge node's own tags and inherited tags into one set
Set<String> nodeTags = new HashSet<>();
if (node.tags != null) {
    for (String t : node.tags.split(":")) {
        if (!t.isEmpty()) nodeTags.add(t);
    }
}
if (node.tags_inherited != null) {
    for (String t : node.tags_inherited.split(":")) {
        if (!t.isEmpty()) nodeTags.add(t);
    }
}

// OR mode: nodeTags ∩ selectedTags is non-empty
// AND mode: nodeTags contains all selectedTags
```

### Descendant scan SQL

Single query for all nodes: `SELECT _id, parent_id, tags, tags_inherited FROM orgdata`. Java-side matching (same logic as above) builds `matchingNodeIds`. Parent map (`_id → parent_id`) is built from the same cursor. Ancestor chains are walked in Java to build `containerIds`.

## Performance Considerations

- Descendant scan: one query, one pass through all rows. Typical org files <5000 nodes → <10ms on main thread.
- Parent chain walk: O(m × d) where m = matching nodes, d = average depth. Acceptable.
- Cache invalidation: `rebuild()` called on filter change or sync complete.
- Tag chip list: typically <50 tags. No pagination needed.
- All operations synchronous on main thread. No AsyncTask complexity unless profiling shows a need.

## Testing

- **Unit**: `OutlineTagFilter.matches()` — OR, AND, inherited tags, empty tags, null tags, merged tags+tags_inherited
- **Unit**: `OutlineTagFilter.rebuild()` — `matchingNodeIds` correct, `containerIds` includes ancestors, `isContainer()` vs `matches()` distinction
- **Unit**: `OutlineTagFilter.isActive()` — false when no tags selected, true when tags selected
- **Unit**: `OutlineTagFilter.clearAll()` — clears selections, `isActive()` returns false
- **Integration**: Filter bar hidden when Tags table empty, visible when tags exist
- **Integration**: State restoration after rotation
- **Integration**: Cross-level filter preservation via Intent extras
- **Integration**: Sync removes stale tag selections; all tags removed → "All" auto-selected
- **Integration**: "All" chip clears all selections; last tag unchecked → "All" auto-selected
- **UI**: Container nodes rendered at alpha=0.5; direct matches at alpha=1.0
