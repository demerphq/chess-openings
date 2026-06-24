# App Parity

This document tracks feature parity between the Apple app and the Android app.
It is scoped to user-visible app behavior, not implementation details that are
expected to differ by platform.

Last audited against both implementations: 24 June 2026.
An additional independent iPhone-to-Android audit was completed the same day.

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

- Incremental turn rendering and feedback:
  - The iPhone applies and renders the user's move immediately, waits before a
    scripted drill reply, and renders the reply as a separate move.
  - Android currently submits a complete drill or Stockfish turn through one
    blocking bridge call and updates the board only after the reply is ready.
    This skips the intermediate user-move position and its piece animation.
  - Android also emits only one move sound for the combined turn, while iPhone
    emits separate user and opponent sounds. Show-line playback on Android does
    not currently emit its per-move sounds either.
- Drill continuity while navigating:
  - Opening settings from an iPhone drill presents a sheet over the active
    session, preserving the board and controls underneath.
  - Android leaves the drill and switches to the settings tab. Returning to
    training does not restore that in-memory session during the same app run.
  - Android also clears the active snapshot when the drill back action is used,
    whereas the iPhone retains its resumable drill snapshot.
- Complete playout thinking presentation:
  - The iPhone shows a pulsing brain indicator while Stockfish is working,
    alongside its textual thinking status.
  - Android currently shows only text and keeps the pre-reply board unchanged
    until the combined bridge call completes.
- Piece-based promotion chooser:
  - The iPhone promotion picker displays the correctly colored queen, rook,
    bishop, and knight artwork.
  - Android currently presents a text-only list of piece names.
- Catalogue ordering:
  - The iPhone train and library opening lists are sorted alphabetically.
  - Android preserves seed and custom insertion order rather than applying the
    same name ordering.
- Tap-to-reselect board interaction:
  - After selecting a piece, tapping another friendly piece on iPhone changes
    the selection to that piece when the attempted move is illegal.
  - Android submits the attempted source-to-source move, reports an error, and
    clears the selection instead of reselecting the second piece.

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
