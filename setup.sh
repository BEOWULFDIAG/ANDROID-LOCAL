#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# ── MJOLNIR TERMINAL — Termux Build Setup ─────────────────────────────────────

ANDROID_SDK_VERSION="11076708"
ANDROID_SDK_ZIP="commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip"
ANDROID_SDK_URL="https://dl.google.com/android/repository/${ANDROID_SDK_ZIP}"
PROOT_URL="https://github.com/proot-me/proot/releases/download/v5.4.0/proot-v5.4.0-aarch64-static"
PROOT_DEST="app/src/main/assets/proot-arm64"

echo "── Installing packages ──────────────────────────────────────────────────"
pkg update -y
pkg install -y openjdk-17 wget unzip zip

echo "── Android SDK setup ────────────────────────────────────────────────────"
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools

if [[ ! -f "${ANDROID_SDK_ZIP}" ]]; then
    wget -q --show-progress "${ANDROID_SDK_URL}"
fi

unzip -q -o "${ANDROID_SDK_ZIP}"
[[ -d "cmdline-tools" ]] && mv cmdline-tools latest || true

export ANDROID_HOME=~/android-sdk
export PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

echo "── SDK licenses and components ──────────────────────────────────────────"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "── Downloading proot binary ─────────────────────────────────────────────"
cd -
mkdir -p app/src/main/assets
wget -q --show-progress -O "${PROOT_DEST}" "${PROOT_URL}"
chmod +x "${PROOT_DEST}"

echo "── Writing local.properties ─────────────────────────────────────────────"
echo "sdk.dir=${ANDROID_HOME}" > local.properties

echo "── Installing Gradle wrapper ────────────────────────────────────────────"
wget -q --show-progress \
    "https://services.gradle.org/distributions/gradle-8.7-bin.zip" \
    -O /tmp/gradle.zip
unzip -q -o /tmp/gradle.zip -d ~/gradle
export PATH="${PATH}:${HOME}/gradle/gradle-8.7/bin"

echo ""
echo "── Setup complete ───────────────────────────────────────────────────────"
echo "   Run: gradle assembleDebug"
echo "   APK: app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "   Add to ~/.bashrc:"
echo "   export ANDROID_HOME=~/android-sdk"
echo "   export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:~/gradle/gradle-8.7/bin"
