# MobileOrg Android — Domain Glossary & Context

## Domain Concepts

These terms come from Org-mode (the Emacs outliner/PIM system this app is a client for).

- **OrgNode** — A single heading/entry in an org file. Has a name (heading text), TODO state, priority, tags, and a payload (body text, properties, timestamps). Nodes form a tree via parentId/fileId.
- **OrgFile** — A logical org file. Each has a root OrgNode (id == fileId) whose children are the top-level headings.
- **OrgEdit** — A pending change to be synced back to the remote. Created when the user modifies a node locally; consumed and deleted during sync push.
- **Payload** — The body text below a heading. Contains SCHEDULED, DEADLINE, properties drawer, clock entries, and free-form text.
- **OLP path** — "Org Link Path": `olp:filename:heading1/heading2/...` — a stable reference to a node by its position in the file tree.
- **Capture** — A quick-entry workflow. New nodes go into a special `CAPTURE_FILE` and are pushed to the remote during next sync.
- **Sync** — The process of pulling remote org files (via WebDAV, SSH, or SD card) into the local SQLite DB, parsing them, and pushing local edits back.

## Architectural Decisions

See `docs/adr/` for formal records.

## App Features

- **Pomodoro** (番茄钟) — A countdown timer (default 25 min) that runs independently of any OrgNode. Managed by `TimeclockService`. Records completion when the timer expires (timeout). Does NOT write CLOCK entries to OrgNode payloads.
- **Clock** (计时) — A task timer that records worked time on a specific OrgNode. Writes `CLOCK:` entries into the node's `:LOGBOOK:` drawer on clock-out. Can run simultaneously with a Pomodoro session.
