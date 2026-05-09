# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MobileOrg Android — an Android client for Org-mode. Fork of the unmaintained [matburt/mobileorg-android](https://github.com/matburt/mobileorg-android). This is the `main` branch, started from the original codebase (commit 39ffb4e) with Android 8+ / 12+ compatibility fixes applied.

## Build

**Requirements**: JDK 8, Android SDK with platform 28 and build-tools 28.0.3.

```bash
./gradlew assembleDebug
```

APK output: `MobileOrg/build/outputs/apk/debug/`

**Build toolchain**: Gradle 4.10.1 + AGP 3.2.1 + compileSdk 28. Max version due to AAPT2 blocker (see Known Pitfalls).

No automated tests exist in this project.

## Architecture

### Build System

- Single module `:MobileOrg` + library module `:libraries:locale`
- 3 local JARs in `MobileOrg/libs/` (CWAC adapters, json_simple) — not on Maven
- Signing: release keystore with hardcoded passwords in `build.gradle`
- Version name comes from `git describe --tags`

### Key Packages (`com.matburt.mobileorg`)

- **`OrgData/`** — Core data layer. `OrgDatabase` (SQLite), `OrgFileParser` (parses org files into DB), `OrgProvider`/`OrgProviderUtils` (ContentProvider), `MobileOrgApplication` (app init). Singletons via static `getInstance()` / `startXxx()`.
- **`Synchronizers/`** — Abstract `Synchronizer` base with implementations: `WebDAVSynchronizer`, `SSHSynchronizer` (JSch), `SDCardSynchronizer`. Each implements `isConfigured()`, `isConnectable()`, `synchronize()`, `postSynchronize()`.
- **`Gui/Outline/`** — Main UI. `OutlineAdapter` prepends 2 fixed header items (TODO, Agenda) before the file list (`numExtraItems = 2`), so all position-to-index conversions must subtract 2.
- **`Services/`** — `SyncService` (sync via `AlarmManager` + background thread, foreground service on API 26+), `TimeclockService` (timer with foreground notification), `CalendarSyncService`.
- **`Gui/`** — Notifications (`SynchronizerNotification`/`Compat` with NotificationChannel support), wizard activities, widgets, search.

### Data Flow

1. `MobileOrgApplication.onCreate()` → init DB, Synchronizer, OrgFileParser, SyncService alarm
2. `SyncService` → `Synchronizer.runSynchronizer()` pulls remote files → `OrgFileParser.parseFile()` → SQLite
3. UI reads via `OrgProvider` ContentProvider / `OrgProviderUtils`
4. `OutlineAdapter.refresh()` reloads file list from ContentProvider

## Android Compatibility (Applied Fixes)

The original code targets API 17 and crashes on modern Android. The following fixes have been applied:

- **PendingIntent FLAG_IMMUTABLE**: All PendingIntent calls use `FLAG_IMMUTABLE` (required on Android 12+ / API 31)
- **NotificationChannel**: Channels created before any `notify()` call (required on Android 8+ / API 26). Channel ID: `mobileorg_sync`, `mobileorg_timeclock`
- **Foreground Service**: `SyncService` and `TimeclockService` call `startForeground()` on API 26+. Alarm PendingIntent uses `getForegroundService()` on API 26+
- **Service startup**: `SyncService.startAlarm()`/`stopAlarm()` and `OutlineActivity.runSynchronize()` use `startForegroundService()` on API 26+

All guards use `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` pattern.

## Known Pitfalls

- **Singleton state**: DB, Parser, Synchronizer are singletons, not thread-safe. Sync thread accesses DB while UI reads it — potential race conditions.
- **RecyclerView + extra items**: `OutlineAdapter` adds 2 header items. Position-to-index must subtract `numExtraItems`.
- **`OrgNodeListActivity`**: No `onSaveInstanceState` — rotation can cause issues.
- **Old build tools**: AGP 3.0.1 + Gradle 4.1 — old but functional.
- **AAPT2 is the upgrade blocker**: AGP 3.2.1 is the last version allowing `android.enableAapt2=false`. AGP 3.3+ forces AAPT2 which causes resource compilation errors. Must fix AAPT2 issues before further AGP upgrade.
- **AndroidX**: Project uses AndroidX (`androidx.*` imports). Jetifier enabled for local JARs.
- **Toolchain ceiling**: Gradle 4.10.1 + AGP 3.2.1 + JDK 8 + compileSdk 28 until AAPT2 issues are resolved.
