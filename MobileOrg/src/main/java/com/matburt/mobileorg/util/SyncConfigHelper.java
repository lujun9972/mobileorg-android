package com.matburt.mobileorg.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.matburt.mobileorg.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SyncConfigHelper {
    private static final String TAG = "MobileOrg";
    public static final String EXPORT_MIME = "application/json";
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

    public static final String DEFAULT_FILENAME = "mobileorg_sync_config.json";

    /**
     * Export sync config to a URI (obtained via SAF ACTION_CREATE_DOCUMENT).
     * @return null on success, error message on failure.
     */
    public static String exportConfig(Context context, Uri uri) {
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

            ContentResolver resolver = context.getContentResolver();
            OutputStream os = resolver.openOutputStream(uri);
            if (os == null) {
                return "无法打开输出流";
            }
            try {
                os.write(json.toString(2).getBytes("UTF-8"));
                os.flush();
            } finally {
                os.close();
            }

            Log.i(TAG, "Sync config exported to " + uri);
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
     * Import sync config from a URI (obtained via SAF ACTION_OPEN_DOCUMENT).
     * @return null on success, error message on failure.
     */
    public static String importConfig(Context context, Uri uri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            InputStream is = resolver.openInputStream(uri);
            if (is == null) {
                return "无法打开输入流";
            }

            String content;
            try {
                byte[] buffer = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int len;
                while ((len = is.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, len, "UTF-8"));
                }
                content = sb.toString();
            } finally {
                is.close();
            }

            JSONObject json = new JSONObject(content);

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

            Log.i(TAG, "Sync config imported: " + importedCount + " keys from " + uri);
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
