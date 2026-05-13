# Tag Filtering Feature Design

**Goal**: Add tag-based filtering to the outline view so users can quickly find nodes by tags.

**Architecture**: Horizontal Chip filter bar above the outline list, loaded from Tags table, filtering OutlineAdapter in-memory. AND/OR toggle for combining selected tags.

**Tech Stack**: RecyclerView (horizontal), Chip/ChipGroup, ContentProvider query for tags.

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Filter scope | All levels (file list + subtrees) | User wants consistent filtering experience |
| Top-level matching | Match if ANY node in file has the tag | Don't miss files containing tagged content |
| Empty tags | Hide filter bar | No visual noise when irrelevant |
| Tag source | Tags table (global list) | Consistent across navigation |
| Filter expansion | Show matching nodes only, no auto-expand | Simpler implementation, clearer results |
| Clear filter | "All" chip clears all selections | One-tap reset |
| Cross-level state | Preserve filter state across navigation | Don't lose user's filter on navigation |

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

- **Filter bar**: `RecyclerView` with `LinearLayoutManager(horizontal)`, items are Material `Chip` widgets.
- **"All" chip**: Always first. When selected, clears all other selections (no filter).
- **Tag chips**: One per unique tag from the `Tags` table. Checked = active filter.
- **AND/OR toggle**: A small `ToggleButton` at the right end. Displays "AND" or "OR". Defaults to "OR".
- **Visibility**: Shown when Tags table has entries. Hidden when no tags available.

## Data Flow

1. **Load tags**: On `OutlineActivity.onResume()`, query `Tags` table via `OrgProviderUtils.getTags()` to get all unique tags. Populate the filter bar. If no tags, hide the bar.
2. **User selects tags**: Click chip → toggle checked state. If "All" is clicked, uncheck all others.
3. **Filter**: `OutlineAdapter` receives the set of selected tags + AND/OR mode.
   - **OR mode**: Show node if its `tags` or `tags_inherited` contains ANY selected tag.
   - **AND mode**: Show node if its `tags` or `tags_inherited` contains ALL selected tags.
   - **No selection**: Show all nodes (no filter).
4. **Tag matching**: Split node's `tags` and `tags_inherited` by `:`, check intersection with selected set.
5. **Top-level special case**: At file-list level (node_id = -1), a file node matches if ANY of its child nodes match the filter. This requires querying child nodes' tags. Implementation: for each file node, query `OrgProviderUtils` for child nodes and check if any match. Cache results to avoid repeated queries.
6. **Cross-level preservation**: When navigating into/out of subtrees, filter state (selected tags + AND/OR mode) is preserved. The adapter re-applies the filter with the new data set.

## State Management

- Selected tags (`Set<String>`) and AND/OR mode (`boolean`) saved in `onSaveInstanceState` / restored in `onCreate`.
- Filter state is per-activity instance, not persisted across sessions.

## Files to Create/Modify

### New Files
- `OutlineTagFilter.java` — Manages tag filter state (selected tags, AND/OR mode), provides `matches(OrgNode)` method.
- `layout/tag_filter_bar.xml` — Horizontal RecyclerView container with AND/OR toggle.
- `layout/tag_filter_chip.xml` — Single chip item layout.

### Modified Files
- `OutlineActivity.java` — Add filter bar initialization, tag loading, filter state management, instance state save/restore.
- `OutlineAdapter.java` — Accept filter criteria, filter displayed nodes in `refresh()`.
- `outline.xml` — Add filter bar above the ListView.

## Error Handling

- Tags table empty: Hide filter bar entirely.
- Query fails: Log error, hide filter bar, show all nodes.
- No matching nodes: ListView shows empty state (existing behavior).

## Performance Considerations

- Top-level file filtering requires querying child nodes per file. Cache file→matches mapping during a single filter pass. Invalidate cache on `refreshDisplay()`.
- Tag chip list is small (typically <50 tags), no pagination needed.
- Filter is applied in-memory on the adapter's list, not as a SQL WHERE clause (simpler, and list sizes are manageable).

## Testing

- Unit test `OutlineTagFilter.matches()` with OR and AND modes, including inherited tags.
- Verify filter bar visibility: hidden when Tags table empty, visible when tags exist.
- Verify state restoration after rotation.
- Verify cross-level filter preservation.
