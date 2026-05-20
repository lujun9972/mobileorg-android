# MobileOrg Android

An Android client for [Org-mode](https://orgmode.org/), forked from the original [matburt/mobileorg-android](https://github.com/matburt/mobileorg-android) with modern Android compatibility fixes and new features.

## About

MobileOrg Android lets you view and edit your Org-mode files on an Android device. It synchronizes with remote org files via WebDAV, SSH (scp), or SD card, then parses them into a local SQLite database for browsing and editing on the go.

This fork brings the original project (API 17) up to modern Android standards — compatible with Android 8+ through 14+, with proper NotificationChannel support, PendingIntent mutability flags, foreground service handling, and Material Components theming.

## Features

- **Multiple sync methods**: WebDAV, SSH (scp via JSch), SD card
- **Org-mode browsing**: Navigate nodes, TODO states, tags, priorities
- **Rich content rendering**: HTML rendering engine with inline markup (`*bold*`, `/italic/`, `~code~`, `=verbatim=`, `_underline_`, `+strike+`), source blocks with syntax highlighting, tables, quote/example blocks, and org-mode link navigation (`[[file:]`, `[[id:]`, `[[*heading]`)
- **Tag filtering**: Filter outline by tags with AND/OR mode, auto-updating chip bar
- **Agenda view**: Day/week/month agenda from your org files
- **Capture**: Quick note entry (like `org-capture`)
- **Search**: Full-text search across all org nodes
- **Timeclock**: Built-in timer for clocking tasks (with Effort estimation support)
- **Quick recording**: Record audio and attach to org nodes (requires RECORD_AUDIO permission)
- **Auto-sync**: Periodic background synchronization via AlarmManager
- **DEADLINE/SCHEDULED reminders**: Individual notifications for upcoming deadlines and scheduled items
- **Daily overview**: Morning summary notification of today's scheduled items and upcoming deadlines
- **Sync config backup**: Export/import sync settings via system file picker (SAF), survives app reinstall
- **Homescreen widgets**: Agenda widget and capture shortcut widget
- **Theme support**: Light and dark themes with theme-aware UI colors

## Tech Stack

| Category | Technology |
|----------|-----------|
| Platform | Android (minSdk 17, targetSdk 34) |
| Build | Gradle 8.5 + AGP 8.2.2 + JDK 17 |
| Database | SQLite via ContentProvider |
| SSH | [JSch](http://www.jcraft.com/jsch/) 0.1.50 |
| Syntax Highlighting | [highlight.js](https://highlightjs.org/) 11.9.0 (Atom One Dark) |
| UI | Material Components, AndroidX AppCompat, RecyclerView |

## Getting Started

### Prerequisites

- JDK 17
- Android SDK with platform 34 and build-tools 34

### Build

```bash
./gradlew assembleDebug
```

APK output: `MobileOrg/build/outputs/apk/debug/`

## Project Structure

```
MobileOrg/src/main/java/com/matburt/mobileorg/
├── Gui/            # Activities, adapters, notifications, widgets, wizards
├── OrgData/        # SQLite database, org file parser, ContentProvider
├── Services/       # SyncService, TimeclockService, CalendarSyncService, RecordingService, ReminderReceiver, DailyOverviewReceiver
├── Synchronizers/  # Sync implementations (WebDAV, SSH, SD card)
├── Settings/       # Preference activities
├── Plugin/         # BroadcastReceiver for external sync triggers
└── util/           # OrgRenderer (org→HTML), OrgUtils, ReminderScheduler, SyncConfigHelper, preferences
```

## License

[GNU General Public License v2.0](LICENSE.txt)

## Changelog

### v2.7.0

- **Repository pattern**: Separated OrgNode into pure domain model (185 lines) and OrgNodeRepository (590 lines) for all data access. ~60+ call sites across 20+ files migrated, all deprecated data-access methods removed from OrgNode.
- **Test migration**: All 94 instrumentation tests migrated to use OrgNodeRepository, dead methods cleaned from OrgProviderUtils.
- **Architecture**: Added ADR-0001 documenting the Repository pattern decision.

### v2.6.1

- **Code simplification**: Removed 500+ lines of dead code across the codebase
- **Performance**: Pre-compiled 11 regex patterns in OrgRenderer, cached duplicate DB queries in Synchronizer
- **Code quality**: Fixed `== false` → `!expr` anti-patterns, extracted shared helpers (`Compat.startForeground`, `OrgNode.writePayloadWithEdits`)
- **Maintainability**: Removed unused `DirectoryBrowser`, stripped `FileUtils` to static-only utilities

### v2.6.0

- **Monochrome theme**: New warm-paper eye-comfort theme with full-screen consistency
- **Theme switching fix**: Theme change in Settings now properly recreates OutlineActivity
- **Timeclock fixes**: Corrected `saveClock` endTime logic, fixed `NumberPicker` stale value bug

### v2.5.0

- **Reminder system**: DEADLINE/SCHEDULED notifications with configurable advance time
- **Daily overview**: Morning summary of today's scheduled items and upcoming deadlines
- **SAF sync config**: Export/import sync settings via system file picker, survives app uninstall
- **Android compatibility**: AlarmManager broadcast delivery fix (API 31+), calendar runtime permission check
- **Bug fixes**: Cursor leak prevention, null safety in reminder system, boundary consistency

### v2.4.0

- Material Components theme migration
- Duration picker for timeclock (replaces broken TimePickerDialog)
- OrgRenderer XSS prevention
- Remote-deleted file cleanup on sync
- Android 8-14 compatibility fixes (NotificationChannel, PendingIntent, foreground service, scoped storage)

See [Releases](https://github.com/lujun9972/mobileorg-android/releases) for full version history.
