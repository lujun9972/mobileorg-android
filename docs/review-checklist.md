# Review Checklist — bug 修复验收清单

**触发时机**：每个 `fix(...)` commit 完成后（review 阶段执行，不由实现 agent 边写边背）。全部四步都通过才算修复完成。

## 1. 坑记录

`docs/pitfalls.md` 对应分类节已有这条坑？

- 没有 → 按条目格式补一条：**规则** → 一句根因 → 指针；同步更新顶部速查表该场景的必查项。
- 完成判据：pitfalls.md 中能找到本条规则，且速查表覆盖其触发场景。

## 2. 同类检查

全局搜索相同模式，避免只修一处。常用 grep：

```bash
grep -rn "android:showAsAction" MobileOrg/src/main/res/menu/     # 应全部为 app:
grep -rn "LIKE'\" *%" MobileOrg/src/main/java --include="*.java"  # 未转义/未守卫的 LIKE 拼接
grep -rn "getAction().equals" MobileOrg/src/main/java             # 常量应在左
grep -rn "new NotificationCompat.Builder(context)" MobileOrg/src/main/java  # 缺 CHANNEL_ID
grep -rn "checkCallingOrSelfPermission" MobileOrg/src/main/java   # 静默权限检查
grep -rn "new Notification(" MobileOrg/src/main/java              # 应为 NotificationCompat
```

（新坑加入对应模式的 grep，见 pitfalls.md。）完成判据：列出搜索结果并逐条判定"需修/已修/不适用"，无未判定项。

## 3. 回归测试

按性质分流：

- **纯逻辑修复**（SQL 构造、解析、状态机、字符串处理）→ `MobileOrg/src/test/` JVM 测试（如 `PomodoroTimerTest`），无需设备。
- **UI / 设备 / 权限行为** → `MobileOrg/src/androidTest/`（注意 pitfalls.md §8 的测试坑）。
- 完成判据：存在一个能在修复前代码上**失败**、修复后代码上通过的测试（手推亦可：说明断言为何捕获旧 bug）。

## 4. 博文更新

新坑追加到 `~/github/lujun9972.github.com/编程之旅/MobileOrg-Android-从API-17迁移到API-34的实战记录.org`（跨仓库，路径固定）。完成判据：博文含本坑的"症状 → 根因 → 修复"段落；纯重构/无新坑的修复注明"无新坑"跳过。

## 附：结构性审计（review 大改动时）

- [ ] 长按菜单改动覆盖 4 套 XML？
- [ ] 主题改动同步了两层（`OrgUtils.setTheme()` + `Gui/Theme/` + `themes.xml`）？
- [ ] `arrays.xml` 每个 entry 有代码分支？
- [ ] 新的危险权限同时改了 manifest + 运行时检查 + 用户反馈？
- [ ] 新增 `notify()` 有渠道 + CHANNEL_ID + IMMUTABLE PendingIntent？
- [ ] 删除的常量/方法在 `src/androidTest/` 无引用？
