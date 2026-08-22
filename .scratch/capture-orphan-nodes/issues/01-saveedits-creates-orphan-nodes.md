# Capture 节点游离：EditActivityControllerCreate 不设 parentId/fileId

Status: needs-triage

## 现象

`EditActivityControllerCreate.saveEdits()` 只设 `newNode.level = 1` 就调
`repo.write(newNode)`。`OrgNode` 默认 `parentId=-1`、`fileId=-1`，因此每个
capture 节点都以游离行存入 OrgData 表：

- 出现在 outline 顶级列表（`getChildren(-1)` 命中），而不是 Captures 分组下
- `getFilename()` 返回 `""`（fileId 查不到 Files 行）
- `getOlpId()` 返回 `""`（顶级 + getOrgFile 抛 OrgFileNotFoundException）
- 上传逻辑按 `FILE_ID = capture file` 找 capture 节点，找不到它们

## 影响

1. 数据完整性：游离节点不属于任何文件，sync 拉取不会被清理，也不随
   captures 上传机制同步
2. 2026-08-22 事故的放大器：游离节点 `getNodeId() == ""` 是
   `updateAllNodes` LIKE '%%' 全表覆盖的前提条件（该 LIKE 已于当日修复，
   见 commit "fix: updateAllNodes full-table overwrite"）

## 建议修复

`saveEdits` 中将节点挂到 capture 文件节点下：

```java
OrgFile captureFile = fileRepo.getOrCreateCaptureFile();
newNode.fileId = captureFile.id;
newNode.parentId = captureFile.nodeId;
newNode.level = 1;
repo.write(newNode);
```

需同步验证：
- `OrgFileRepository.getChangesCount()` 对 capture 的计数逻辑（按
  `FILE_ID` 查）在修复后行为
- capture 上传（WebDAV/SSH push mobileorg.org）路径能找到挂载后的节点
- 已存在的游离节点迁移（可选：一次性把 fileId=-1 的行挂到 capture 文件）

## Comments
