# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MobileOrg Android — an Android client for Org-mode. Fork of the unmaintained [matburt/mobileorg-android](https://github.com/matburt/mobileorg-android). This is the `main` branch, started from the original codebase (commit 39ffb4e) with Android 8+ / 12+ compatibility fixes applied.

## Build

**Requirements**: JDK 17, Android SDK with platform 34 and build-tools 34.

```bash
./gradlew assembleDebug
```

APK output: `MobileOrg/build/outputs/apk/debug/`

**Build toolchain**: Gradle 8.5 + AGP 8.2.2 + JDK 17 + compileSdk 34 + targetSdk 34.

**注意**：不在本地构建。推送到远端由 CI 进行构建，用 `gh run list` / `gh run view` 检查 CI 结果即可。

**测试设备**: 无线调试已开启，`adb connect 192.168.31.198:34217` 可连接。若需本地运行 instrumentation 测试，先连接设备再执行 `./gradlew connectedDebugAndroidTest`。

**Remote/CI**: Git remote `git.zhlh6.cn` is a Gitea proxy that auto-syncs to GitHub. Pushing to it triggers GitHub Actions CI. Use `gh` CLI against the GitHub repo to check CI status (e.g. `gh run list`).

**Tests**: 94 instrumentation tests in `MobileOrg/src/androidTest/` using `ProviderTestCase2` + `AndroidJUnit4`. Run via `./gradlew connectedDebugAndroidTest` (requires emulator). CI runs on API 30 emulator via GitHub Actions.

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
- **`Services/`** — `SyncService` (sync via `AlarmManager` + background thread, foreground service on API 26+), `TimeclockService` (timer with foreground notification), `CalendarSyncService`, `ReminderReceiver` (individual deadline/scheduled notifications), `DailyOverviewReceiver` (daily summary notification).
- **`Gui/`** — Notifications (`SynchronizerNotification`/`Compat` with NotificationChannel support), wizard activities, widgets, search.
- **`util/ReminderScheduler`** — Scans OrgData for DEADLINE/SCHEDULED dates, registers AlarmManager reminders. Called after sync and on boot.

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
- **ProviderTestCase2 on API 30+**: `RenamingDelegatingContext` has a null delegate, causing NPE in `getDatabasePath()`. Fix: call `setContext(ApplicationProvider.getApplicationContext())` before `super.setUp()`. Also: (1) DB data persists between tests — must clean all tables (Edits, OrgData, Files) in setUp(); (2) `getMockContext()` may expose `MockContext` methods that throw `UnsupportedOperationException` (e.g. `getPackageName()`). When passing context to code that calls `getPackageName()`/`sendBroadcast()`, use `ContextWrapper` wrapping `ApplicationProvider.getApplicationContext()` with `getContentResolver()` overridden to return the test `MockContentResolver`.
- **Instrumentation test `useLibrary`**: Tests using deprecated `android.test.*` classes (ProviderTestCase2, MockContentResolver) require `useLibrary 'android.test.base'`, `useLibrary 'android.test.runner'`, and `useLibrary 'android.test.mock'` in `android {}` block of build.gradle.
- **Fragment inner classes must be `public static`**: AndroidX Fragment 1.2+ requires all Fragment subclasses to be `public static` (not non-static inner classes). Non-static inner classes hold implicit references to the enclosing instance and cannot be recreated via the required no-arg constructor. Affected: `TimeclockDialog.EditTimePickerFragment`, `DateTableRow.{StartTimePickerDialogFragment, EndTimePickerDialogFragment, DatePickerDialogFragment}`. Fix: make class `public static`, pass data via `Bundle` arguments, access enclosing Activity/View via cast of `getActivity()`.
- **Dangerous permission checks at every entry point + defensive coding in Service**: (1) When a feature is accessible from multiple UI paths (e.g. ActionMode menu AND options menu), every path must independently check dangerous permissions before starting the Service. Do not assume the user came through the permission-checked path. (2) The Service itself must also be defensively coded — wrap hardware API calls (like `MediaRecorder.setAudioSource()`) in try-catch, because permissions can be revoked between the UI check and Service execution. Never let a permission denial crash the app.
- **Permission check must provide user feedback**: Never use `checkCallingOrSelfPermission()` in UI paths — it silently returns with no user-visible feedback when permission is denied. Instead, use `ContextCompat.checkSelfPermission()` + `ActivityCompat.requestPermissions()` to show the system permission dialog. When multiple UI paths need the same permission check, delegate to a single method (e.g. `tryStartRecording()`) rather than duplicating the logic.
- **Notification action icons must be distinct**: Each `NotificationCompat.Action` should have its own semantically meaningful icon (pause=`⏸`, stop=`⏹`, play=`▶`). Using the same icon for all actions confuses users. Same applies to inline UI buttons.
- **Vector drawable namespace**: `<vector>` XML must use `xmlns:android="http://schemas.android.com/apk/res/android"`. Using `res-auto` (which is for `app:` attributes in layouts) causes AAPT build failure with "attribute not found" errors.
- **MaterialComponents theme migration**: When migrating from AppCompat to MaterialComponents (required for Chip, ChipGroup, etc.), style parents change: `Theme.AppCompat` → `Theme.MaterialComponents`, `Widget.AppCompat.ActionBar.Solid` → `Widget.MaterialComponents.ActionBar.Solid`. The `.Inverse` variants (e.g. `Widget.AppCompat.Light.ActionBar.Solid.Inverse`) have NO MaterialComponents equivalent — use the `.Solid` variant instead, since `Theme.MaterialComponents.Light.DarkActionBar` already provides a dark ActionBar.
- **Multidex for large dependencies**: Adding Material Components library (~21000 methods) can push total DEX method count over the 65536 single-dex limit. For minSdk < 21 (no native multidex), must: (1) add `multiDexEnabled true` in `defaultConfig`, (2) add `implementation 'androidx.multidex:multidex:2.0.1'`, (3) override `attachBaseContext()` in Application class to call `MultiDex.install(this)`.
- **MaterialComponents widgets require explicit layout_width/layout_height**: When using MaterialComponents theme (`Theme.MaterialComponents`), all widgets including `ChipGroup`, `Chip`, etc. must have explicit `android:layout_width` and `android:layout_height` in XML. Unlike some AppCompat widgets that may inherit defaults, MaterialComponents views will crash with `UnsupportedOperationException: You must supply a layout_width attribute` during inflation if these attributes are missing. Always verify every view element in layout XML has both dimensions declared.
- **Never wrap Activity lifecycle in broad try-catch**: Wrapping `onCreate()` in `try { ... } catch (Exception e) { log(e); }` silently swallows initialization failures (including `setContentView()` errors). The Activity continues without views, and subsequent lifecycle methods (`onResume()`) crash with misleading NPEs on null views. The original error is invisible if logged at INFO level while user filters logcat for errors. Instead: let RuntimeExceptions propagate naturally, and add null checks in methods called from `onResume()` or broadcast receivers for defensive coding against configuration changes.
- **OrgNode.addLogbook must persist to DB**: `addLogbook()` was calling `setPayload()` which only updates the in-memory field, not the database. After clocking out, the CLOCK entry only existed in the OrgEdit table and was invisible in the node editor. Fix: call `write(resolver)` after `setPayload()` to persist the payload. This applies to any method that modifies `payload` via `setPayload()` — always follow with `write(resolver)` if the change should be visible locally, not just synced.
- **saveClock: endTime=now, startTime=now-duration**: When clocking out, `endTime` should be `System.currentTimeMillis()` (clock-out moment) and `startTime = endTime - duration`. This way editing the duration adjusts the start time backward, not the end time forward into the future. Previous approach of preserving the original clock-in time and computing `endTime = startTime + duration` pushed the end time into the future when duration was edited shortly after clock-in.
- **Do not use TimePickerDialog for duration input**: `android.app.TimePickerDialog` on some devices (confirmed on API 34 with MaterialComponents theme) returns initial values in `onTimeSet()` callback params AND `view.getHour()/getMinute()` — user's selection is completely ignored. This makes it impossible to edit clock duration. Fix: replaced with `AlertDialog` + two `NumberPicker` widgets for hours/minutes in `DurationPickerFragment`.
- **NumberPicker.getValue() must call clearFocus() first**: On some devices, `NumberPicker` does not commit the scrolled position to its internal value until focus is cleared. Without `clearFocus()`, `getValue()` returns the initial `setValue()` value, ignoring user input. Always call `picker.clearFocus()` before `picker.getValue()`.
- **Sync does not remove remote-deleted files**: `Synchronizer.pull()` only compared remote checksums to find files needing download, but never checked for local files absent from the remote index. Files removed from server's `index.org` persisted indefinitely in the local DB. Fix: added `removeRemoteDeletedFiles()` that compares local DB files against remote `filenameMap` and removes orphans (excluding `CAPTURE_FILE` and `AGENDA_FILE`). Also moved index.org parsing before the `changedFiles.size() == 0` early return, since file deletions alone produce no changed files but still need cleanup.
- **AlarmManager BroadcastReceiver must be exported="true"**: AlarmManager delivers PendingIntent broadcasts through the system process (uid 2000), which is a different UID than the app. With `android:exported="false"`, Android rejects the broadcast with `Permission Denial: not exported from uid`. All static BroadcastReceivers triggered by AlarmManager must use `exported="true"`, even if the broadcast action is app-private.
- **Calendar permission needs runtime check in Settings**: `CalendarWrapper.getCalendars()` queries CalendarProvider, which requires `READ_CALENDAR` runtime permission. The call from `SettingsActivity.populateCalendarNames()` was throwing `SecurityException` silently, showing "日历不存在". Fix: check permission before querying and request via `ActivityCompat.requestPermissions()` if not granted, retry in `onRequestPermissionsResult()` callback.
- **Theme change not applied on resume**: `setTheme()` must be called before `setContentView()` (Android framework requirement), so it only works in `onCreate()`. When returning from Settings after changing the theme, only `onResume()` fires — the Activity keeps the old theme. Fix: track the current theme name in a field, check in `onResume()` if it changed, and call `recreate()` to rebuild the Activity. This applies to any Activity that can be resumed after a theme change — in this app only `OutlineActivity` is affected, as all other activities are freshly created each time.
- **Theme system has two independent layers**: (1) **XML theme** (`themes.xml` + `OrgUtils.setTheme()`) controls system UI — ActionBar, preference screens, dialogs, text colors in standard widgets. (2) **Java theme** (`DefaultTheme`/`MonoTheme`/`WhiteTheme` in `Gui/Theme/`) controls custom outline rendering — list item colors, TODO states, level indentation, tags. Both layers must be aligned. A new theme option must update BOTH `OrgUtils.setTheme()` (XML theme selection) and `DefaultTheme.getTheme()` (Java theme selection), AND define matching styles in `themes.xml` with the correct parent (dark vs light base). If only one layer is updated, you get inconsistent appearance across screens.
- **Preference options must have implementation branches**: `arrays.xml` defined "Monochrome" as a theme option, but `OrgUtils.setTheme()` had no branch for it — it fell through to the Light theme silently. When adding a `ListPreference` option, always verify the corresponding code handles every value in the entries array.

