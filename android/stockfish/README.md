# Android Stockfish Assets

Generated Stockfish binaries and NNUE files are staged here by:

```sh
make android-stockfish-build
```

The generated files are intentionally ignored by git. Gradle packages them when
present:

- `android/stockfish/x86_64/stockfish`
- `android/stockfish/arm64-v8a/stockfish`
- `android/stockfish/nnue/*.nnue`

Stockfish is GPL-3.0-or-later. The build script downloads the pinned official
Stockfish source release and stages `LICENSE-stockfish.txt` here.
