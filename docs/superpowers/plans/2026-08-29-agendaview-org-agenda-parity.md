# AgendaView 对齐 org-agenda 视图（org-mobile 原生协议）实现计划

> **面向 AI 代理的工作者：** 必需子技能：平台支持子代理且计划较大/可安全分 wave 时使用 superpowers:parallel-executing-plans；计划较小、任务强耦合或平台不支持子代理时使用 superpowers:serial-executing-plans。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 消除 PC 与云侧 org-agenda 视图定义漂移——建立 `agenda-views.el` 单一真相文件（10 个 custom views + NOAGENDA 全局 skip + 排序补强），云侧 `org-mobile-push` 按它生成完整 `agendas.org`，手机 Agenda Views 呈现与 PC `M-x org-agenda` 一致的 11 个视图，编辑经 `:ORIGINAL_ID:` 回流源文件。

**架构：** 新建 `~/github/mobileorg-sync/agenda-views.el` 作为视图定义唯一来源；`mobileorg-sync.el`（云侧 sync 脚本）删除自带缩水定义、改为 load 共享文件并 `(setq org-mobile-agendas 'all)`；PC 端 `init-GTD-org.org` 的内联定义同样替换为 load。Android 端零改动（`combineBlockAgendas()` 原生解析已验证可用）。

**技术栈：** Elisp（org 9.x 内置 org-mobile 协议）、bash 断言脚本、/tmp 沙箱端到端验证。

**跨仓库声明：** 本计划涉及 3 个 git 仓库，所有路径用绝对路径（`~` = `/home/lujun9972`）：
- `~/github/mobileorg-sync`（remote origin = GitHub）——任务 1、2、4
- `~/.spacemacs.d`——任务 3
- `~/github/mobileorg-android`——仅存放本计划与规格文档，无代码变更

**关键机制事实（实施前必读）：**
- `mobileorg-sync.el` 是 `emacs --quick --script` 脚本，**尾部两个分支都执行 `(mobileorg-sync)`**（有真实副作用：git pull/push）——绝不能用 `--batch -l` 整体 load 验证，只能用 `byte-compile-file` 编译验证
- `MY-GTD-PATH` 在 `mobileorg-sync.el:26` 定义为 `"~/我的GTD"`；agenda-views.el 中用 `(defvar MY-GTD-PATH "~/我的GTD")` 兜底（defvar 不覆盖已绑定值）
- `file-name-concat` 是 Emacs 28+ 函数，云侧 Emacs 版本未知——o 视图改用 `expand-file-name`（行为等价）
- `org-mobile-agendas 'all` = custom commands + 默认 `a`（Agenda 周视图）；默认 `t`（ALL TODO）仅在 custom 无 `t` 键时附加——本配置 custom 已含 `t`，故共 **11 个视图**
- `org/schedule-until` 断言 `n` 视图无未来 scheduled 条目；`my/org-skip-noagenda` 经 `org-agenda-skip-function-global` 排除 NOAGENDA tag 条目

---

## 文件结构

| 文件 | 操作 | 仓库 | 职责 |
|---|---|---|---|
| `~/github/mobileorg-sync/agenda-views.el` | 新建 | mobileorg-sync | 视图定义单一真相：10 custom commands + 2 个 skip 函数 + 全局 skip 注册 |
| `~/github/mobileorg-sync/mobileorg-sync.el` | 修改 | mobileorg-sync | 删自带定义，load 共享文件，设 `org-mobile-agendas 'all` |
| `~/.spacemacs.d/layers/my-GTD/init-GTD-org.org` | 修改 | spacemacs.d | 「配置agenda view」src block 三段内联定义替换为 load |
| `~/.spacemacs.d/layers/my-GTD/init-GTD-org.el` | 重新生成 | spacemacs.d | org-babel-tangle 产物 |
| `~/github/mobileorg-sync/tests/e2e-agendas.sh` | 新建 | mobileorg-sync | /tmp 沙箱端到端回归脚本（org-mobile-push → 断言 agendas.org） |

---

### 任务 1：创建 agenda-views.el 单一真相文件

**依赖：** 无
**文件集：** `~/github/mobileorg-sync/agenda-views.el`
**导出/变更接口：** `agenda-views.el::org/schedule-until`, `agenda-views.el::my/org-skip-noagenda`, `agenda-views.el::org-agenda-custom-commands`
**消费接口：** 无
**复杂度：** standard

**文件：**
- 创建：`~/github/mobileorg-sync/agenda-views.el`

