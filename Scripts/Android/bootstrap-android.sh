#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
SDK_ROOT="$ANDROID_DIR/.android-sdk"
GRADLE_ROOT="$ANDROID_DIR/.gradle-local"
DOWNLOAD_DIR="${TMPDIR:-/tmp}/chess-openings-android"

CMDLINE_TOOLS_VERSION="14742923"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
GRADLE_VERSION="9.6.0"
GRADLE_ZIP="gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/${GRADLE_ZIP}"

mkdir -p "$SDK_ROOT/cmdline-tools" "$GRADLE_ROOT" "$DOWNLOAD_DIR"

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
    echo "Installing Android command-line tools..."
    curl -fL "$CMDLINE_TOOLS_URL" -o "$DOWNLOAD_DIR/$CMDLINE_TOOLS_ZIP"
    rm -rf "$SDK_ROOT/cmdline-tools/latest" "$DOWNLOAD_DIR/cmdline-tools"
    unzip -q "$DOWNLOAD_DIR/$CMDLINE_TOOLS_ZIP" -d "$DOWNLOAD_DIR"
    mv "$DOWNLOAD_DIR/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

if [[ ! -x "$GRADLE_ROOT/gradle-${GRADLE_VERSION}/bin/gradle" ]]; then
    echo "Installing Gradle $GRADLE_VERSION..."
    curl -fL "$GRADLE_URL" -o "$DOWNLOAD_DIR/$GRADLE_ZIP"
    rm -rf "$GRADLE_ROOT/gradle-${GRADLE_VERSION}"
    unzip -q "$DOWNLOAD_DIR/$GRADLE_ZIP" -d "$GRADLE_ROOT"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$GRADLE_ROOT/gradle-${GRADLE_VERSION}/bin:$PATH"

set +o pipefail
yes | sdkmanager --licenses >/dev/null
license_status=${PIPESTATUS[1]}
set -o pipefail
if [[ $license_status -ne 0 ]]; then
    exit "$license_status"
fi

sdkmanager \
    "platform-tools" \
    "emulator" \
    "platforms;android-36" \
    "build-tools;36.0.0"

cat > "$ANDROID_DIR/local.properties" <<EOF
sdk.dir=$SDK_ROOT
EOF

echo
echo "Android development environment is ready."
echo "Add these to your shell when working outside Makefile targets:"
echo "  export ANDROID_HOME=\"$SDK_ROOT\""
echo "  export ANDROID_SDK_ROOT=\"$SDK_ROOT\""
echo "  export PATH=\"$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$GRADLE_ROOT/gradle-${GRADLE_VERSION}/bin:\$PATH\""
