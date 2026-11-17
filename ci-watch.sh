#!/usr/bin/env bash
# 等待 main 最新 push 触发的两个 workflow 跑完，分别输出结论后以汇总退出码返回。
# 注意：Build APK 只跑 assembleRelease，不编译 test 源集——测试结论以
# Instrumentation Tests 为准（push 失败测试时 Build APK 依然绿）。
#
# 用法: ./ci-watch.sh [--id <build-run-id> <test-run-id>]   # 默认取最新 push 的两个 run
set -uo pipefail

REPO=lujun9972/mobileorg-android

if [ "${1:-}" = "--id" ]; then
  BUILD_ID=$2; TEST_ID=$3
else
  # Gitea 代理转发到 GitHub 有延迟，push 后 run 可能尚未创建——轮询等待
  for _ in $(seq 1 20); do
    read -r BUILD_ID TEST_ID < <(gh run list -R "$REPO" --limit 10 \
      --json databaseId,name,headBranch,event \
      --jq '[(.[] | select(.event=="push" and .headBranch=="main"))] |
            (map(select(.name=="Build APK")) | .[0].databaseId),
            (map(select(.name=="Instrumentation Tests")) | .[0].databaseId)')
    [ -n "${BUILD_ID:-}" ] && [ -n "${TEST_ID:-}" ] && break
    sleep 15
  done
  [ -n "${BUILD_ID:-}" ] && [ -n "${TEST_ID:-}" ] || { echo "5 分钟内没等到 main 的 CI run"; exit 1; }
fi

echo "watching Build APK $BUILD_ID, Instrumentation Tests $TEST_ID"
BUILD=OK; TEST=OK
gh run watch "$BUILD_ID" -R "$REPO" --exit-status >/dev/null || BUILD=FAIL
gh run watch "$TEST_ID" -R "$REPO" --exit-status >/dev/null || TEST=FAIL
echo "BUILD: $BUILD  TEST: $TEST"
[ "$BUILD" = "OK" ] && [ "$TEST" = "OK" ]
