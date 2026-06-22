#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CORE_DIR="$ROOT_DIR/Packages/ChessOpeningsCore"
SDK_ROOT="${SWIFT_ANDROID_SDK_ROOT:-$HOME/.swiftpm/swift-sdks/swift-6.3.2-RELEASE_android.artifactbundle/swift-android}"
SWIFT_LIB_ROOT="$SDK_ROOT/swift-resources/usr/lib"
NDK_LIB_ROOT="$SDK_ROOT/android-ndk-r27d/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib"
JNI_LIBS_DIR="$ROOT_DIR/android/app/src/main/jniLibs"

copy_abi() {
    local triple="$1"
    local android_abi="$2"
    local swift_abi="$3"
    local ndk_abi="$4"
    local build_dir="$CORE_DIR/.build/$triple/debug"
    local bridge="$build_dir/libChessOpeningsCoreBridge.so"
    local runtime_dir="$SWIFT_LIB_ROOT/$swift_abi/android"
    local ndk_runtime_dir="$NDK_LIB_ROOT/$ndk_abi"
    local out_dir="$JNI_LIBS_DIR/$android_abi"
    local copied="$out_dir/.copied-dependencies"

    if [[ ! -f "$bridge" ]]; then
        echo "missing $bridge"
        echo "run: make core-android-bridge-build"
        exit 1
    fi
    if [[ ! -d "$runtime_dir" ]]; then
        echo "missing Swift Android runtime directory: $runtime_dir"
        exit 1
    fi
    if [[ ! -d "$ndk_runtime_dir" ]]; then
        echo "missing Android NDK runtime directory: $ndk_runtime_dir"
        exit 1
    fi

    mkdir -p "$out_dir"
    : > "$copied"
    cp "$bridge" "$out_dir/"

    copy_needed_libs "$bridge" "$build_dir" "$runtime_dir" "$ndk_runtime_dir" "$out_dir" "$copied"
    rm -f "$copied"
}

is_system_lib() {
    case "$1" in
        libc.so|libdl.so|liblog.so|libm.so)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

copy_needed_libs() {
    local object="$1"
    local build_dir="$2"
    local runtime_dir="$3"
    local ndk_runtime_dir="$4"
    local out_dir="$5"
    local copied="$6"

    readelf -d "$object" \
        | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' \
        | while read -r lib; do
            if is_system_lib "$lib" || grep -Fxq "$lib" "$copied"; then
                continue
            fi

            local source=""
            if [[ -f "$build_dir/$lib" ]]; then
                source="$build_dir/$lib"
            elif [[ -f "$runtime_dir/$lib" ]]; then
                source="$runtime_dir/$lib"
            elif [[ -f "$ndk_runtime_dir/$lib" ]]; then
                source="$ndk_runtime_dir/$lib"
            fi

            if [[ -n "$source" ]]; then
                echo "$lib" >> "$copied"
                cp "$source" "$out_dir/"
                copy_needed_libs "$source" "$build_dir" "$runtime_dir" "$ndk_runtime_dir" "$out_dir" "$copied"
            fi
        done
}

copy_abi "x86_64-unknown-linux-android28" "x86_64" "swift-x86_64" "x86_64-linux-android"
copy_abi "aarch64-unknown-linux-android28" "arm64-v8a" "swift-aarch64" "aarch64-linux-android"

echo "Swift bridge libraries staged in $JNI_LIBS_DIR"
