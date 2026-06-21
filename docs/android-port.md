# Android Port

The project should stay native on both platforms:

- Apple app: SwiftUI, SwiftData, ChessKit, existing Xcode project.
- Android app: Kotlin, Jetpack Compose, Room/DataStore later, Gradle project under `android/`.
- Shared app logic: Swift package under `Packages/ChessOpeningsCore`, called from Android through JNI / `swift-java`.
- Shared assets: generated seed JSON, chess piece images, sounds, and Stockfish license/assets where Android playout support is added.
- Shared domain behavior: keep `ChessKit` and drill/session rules in Swift. Do not duplicate these in Kotlin unless the Swift Android SDK route becomes blocked.

This avoids a full rewrite of the shipping iOS app while giving Android a first-class UI and persistence model.

## Validation Result

`chesskit-swift` 0.17.0 has been validated with Swift 6.3.2 and the official Swift SDK for Android. A minimal package importing `ChessKit` built for:

- Linux host
- `x86_64-unknown-linux-android28`
- `aarch64-unknown-linux-android28`

That means the Android port can share the same ChessKit-backed Swift domain logic as the Apple app.

## Local Setup

Install host packages once:

```sh
sudo apt update
sudo apt install -y ca-certificates curl unzip zip git make perl build-essential openjdk-17-jdk qemu-kvm cpu-checker libvirt-daemon-system libvirt-clients bridge-utils android-sdk-platform-tools-common
sudo usermod -aG kvm,libvirt "$USER"
```

Log out and back in after changing groups.

Then bootstrap the repo-local Android SDK and Gradle:

```sh
./Scripts/Android/bootstrap-android.sh
```

The script writes Android SDK state to `android/.android-sdk`, local Gradle state to `android/.gradle-local`, and `android/local.properties`. All three are ignored.

Bootstrap Swift and the Swift SDK for Android:

```sh
./Scripts/Android/bootstrap-swift-android.sh
```

This installs the Swift 6.3.2 Ubuntu 24.04 toolchain under `~/.local/share/swift-toolchains`, installs the matching Android Swift SDK under `~/.swiftpm/swift-sdks`, downloads Android NDK r27d, and runs Swift's Android SDK setup script. The direct Swift toolchain path is used because the `swiftly` binary crashed with `Illegal instruction` on this laptop.

## Build

```sh
make core-test
make core-android-build
make android-build
make android-test
```

The initial Android app is a Compose shell. The next useful milestone is to move the existing iOS drill domain files into `Packages/ChessOpeningsCore`, keep the iOS app importing that package, and expose a narrow Swift/JNI API for the Android UI.
