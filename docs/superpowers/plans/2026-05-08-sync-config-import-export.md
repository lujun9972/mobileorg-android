# Sync Config Import/Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在设置页添加同步配置的导入导出功能，用户可导出 JSON 文件备份同步设置，或从 JSON 文件恢复。

**Architecture:** 新建一个 `SyncConfigHelper` 工具类负责 JSON 序列化/反序列化和文件 I/O。在 `SettingsActivity` 中注册两个 Preference 的点击监听器，调用该工具类完成导入导出。UI 入口添加到 `preferences.xml` 的 `preference_actions` 区域。

**Tech Stack:** Android SharedPreferences, org.json（SDK 内置）, java.io.File

---

### Task 1: 添加字符串资源

**Files:**
- Modify: `MobileOrg/src/main/res/values/strings.xml`

- [ ] **Step 1: 在 strings.xml 末尾（`<!-- others -->` 之前）添加导入导出相关字符串**

```xml
    <!-- sync config import/export -->
    <string name="preference_export_sync_config">导出同步配置</string>
    <string name="preference_export_sync_config_summary">将同步设置导出到文件</string>
    <string name="preference_import_sync_config">导入同步配置</string>
    <string name="preference_import_sync_config_summary">从文件恢复同步设置</string>
    <string name="sync_config_export_success">同步配置已导出到:\n%s</string>
    <string name="sync_config_export_failed">导出失败:\n%s</string>
    <string name="sync_config_import_success">同步配置导入成功，请重新进入设置页确认</string>
    <string name="sync_config_import_failed">导入失败:\n%s</string>
    <string name="sync_config_no_data">没有可导出的同步配置</string>
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/main/res/values/strings.xml
git commit -m "feat: add string resources for sync config import/export"
```

---

### Task 2: 在 preferences.xml 中添加导入导出按钮

**Files:**
- Modify: `MobileOrg/src/main/res/xml/preferences.xml`

- [ ] **Step 1: 在 `preference_actions` category（第 199–206 行）的 `clearDB` Preference 之后，添加两个 Preference 项**

在 `clearDB` Preference（第 203–205 行）之后、`</PreferenceCategory>` 之前插入：

```xml
        <Preference
            android:key="exportSyncConfig"
            android:summary="@string/preference_export_sync_config_summary"
            android:title="@string/preference_export_sync_config" />
        <Preference
            android:key="importSyncConfig"
            android:summary="@string/preference_import_sync_config_summary"
            android:title="@string/preference_import_sync_config" />
```

完成后 `preference_actions` 区域应为：

```xml
    <PreferenceCategory android:title="@string/preference_actions" >
        <Preference android:title="@string/preference_setup_wizard" >
            <intent android:action="com.matburt.mobileorg.Settings.SETUP_WIZARD" />
        </Preference>
        <Preference
            android:key="clearDB"
            android:title="@string/preference_clear_db" />
        <Preference
            android:key="exportSyncConfig"
            android:summary="@string/preference_export_sync_config_summary"
            android:title="@string/preference_export_sync_config" />
        <Preference
            android:key="importSyncConfig"
            android:summary="@string/preference_import_sync_config_summary"
            android:title="@string/preference_import_sync_config" />
    </PreferenceCategory>
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/main/res/xml/preferences.xml
git commit -m "feat: add import/export sync config preference items"
```

---

### Task 3: 创建 SyncConfigHelper 工具类

**Files:**
- Create: `MobileOrg/src/main/java/com/matburt/mobileorg/util/SyncConfigHelper.java`

- [ ] **Step 1: 创建 SyncConfigHelper.java**

