# chess openings — dev makefile
#
# wraps the usual xcodebuild invocations so they're short to type and
# easy to read. override PROJECT, SCHEME, or DESTINATION on the command
# line if you need a different target (e.g. a different simulator).

PROJECT     ?= Chess Openings.xcodeproj
SCHEME      ?= Chess Openings
DESTINATION ?= platform=iOS Simulator,name=iPhone 16 Pro

XCB = taskpolicy -d throttle nice -n20 xcodebuild -project "$(PROJECT)" -scheme "$(SCHEME)" -destination "$(DESTINATION)"

ANDROID_DIR ?= android
ANDROID_GRADLE ?= $(ANDROID_DIR)/.gradle-local/gradle-9.6.0/bin/gradle
ANDROID_ENV = ANDROID_HOME="$(CURDIR)/$(ANDROID_DIR)/.android-sdk" ANDROID_SDK_ROOT="$(CURDIR)/$(ANDROID_DIR)/.android-sdk"
ANDROID_ADB = $(ANDROID_DIR)/.android-sdk/platform-tools/adb
ANDROID_AVD ?= ChessOpeningsPhone
ANDROID_DEVICE ?= pixel_7
ANDROID_EMULATOR = $(ANDROID_DIR)/.android-sdk/emulator/emulator
ANDROID_SDKMANAGER = $(ANDROID_DIR)/.android-sdk/cmdline-tools/latest/bin/sdkmanager
ANDROID_AVDMANAGER = $(ANDROID_DIR)/.android-sdk/cmdline-tools/latest/bin/avdmanager
ANDROID_SYSTEM_IMAGE ?= system-images;android-36;google_apis;x86_64
ANDROID_APK ?= $(ANDROID_DIR)/app/build/outputs/apk/debug/app-debug.apk
SWIFT_BIN ?= $(HOME)/.local/share/swift-toolchains/swift-6.3.2-RELEASE-ubuntu24.04/usr/bin/swift
CORE_DIR ?= Packages/ChessOpeningsCore

.PHONY: build test test-all clean android-bootstrap android-build android-test android-clean android-emulator-image android-emulator-create android-emulator-launch android-emulator-wait android-install android-run android-emulator-run swift-android-bootstrap core-test core-android-build

# build only (no tests)
build:
	$(XCB) build

# run every test in the scheme
test-all:
	$(XCB) test

# run a subset of tests. usage:
#   make test T="Chess OpeningsTests/ChessCoreTests"
#   make test T="Chess OpeningsTests/ChessCoreTests/test_side_opposite"
# you can pass multiple by repeating the flag manually with ONLY, e.g.
#   make test ONLY='-only-testing:"A" -only-testing:"B"'
ONLY ?= $(if $(T),-only-testing:"$(T)",)
test:
	$(XCB) test $(ONLY)

clean:
	$(XCB) clean

android-bootstrap:
	./Scripts/Android/bootstrap-android.sh

android-build:
	$(ANDROID_ENV) $(ANDROID_GRADLE) -p "$(ANDROID_DIR)" assembleDebug

android-test:
	$(ANDROID_ENV) $(ANDROID_GRADLE) -p "$(ANDROID_DIR)" testDebugUnitTest

android-clean:
	$(ANDROID_ENV) $(ANDROID_GRADLE) -p "$(ANDROID_DIR)" clean

android-emulator-image:
	$(ANDROID_ENV) $(ANDROID_SDKMANAGER) "$(ANDROID_SYSTEM_IMAGE)"

android-emulator-create:
	printf 'no\n' | $(ANDROID_ENV) $(ANDROID_AVDMANAGER) create avd --name "$(ANDROID_AVD)" --package "$(ANDROID_SYSTEM_IMAGE)" --device "$(ANDROID_DEVICE)" --force

# Keep this attached to the terminal. In this environment detached emulator
# launches can exit early before the GUI is ready.
android-emulator-launch:
	$(ANDROID_ENV) $(ANDROID_EMULATOR) -avd "$(ANDROID_AVD)" -gpu swiftshader_indirect -no-snapshot-load

android-emulator-wait:
	$(ANDROID_ENV) $(ANDROID_ADB) wait-for-device
	@for i in $$(seq 1 60); do \
		state=$$($(ANDROID_ENV) $(ANDROID_ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r'); \
		if [ "$$state" = "1" ]; then echo "emulator booted"; exit 0; fi; \
		sleep 3; \
	done; \
	echo "timed out waiting for emulator boot"; exit 1

android-install: android-build
	$(ANDROID_ENV) $(ANDROID_ADB) install -r "$(ANDROID_APK)"

android-run:
	$(ANDROID_ENV) $(ANDROID_ADB) shell monkey -p com.chessopenings.app -c android.intent.category.LAUNCHER 1

android-emulator-run: android-emulator-wait android-install android-run

swift-android-bootstrap:
	./Scripts/Android/bootstrap-swift-android.sh

core-test:
	$(SWIFT_BIN) test --package-path "$(CORE_DIR)"

core-android-build:
	$(SWIFT_BIN) build --package-path "$(CORE_DIR)" --swift-sdk x86_64-unknown-linux-android28 --static-swift-stdlib
	$(SWIFT_BIN) build --package-path "$(CORE_DIR)" --swift-sdk aarch64-unknown-linux-android28 --static-swift-stdlib
