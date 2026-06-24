# App Parity

This document tracks feature parity between the Apple app and the Android app.
It is scoped to user-visible app behavior, not implementation details that are
expected to differ by platform.

Last audited against both implementations: 24 June 2026.

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

- Last-move highlighting during Stockfish playout:
  - Android deliberately suppresses the yellow source and destination
    highlights once playout starts.
  - The iPhone app continues to highlight the latest move throughout playout.
- Stable identity and cleanup for custom-opening progress:
  - Android derives a line's progress key from its visible content instead of
    its opening and line IDs. Identical custom lines can therefore share
    progress and active-session state.
  - Deleting a custom opening on Android removes its definition but leaves its
    stored progress behind, allowing that progress to reappear if equivalent
    content is created later. SwiftData IDs and cascade deletion keep these
    records independent on iPhone.
- Context-sensitive chess sounds:
  - Android currently uses generic generated tones for move, error, and
    completion feedback.
  - The iPhone app has separate sounds for the user's move, the opponent's move,
    captures, checks, castling, promotion, errors, and completion events.
- Pulsing source-square hints:
  - Android's first hint state uses a static blue source-square highlight.
  - The iPhone app pulses the source square to draw attention to the piece that
    should move.
- Explicit playout move counter:
  - The iPhone move list includes a separate `move N` counter during playout.
  - Android numbers moves in the move list but has no equivalent compact
    current-move counter.
- Playout analysis precomputation:
  - The iPhone app starts full-strength best-move analysis while the user is
    thinking and reuses it when grading the submitted move.
  - Android starts move-quality analysis only after submission, increasing the
    wait before the engine reply on slower devices.

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
- Engine playout promotion handling:
  - The shared engine path used by Android accepts five-character promotion UCI
    moves such as `e7e8q`.
  - The iPhone-specific engine playout parser currently rejects those moves and
    documents promotion handling as deferred work.
