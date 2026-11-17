#!/usr/bin/env bash
# 从 CI 下载最新 APK 并安装到设备。
# 只用 adb install -r 更新，绝不 uninstall——本地未同步数据在卸载时会永久丢失。
#
# 用法:
#   ./deploy.sh            安装最新成功的 Build APK
#   ./deploy.sh --watch    等待最新一次 Build APK 跑完（成功才装）
#   ./deploy.sh <run-id>   安装指定 run
set -euo pipefail

REPO=lujun9972/mobileorg-android   # git remote 是 Gitea 代理，gh 必须直连 GitHub
WORKFLOW=build.yml
ARTIFACT=MobileOrg-release
PKG=com.matburt.mobileorg

command -v gh >/dev/null || { echo "缺少 gh CLI"; exit 1; }
command -v adb >/dev/null || { echo "缺少 adb"; exit 1; }

# 设备在线检查——历史上两次数据丢失事故都发生在无确认的卸载/重装上
if ! adb get-state >/dev/null 2>&1; then
  echo "无设备连接。先 adb connect <ip>:<port>（无线）或插 USB。"
  exit 1
fi

RUN_ID=${1:-}
MODE=latest-success
if [ "${RUN_ID:-}" = "--watch" ]; then
  MODE=watch
  RUN_ID=""
fi

if [ -z "$RUN_ID" ]; then
  RUN_ID=$(gh run list -R "$REPO" --workflow="$WORKFLOW" --limit 10 \
    --json databaseId,status,conclusion,headSha,headBranch,event \
    --jq '[.[] | select(.event=="push" and .headBranch=="main")][0].databaseId')
  [ -n "$RUN_ID" ] || { echo "main 分支没有 Build APK 记录"; exit 1; }
fi

if [ "$MODE" = "watch" ]; then
  echo "等待 run $RUN_ID 完成..."
  gh run watch "$RUN_ID" -R "$REPO" --exit-status
fi

CONCLUSION=$(gh run view "$RUN_ID" -R "$REPO" --json conclusion --jq .conclusion)
[ "$CONCLUSION" = "success" ] || { echo "run $RUN_ID 结论是 $CONCLUSION，不安装"; exit 1; }

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
gh run download "$RUN_ID" -R "$REPO" -n "$ARTIFACT" -D "$OUT"
APK=$(find "$OUT" -name '*.apk' | head -1)
[ -n "$APK" ] || { echo "artifact 中没有 APK"; exit 1; }

echo "安装 $(basename "$APK") (run $RUN_ID)"
adb install -r "$APK"

if [ "$MODE" != "watch" ] || [ "${2:-}" != "--no-launch" ]; then
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
fi
echo "完成。"
