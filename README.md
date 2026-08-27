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
- **Pomodoro timer**: Configurable duration with alarm sound on completion, overtime tracking, and statistics
- **Pomodoro statistics**: Day/week/month charts showing completed pomodoro sessions, with trend analysis (MPAndroidChart)
- **Timeclock**: Built-in timer for clocking tasks (with Effort estimation support)
- **Quick recording**: Record audio and attach to org nodes (requires RECORD_AUDIO permission)
- **Auto-sync**: Periodic background synchronization
- **DEADLINE/SCHEDULED reminders**: Exact-time notifications for upcoming deadlines and scheduled items
- **Daily overview**: Morning summary notification of today's scheduled items and upcoming deadlines
- **Undo**: Undo the most recent edit batch (LIFO) from the outline menu — field edits, TODO changes, body/logbook edits, and voice-recording attachments each form one batch
- **Share node**: Share a node's entire subtree as org-format plain text (normalized heading levels) via the Android share sheet — from the outline long-press menu or the node view menu; works on any node including agenda entries and whole files
- **Sync config backup**: Export/import sync settings via system file picker (SAF), survives app reinstall; the first-run wizard also offers direct import from a config file
- **In-app help center**: Built-in documentation (Chinese/English, follows app locale) with quick start, sync setup, outline usage, pomodoro, statistics, reminders, and extras topics — dark-theme aware, with in-page navigation and external link handling; accessible from the outline menu
- **Homescreen widgets**: Agenda widget and capture shortcut widget
- **Theme support**: Light and dark themes with theme-aware UI colors
- **Bilingual UI**: Full Chinese/English interface with in-app language switching (follow system / Chinese / English)

## Tech Stack

| Category | Technology |
|----------|-----------|
| Platform | Android (minSdk 17, targetSdk 34) |
| Build | Gradle 8.5 + AGP 8.2.2 + JDK 17 |
| Database | SQLite via ContentProvider |
| SSH | [JSch](http://www.jcraft.com/jsch/) 0.1.50 |
| Syntax Highlighting | [highlight.js](https://highlightjs.org/) 11.9.0 (Atom One Dark) |
| Charts | [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) v3.1.0 (via JitPack) |
| UI | Material Components, AndroidX AppCompat, RecyclerView, ViewPager2 |

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

### v2.12.0

- **In-app language switching**: New "Language" setting (Follow system / 中文 / English) at the top of Settings, built on AppCompat per-app language APIs. Works on Android 8+ (persists across restarts, framework activities included) and integrates with the system per-app language panel on Android 13+.
- **Complete Chinese translation**: ~150 missing UI strings and 11 preference arrays translated; 53 Chinese-hardcoded default strings converted to proper English defaults with zh translations; hardcoded text in pomodoro/timeclock notifications and dialogs, the setup wizard, and the outline dashboard extracted to localized resources.
- **Fix**: Help center ActionBar title now follows the selected language on Android < 13.

### v2.11.0

- **In-app help center**: New Help entry in the outline menu opens a built-in documentation hub — 7 topics (quick start, sync, outline, pomodoro, statistics, reminders, extras) rendered in a WebView, in Chinese or English following the app locale. Dark theme applies automatically; in-page links navigate within the help section, external links open via the system handler.
- **Fix**: Help page internal links were silently broken — `loadDataWithBaseURL` baseUrl lacked the locale subdirectory, so relative links resolved to nonexistent asset paths (images worked because they live in a shared root directory). baseUrl is now derived from the asset path, and internal navigation is intercepted to preserve dark-theme injection.

### v2.10.0

- **Share node**: Share a node's entire subtree as org-format text via the Android share sheet (`ACTION_SEND`, text/plain). Heading levels are normalized to start at `*` regardless of source depth; payload (SCHEDULED/DEADLINE/clock log) kept verbatim. Entry points: outline long-press menu (nodes and files, editable or not) and the node view overflow menu. Oversized subtrees are truncated at 400,000 chars to stay under the Binder transaction limit.

### v2.9.0

- **Undo**: Menu-driven undo of the latest edit batch (LIFO). Field-level edits are grouped into labeled batches; structural operations (add/delete node) stay outside undo. A finished voice recording is written as a single batch.
- **Wizard config import**: "Import from config file" button on the first-run wizard page — restores previously exported sync settings without manual setup.
- **Fix**: Sync spinner could rotate forever when the options menu was rebuilt mid-sync.

### v2.8.2

- Fix: crash when pressing Back in EditActivity with inconsistent location data

### v2.8.1

- **Consecutive pomodoro mode**: automatic work/rest cycles with a dedicated REST notification channel
- Fixes: REST end notification silent, sync animation stuck, CalendarSyncService OOM on large org files, pomodoro timeout delayed by Doze on vendor ROMs, ReminderScheduler SecurityException on Android 12+ without exact-alarm grant, cursor leak in repositories

### v2.8.0

- **Pomodoro statistics**: New statistics screen with overview (today/week/all-time counts) and trend charts (bar chart + daily line chart), accessible from outline menu. Day/week/month granularity with navigation. Uses MPAndroidChart with theme-aware colors.
- **AlarmManager → Handler migration**: Replaced AlarmManager-based timing in TimeclockService with Handler.postDelayed(). Foreground service timers (pomodoro countdown, timeout) now work reliably in background — no more missed timeouts or stale notification displays.
- **Exact alarm reminders**: DEADLINE/SCHEDULED reminders now use setExactAndAllowWhileIdle() with SCHEDULE_EXACT_ALARM permission, replacing setWindow() which was elongated to 10 minutes on Android 12+.
- **Daily overview fix**: Replaced setRepeating() (inexact since API 19) with one-shot setExact() + reschedule in BroadcastReceiver for precise daily timing.

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
