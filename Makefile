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
SWIFT_BIN ?= $(HOME)/.local/share/swift-toolchains/swift-6.3.2-RELEASE-ubuntu24.04/usr/bin/swift
CORE_DIR ?= Packages/ChessOpeningsCore

.PHONY: build test test-all clean android-bootstrap android-build android-test android-clean swift-android-bootstrap core-test core-android-build

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

swift-android-bootstrap:
	./Scripts/Android/bootstrap-swift-android.sh

core-test:
	$(SWIFT_BIN) test --package-path "$(CORE_DIR)"

core-android-build:
	$(SWIFT_BIN) build --package-path "$(CORE_DIR)" --swift-sdk x86_64-unknown-linux-android28 --static-swift-stdlib
	$(SWIFT_BIN) build --package-path "$(CORE_DIR)" --swift-sdk aarch64-unknown-linux-android28 --static-swift-stdlib
