# 向导页"从配置文件导入"按钮 实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在同步向导第 0 页（"同步我想使用:" RadioGroup 下方）加"从配置文件导入"按钮，通过 SAF 选择 `mobileorg_sync_config.json` 并调用既有 `SyncConfigHelper.importConfig()` 写入配置，成功 toast + finish，失败 toast 留在第 0 页。

**架构：** 纯 UI glue。复用 Settings 页已有的 SAF 导入链路（`ACTION_OPEN_DOCUMENT` → `onActivityResult` → `SyncConfigHelper.importConfig(context, uri)`，返回 null 成功 / 错误串失败）。不修改 `SyncConfigHelper`，不动 Settings 现有导入。成功后 `finish()` 返回 OutlineActivity，`onResume()` 不会重弹向导（`displayNewUserDialogs()` 只在 `onCreate()` 调用），且 `isSyncConfigured()` 已为 true。按钮用 `setOnClickListener` 在 Java 侧注册（非 XML `android:onClick`），保持资源 → 代码的单向依赖。

**技术栈：** Android Framework Activity（`android.app.Activity`，非 AppCompat）、SAF（`ACTION_OPEN_DOCUMENT`）、SharedPreferences。

**执行分支：** 基于最新 `main` 创建专用 worktree 分支 `wizard-import-config`（undo 分支尚未合入，本功能与其无文件交集）。

```bash
cd /home/lujun9972/github/mobileorg-android
git fetch origin main && git worktree add .claude/worktrees/wizard-import-config -b wizard-import-config origin/main
cd .claude/worktrees/wizard-import-config
```

**编码约束：**
- `WizardActivity.java` 使用 TAB 缩进
- 无新单元测试：本功能为纯 UI glue，全部持久化逻辑在既有 `SyncConfigHelper.importConfig()` 中；项目无 UI 测试框架。验证 = 编译 + CI 全量 instrumentation 回归 + 手测
- 本项目默认 `values/` 为英文、`values-zh/` 为中文。注意既有 `sync_config_*` 字符串只存在于默认 values 且为中文（历史遗留错位，本计划不修，复用时照常引用 `R.string.sync_config_import_failed`）

---

### 任务 1：字符串与布局按钮

**依赖：** 无
**文件集：** `MobileOrg/src/main/res/values/strings.xml`, `MobileOrg/src/main/res/values-zh/strings.xml`, `MobileOrg/src/main/res/layout/wizard_page1.xml`
**导出/变更接口：** `values/strings.xml::wizard_import_config_button`, `values/strings.xml::wizard_import_config_success`, `wizard_page1.xml::wizard_import_button`
**消费接口：** 无
**复杂度：** quick

**文件：**
- 修改：`MobileOrg/src/main/res/values/strings.xml`（:272 `sync_config_no_data` 行后）
- 修改：`MobileOrg/src/main/res/values-zh/strings.xml`（文件尾 `</resources>` 前）
- 修改：`MobileOrg/src/main/res/layout/wizard_page1.xml`（`</RadioGroup>` 之后、外层 `</LinearLayout>` 之前）

- [ ] **步骤 1：添加字符串**

`values/strings.xml` 在 `<string name="sync_config_no_data">没有可导出的同步配置</string>` 之后插入：

```xml
    <string name="wizard_import_config_button">Import from config file</string>
    <string name="wizard_import_config_success">Sync config imported</string>
```

`values-zh/strings.xml` 在 `</resources>` 之前插入：

```xml
  <string name="wizard_import_config_button">从配置文件导入</string>
  <string name="wizard_import_config_success">同步配置导入成功</string>
```

失败提示复用既有 `R.string.sync_config_import_failed`（格式串 `导入失败:\n%s`，取一个 String 参数）。

- [ ] **步骤 2：添加布局按钮**

第 0 页实际布局是 `wizard_page1.xml`（`wizard.xml` 通过 `<include layout="@layout/wizard_page1" />` 引入，非 `wizard.xml` 本身）。在 `</RadioGroup>`（:50）后、外层 `</LinearLayout>` 前插入：

```xml
  <Button
     android:id="@+id/wizard_import_button"
     android:layout_width="wrap_content"
     android:layout_height="wrap_content"
     android:layout_gravity="center_horizontal"
     android:layout_marginTop="20dp"
     android:textSize="18dp"
     android:text="@string/wizard_import_config_button" />
```

说明：外层 LinearLayout 为 vertical + `layout_gravity="center"`。

- [ ] **步骤 3：编译验证资源**

