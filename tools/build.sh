#!/usr/bin/env bash
# 用项目内自带的工具链跑 Gradle，不依赖系统环境。
#
#   bash tools/build.sh assembleDebug
#   bash tools/build.sh test
#   bash tools/build.sh lint
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export JAVA_HOME="$ROOT/.toolchain/jdk"
export ANDROID_HOME="$ROOT/.toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

# 与 setup-toolchain.sh 保持一致：所有缓存都留在项目内，不碰 ~/.android 与 ~/.gradle。
# 注意：只能设 ANDROID_USER_HOME。若同时设 ANDROID_PREFS_ROOT，AGP 会以
# 「多个来源指向 Android Preferences 目录」为由直接报错。
export ANDROID_USER_HOME="$ROOT/.toolchain/android-home"
unset ANDROID_PREFS_ROOT
export GRADLE_USER_HOME="$ROOT/.toolchain/gradle-home"
mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"

GRADLE="$ROOT/.toolchain/gradle/gradle-8.11.1/bin/gradle"

if [ ! -x "$GRADLE" ]; then
  echo "找不到 Gradle：$GRADLE" >&2
  echo "请先运行：bash tools/setup-toolchain.sh" >&2
  exit 1
fi

exec "$GRADLE" -p "$ROOT" --console=plain "$@"
