# App Parity

This document tracks feature parity between the Apple app and the Android app.
It is scoped to user-visible app behavior, not implementation details that are
expected to differ by platform.

Last audited against both implementations: 24 June 2026, after the latest main-branch merge.

## General Parity Issues We Cannot Or Will Not Change

- The Apple app is SwiftUI + SwiftData. The Android app is Kotlin + Jetpack
  Compose, with shared Swift chess/session logic bridged through JNI where
  practical.
- The Apple app is built and installed through Xcode tooling. The Android app is
  built and installed through Gradle, the Android SDK, and the local emulator or
  an Android device.
- Native platform controls should remain native where appropriate. Exact visual
  parity is less important than matching workflows, labels, chess behavior, and
  information architecture.
- Persistence will not be byte-for-byte identical. Apple uses SwiftData models;
  Android currently uses app-local preferences for settings, progress, custom
  openings, and active drill/playout snapshots.
- Platform-specific packaging and engine asset handling will differ. Stockfish,
  NNUE files, app icons, permissions, and install flows are expected to use each
  platform's conventions.

## Missing From Android

- None currently tracked after the two audits completed on 24 June 2026.

## Missing From iPhone

- Main settings tab:
  - Android exposes settings as a persistent bottom-navigation tab.
  - The iPhone app currently exposes settings from the drill toolbar instead of
    the root tab bar.
- Unified drill hint control:
  - Android uses a single multi-state control: `show hint` -> `show solution` ->
    `hide solution`.
  - The iPhone app still has separate hint and solution controls.
- Android-style drill prompt wording:
  - Android hides the next SAN in normal drill mode and prompts the user to
    select a move.
  - The iPhone app still says `your move` and does not match that drill-mode
    wording.
- Animated solution arrows:
  - Android animates move arrows from short to full length, then holds at full
    length with increasingly long pauses.
  - The iPhone app currently renders a static arrow.
- Knight move arrow routing:
  - Android routes knight arrows as a diagonal-then-orthogonal bent arrow.
  - The iPhone app currently renders arrows as a straight shape.
- Android board highlight color updates:
  - Android uses blue for active move hints and solution squares.
  - Android uses a lighter yellow for last-move highlights.
  - Android adds a subtle border around highlighted cells so adjacent highlighted
    squares keep a visible boundary.
- Engine playout promotion handling:
  - The shared engine path used by Android accepts five-character promotion UCI
    moves such as `e7e8q`.
  - The iPhone-specific engine playout parser currently rejects those moves and
    documents promotion handling as deferred work.
