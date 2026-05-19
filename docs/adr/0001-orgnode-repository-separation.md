# ADR 0001: Separate OrgNode Domain Model from Data Access

## Status

Proposed

## Context

`OrgNode` (640 lines) serves as both a domain model (fields, toString, equals, payload parsing) and a data access layer (write, delete, getChildren, archive — all taking ContentResolver). This violates single responsibility:

- Domain logic (payload diff, tag parsing) is buried among database queries.
- Every method passes `ContentResolver` as a parameter — callers must know persistence details.
- Testing domain logic requires ProviderTestCase2 (heavy Android test infrastructure).
- `OrgProviderUtils` duplicates some node operations as static methods.

56 call sites across 18 files call OrgNode methods with ContentResolver.

## Decision

Introduce `OrgNodeRepository` to hold all data access methods. `OrgNode` becomes a pure domain model.

### Design choices confirmed:

1. **Single repository** — one `OrgNodeRepository` class holding all node data access (~20 methods). Splitting into multiple repos would break locality for edit generation (which reads nodes, computes diffs, and writes edits as one transaction).

2. **Gradual migration** — Repository created first; OrgNode data-access methods deprecated and delegated; callers migrated in batches; old methods deleted last. Not a single big-bang refactor.

3. **Field-held instances** — Each Activity/Service creates `new OrgNodeRepository(getContentResolver())` in `onCreate()` and stores it as a field. No new singleton, no DI framework.

4. **OrgNode(Cursor) stays** — The Cursor constructor remains on OrgNode itself. Cursor is just a data-reading interface; keeping it avoids an extra factory indirection.

5. **generateApplyEditNodes mutation preserved** — The method mutates the input `oldNode` during diff computation. This is kept as-is for behavioral safety during migration. Pure-function extraction is a possible follow-up.

### Interface sketch:

```java
public class OrgNodeRepository {
    private final ContentResolver resolver;

    public OrgNodeRepository(ContentResolver resolver);

    // CRUD
    public OrgNode getById(long id);
    public void write(OrgNode node);
    public void deleteNode(OrgNode node);

    // Tree traversal
    public ArrayList<OrgNode> getChildren(long nodeId);
    public OrgNode getChild(long parentId, String name);
    public boolean hasChildren(long nodeId);
    public OrgNode getParent(long nodeId);

    // Node identity & location
    public String getNodeId(OrgNode node);
    public String getOlpId(OrgNode node);
    public OrgNode findOriginalNode(OrgNode node);
    public OrgNode getOrgNodeFromOlpPath(String olpPath);

    // Edit generation
    public ArrayList<OrgEdit> generateApplyEditNodes(OrgNode old, OrgNode new_, String olpPath);
    public void archiveNode(OrgNode node);
    public void addLogbook(OrgNode node, long start, long end, String elapsed);

    // Serialization
    public StringBuilder nodesToString(long nodeId, long level);
}
```

## Consequences

- OrgNode drops from ~640 to ~250 lines (fields, constructors, pure methods only).
- OrgProviderUtils shrinks — node operations move to repository, file/metadata operations stay.
- Callers change from `node.getChildren(resolver)` to `repo.getChildren(node.id)`.
- Domain logic tests can use plain JUnit (no Android test infrastructure needed).
- Intermediate state: deprecated OrgNode methods coexist with repository methods during migration.
