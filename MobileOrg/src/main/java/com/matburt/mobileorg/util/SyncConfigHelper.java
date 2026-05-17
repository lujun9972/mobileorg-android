package com.matburt.mobileorg.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.matburt.mobileorg.R;

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

    private static final Set<String> BOOLEAN_KEYS = new HashSet<String>(Arrays.asList(
            "doAutoSync",
            "syncWifiOnly"
    ));

    private static final Set<String> SYNC_KEYS = new HashSet<String>(Arrays.asList(
            "syncSource",
            "doAutoSync",
            "autoSyncInterval",
            "syncWifiOnly",
            "scpHost",
            "scpUser",
            "scpPass",
            "scpPath",
            "scpPort",
            "scpPubFile",
            "webUrl",
            "webUser",
            "webPass",
            "indexFilePath",
            "dropboxPath"
    ));

    public static File getExportFile(Context context) {
        // Use public Downloads directory so the file survives app uninstall
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(dir, EXPORT_FILE);
    }

    /**
     * Export sync config to JSON file.
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
                if (BOOLEAN_KEYS.contains(key)) {
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

            File file = getExportFile(context);
            FileWriter writer = new FileWriter(file);
            writer.write(json.toString(2));
            writer.flush();
            writer.close();

            Log.i(TAG, "Sync config exported to " + file.getAbsolutePath());
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "JSON error exporting config", e);
            return "JSON error: " + e.getMessage();
        } catch (IOException e) {
            Log.e(TAG, "IO error exporting config", e);
            return "IO error: " + e.getMessage();
        }
    }

    /**
     * Import sync config from JSON file.
     * @return null on success, error message on failure.
     */
    public static String importConfig(Context context) {
        File file = getExportFile(context);
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

                if (BOOLEAN_KEYS.contains(key)) {
                    editor.putBoolean(key, json.getBoolean(key));
                } else {
                    editor.putString(key, json.getString(key));
                }
                importedCount++;
            }

            editor.apply();

            Log.i(TAG, "Sync config imported: " + importedCount + " keys from " + file.getAbsolutePath());
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "JSON error importing config", e);
            return "JSON 解析错误: " + e.getMessage();
        } catch (IOException e) {
            Log.e(TAG, "IO error importing config", e);
            return "IO 错误: " + e.getMessage();
        }
    }
}
