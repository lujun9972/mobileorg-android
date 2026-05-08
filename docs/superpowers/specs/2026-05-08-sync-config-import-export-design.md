# Sync Config Import/Export Design

## 目标

在设置页添加同步配置的导入导出功能，方便用户在换设备或重装时快速恢复同步设置。

## 范围

仅导出**同步相关的配置**（约 15 个 key）：

| 类别 | Keys |
|------|------|
| 通用 | `syncSource`, `doAutoSync`, `autoSyncInterval` |
| SSH | `scpHost`, `scpUser`, `scpPass`, `scpPath`, `scpPort`, `scpPubFile` |
| WebDAV | `webUrl`, `webUser`, `webPass` |
| SD Card | `indexFilePath` |
| Dropbox | `dropboxPath` |
| Auto Sync | `syncWifiOnly` |

密码等敏感信息明文存储在 JSON 中（与当前 SharedPreferences 行为一致，不额外加密）。

## 文件格式

JSON，导出到 `/sdcard/mobileorg_sync_config.json`。

```json
{
  "version": 1,
  "syncSource": "scp",
  "scpHost": "example.com",
  "scpUser": "user",
  "scpPass": "password",
  "scpPath": "/path/to/index.org",
  "scpPort": "22",
  "doAutoSync": true,
  "autoSyncInterval": "1800000"
}
```

- 只包含有值的 key，空值不导出
- `version` 字段用于将来格式升级时兼容

## 导入行为

1. 读取 `/sdcard/mobileorg_sync_config.json`
2. 校验 `version` 字段（当前只支持 version 1）
3. 将所有 key 写入 SharedPreferences
4. 用 Toast 提示导入成功/失败

## UI 入口

在 `preferences.xml` 的同步配置区域（`SynchronizerPreferences` category）内添加两个 Preference：
- **导出同步配置** → 点击导出到文件，Toast 提示文件路径
- **导入同步配置** → 点击从文件导入，Toast 提示结果

使用标准 `Preference` + `OnPreferenceClickListener`，无需新 Activity。

## 错误处理

- 导出失败（无法写文件）：Toast 提示错误
- 导入失败（文件不存在/格式错误/version 不匹配）：Toast 提示具体原因
- 导入成功后建议用户重新进入设置页确认

## 不做的事

- 不加密密码（保持和现有行为一致）
- 不支持选择性导入
- 不做云同步
- 不自动备份
