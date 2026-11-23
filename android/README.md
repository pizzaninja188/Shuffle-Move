# Shuffle Move for Android

An Android port of Shuffle Move. A floating bubble sits over Pokémon Shuffle. Tap it and the app
captures the screen, recognizes the 6×6 board, runs Shuffle Move's simulator on the phone, and
draws the best move as an arrow over the game.

## How it fits together

| Module | What it is |
|---|---|
| `:core` (`core/`) | Shuffle Move's simulator and config parsing, compiled from the shared `src/` tree without AWT/Swing. `shuffle.fwk.headless.ShuffleEngine` is the entry point. |
| `:desktop` (`desktop/`) | The original Swing app, built from the rest of `src/`. |
| `:android:vision` | Board recognition in plain Kotlin (JVM-testable): grid geometry, cell features, nearest-neighbour classifier, frozen detection, reference store. |
| `:android:app` | The app: overlay service (MediaProjection capture, bubble, arrow), setup UI, practice board, capture review and teaching, calibration. |

Recognition compares each cell, after downscaling and normalizing, against Shuffle Move's own icons
and against crops you have confirmed from your own screenshots ("teaching"). Taught crops match the
game's rendering exactly, so accuracy improves quickly once you correct a few cells. Cells it is
unsure about are outlined in blue in the overlay.

## Build

Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`).

```
./gradlew :android:app:assembleDebug      # APK in android/app/build/outputs/apk/debug/
./gradlew check :android:vision:test      # core, desktop (incl. engine-vs-desktop parity) and vision tests
./gradlew :desktop:run                    # the original desktop app
```

## First use

1. **Change stage & team**: pick the stage, your 4 Pokémon (plus any stage disruptions you want
   recognized), levels, skill levels and mega slot. The team is saved per stage, like the desktop app.
2. **Start overlay**: allow "display over other apps", then allow screen recording. Choosing
   *A single app → Pokémon Shuffle* is enough and keeps other apps private.
3. In a stage, **long-press the bubble**, then **Calibrate position**: fit the grid over the board once
   for your screen size.
4. On the review screen, tap any wrong cells to correct them, or use **Confirm all** if everything is
   right. This teaches the recognizer.
5. From then on, **tap the bubble** on each turn. Tap again to dismiss the arrow.

Settings: mobile vs 3DS data, skyfall samples per move (accuracy versus time), time limit, moves left
and mega progress.

## License

GPL-3.0, same as Shuffle Move. Pokémon art belongs to Nintendo / The Pokémon Company.