All guards use `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` pattern.

## Bug Fix Workflow

每次修复一个 bug 后，必须完成以下步骤：

1. **总结经验写入 CLAUDE.md** — 将根因和修复方法记录到 "Android Compatibility (Applied Fixes)" 部分
2. **检查同类问题** — 全局搜索相同模式（如 `grep -r "android:showAsAction"`），避免只修一处遗漏其他
3. **补充单元测试** — 为修复的场景编写测试，防止回归
4. **更新博文** — 将新坑补充到 `~/github/lujun9972.github.com/编程之旅/MobileOrg-Android-从API-17迁移到API-34的实战记录.org`

## Known Pitfalls

- **Singleton state**: DB, Parser, Synchronizer are singletons, not thread-safe. Sync thread accesses DB while UI reads it — potential race conditions.
- **RecyclerView + extra items**: `OutlineAdapter` adds 2 header items. Position-to-index must subtract `numExtraItems`.
- **`OrgNodeListActivity`**: No `onSaveInstanceState` — rotation can cause issues.
- **AndroidX**: Project uses AndroidX (`androidx.*` imports). Jetifier enabled for local JARs.
- **OrgRenderer XSS prevention**: When rendering user content to HTML, always call `htmlEncode()` BEFORE `applyInlineMarkup()`. The order encode→markup→links prevents injection while preserving org formatting.
- **OrgRenderer uses raw payload**: `OrgRenderer` calls `node.getPayload()` (raw) and handles cleaning internally via `preClean()`. Do NOT use `getCleanedPayload()` which strips `#+BEGIN_SRC` blocks needed for source code rendering.
- **OrgRenderer recursion depth**: `nodeToHTMLRecursive()` has `MAX_RECURSION_DEPTH = 50`. Deeply nested org files beyond 50 levels will be silently truncated.
- **PayloadFragment preview sync**: Before rendering, must call `node.setPayload(payload.get())` to sync edited content. Otherwise the preview shows stale data.
- **OrgData is an inner class**: `OrgData` is `OrgContract.OrgData`, not a standalone class. When importing from outside the `OrgData` package, use `import com.matburt.mobileorg.OrgData.OrgContract.OrgData`. The short `import ...OrgData.OrgData` only works within the `OrgData` package itself.
- **Cross-package static methods must be public**: Package-private (`static` without `public`) methods are invisible to classes in other packages. If a utility method in `util/` is called from `Services/`, it must be `public static`.
- **Sync config export/import must use SAF**: Direct `FileWriter` to `getExternalFilesDir()` is lost on uninstall, and `getExternalStoragePublicDirectory()` fails with EACCES on API 29+ (Scoped Storage). Use `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` (Storage Access Framework) so users pick the file location and config survives reinstalls.
- **Never uninstall app without checking unsynced data**: Local captures/edits exist only in the app-private SQLite database. `adb uninstall` permanently deletes them with no recovery. Always warn the user to sync or export data first.
- **Mass regex refactoring must be verified by CI before considering done**: Batch find-and-replace patterns can be too aggressive. The `== false` regex `(\b\S+) == false\b` captured `if(` prefix producing `!if(expr)` and `!while(expr)`. The `return true/false` pattern with non-greedy `(.+?)` matched unintended `if` bodies like `if (entry.get(name))` → `return entry.get(name))`. Always push a single commit and wait for CI — do not stack up multiple mechanical changes without verification.
- **Deleting a constant/field must grep entire repo including tests**: When removing a symbol from main source, search both `src/main/` and `src/androidTest/` for references. Tests often reference the same constants (`Synchronizer.CAPTURE_FILE`), and broken test imports cause CI failures that the build step alone won't catch.
- **Constructor params should use base types for testability**: `Synchronizer` constructor took `SynchronizerNotification` (concrete subclass), but test stubs extend `SynchronizerNotificationCompat` (base class). Using the base class as the param type allows test stubs to be passed in without type errors.
- **Never use `git add -A`**: It stages untracked files (`debug.sh`, `.superpowers/`, docs) that should not be committed. Always stage specific files with `git add <file>`, then use `git status` / `git diff --cached` to review before committing.

## Agent skills

### Issue tracker

Issues and PRDs live as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles use default names: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `CONTEXT.md` at repo root + `docs/adr/`. See `docs/agents/domain.md`.
