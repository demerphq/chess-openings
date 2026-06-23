#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
SDK_ROOT="${ANDROID_HOME:-$ANDROID_DIR/.android-sdk}"
NDK_VERSION="${ANDROID_NDK_VERSION:-29.0.14033849}"
NDK_DIR="${ANDROID_NDK_HOME:-$SDK_ROOT/ndk/$NDK_VERSION}"
TOOLCHAIN_DIR="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin"

STOCKFISH_VERSION="${STOCKFISH_VERSION:-sf_18}"
STOCKFISH_GIT_SHA="${STOCKFISH_GIT_SHA:-cb3d4ee}"
STOCKFISH_GIT_DATE="${STOCKFISH_GIT_DATE:-20260131}"
SOURCE_URL="https://github.com/official-stockfish/Stockfish/archive/refs/tags/$STOCKFISH_VERSION.tar.gz"
BUILD_ROOT="$ANDROID_DIR/.stockfish-build"
TARBALL="$BUILD_ROOT/stockfish-$STOCKFISH_VERSION.tar.gz"
SOURCE_DIR="$BUILD_ROOT/Stockfish-$STOCKFISH_VERSION"
OUTPUT_DIR="$ANDROID_DIR/stockfish"

if [[ ! -x "$TOOLCHAIN_DIR/aarch64-linux-android29-clang++" ]]; then
    cat >&2 <<EOF
Android NDK not found at:
  $NDK_DIR

Install it with:
  android/.android-sdk/cmdline-tools/latest/bin/sdkmanager "ndk;$NDK_VERSION"
EOF
    exit 1
fi

mkdir -p "$BUILD_ROOT" "$OUTPUT_DIR/nnue"

if [[ ! -f "$TARBALL" ]]; then
    echo "Downloading Stockfish $STOCKFISH_VERSION source..."
    curl -L "$SOURCE_URL" -o "$TARBALL"
fi

rm -rf "$SOURCE_DIR"
mkdir -p "$SOURCE_DIR"
tar -xzf "$TARBALL" -C "$SOURCE_DIR" --strip-components=1

required_nets() {
    sed -n 's/.*\(nn-[a-z0-9]\{12\}\.nnue\).*/\1/p' "$SOURCE_DIR/src/evaluate.h" | sort -u
}

validate_net() {
    local file="$1"
    local name
    name="$(basename "$file")"
    [[ -f "$file" ]] || return 1
    [[ "$name" == "nn-$(sha256sum "$file" | cut -c 1-12).nnue" ]]
}

download_net() {
    local name="$1"
    local target="$SOURCE_DIR/src/$name"
    local staged="$OUTPUT_DIR/nnue/$name"
    if validate_net "$staged"; then
        cp "$staged" "$target"
        return
    fi

    if validate_net "$target"; then
        cp "$target" "$OUTPUT_DIR/nnue/$name"
        return
    fi

    rm -f "$target"
    for url in \
        "https://tests.stockfishchess.org/api/nn/$name" \
        "https://github.com/official-stockfish/networks/raw/master/$name"; do
        echo "Downloading $name..."
        if curl -fL "$url" -o "$target" && validate_net "$target"; then
            cp "$target" "$OUTPUT_DIR/nnue/$name"
            return
        fi
        rm -f "$target"
    done

    echo "Failed to download valid Stockfish network: $name" >&2
    exit 1
}

while IFS= read -r net; do
    [[ -n "$net" ]] && download_net "$net"
done < <(required_nets)

build_abi() {
    local abi="$1"
    local arch="$2"
    local target_dir="$OUTPUT_DIR/$abi"

    echo "Building Stockfish $STOCKFISH_VERSION for $abi..."
    PATH="$TOOLCHAIN_DIR:$PATH" make -C "$SOURCE_DIR/src" clean build \
        ARCH="$arch" \
        COMP=ndk \
        GIT_SHA="$STOCKFISH_GIT_SHA" \
        GIT_DATE="$STOCKFISH_GIT_DATE" \
        -j"$(nproc)"
    "$TOOLCHAIN_DIR/llvm-strip" "$SOURCE_DIR/src/stockfish"
    mkdir -p "$target_dir"
    cp "$SOURCE_DIR/src/stockfish" "$target_dir/stockfish"
    chmod 0755 "$target_dir/stockfish"
}

build_abi "x86_64" "x86-64"
build_abi "arm64-v8a" "armv8"

cp "$SOURCE_DIR/Copying.txt" "$OUTPUT_DIR/LICENSE-stockfish.txt"

echo "Stockfish Android assets staged in $OUTPUT_DIR"
