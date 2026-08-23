# 编辑窗口 checkbox 点击 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** EditActivity 预览 WebView 中的 checkbox 可点击切换；toggle 作用于内存工作副本并经 `saveEdits()` 整体落库（单一真相源）。

**架构：** PayloadFragment 覆写 ViewFragment 的 `handleCheckboxToggle`，改走工作副本路径（toggle → saveEdits → 刷新预览）；渲染时用真实 node.id 使链接可生成（id=-1 时自然降级纯符号）。ViewActivity 的 DB 直写路径保持不变。

**技术栈：** 既有 OrgRenderer 链接渲染、OrgUtils.toggleCheckboxLine/refreshCookies 纯函数（均已实现并有测试）、EditActivity.saveEdits 落库流。

---

### 任务 1：PayloadFragment 工作副本点击路径

**依赖：** 无
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/EditHost.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java`, `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/PayloadFragment.java`
**导出/变更接口：** `EditHost.java::saveEdits`, `ViewFragment.java::handleCheckboxToggle`
**消费接口：** `OrgUtils.java::toggleCheckboxLine`, `OrgUtils.java::refreshCookies`, `EditActivity.java::saveEdits`
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/EditHost.java`（加一行接口方法）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java:186`（private → protected）
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/PayloadFragment.java`（switchToView + 新 override）

- [ ] **步骤 1：EditHost 接口加 saveEdits 声明**

```java
public interface EditHost {
	public EditActivityController getController();
	public void saveEdits();
}
```

EditActivity 已有 `public void saveEdits()`（EditActivity.java:114），无需改动即满足接口。

- [ ] **步骤 2：ViewFragment.handleCheckboxToggle 改 protected**

`private void handleCheckboxToggle(String ref)` → `protected void handleCheckboxToggle(String ref)`，方法体不动（DB 路径保留给 ViewActivity）。

- [ ] **步骤 3：PayloadFragment 实现工作副本路径**

switchToView 删除 `previewNode.id = -1;` 一行（真实 id 渲染可点击链接；Create 模式新节点 id=-1 时 render() 已有 `nodeId >= 0` 守卫自然降级纯符号）。

新增 override（紧跟 switchToView 之后）：

```java
@Override
protected void handleCheckboxToggle(String ref) {
	try {
		String[] parts = ref.split(":");
		int rawLine = Integer.parseInt(parts[1]);
		setPayload(OrgUtils.refreshCookies(
				OrgUtils.toggleCheckboxLine(payload.get(), rawLine)));
		((EditHost) getActivity()).saveEdits();
		switchToView();
	} catch (Exception e) {
		Toast.makeText(getActivity(), R.string.node_not_found, Toast.LENGTH_SHORT).show();
	}
}
```

要点：
- `payload.get()` 是工作副本（含所有未保存修改），toggle/setPayload 只动它
- `saveEdits()` 经 EditActivity 收集全部 fragment 当前值整体落库，不 finish
- 落库后重新 switchToView 刷新预览；工作副本=DB，返回时 hasEdits()=false 不再弹放弃提示
- 需要 import：`com.matburt.mobileorg.util.OrgUtils`、`android.widget.Toast`、`com.matburt.mobileorg.R`（按编译缺失补）

- [ ] **步骤 4：Commit + CI 验证**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/EditHost.java \
        MobileOrg/src/main/java/com/matburt/mobileorg/Gui/ViewFragment.java \
        MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Capture/PayloadFragment.java
git commit -m "feat: toggle checkbox from EditActivity preview via working copy + saveEdits"
git push origin checkbox-toggle-editor
gh workflow run "Instrumentation Tests" --ref checkbox-toggle-editor --repo lujun9972/mobileorg-android
gh workflow run "Build APK" --ref checkbox-toggle-editor --repo lujun9972/mobileorg-android
```

预期：双 CI 绿（既有 95 测试不回归；无新增可自动化测试——纯函数已覆盖，接线层手测）。

### 任务 2：设备手测

**依赖：** 任务 1
**文件集：** 无
**导出/变更接口：** 无
**消费接口：** 无
**复杂度：** quick

- [ ] **步骤 1：装 APK**（CI 绿后 `gh run download` + `adb install -r`）
- [ ] **步骤 2：手测清单**（设备操作，逐步给用户指引）

1. 默认配置（viewOnClick=false）下 outline 点击含 checkbox 的叶子节点 → 进入 EditActivity 预览 → checkbox 显示为可点击的 ☐/☑
2. 点击 ☐ → 变 ☑，cookie 刷新，预览整体刷新
3. **未保存修改连带落库**：点铅笔进编辑 → 改一行文本 → 点屏内 ✓ → 预览点 checkbox → 按返回 → 不弹"放弃修改？"（已落库）→ 重新进入节点，文本修改与勾选都在
4. **undo**：预览点选后回主界面 ⋮ → "撤销：编辑内容" → 恢复
5. capture 场景：在 Capture 新建（Create 模式）预览为纯符号不可点（id=-1 降级）
6. ViewActivity 路径回归：长按节点 → 查看 → 点击仍正常

- [ ] **步骤 3：收尾**（finishing-a-development-branch）

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
