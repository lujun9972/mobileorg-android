# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Bug Fix Checklist

每次修复一个 bug 后，必须完成以下三件事：

1. **总结经验写入 CLAUDE.md** — 在 "Android Compatibility (Applied Fixes)" 或 "Known Pitfalls" 中记录
2. **全局检查同类问题** — 用 grep 搜索代码中是否有相同模式的其他实例，一次性修完
3. **更新博文** — 将新踩的坑追加到 `~/github/lujun9972.github.com/编程之旅/MobileOrg-Android-从API-17迁移到API-34的实战记录.org`

## Project Overview

MobileOrg Android — an Android client for Org-mode. Fork of the unmaintained [matburt/mobileorg-android](https://github.com/matburt/mobileorg-android). This is the `main` branch, started from the original codebase (commit 39ffb4e) with Android 8+ / 12+ compatibility fixes applied.

## Build

**Requirements**: JDK 17, Android SDK with platform 34 and build-tools 34.

```bash
./gradlew assembleDebug
```

APK output: `MobileOrg/build/outputs/apk/debug/`

**Build toolchain**: Gradle 8.5 + AGP 8.2.2 + JDK 17 + compileSdk 34 + targetSdk 34.

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
- **Foreground Service Type**: SyncService declares `dataSync`, TimeclockService declares `specialUse` (required on API 34+)
- **POST_NOTIFICATIONS**: Requested at runtime on API 33+ before sync
- **Scoped Storage**: `WRITE_EXTERNAL_STORAGE` limited to maxSdkVersion 28; `requestLegacyExternalStorage=true` for SDCard sync compat
- **NotificationCompat.Builder**: All Builder constructors must pass CHANNEL_ID (e.g. `new NotificationCompat.Builder(context, CHANNEL_ID)`). Without it, notifications reference no channel and crash with `CannotPostForegroundServiceNotificationException` on API 26+.
- **Runtime permissions**: Calendar permissions (`READ_CALENDAR`, `WRITE_CALENDAR`) are dangerous and must be checked before accessing CalendarProvider. `CalendarSyncService` checks in `onCreate()`/`onStartCommand()` and stops self if not granted. When adding any new dangerous permission usage, always add a runtime check — manifest declaration alone is insufficient on API 23+.
- **Menu XML showAsAction**: Project uses AppCompat (`AppCompatActivity`), so all menu XML files must use `app:showAsAction` (from `xmlns:app="http://schemas.android.com/apk/res-auto"`) instead of `android:showAsAction`. The `android:` version is silently ignored by AppCompat Toolbar/ActionBar, causing menu icons to not appear.
- **Service early return → onDestroy NPE**: When adding early returns in `onCreate()`/`onStartCommand()` (e.g. for permission checks), `onDestroy()` will still be called by Android. Any fields that would have been initialized in the skipped code must be null-checked in `onDestroy()` before use.
- **No network on main thread**: Synchronizer constructors are called from `SyncService.getSynchronizer()` on the main thread. Never do network I/O (SSH connect, HTTP requests) in constructors. All network operations must happen on the background sync thread.
- **sendBroadcast must use setPackage()**: With targetSdk 34, implicit broadcasts may not be delivered to `RECEIVER_NOT_EXPORTED` receivers. Always add `intent.setPackage(context.getPackageName())` before `sendBroadcast()` to make it explicit. This applies to all `OrgUtils.announceSync*()` methods.
- **Preference intent must use targetPackage/targetClass**: In XML preferences, use `android:targetPackage` + `android:targetClass` instead of implicit `android:action`. Implicit action intents can resolve incorrectly or fail on modern Android.
- **View.startAnimation() needs attached view**: When using `MenuItem.setActionView()` with animation, call `setActionView()` first, then use `View.post()` to start the animation. Starting animation on an unattached view is silently ignored.
- **Intent.getAction() can be null**: When navigating with `FLAG_ACTIVITY_SINGLE_TOP` or `FLAG_ACTIVITY_CLEAR_TOP`, the existing Activity receives `onNewIntent()` callback. The incoming intent may have no action (`getAction()` returns null). Always use `CONSTANT.equals(intent.getAction())` (constant on left) instead of `intent.getAction().equals(CONSTANT)` to avoid NPE.

All guards use `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` pattern.

## Known Pitfalls

- **Singleton state**: DB, Parser, Synchronizer are singletons, not thread-safe. Sync thread accesses DB while UI reads it — potential race conditions.
- **RecyclerView + extra items**: `OutlineAdapter` adds 2 header items. Position-to-index must subtract `numExtraItems`.
- **`OrgNodeListActivity`**: No `onSaveInstanceState` — rotation can cause issues.
- **AndroidX**: Project uses AndroidX (`androidx.*` imports). Jetifier enabled for local JARs.
