# 番茄钟记录同步设计

## 背景

番茄钟记录（`pomodoro_sessions` 表）只存在于单台设备的本地 SQLite。用户在**多台设备**上使用番茄钟，各设备的统计互相不可见；卸载重装后统计历史归零。计时（Clock）功能的时间记录已随节点 payload 同步，番茄钟是唯一不同步的本地记录。

## 目标

1. 每次同步时把番茄记录投影到远端 `pomodoros.org`（Emacs 侧可 clocktable 聚合）。
2. 多设备收敛：每台设备同步后拥有**所有设备**记录的并集，统计页显示总和。
3. 重装恢复：union 合并使任何设备的首次同步自动拉回全部历史，无需独立恢复路径。

## 非目标

- Emacs 侧消费集成（clocktable/agenda/starter.sh 改动）——用户自行发挥
- 删除传播（只增不删，见决策）
- 轮次元数据（连续模式第 N/M 轮不入库不导出）
- 进行中会话状态同步（单设备功能）
- Clock 条目（已随 payload 走 OrgEdit 通道，与本设计无关）

## 核心决策（grill 定案，细节见各节）

| 决策点 | 定案 |
|---|---|
| 协议接入 | 协议外魔法文件（ADR 0005）：绕过 index.org 白名单，不进大纲，不注册 Emacs `org-mobile-files` |
| 合并 | pull→merge→push，按 `started_at` union，各设备收敛于全设备并集 |
| 删除 | 只增不删；本地 DB（合并视图）= source of truth |
| CLOCK 行 | 名义时段（start → start+duration）；`completed_at` 存 property |
| properties | `STARTED_AT` / `COMPLETED_AT` / `DURATION` / `NODE` / `DEVICE` |
| 节点引用 | olp 历史快照，永不回写改 |
| 文件权属 | app 全权管理，文件头注释声明勿手工编辑 |
| 触发 | 随常规同步，pull 阶段 merge、push 阶段投影，无独立调度 |

## 设计

### 1. 文件格式 `pomodoros.org`

```org
# 由 MobileOrg 自动生成与管理，请勿手工编辑（Emacs 侧只读消费）

* 2026-11-16 10:00 番茄 25min
:LOGBOOK:
CLOCK: [2026-11-16 四 10:00]--[2026-11-16 四 10:25] =>  0:25
:END:
:PROPERTIES:
:STARTED_AT: 1763258400000
:COMPLETED_AT: 1763260500000
:DURATION: 25
:NODE: olp:home.org:工作/MobileOrg 开发
:DEVICE: 红米Note11
:END:
```

- headline 标题：`yyyy-MM-dd HH:mm 番茄 <duration>min`（人类可读摘要；解析**不依赖**标题，只信 properties）
- CLOCK 行：名义时段，本地时区 org 格式（Emacs clocktable 直接聚合）；`=> 0:25` 合计串照 org 惯例写名义时长
- `STARTED_AT`/`COMPLETED_AT`：epoch millis——解析唯一可信来源，时区安全
- `NODE`：olp 历史快照，可缺省（从通知栏/统计入口启动的番茄无节点上下文）
- `DEVICE`：`Settings.Global "device_name"`，取不到 fallback `Build.MODEL`
- 头部 `#` 注释行与所有非 headline 内容：parser 忽略

### 2. 数据层变更

DB 版本 7 → 8（`OrgDatabase.DATABASE_VERSION`），`onCreate()` 与 `onUpgrade()` 两处同步（沿用 case 模式显式加分支）：

```sql
ALTER TABLE pomodoro_sessions ADD COLUMN node_ref TEXT;
ALTER TABLE pomodoro_sessions ADD COLUMN device TEXT;
```

- 不加 UNIQUE 索引做幂等——迁移对存量数据脆弱；判重由 merge 逻辑负责（`SELECT 1 ... WHERE started_at = ?`，存在即跳过）。两设备同一毫秒启动番茄的概率为零。
- `recordPomodoroSession()` 签名扩为 `(startedAt, durationMin, nodeRef, device)`，现有唯一写入点 `TimeclockService.handlePomodoroFinish()` 补传新值。

