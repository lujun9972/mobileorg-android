# 日历权限对话框重复弹出

Status: fixed (2026-08-28, SettingsCalendarPermissionTest 4 用例守护)

修复：SettingsActivity.populateCalendarNames() 三重守卫——
1. `calendarEnabled=false` 直接 return（未启用日历同步绝不申请）
2. 已申请过（`calendarPermissionPromptShown=true`）直接 return（拒绝后不再自动重弹）
3. 权限已授予时清除 `calendarPermissionPromptShown` 标志（日后撤销权限可再次申请）

## 现象

每次 Activity 重建（切换语言 recreate、进出设置页、向导流程）都会弹出一次
`READ_CALENDAR` 运行时权限对话框。用户拒绝后下一次重建仍会再弹，频率明显偏高。

发现场景：2026-08-27 双语界面真机复验（MI PAD 4 / API 27）——设置页每次切换语言
recreate 都重新申请；向导流程中反复出现。复验 agent 需连续拒绝多次才能走完流程。

## 复现步骤

1. 设备未授予日历权限，冷启动 app
2. 设置 → 语言 → 切换任意语言（触发 Activity recreate）
3. 每次切换都弹一次日历权限对话框；拒绝后再次切换仍弹

## 根因线索

`SettingsActivity` 的日历权限检查逻辑（CLAUDE.md 已有坑记录：设置中的日历权限
需要运行时检查——`populateCalendarNames()` 查询 CalendarProvider 前检查权限，
未授权则 `ActivityCompat.requestPermissions()`）。该检查位于 Activity
创建/resume 路径上，无「用户已拒绝」记忆，导致每次重建都重新申请。

## 期望行为

- 用户明确拒绝后不再自动重弹（系统本身在二次拒绝后会静默，但首次拒绝每次重建仍弹）
- 仅在用户实际进入需要日历的功能（日历同步设置页）时才申请，而非设置主页每次重建都申请
- 参考 `shouldShowRequestPermissionRationale()` 或「拒绝过则置灰日历项」模式

## 影响

低（功能不受影响，纯 UX 打扰），但与双语切换场景叠加后弹窗频率被放大
（每次切语言 recreate 一次就弹一次）。
