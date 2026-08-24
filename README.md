# Velox

Arabic-first Android media player. Dark glass UI, Kotlin + Jetpack Compose,
Media3/ExoPlayer for playback. See `PROGRESS.md` for exactly what's implemented
vs. placeholder — this README is build/run instructions and a map of the project.

## Requirements

- Android Studio (a recent stable channel release — this project targets
  compileSdk 36 / AGP 9.3.1, so use whatever Android Studio version currently
  bundles or supports that AGP line)
- JDK 17
- An Android device or emulator running **API 26+**

## Build & run

1. Open the project root in Android Studio and let it sync. First sync will
   download the Gradle wrapper distribution, Android Gradle Plugin, and all
   dependencies — this project was written without network access to Maven
   Central / Google's Maven, so **this sync is the first time any of this has
   actually been compiled**. See `PROGRESS.md`'s "What hasn't been verified"
   section before assuming it'll be clean on the first try.
2. Run the `app` configuration on a device/emulator.
3. Grant the media permission when the Library screen prompts for it — Velox
   reads the on-device MediaStore, nothing is bundled as sample content.
4. First launch scans your device's audio/video via MediaStore automatically
   once permission is granted.

If `gradlew`/`gradlew.bat` are missing: Android Studio regenerates the wrapper
automatically on open, or run `gradle wrapper --gradle-version 9.5.1` once you
have a local Gradle install.

## Project map

```
velox/
├── app/                    composition root: MainActivity, NavHost, Onboarding, DI wiring
├── core/
│   ├── common/              dispatcher/scope qualifiers, ScreenState, duration formatting
│   ├── ui/                  VeloxTheme, design tokens, GlassCard/buttons/empty-states
│   ├── domain/               plain Kotlin: models, repository + PlayerController + AudioEffectsController interfaces, use cases
│   └── data/                 Room, MediaStore scanner, DataStore (onboarding/settings/EQ), repository impls
├── feature/
│   ├── library/               Library (5 tabs, sort) + Search
│   ├── player/                Mini Player, Now Playing (+queue & sleep-timer sheets), Video Player (gestures, PiP, tracks, subtitles)
│   ├── playlists/              Playlists list (create/import) + detail (play, add/remove, M3U export)
│   ├── settings/                Full Settings (appearance, playback, subtitles, language, storage, about)
│   ├── equalizer/                10-band EQ with presets + bass boost/virtualizer
│   └── subtitles/                 scaffolded, empty — no Phase 0/1 work yet
└── player/
    ├── engine/                    ExoPlayer factory, audio effects, domain↔Media3 mapping, video surface
    └── service/                    MediaSessionService + the MediaController-backed PlayerController
```

Dependency direction is strictly `app → feature → core`, with `player:engine` /
`player:service` sitting alongside `core` as the playback foundation everything
else observes through `core.domain.player.PlayerController` — nothing outside
those two player modules imports `androidx.media3.*` directly.

## Source docs

Built from the doc set in `velox-docs.zip` (`README.md`, `ARCHITECTURE.md`,
`TECHNICAL_PLAN.md`, `DESIGN_SYSTEM.md`, `BRANDING.md`, `FEATURES.md`,
`ROADMAP.md`, `LOCALIZATION.md`, `SCREENS_OVERVIEW.md`, and the per-screen
`SCREEN_*.md` files) plus the Stitch UI reference in `velox-UI.zip` (layout/
composition reference only — see `PROGRESS.md` item 1 on why its color palette
wasn't used). Where the docs conflict or leave something unspecified, the
decision made and its reasoning are logged in `PROGRESS.md`.
