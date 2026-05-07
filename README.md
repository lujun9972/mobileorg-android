# MobileOrg Android

An Android client for [Org-mode](https://orgmode.org/), based on the original [matburt/mobileorg-android](https://github.com/matburt/mobileorg-android) with Android 8+ / 12+ compatibility fixes.

## About

MobileOrg Android lets you view and edit your Org-mode files on an Android device. It synchronizes with remote org files via WebDAV, SSH (scp), Dropbox, Git, or local SD card, then parses them into a local SQLite database for browsing and editing on the go.

This fork addresses the original project's incompatibility with modern Android — adding NotificationChannel support, PendingIntent mutability flags, and foreground service handling required by Android 8+ and 12+.

## Features

- **Multiple sync methods**: WebDAV, SSH (scp via JSch), Dropbox, Git (JGit), SD card
- **Org-mode browsing**: Navigate nodes, TODO states, tags, priorities
- **Agenda view**: Day/week/month agenda from your org files
- **Capture**: Quick note entry (like `org-capture`)
- **Search**: Full-text search across all org nodes
- **Timeclock**: Built-in timer for clocking tasks (with Effort estimation support)
- **Auto-sync**: Periodic background synchronization via AlarmManager
- **Homescreen widgets**: Agenda widget and capture shortcut widget

## Tech Stack

| Category | Technology |
|----------|-----------|
| Platform | Android (minSdk 9, compileSdk 23) |
| Build | Gradle 2.8 + Android Gradle Plugin 1.5.0 |
| Database | SQLite via ContentProvider |
| SSH | [JSch](http://www.jcraft.com/jsch/) 0.1.50 |
| Git sync | [JGit](https://www.eclipse.org/jgit/) (via `org.eclipse.jgit` library) |
| Dropbox | Dropbox Core SDK (local JAR) |
| UI | ActionBarSherlock, RecyclerView, RemoteViews (notifications) |

## Getting Started

### Prerequisites

- JDK 8
- Android SDK with platform 23 and build-tools 23.0.2

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (signed with bundled keystore)
./gradlew assembleRelease
```

APK output: `MobileOrg/build/outputs/apk/`

### Development

See [HACKING.md](HACKING.md) for setup instructions.

## Project Structure

```
MobileOrg/src/main/java/com/matburt/mobileorg/
├── Gui/            # Activities, adapters, notifications, widgets, wizards
├── OrgData/        # SQLite database, org file parser, ContentProvider
├── Services/       # SyncService, TimeclockService, CalendarSyncService
├── Synchronizers/  # Sync implementations (WebDAV, SSH, Git, Dropbox, SD card)
├── Settings/       # Preference activities
├── Plugin/         # BroadcastReceiver for external sync triggers
└── util/           # Utility classes
```

## License

[GNU General Public License v2.0](LICENSE.txt)