运行：`./gradlew :MobileOrg:processDebugResources`
预期：BUILD SUCCESSFUL（资源键拼写错误会在此步报错）

- [ ] **步骤 4：Commit**

```bash
git add MobileOrg/src/main/res/values/strings.xml MobileOrg/src/main/res/values-zh/strings.xml MobileOrg/src/main/res/layout/wizard_page1.xml
git commit -m "feat(wizard): add import-config button and strings to first wizard page"
```

### 任务 2：WizardActivity 接线

**依赖：** 任务 1
**文件集：** `MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Wizard/WizardActivity.java`
**导出/变更接口：** `WizardActivity.java::onImportConfigClicked`, `WizardActivity.java::REQUEST_IMPORT_CONFIG`
**消费接口：** `util/SyncConfigHelper.java::importConfig`, `util/SyncConfigHelper.java::EXPORT_MIME`, `values/strings.xml::wizard_import_config_success`, `values/strings.xml::sync_config_import_failed`, `wizard_page1.xml::wizard_import_button`
**复杂度：** standard

**文件：**
- 修改：`MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Wizard/WizardActivity.java`（TAB 缩进）

- [ ] **步骤 1：添加 import 与常量**

新增 import（与现有 import 区风格一致）：

```java
import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import com.matburt.mobileorg.util.SyncConfigHelper;
```

类字段区（`private int syncWebDav, syncSdCard, syncNull, syncSSH;` 之后）加：

```java
	private static final int REQUEST_IMPORT_CONFIG = 2003;
```

（SettingsActivity 用 2002；request code 为 Activity 私有命名空间，独立编号避免混淆。）

- [ ] **步骤 2：onCreate 注册点击监听**

在 `onCreate()` 末尾（`ViewTreeObserver` 注册之前或之后均可）添加：

```java
		findViewById(R.id.wizard_import_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						onImportConfigClicked();
					}
				});
```

- [ ] **步骤 3：添加点击方法**

在 `onCreate` 之后添加（SAF intent 写法对齐 `SettingsActivity.onImportSyncConfigClick` :360-369）：

```java
	private void onImportConfigClicked() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType(SyncConfigHelper.EXPORT_MIME);
		startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
	}
```

- [ ] **步骤 4：onActivityResult 添加分支**

在既有 `SSHWizard.SSH_CHOOSE_PUB` 分支后追加 `else if`（`data.getData()` 判空防用户返回无选择；`importConfig` 返回 null 即成功）：

```java
		} else if (requestCode == REQUEST_IMPORT_CONFIG) {
			if (resultCode == RESULT_OK && data != null && data.getData() != null) {
				Uri uri = data.getData();
				String error = SyncConfigHelper.importConfig(this, uri);
				if (error == null) {
					Toast.makeText(this, R.string.wizard_import_config_success,
							Toast.LENGTH_SHORT).show();
					finish();
				} else {
					Toast.makeText(this,
							getString(R.string.sync_config_import_failed, error),
							Toast.LENGTH_SHORT).show();
				}
			}
		}
```

- [ ] **步骤 5：编译验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 6：推送并触发 CI 全量回归**

```bash
git add MobileOrg/src/main/java/com/matburt/mobileorg/Gui/Wizard/WizardActivity.java
git commit -m "feat(wizard): import sync config from file on first wizard page"
git push origin wizard-import-config
gh workflow run build.yml --ref wizard-import-config
gh workflow run test.yml --ref wizard-import-config
```

用 `gh run watch` 或 `gh run list --branch wizard-import-config` 等待两 workflow 均成功。

- [ ] **步骤 7：真机手测**

设备无线调试（IP 见主 repo CLAUDE.md，端口每次会话变化需现取）。安装 release APK：

```bash
./gradlew assembleRelease
adb connect <ip:port>
adb install -r MobileOrg/build/outputs/apk/release/MobileOrg-release.apk
```

手测清单：
1. `adb shell pm clear com.matburt.mobileorg` 清数据（或卸载重装，注意先确认无未同步数据）→ 启动 app → 向导第 0 页出现"从配置文件导入"按钮
2. 点击按钮 → 系统 SAF 选择器弹出
3. 选择先前导出的 `mobileorg_sync_config.json` → toast"同步配置导入成功" → 向导自动关闭回到 Outline（不再重弹向导）
4. 设置 → 同步设置页各字段（URL/用户名/syncSource 等）与配置文件一致
5. 选择一个损坏/非 JSON 文件 → toast"导入失败: <错误>" → 停留在第 0 页可继续手动配置
6. SAF 中按返回键取消 → 无 toast，停留第 0 页

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2（依赖 1）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真