### 3. 启动链路补节点归属

现状：`ACTION_POMODORO_START` Intent 只带 duration/count（`OutlineTimeclockController`），node 不入链路。

- `OutlineTimeclockController` 启动番茄时附 `POMODORO_NODE_ID` extra（长按菜单上下文里有 node id）
- `TimeclockService` 收到后暂存于内存字段；`handlePomodoroFinish()` 落库时换算为 olp（`OrgNodeRepository` 既有路径，与 `getOlpId()` 同源）写入 `node_ref`
- 通知栏/统计入口等无节点上下文的启动路径：extra 缺省 → `node_ref` 为 NULL → `:NODE:` property 省略
- olp 是**落库时刻**的快照，此后节点移动/改名不回写（历史快照语义）

### 4. 同步集成

挂载点：`Synchronizer.runSynchronizer()` = `pull(parser)` + `pushCaptures()`，追加第三步 `syncPomodoroLog()`（`pushCaptures()` 之后），**独立 try-catch**——失败 `Log.w` 不阻断、不影响主同步的错误通知。

```
syncPomodoroLog():
  merge:  syncher.getRemoteFile("pomodoros.org")
          ├─ 不存在 / {"error": 开头 → 视为空（首次 push 前的常态）
          └─ 否则 PomodoroLogParser 解析出 session 行
             → 逐条判重（started_at 已存在则跳过）→ INSERT
  push:   查 pomodoro_sessions 全表（_id 无关序，按 started_at 升序）
          → PomodoroLogSerializer 生成全文
          → syncher.putRemoteFile("pomodoros.org", text)   ← 覆写语义
```

- **SDCard 契约修复**：`SDCardSynchronizer.putRemoteFile` 用 `FileWriter(file, true)` append，违反接口 javadoc 的 "Replaces the file" 契约；且 `Synchronizer.pushCaptures()` 已前置拼接远端内容再上传，append 实际造成 SDCard 模式 mobileorg.org 内容重复膨胀（既有 bug）。修复 = 去掉 append 标志恢复覆写契约，pomodoros.org 的覆写需求随之满足，不做文件名分支。
- merge 与 push 同步内先后紧邻执行：A 设备 push 的记录，B 设备下一次同步 merge 进本地并随自身 push 扩写——并集收敛。
- 不经 `OrgFileParser`、不进 `files` 表、不产生 checksum 比对（ADR 0005）。

### 5. `PomodoroLogParser` 容错

- 逐 headline 取 properties drawer；`STARTED_AT` 非法数字 / 缺失 → 跳过该条并 `Log.w`（跳单条不弃全文件）
- `DURATION` 非法 → 用 STARTED_AT/COMPLETED_AT 差值分钟数兜底；仍无效则跳过
- `COMPLETED_AT` 缺失 → 用 STARTED_AT + DURATION 推算
- 未知 property / 额外字段：忽略（向前兼容）
- 纯 JVM 可测（输入 String → 输出 session 行列表），落 `src/test/`

### 6. 与既有系统的边界

- **统计功能**：零改动。merge 行回填原 `completed_at`，按 `completed_at` 分组的既有口径跨设备/重装自然一致。
- **Undo**：pomodoro 不产生 OrgEdit，不进 edit 批次，与撤销无关。
- **`removeRemoteDeletedFiles()`**：pomodoros.org 不在 files 表 → 该清理逻辑天然不触碰它。
- **Emacs 端部署**：不要把 `pomodoros.org` 加进 `org-mobile-files`（ADR 0005）；无其他部署要求。

### 7. 验收场景（手测）

1. 设备 A 完成 2 个番茄、设备 B 完成 1 个 → 双方各同步一次 → 两边统计页都显示 3 个
2. 设备 A 重装（数据清零）→ 同步一次 → 统计页恢复全部历史
3. 从节点长按启动的番茄 → `pomodoros.org` 中该条含 `:NODE:`；通知栏启动的不含
4. Emacs 侧 `org-clocktable` 对 `pomodoros.org` 出日报表，时长合计与 app 统计一致
5. 手工破坏文件中一条记录的 `STARTED_AT` → 同步不崩，坏行跳过，其余行正常合并
