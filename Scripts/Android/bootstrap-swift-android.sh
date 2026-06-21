#!/usr/bin/env bash
set -euo pipefail

SWIFT_VERSION="6.3.2"
SWIFT_RELEASE="swift-${SWIFT_VERSION}-RELEASE"
TOOLCHAIN_ROOT="${SWIFT_TOOLCHAIN_ROOT:-$HOME/.local/share/swift-toolchains}"
TOOLCHAIN_DIR="$TOOLCHAIN_ROOT/${SWIFT_RELEASE}-ubuntu24.04"
SWIFT_BIN="$TOOLCHAIN_DIR/usr/bin/swift"
DOWNLOAD_DIR="${TMPDIR:-/tmp}/chess-openings-swift-android"
ANDROID_SDK_NAME="${SWIFT_RELEASE}_android"
ANDROID_BUNDLE_DIR="$HOME/.swiftpm/swift-sdks/${SWIFT_RELEASE}_android.artifactbundle/swift-android"
NDK_DIR="$ANDROID_BUNDLE_DIR/android-ndk-r27d"

mkdir -p "$TOOLCHAIN_ROOT" "$DOWNLOAD_DIR"

if [[ ! -x "$SWIFT_BIN" ]]; then
    archive="${SWIFT_RELEASE}-ubuntu24.04.tar.gz"
    curl -fL -o "$DOWNLOAD_DIR/$archive" \
        "https://download.swift.org/swift-${SWIFT_VERSION}-release/ubuntu2404/${SWIFT_RELEASE}/${archive}"
    rm -rf "$TOOLCHAIN_DIR"
    tar -xzf "$DOWNLOAD_DIR/$archive" -C "$TOOLCHAIN_ROOT"
fi

if ! "$SWIFT_BIN" sdk list 2>/dev/null | grep -q "$ANDROID_SDK_NAME"; then
    "$SWIFT_BIN" sdk install \
        "https://download.swift.org/swift-${SWIFT_VERSION}-release/android-sdk/${SWIFT_RELEASE}/${SWIFT_RELEASE}_android.artifactbundle.tar.gz" \
        --checksum 939e933549d12d28f2e0bf71019d734d309859e9773c572657ce565a81f85d68
fi

if [[ ! -d "$NDK_DIR" ]]; then
    curl -fL -o "$DOWNLOAD_DIR/android-ndk-r27d-Linux.zip" \
        https://dl.google.com/android/repository/android-ndk-r27d-Linux.zip
    unzip -qo "$DOWNLOAD_DIR/android-ndk-r27d-Linux.zip" -d "$ANDROID_BUNDLE_DIR"
fi

export ANDROID_NDK_HOME="$NDK_DIR"
"$ANDROID_BUNDLE_DIR/scripts/setup-android-sdk.sh"

echo
echo "Swift Android development environment is ready."
echo "Swift: $SWIFT_BIN"
echo "Android Swift SDK: $ANDROID_SDK_NAME"
echo "NDK: $NDK_DIR"