- [ ] **步骤 1：编写失败的断言命令（RED）**

```bash
emacs --batch --eval '(progn
  (require (quote org-agenda))
  (load "~/github/mobileorg-sync/agenda-views.el")
  (let ((keys (mapcar (lambda (c) (car c)) org-agenda-custom-commands)))
    (unless (equal keys (list "n" "t" "T" "w" "s" "m" "o" "h" "r" " "))
      (error "keys mismatch: %s" keys)))
  (unless (fboundp (quote org/schedule-until)) (error "org/schedule-until missing"))
  (unless (fboundp (quote my/org-skip-noagenda)) (error "my/org-skip-noagenda missing"))
  (unless (memq (quote my/org-skip-noagenda) org-agenda-skip-function-global)
    (error "global skip not registered"))
  (dolist (k (list "n" "t" "T" "w" "o"))
    (let ((settings (nth 3 (assoc k org-agenda-custom-commands))))
      (unless (member (quote (org-agenda-sorting-strategy (scheduled-up priority-down))) settings)
        (error "view %s missing sorting" k))))
  (message "ALL ASSERTIONS PASSED"))'
```

运行预期：**FAIL**，报 `Cannot open load file: .../agenda-views.el`（文件不存在）。

- [ ] **步骤 2：创建 agenda-views.el 完整内容**

写入 `~/github/mobileorg-sync/agenda-views.el`（内容完整，无省略）：

```elisp
;;; agenda-views.el --- org-agenda 视图定义单一真相（PC spacemacs + 云 mobileorg-sync 共用）
;; 加载方：mobileorg-sync.el（云侧 sync）、init-GTD-org.el（PC spacemacs）
;; 仅依赖 org 内置，不依赖 spacemacs 环境
;;; Code:

(require 'org-agenda)

(defvar MY-GTD-PATH "~/我的GTD") ; 兜底定义：调用方已定义时不覆盖

;; 不要显示NOAGENDA tag的事项
(defun my/org-skip-noagenda ()
  "Skip tree if it has NOAGENDA tag."
  (save-excursion
    (org-back-to-heading)
    (if (member "NOAGENDA" (org-get-tags))
        (progn (outline-next-heading) (point))
      nil)))

(add-to-list 'org-agenda-skip-function-global #'my/org-skip-noagenda)

(defun org/schedule-until (&optional time)
  "List Agenda items that scheduled until TIME."
  (let* ((time (or time (format-time-string "%Y-%m-%d 23:59:59")))
         (next-headline (save-excursion (or (outline-next-heading) (point-max))))
         (subtree-end (save-excursion (org-end-of-subtree t)))
         (scheduled-time (org-get-scheduled-time (point)))
         (time (apply #'encode-time (org-parse-time-string time)))
         (subtree-valid (or (null scheduled-time) (time-less-p scheduled-time time))))
    (when (not subtree-valid)
      next-headline
      )))

(setq org-agenda-custom-commands
      '(("n" "Next" todo "NEXT"
         ((org-agenda-overriding-header "下一步行动")
          (org-tags-match-list-sublevels t)
          (org-agenda-skip-function 'org/schedule-until)
          (org-agenda-sorting-strategy '(scheduled-up priority-down))))
        ("t" "Today" todo "TODAY|PROG|WAITING"
         ((org-agenda-overriding-header "今日事项")
          (org-tags-match-list-sublevels t)
          (org-agenda-skip-function 'org/schedule-until)
          (org-agenda-sorting-strategy '(scheduled-up priority-down))))
        ("T" "Tomorrow" todo "TODAY"
         ((org-agenda-overriding-header "明日事项")
          (org-tags-match-list-sublevels t)
          (org-agenda-skip-function (lambda ()
                                      (org/schedule-until (format-time-string "%Y-%m-%d 23:59:59"
                                                                               (time-add 86400 (current-time))))))
          (org-agenda-sorting-strategy '(scheduled-up priority-down))))
        ("w" "Wait" todo "WAITING"
         ((org-agenda-overriding-header "分配给他人的任务")
          (org-tags-match-list-sublevels t)
          (org-agenda-skip-function 'org/schedule-until)
          (org-agenda-sorting-strategy '(scheduled-up priority-down))))
        ("s" "超市" tags "超市"
         ((org-agenda-overriding-header "超市")
          (org-tags-match-list-sublevels t)))
        ("m" "会议" tags "MEETING"
         ((org-agenda-overriding-header "会议")
          (org-tags-match-list-sublevels t)))
        ("o" "OFFICE" todo "TODAY|PROG"
         ((org-agenda-overriding-header "工作任务")
          (org-agenda-files (list (expand-file-name "office.org" MY-GTD-PATH)))
          (org-tags-match-list-sublevels t)
          (org-agenda-skip-function 'org/schedule-until)
          (org-agenda-sorting-strategy '(scheduled-up priority-down))))
        ("h" "Habits" tags-todo "STYLE=\"habit\""
         ((org-agenda-overriding-header "习惯")
          (org-agenda-sorting-strategy
           '(todo-state-down effort-up category-keep))))
        ("r" "季度报告"
         ((agenda "" ((org-agenda-overriding-header "季度工作列表")
                      (org-agenda-span 90)
                      (org-agenda-start-day "-90d")))))
        (" " "Agenda"
         ((agenda "" nil)
          (tags "REFILE"
                ((org-agenda-overriding-header "Tasks to Refile")
                 (org-tags-match-list-sublevels nil)))
          (alltodo ""))
         nil)))

(provide 'agenda-views)
;;; agenda-views.el ends here
```