```java
package com.matburt.mobileorg.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SyncConfigHelper {
    private static final String TAG = "MobileOrg";
    private static final String EXPORT_FILE = "mobileorg_sync_config.json";
    private static final int FORMAT_VERSION = 1;

    private static final Set<String> SYNC_KEYS = new HashSet<String>(Arrays.asList(
            // General sync
            "syncSource",
            "doAutoSync",
            "autoSyncInterval",
            "syncWifiOnly",
            // SSH
            "scpHost",
            "scpUser",
            "scpPass",
            "scpPath",
            "scpPort",
            "scpPubFile",
            // WebDAV
            "webUrl",
            "webUser",
            "webPass",
            // SD Card
            "indexFilePath",
            // Dropbox
            "dropboxPath"
    ));

    public static File getExportFile() {
        File dir = android.os.Environment.getExternalStorageDirectory();
        return new File(dir, EXPORT_FILE);
    }

    /**
     * Export sync config to JSON file on external storage.
     * @return null on success, error message on failure.
     */
    public static String exportConfig(Context context) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());

        JSONObject json = new JSONObject();
        try {
            json.put("version", FORMAT_VERSION);

            boolean hasData = false;
            for (String key : SYNC_KEYS) {
                // doAutoSync is boolean
                if ("doAutoSync".equals(key)) {
                    boolean value = prefs.getBoolean(key, false);
                    if (value) {
                        json.put(key, value);
                        hasData = true;
                    }
                } else {
                    String value = prefs.getString(key, "");
                    if (value != null && !value.equals("")) {
                        json.put(key, value);
                        hasData = true;
                    }
                }
            }

            if (!hasData) {
                return context.getString(R.string.sync_config_no_data);
            }

            File file = getExportFile();
            FileWriter writer = new FileWriter(file);
            writer.write(json.toString(2));
            writer.flush();
            writer.close();

            Log.i(TAG, "Sync config exported to " + file.getAbsolutePath());
            return null; // success
        } catch (JSONException e) {
            Log.e(TAG, "JSON error exporting config", e);
            return "JSON error: " + e.getMessage();
        } catch (IOException e) {
            Log.e(TAG, "IO error exporting config", e);
            return "IO error: " + e.getMessage();
        }
    }

    /**
     * Import sync config from JSON file on external storage.
     * @return null on success, error message on failure.
     */
    public static String importConfig(Context context) {
        File file = getExportFile();
        if (!file.exists()) {
            return "文件不存在: " + file.getAbsolutePath();
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());

            // Validate version
            if (!json.has("version")) {
                return "无效的配置文件：缺少 version 字段";
            }
            int version = json.getInt("version");
            if (version != FORMAT_VERSION) {
                return "不支持的配置版本: " + version + "，当前仅支持版本 " + FORMAT_VERSION;
            }

            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context.getApplicationContext());
            SharedPreferences.Editor editor = prefs.edit();

            int importedCount = 0;
            for (String key : SYNC_KEYS) {
                if (!json.has(key)) continue;

                if ("doAutoSync".equals(key)) {
                    editor.putBoolean(key, json.getBoolean(key));
                } else {
                    editor.putString(key, json.getString(key));
                }
                importedCount++;
            }

            editor.apply();

            Log.i(TAG, "Sync config imported: " + importedCount + " keys from " + file.getAbsolutePath());
            return null; // success
        } catch (JSONException e) {
            Log.e(TAG, "JSON error importing config", e);
            return "JSON 解析错误: " + e.getMessage();
        } catch (IOException e) {
            Log.e(TAG, "IO error importing config", e);
            return "IO 错误: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/util/SyncConfigHelper.java
git commit -m "feat: add SyncConfigHelper for JSON import/export"
```

---

### Task 4: 在 SettingsActivity 中注册点击监听器

**Files:**
- Modify: `MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java`

- [ ] **Step 1: 添加 import 语句**

在现有 import 区域添加：

```java
import android.widget.Toast;
import com.matburt.mobileorg.util.SyncConfigHelper;
```

- [ ] **Step 2: 在 `onCreate` 方法中注册监听器**

在 `findPreference("clearDB").setOnPreferenceClickListener(onClearDBClick);`（第 59 行）之后添加：

```java
			findPreference("exportSyncConfig").setOnPreferenceClickListener(onExportSyncConfigClick);
			findPreference("importSyncConfig").setOnPreferenceClickListener(onImportSyncConfigClick);
```

- [ ] **Step 3: 在类的末尾（`onClearDBClick` 之后）添加两个监听器**

```java
		private Preference.OnPreferenceClickListener onExportSyncConfigClick = new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				String error = SyncConfigHelper.exportConfig(SettingsActivity.this);
				if (error != null) {
					Toast.makeText(SettingsActivity.this,
							getString(R.string.sync_config_export_failed, error),
							Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(SettingsActivity.this,
							getString(R.string.sync_config_export_success,
									SyncConfigHelper.getExportFile().getAbsolutePath()),
							Toast.LENGTH_LONG).show();
				}
				return false;
			}
		};

		private Preference.OnPreferenceClickListener onImportSyncConfigClick = new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				String error = SyncConfigHelper.importConfig(SettingsActivity.this);
				if (error != null) {
					Toast.makeText(SettingsActivity.this,
							getString(R.string.sync_config_import_failed, error),
							Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(SettingsActivity.this,
							R.string.sync_config_import_success,
							Toast.LENGTH_LONG).show();
				}
				return false;
			}
		};
```

- [ ] **Step 4: Commit**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Settings/SettingsActivity.java
git commit -m "feat: wire up import/export sync config click handlers"
```

---

### Task 5: 构建验证

- [ ] **Step 1: 执行 debug 构建**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 修复构建错误（如有）**

如果编译失败，根据错误信息修复并重新构建。

- [ ] **Step 3: 最终 Commit（如有修复）**

```bash
git add -A
git commit -m "fix: resolve build issues for sync config import/export"
```

---

## 自查清单

**1. Spec 覆盖度：**
- ✅ 导出 15 个同步配置 key → Task 3 SYNC_KEYS 集合
- ✅ JSON 格式含 version 字段 → Task 3 exportConfig()
- ✅ 空值不导出 → Task 3 exportConfig() 跳过空字符串
- ✅ 导出到 /sdcard/mobileorg_sync_config.json → Task 3 getExportFile()
- ✅ 导入校验 version → Task 3 importConfig()
- ✅ 导入写入 SharedPreferences → Task 3 importConfig()
- ✅ Toast 提示成功/失败 → Task 4 点击监听器
- ✅ 设置页按钮入口 → Task 2 preferences.xml
- ✅ 密码明文存储 → 与 spec 一致，不额外加密

**2. 占位符扫描：** 无 TBD/TODO，所有步骤包含完整代码。

**3. 类型一致性：**
- `exportConfig()` 返回 `String`（null=成功，非 null=错误信息）
- `importConfig()` 返回 `String`（同上）
- Task 4 中两个监听器均使用相同的返回值约定
- `getExportFile()` 返回 `File`，在监听器中通过 `getAbsolutePath()` 展示
