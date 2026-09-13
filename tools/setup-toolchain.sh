#!/usr/bin/env bash
# 免 root 安装 Android 构建工具链到项目内的 .toolchain/ 目录。
# 全部装在用户目录下，不需要 sudo，删除 .toolchain/ 即可完全卸载。
#
# 用法：  bash tools/setup-toolchain.sh
#
# 安装内容：
#   .toolchain/jdk/            JDK 21 (Temurin, 清华镜像)
#   .toolchain/android-sdk/    Android SDK (cmdline-tools + platform-tools
#                              + platforms;android-35 + build-tools;35.0.0)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="$ROOT/.toolchain"
JDK_DIR="$TC/jdk"
SDK_DIR="$TC/android-sdk"

JDK_URL="https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/linux/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

SDK_PACKAGES=(
  "platform-tools"
  "platforms;android-35"
  "build-tools;35.0.0"
)

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

unzip_into() {  # unzip_into <zip> <dest>
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$1" -d "$2"
  else
    python3 -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$1" "$2"
  fi
}

mkdir -p "$TC"

# ---------------------------------------------------------------- JDK 21
if [ -x "$JDK_DIR/bin/javac" ]; then
  log "JDK 已存在，跳过"
else
  log "下载 JDK 21（清华镜像，约 200MB）"
  curl -fL --retry 3 -o "$TC/jdk.tar.gz" "$JDK_URL"
  log "解压 JDK"
  rm -rf "$JDK_DIR"; mkdir -p "$JDK_DIR"
  tar xzf "$TC/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm -f "$TC/jdk.tar.gz"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
log "JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

# ------------------------------------------------------- Android SDK tools
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
  log "cmdline-tools 已存在，跳过"
else
  log "下载 Android cmdline-tools（约 150MB）"
  curl -fL --retry 3 -o "$TC/cmdline-tools.zip" "$CMDLINE_URL"
  log "解压 cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools"
  mkdir -p "$SDK_DIR/cmdline-tools"
  unzip_into "$TC/cmdline-tools.zip" "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -f "$TC/cmdline-tools.zip"
  chmod +x "$SDKMANAGER"
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

# sdkmanager / Gradle 默认会写 ~/.android 与 ~/.gradle。
# 本项目要求全部自包含，且这些目录在受限环境下往往不可写，
# 因此统一重定向到 .toolchain/ 内。
#
# 注意：只能设 ANDROID_USER_HOME。若同时设 ANDROID_PREFS_ROOT，
# AGP 会以「多个来源指向 Android Preferences 目录」为由直接报错。
export ANDROID_USER_HOME="$TC/android-home"
unset ANDROID_PREFS_ROOT
export GRADLE_USER_HOME="$TC/gradle-home"
mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"

# ------------------------------------------------------------ SDK packages
log "接受 SDK 许可协议"
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true

log "安装 SDK 组件：${SDK_PACKAGES[*]}"
"$SDKMANAGER" --sdk_root="$SDK_DIR" "${SDK_PACKAGES[@]}"

# ------------------------------------------------------------------ Gradle
GRADLE_VERSION="8.11.1"
GRADLE_DIR="$TC/gradle"
GRADLE_BIN="$GRADLE_DIR/gradle-$GRADLE_VERSION/bin/gradle"
if [ -x "$GRADLE_BIN" ]; then
  log "Gradle 已存在，跳过"
else
  log "下载 Gradle $GRADLE_VERSION（约 130MB）"
  # 官方源会 307 跳到 GitHub Releases，国内基本拉不动。
  # 实测：华为云 ~1.3MB/s，腾讯云时会限速，故按此顺序回退。
  GRADLE_MIRRORS=(
    "https://repo.huaweicloud.com/gradle/gradle-$GRADLE_VERSION-bin.zip"
    "https://mirrors.cloud.tencent.com/gradle/gradle-$GRADLE_VERSION-bin.zip"
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  )
  for url in "${GRADLE_MIRRORS[@]}"; do
    echo "  尝试：$url"
    if curl -fL --retry 2 --connect-timeout 20 --speed-time 30 --speed-limit 20000 \
      -o "$TC/gradle.zip" "$url"; then
      break
    fi
    rm -f "$TC/gradle.zip"
  done
  [ -s "$TC/gradle.zip" ] || { echo "Gradle 下载失败" >&2; exit 1; }
  log "解压 Gradle"
  rm -rf "$GRADLE_DIR"; mkdir -p "$GRADLE_DIR"
  unzip_into "$TC/gradle.zip" "$GRADLE_DIR"
  rm -f "$TC/gradle.zip"
  chmod +x "$GRADLE_BIN"
fi

log "安装完成"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
"$SDKMANAGER" --sdk_root="$SDK_DIR" --list_installed | sed 's/^/  /'