迁移要点（相对 init-GTD-org.org 原版）：n/t/T/w/o 各补 `org-agenda-sorting-strategy`；o 视图 `file-name-concat` → `expand-file-name`；␣ 块中已注释的 bh/* 死代码不迁移。

- [ ] **步骤 3：运行断言命令验证通过（GREEN）**

运行步骤 1 的命令。预期输出 `ALL ASSERTIONS PASSED`。

- [ ] **步骤 4：byte-compile 干净性检查**

```bash
emacs --batch -Q --eval '(byte-compile-file "~/github/mobileorg-sync/agenda-views.el")'
```

预期：无 error（style warning 可接受）。产生的 `.elc` 删除（仓库不留编译产物）：`rm ~/github/mobileorg-sync/agenda-views.elc`

- [ ] **步骤 5：Commit（mobileorg-sync 仓库）**

```bash
cd ~/github/mobileorg-sync && git add agenda-views.el && git commit -m "feat: agenda-views.el 视图定义单一真相（10 视图 + NOAGENDA skip + 排序）"
```

---

### 任务 2：mobileorg-sync.el 改为 load 共享文件

**依赖：** 任务 1
**文件集：** `~/github/mobileorg-sync/mobileorg-sync.el`
**导出/变更接口：** `mobileorg-sync.el::agenda-views 加载机制`
**消费接口：** `agenda-views.el::org-agenda-custom-commands`, `agenda-views.el::org/schedule-until`, `agenda-views.el::my/org-skip-noagenda`
**复杂度：** quick

**文件：**
- 修改：`~/github/mobileorg-sync/mobileorg-sync.el:23`（插入 load）、`mobileorg-sync.el:236-273`（删除旧定义）

- [ ] **步骤 1：删除旧定义**

删除 `mobileorg-sync.el` 两段（Read 后以精确文本为 old_string）：
1. L236-247：`; Custom agenda command definitions` 注释行 + `(defun org/schedule-until ...)` 整个 defun
2. L248-273：`(setq org-agenda-custom-commands '(("n" ... ) ("s" ...) ("m" ...) ("o" ...)))` 整段（含重复 `t` 键的 lambda 型第二个定义）

保留 L274-275（`;;总是显示继承的tag` + `org-agenda-show-inherited-tags`）及 L230-235 的其他 org 配置。

- [ ] **步骤 2：插入 load 与 org-mobile-agendas 设置**

在 L23 `(require 'org-mobile)` 之后插入：

```elisp
(unless (load (expand-file-name "agenda-views.el"
                                (file-name-directory load-file-name)) nil t)
  (error "mobileorg-sync: failed to load agenda-views.el"))
(setq org-mobile-agendas 'all)
```

- [ ] **步骤 3：编译与 grep 验证**

```bash
# 编译验证（绝不能 --batch -l load：脚本尾部会执行 mobileorg-sync 触发真实 git 操作）
emacs --batch -Q --eval '(byte-compile-file "~/github/mobileorg-sync/mobileorg-sync.el")'
rm -f ~/github/mobileorg-sync/mobileorg-sync.elc
# 结构断言
grep -c 'org-agenda-custom-commands' ~/github/mobileorg-sync/mobileorg-sync.el   # 预期 0
grep -c 'org/schedule-until' ~/github/mobileorg-sync/mobileorg-sync.el            # 预期 0
grep -c 'agenda-views.el' ~/github/mobileorg-sync/mobileorg-sync.el               # 预期 1
grep -c "org-mobile-agendas" ~/github/mobileorg-sync/mobileorg-sync.el            # 预期 1
```

预期：byte-compile 无 error；4 个 grep 计数依次为 `0 0 1 1`。

- [ ] **步骤 4：Commit（mobileorg-sync 仓库）**

```bash
cd ~/github/mobileorg-sync && git add mobileorg-sync.el && git commit -m "refactor: 删除自带视图定义，load agenda-views.el 单一真相，启用 org-mobile-agendas all"
```

---

### 任务 3：init-GTD-org.org 替换内联定义为 load

**依赖：** 任务 1
**文件集：** `~/.spacemacs.d/layers/my-GTD/init-GTD-org.org`, `~/.spacemacs.d/layers/my-GTD/init-GTD-org.el`
**导出/变更接口：** `init-GTD-org.el::agenda-views 加载机制`
**消费接口：** `agenda-views.el::org-agenda-custom-commands`, `agenda-views.el::org/schedule-until`, `agenda-views.el::my/org-skip-noagenda`
**复杂度：** standard

**文件：**
- 修改：`~/.spacemacs.d/layers/my-GTD/init-GTD-org.org`（「配置agenda view」src block，L62 起）
- 重新生成：`init-GTD-org.el`（tangle 产物，随 org 文件一起 commit）

- [ ] **步骤 1：替换「配置agenda view」block 中的三段定义**

对该 block（`#+BEGIN_SRC emacs-lisp` 在 L63）做三处编辑：

编辑 1 — 将 L64-73 整段（`;; 不要显示NOAGENDA tag的事项` 注释 + `my/org-skip-noagenda` defun + `(add-to-list 'org-agenda-skip-function-global ...)`）替换为：

```emacs-lisp
  ;; agenda 视图定义改由单一真相文件提供（与云侧 mobileorg-sync 共用）
  (unless (load "~/github/mobileorg-sync/agenda-views.el" nil t)
    (message "WARN: agenda-views.el not found; org-agenda views unavailable"))
```

编辑 2 — 删除 L81-92（`;; Custom agenda command definitions` 注释 + `org/schedule-until` defun 整段）。

编辑 3 — 删除 L93-194（`(setq org-agenda-custom-commands '(("n" ...` 起至 `(alltodo ""))\n           nil)))` 止的整段）。

**必须保留**（不得误删）：L75-79（`org-agenda-dim-blocked-tasks`、`org-agenda-compact-blocks`）、L195 起（`org-agenda-text-search-extra-files`、`org-agenda-show-all-dates`、`org-agenda-start-on-weekday` 等）。

- [ ] **步骤 2：重新 tangle**

```bash
emacs --batch --eval '(progn (require (quote org))
  (org-babel-tangle-file "~/.spacemacs.d/layers/my-GTD/init-GTD-org.org"))'
```

预期输出含 `tangled ... init-GTD-org.el`。

- [ ] **步骤 3：tangle 范围与结构断言**

```bash
cd ~/.spacemacs.d && git status --porcelain
# 预期仅两个文件变化：layers/my-GTD/init-GTD-org.org 与 layers/my-GTD/init-GTD-org.el
# 出现其他文件则中止（tangle 意外扩散），报告用户
grep -c 'org-agenda-custom-commands' ~/.spacemacs.d/layers/my-GTD/init-GTD-org.el   # 预期 0
grep -c 'agenda-views.el' ~/.spacemacs.d/layers/my-GTD/init-GTD-org.el              # 预期 1
grep -c 'org/schedule-until' ~/.spacemacs.d/layers/my-GTD/init-GTD-org.el           # 预期 0
```

- [ ] **步骤 4：Commit（spacemacs.d 仓库）**

```bash
cd ~/.spacemacs.d && git add layers/my-GTD/init-GTD-org.org layers/my-GTD/init-GTD-org.el \
  && git commit -m "refactor: agenda 视图定义改 load agenda-views.el 单一真相（与 mobileorg-sync 共用）"
```

注意：PC 端 Emacs 内 `M-x org-agenda` 行为回归属于任务 5 验收（需重启/重载 spacemacs），本任务只做结构验证。

---

### 任务 4：沙箱端到端验证 org-mobile-push 产物

**依赖：** 任务 1
**文件集：** `~/github/mobileorg-sync/tests/e2e-agendas.sh`
**导出/变更接口：** `tests/e2e-agendas.sh::e2e-agendas`（回归脚本）
**消费接口：** `agenda-views.el::org-agenda-custom-commands`, `agenda-views.el::org/schedule-until`, `agenda-views.el::my/org-skip-noagenda`
**复杂度：** deep

**文件：**
- 创建：`~/github/mobileorg-sync/tests/e2e-agendas.sh`（可重复执行的回归脚本）
- 临时产物：`/tmp/gtd-e2e-sandbox/`（不 commit）

- [ ] **步骤 1：编写 e2e-agendas.sh**

```bash
#!/bin/bash
# 端到端验证：沙箱 GTD + agenda-views.el → org-mobile-push → 断言 agendas.org
# 零副作用：只读 ~/我的GTD 拷贝，产物全在 /tmp/gtd-e2e-sandbox
set -euo pipefail

SANDBOX=/tmp/gtd-e2e-sandbox
GTD_SRC=~/我的GTD
rm -rf "$SANDBOX"
mkdir -p "$SANDBOX/gtd" "$SANDBOX/mobile"
cp "$GTD_SRC"/*.org "$SANDBOX/gtd/"

cat > "$SANDBOX/gtd/z-e2e-fixture.org" <<'EOF'
* TODO 验证NOAGENDA排除 :NOAGENDA:
* NEXT 未来scheduled的next
SCHEDULED: <2027-01-01 五>
* NEXT 手测锚点next
EOF

cat > "$SANDBOX/driver.el" <<'EOF'
(require 'org)
(require 'org-agenda)
(require 'org-mobile)
(setq MY-GTD-PATH "/tmp/gtd-e2e-sandbox/gtd")
(setq org-directory "/tmp/gtd-e2e-sandbox/gtd")
(setq org-mobile-directory "/tmp/gtd-e2e-sandbox/mobile")
(setq org-agenda-files (list org-directory))
(setq org-agenda-file-regexp "\\`[^.].*\\.org\\(_archive\\)?\\'")
(setq org-todo-keywords
      '((type "TODO(t)" "NEXT(n)" "PROG(p)" "TODAY(T)" "WAITING(w@/!)" "|"
              "DONE(d)" "CANCELLED(c@/!)" "SUSPEND(s@/!)")))
(load "~/github/mobileorg-sync/agenda-views.el")
(org-mobile-push)
EOF

emacs --batch -l "$SANDBOX/driver.el"

A="$SANDBOX/mobile/agendas.org"
echo "=== assertions on $A ==="
test -f "$A" || { echo "FAIL: agendas.org not generated"; exit 1; }

n=$(grep -c '<after>KEYS=' "$A")
[ "$n" -ge 11 ] || { echo "FAIL: expected >=11 views, got $n"; exit 1; }
echo "PASS: $n view blocks (>=11)"

for title in 下一步行动 今日事项 明日事项 分配给他人的任务 超市 会议 工作任务 习惯 季度工作列表; do
  grep -q "$title" "$A" || { echo "FAIL: view title missing: $title"; exit 1; }
done
echo "PASS: all custom view titles present"

if grep -q '验证NOAGENDA排除' "$A"; then echo "FAIL: NOAGENDA item leaked"; exit 1; fi
echo "PASS: NOAGENDA excluded (global skip)"

# n 视图块内断言（注意：不能对全文件断言未来 scheduled——␣ 块的 alltodo 会合法列出它）
# n 视图块 = 「下一步行动」标题行到下一个一级标题行（去尾行）
sed -n '/下一步行动/,/^\* /p' "$A" | sed '$d' > "$SANDBOX/n-block.txt"

if grep -q '未来scheduled的next' "$SANDBOX/n-block.txt"; then
  echo "FAIL: future-scheduled NEXT leaked into n view"; exit 1
fi
echo "PASS: future scheduled excluded from n view (org/schedule-until)"

grep -q '手测锚点next' "$SANDBOX/n-block.txt" \
  || { echo "FAIL: anchor NEXT missing from n view"; exit 1; }
echo "PASS: anchor NEXT present in n view"

echo "ALL E2E ASSERTIONS PASSED"
```

说明：
- `emacs --batch -l driver.el` 会在 driver 顶层执行 `org-mobile-push`（driver 无函数定义外的副作用分支）。
- NOAGENDA 断言用全文件（全局 skip 对所有视图生效，含 alltodo）。
- 未来 scheduled 断言必须限定 n 视图块内：␣ 块的 `alltodo` 没有 `org/schedule-until` skip，合法列出该节点；a 周视图（本周）与 r 视图（[今天-90d, 今天+90d]）均不含 2027-01-01。
- NOAGENDA 排除同时验证了 `my/org-skip-noagenda` 已注册到 `org-agenda-skip-function-global`。

- [ ] **步骤 2：chmod + 运行（RED→GREEN）**

```bash
chmod +x ~/github/mobileorg-sync/tests/e2e-agendas.sh
~/github/mobileorg-sync/tests/e2e-agendas.sh
```

预期输出末行 `ALL E2E ASSERTIONS PASSED`。任一 FAIL 则修 agenda-views.el 后重跑（本任务内闭环，不动其他文件）。

- [ ] **步骤 3：Commit（mobileorg-sync 仓库）**

```bash
cd ~/github/mobileorg-sync && git add tests/e2e-agendas.sh \
  && git commit -m "test: 沙箱端到端验证 agendas.org（NOAGENDA 排除/schedule-until/视图完整性）"
```

---

### 任务 5：部署与三端验收（用户协作）

**依赖：** 任务 2, 任务 3, 任务 4
**文件集：** 无（操作型任务：git push、ssh、手机/PC 手测）
**导出/变更接口：** 无
**消费接口：** `agenda-views.el::org-agenda-custom-commands`
**复杂度：** deep

- [ ] **步骤 1：推送 mobileorg-sync**

```bash
cd ~/github/mobileorg-sync && git push origin
```

- [ ] **步骤 2：云上拉取（需用户协助确认仓库路径）**

云上 mobileorg-sync 仓库路径未知。先探测：

```bash
ssh -p 8022 lujun9972@tencent_cloud.lujun9972.win \
  'find ~ -maxdepth 4 -name mobileorg-sync.el -not -path "*/.git/*" 2>/dev/null'
```

得到路径 `<DIR>` 后：`ssh -p 8022 lujun9972@tencent_cloud.lujun9972.win 'cd <DIR> && git pull'`

- [ ] **步骤 3：验证云侧 agendas.org**

等待一个 sync 周期（5 分钟）或手动触发云侧 sync，然后：

```bash
ssh -p 8022 lujun9972@tencent_cloud.lujun9972.win \
  'grep -c "<after>KEYS=" ~/mobileorg/agendas.org && grep -l "下一步行动\|工作任务" ~/mobileorg/agendas.org'
```

预期：计数 ≥ 11；两个标题 grep 命中。若云侧 Emacs 因 `org-get-tags` 等 API 过旧报错，将报错原文回报用户决策。

- [ ] **步骤 4：手机验收（对应规格验收标准 2、3）**

设备 `192.168.31.198:<port>`（无线调试，端口以 `adb devices` 为准）或 USB。请用户在手机上：
1. MobileOrg 手动同步 → 「Agenda Views」应显示 11 个视图块，块标题干净（无 `<after` 残留）
2. 对某节点切换 todo（如 TODO→DONE）→ 等一个 sync 周期 → PC 端 `git -C ~/我的GTD log` 显示对应节点状态变更（端到端回流）

- [ ] **步骤 5：PC 回归验收（对应规格验收标准 4）**

请用户重启 Emacs（或重载 my-GTD layer）后逐个执行 `M-x org-agenda` n/t/T/w/s/m/o/h/r/␣，确认行为与改动前一致且 n/t/T/w/o 排序生效（scheduled 在前）。

- [ ] **步骤 6：验收结果记录**

三端验收结果（通过/问题）汇总回报用户。全部通过即计划完成。

---

## 并行执行图

> 仅 `parallel-executing-plans` 使用；`serial-executing-plans` 忽略本节。

**Critical Path:** 任务 1 → 任务 2 → 任务 5

- Wave 1（无依赖）：任务 1
- Wave 2（依赖 Wave 1）：任务 2（依赖 1）, 任务 3（依赖 1）, 任务 4（依赖 1）
- Wave 3（依赖 Wave 2）：任务 5（依赖 2, 3, 4；用户协作部署验收，建议 controller 亲自执行而非子代理）
- Wave FINAL（所有任务完成后）：F1 规格合规、F2 代码质量、F3 真实手测、F4 范围保真

**执行方式提示：** 本计划任务 5 需用户协作（云上路径、手机/PC 手测），且任务总量 5 个——推荐串行执行（serial-executing-plans）。
