# ADR 0003: Undo as Pre-Sync Batch Pop, LIFO Only

## Status

Accepted

## Context

Users make mistakes on mobile: a wrong TODO toggle, a bad edit saved from `EditActivity`, clocking out on the wrong node. The local Edits table already records `oldValue`/`newValue` for every field-level edit (TODO/HEADING/PRIORITY/BODY/TAGS), which makes it a natural undo log. Clock-out records land in this system automatically — `addLogbook()` generates a BODY edit carrying the full previous payload.

Two structural facts constrain the design:

1. Sync push consumes the log: edit rows are serialized into `mobileorg.org`, pushed to the remote, then deleted (`Synchronizer.java`). After sync, the old value no longer exists anywhere locally — the DB is re-parsed from the server, which has the new value.
2. One user action produces multiple rows: a single `EditActivity` save can emit up to 5 rows (one per changed field). Row-level undo would show half-undone intermediate states.

### Alternatives considered

1. **Undo across sync (archive consumed edits)** — Requires a history table with pruning, plus undo-after-sync means creating a new forward edit pushed back to a server file that may have changed (conflict handling). An order of magnitude more complexity for mistakes noticed long after the fact.
2. **Row-level undo** — No schema change, but "one save" needs up to 5 undos through incoherent intermediate states.
3. **Arbitrary-batch undo (picker UI)** — Not just more UI: it is *unsound* without conflict detection. If batch 3 and batch 5 both edit node X's TODO, undoing batch 3 restores the pre-batch-3 value, silently destroying batch 5's intentional change. There is no rebase machinery here.
4. **Batch-level LIFO pop** (chosen) — Popping the top batch guarantees no later edit touches the restored fields; `oldValue` of batch N is exactly the state after batch N-1. Stack discipline makes wholesale restore precise.

## Decision

Undo reverts the **last user action** (LIFO), only while its edits are still pending (pre-sync).

- Edits table gains a `batch_id` column. Each user action (editor save, outline quick edit, clock-out, recording finish) allocates one batch id for all rows it writes.
- Undo pops the highest batch: restores each field's `oldValue` on the local node (BODY restores the full raw payload), then deletes the batch's rows.
- Single entry point in the `OutlineActivity` options menu, titled with what will be undone (e.g. "撤销：clock-out '学日语'"), disabled when no pending batch exists. Snackbar was rejected because clock-out from the notification shade happens outside the app and cannot show one.
- After an undo, the previous batch becomes the top — repeated undo works with zero extra UI.
- If the target node was deleted locally (delete/archive are not undoable and remove nodes from the DB), undo refuses with a toast and keeps the batch.

Out of scope: undoing node deletion/refile/archive/add (structural ops store no old state), capture-file edits (capture generates no edit rows).

## Consequences

- The undo window equals the sync interval; a user who syncs immediately has no undo. Accepted per usage pattern: mistakes are noticed immediately.
- Batch grouping must be wired into all 4 edit-writing entry points (`EditActivityControllerEdit`, `OutlineItem`, `TimeclockService.doClockOut`, `RecordingService`). Missing one causes fragmented batches — a save partially undone.
- Edits written before the schema migration have `batch_id = NULL`; they are never popped by undo (harmless: they still sync normally).
- Upgrading to a picker later requires per-field conflict detection ("a later batch also edits this field"), not just UI.
