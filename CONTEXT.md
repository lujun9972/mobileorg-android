# MobileOrg Android — Domain Glossary & Context

## Domain Concepts

These terms come from Org-mode (the Emacs outliner/PIM system this app is a client for).

- **OrgNode** — A single heading/entry in an org file. Has a name (heading text), TODO state, priority, tags, and a payload (body text, properties, timestamps). Nodes form a tree via parentId/fileId.
- **OrgFile** — A logical org file. Each has a root OrgNode (id == fileId) whose children are the top-level headings. Pure domain model — no data access methods.
- **OrgEdit** — A pending change to be synced back to the remote. Created when the user modifies a node locally; consumed and deleted during sync push.
- **Payload** — The body text below a heading. Contains SCHEDULED, DEADLINE, properties drawer, clock entries, and free-form text.
- **OLP path** — "Org Link Path": `olp:filename:heading1/heading2/...` — a stable reference to a node by its position in the file tree.
- **Capture** — A quick-entry workflow. New nodes go into a special `CAPTURE_FILE` and are pushed to the remote during next sync.
- **Sync** — The process of pulling remote org files (via WebDAV, SSH, or SD card) into the local SQLite DB, parsing them, and pushing local edits back.

## Repository Layer

All data access goes through repository classes. Domain models (`OrgNode`, `OrgFile`) are pure data holders with no `ContentResolver` dependency.

- **OrgNodeRepository** — Node-level data access: CRUD on nodes, tree traversal (getOrgNodeFromOlpPath, getOrgNodeFromFilename), payload read/write, node serialization. ~670 lines.
- **OrgFileRepository** — File-level and metadata access: file CRUD (getById, getByFilename, getOrCreateFile, getOrCreateCaptureFile, addFile, removeFile), file-to-node conversion (getOrgNode, nodesToString), metadata (getTodos, getTags, getPriorities, setTodos, setTags, setPriorities), queries (getFilenames, getFileChecksums, getFileSchedule, search, getChangesCount, getActiveTodos, isTodoActive, getFileAliases). ~390 lines.

The old `OrgProviderUtils` static utility class (315 lines) has been deleted; all its methods now live in the appropriate repository.

## Timer Layer

- **PomodoroTimer** — Pure-logic countdown timer (no Android dependencies). Tracks duration, start time, remaining time, overtime. JUnit-testable.
- **ClockTimer** — Pure-logic task clock (no Android dependencies). Tracks duration, start/end times. JUnit-testable.
- **TimeclockService** — Android foreground service that composes PomodoroTimer + ClockTimer. Handles notifications, alarm sound, DB persistence (pomodoro sessions). ~350 lines.

## Architectural Decisions

See `docs/adr/` for formal records.

## App Features

- **Pomodoro** (番茄钟) — A countdown timer (default 25 min) that runs independently of any OrgNode. Managed by `TimeclockService`. Records completion when the timer expires (timeout). Does NOT write CLOCK entries to OrgNode payloads. When started from a node context (long-press menu), reads the node's `POMODORO_COUNT` property as the default count for consecutive mode — no further node association after that.
- **Pomodoro Session** (番茄) — A single completed pomodoro countdown. One row in `pomodoro_sessions` table. Multiple sessions can be chained in **consecutive mode**.
- **Consecutive Mode** (连续番茄) — Chains N pomodoros with rest breaks between them. States: WORK → REST → (user confirms) → WORK → … → all done. Managed by `PomodoroTimer` state machine.
- **Clock** (计时) — A task timer that records worked time on a specific OrgNode. Writes `CLOCK:` entries into the node's `:LOGBOOK:` drawer on clock-out. Can run simultaneously with a Pomodoro session.
