# App Parity

This document tracks feature parity between the Apple app and the Android app.
It is scoped to user-visible app behavior, not implementation details that are
expected to differ by platform.

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
  Android currently uses app-local preferences for settings, progress, and active
  drill/playout snapshots.
- Platform-specific packaging and engine asset handling will differ. Stockfish,
  NNUE files, app icons, permissions, and install flows are expected to use each
  platform's conventions.

## Missing From Android

- Custom library authoring:
  - Create a new custom opening.
  - Delete custom openings.
  - Add a custom SAN line to a custom opening.
  - Validate custom SAN input and show parse/illegal-move errors.
- Playout best-move hints:
  - Ask Stockfish for the best move while in engine playout.
  - Show a playout hint source square.
  - Show a playout best-move arrow/solution.
- Board interaction polish:
  - Drag and drop pieces.
  - Show legal target highlights for the selected piece.
  - Show capture target indicators.
  - Animate pieces moving between squares.
- Drill completion polish:
  - Show the richer completion banner used by Apple.
  - Show the speedy run badge.
  - Fire confetti when a line first becomes learned.
  - Provide the inline "play it out" completion action.
- Move list presentation:
  - Use a scrollable flow layout for longer move lists.
  - Visually distinguish the original drill line from the engine playout
    continuation.

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
  - The iPhone app should be checked for equivalent wording and leakage.
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
- Android emulator/developer workflow documentation:
  - Android has explicit Linux emulator build/run documentation.
  - Apple developer workflow documentation should stay comparable if Apple-side
    onboarding becomes a goal.
