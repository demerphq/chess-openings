# Android Build And Test Guide

This guide explains how to build and test the Android version of Chess Openings on this Linux laptop.

You do not need Android Studio for the commands below. Everything runs from a terminal.

## One-Time Setup

Open a terminal in the project folder:

```sh
cd /home/demerphq/git_tree/chess/chess-openings
```

Install the Android tools used by this project:

```sh
make android-bootstrap
```

Install the Swift tools used by the shared app logic:

```sh
make swift-android-bootstrap
```

These commands download tools into local folders. They may take several minutes the first time. Running them again is safe; they will reuse what is already installed.

## Build The Shared Swift Core

The Android app is expected to share chess/drill logic with the Apple app through Swift. Check that this shared Swift code builds and tests first:

```sh
make core-test
make core-android-build
```

Expected result: both commands end without an error. `core-test` should report that the Swift test passed.

## Build The Android App

If you want the Android app to use the real Stockfish engine during playout,
build the Stockfish assets once first:

```sh
make android-stockfish-build
```

This downloads the pinned official Stockfish source release, builds the
emulator and arm64 Android engine binaries, downloads the matching NNUE files,
and stages everything under `android/stockfish/`. Those generated files are not
committed to git.

Build the debug Android app:

```sh
make android-build
```

Expected result: the command ends with:

```text
BUILD SUCCESSFUL
```

The APK file will be here:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Run Android Tests

Run the Android unit-test target:

```sh
make android-test
```

Expected result: the command ends with:

```text
BUILD SUCCESSFUL
```

At the moment, most important validation is in `make core-test` and `make core-android-build`, because the Android app shell is still thin and the shared Swift core is where chess behavior will live.

## Normal Daily Check

After changing Android or shared Swift code, run:

```sh
make core-test
make core-android-build
make android-build
make android-test
```

If all four commands pass, the Android side is in a good basic state.

## Installing On A Connected Android Device

Connect an Android phone with USB debugging enabled, then check that the laptop can see it:

```sh
android/.android-sdk/platform-tools/adb devices
```

If a device is listed, install the debug APK:

```sh
android/.android-sdk/platform-tools/adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Then open `Chess Openings` on the phone.

## Running On An Emulator

The Android emulator lets you run the app without a physical Android phone.

First check that hardware acceleration is available:

```sh
kvm-ok
```

Expected result: it should say that KVM acceleration can be used. If it says permission is denied, log out and back in, then try again.

Install an emulator system image:

```sh
android/.android-sdk/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;google_apis;x86_64"
```

Create an emulator named `ChessOpeningsPhone`:

```sh
android/.android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
  --name ChessOpeningsPhone \
  --package "system-images;android-36;google_apis;x86_64" \
  --device pixel_7
```

If it asks whether to create a custom hardware profile, answer:

```text
no
```

Start the emulator:

```sh
android/.android-sdk/emulator/emulator -avd ChessOpeningsPhone
```

Leave that terminal open. The emulator may take a minute or two to boot.

Open a second terminal in the project folder:

```sh
cd /home/demerphq/git_tree/chess/chess-openings
```

Build the APK:

```sh
make android-build
```

Check that the emulator is visible:

```sh
android/.android-sdk/platform-tools/adb devices
```

Install the app:

```sh
android/.android-sdk/platform-tools/adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Then open `Chess Openings` in the emulator.

To stop the emulator, close the emulator window.

## Common Problems

If `adb devices` shows `unauthorized`, unlock the phone and accept the USB debugging prompt.

If `adb devices` shows `emulator-5554 offline`, wait another minute and try again.

If `avdmanager` says the package is missing, rerun the `sdkmanager "system-images;android-36;google_apis;x86_64"` command.

If the emulator is very slow or will not start, run `kvm-ok` again. The emulator needs KVM acceleration to be usable on Linux.

If `make android-build` says Gradle cannot load a native library, run the command again from a normal terminal. The sandboxed terminal used by some tools can block Gradle native helpers.

If a command cannot resolve a host such as `github.com` or `dl.google.com`, the laptop needs network access. Retry when online.

If setup says permissions changed for `kvm` or `libvirt`, log out and back in before using the Android emulator.

If you are unsure whether setup completed, rerun:

```sh
make android-bootstrap
make swift-android-bootstrap
```
